package com.opendroid.ai.core.agent

import android.content.Context
import com.opendroid.ai.actions.ActionDispatcher
import com.opendroid.ai.actions.base.ActionResult
import com.opendroid.ai.core.llm.LLMProviderFactory
import com.opendroid.ai.core.llm.LLMRequest
import com.opendroid.ai.core.llm.ResponseFormat
import com.opendroid.ai.core.llm.prompts.PlanningPrompts
import com.opendroid.ai.core.memory.MemoryManager
import com.opendroid.ai.data.models.ChatMessage
import com.opendroid.ai.data.models.Plan
import com.opendroid.ai.data.models.PlanStatus
import com.opendroid.ai.data.models.PlanStep
import com.opendroid.ai.data.models.StepStatus
import com.opendroid.ai.data.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_NEEDS_INPUT_PROMPTS = 5
private const val LONG_REPLY_MAX_TOKENS = 50_000
private val CONTACT_NUMBER_PROMPT_ACTIONS = setOf("MAKE_CALL", "SEND_SMS", "SEND_WHATSAPP")

internal fun paramKeyForNeedsInput(needsInput: ActionResult.NeedsInput, actionName: String): String {
    needsInput.metadata["param"]?.let { return it }

    val asksForNumber = needsInput.question.contains("number", ignoreCase = true) ||
            needsInput.question.contains("phone", ignoreCase = true)
    return if (actionName.uppercase() in CONTACT_NUMBER_PROMPT_ACTIONS && asksForNumber) {
        "contact"
    } else {
        "value"
    }
}

sealed interface AgentState {
    object Idle : AgentState
    object Listening : AgentState
    object Thinking : AgentState
    data class PlanProposed(val plan: Plan) : AgentState
    data class ExecutingPlan(val currentStepDesc: String) : AgentState
    data class Speaking(val text: String) : AgentState
    data class Error(val message: String) : AgentState
}

@Singleton
class AgentLoop @Inject constructor(
    private val intentClassifier: IntentClassifier,
    private val llmProviderFactory: LLMProviderFactory,
    private val planManager: PlanManager,
    private val actionDispatcher: ActionDispatcher,
    private val memoryManager: MemoryManager,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: com.opendroid.ai.data.repository.SettingsRepository,
    private val reEvalEngine: dagger.Lazy<ReEvaluationEngine>
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _userInputFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    var isWaitingForUserInput: Boolean = false
        private set

    suspend fun awaitUserResponse(): String {
        isWaitingForUserInput = true
        try {
            return _userInputFlow.first()
        } finally {
            isWaitingForUserInput = false
        }
    }

    fun setAgentState(state: AgentState) {
        _agentState.value = state
    }

    // Speak callback to be implemented by TTS service
    var onSpeakCallback: ((String) -> Unit)? = null

    fun processQuery(query: String, context: Context) {
        scope.launch {
            try {
                // Capture screenshot of the active screen if accessibility service is active
                val screenshotBase64 = com.opendroid.ai.accessibility.OpenDroidAccessibilityService.getInstance()?.takeScreenshotAndEncode()

                // Save user message
                val userMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = query,
                    sender = ChatMessage.Sender.USER,
                    modelBadge = null,
                    imageBase64 = screenshotBase64
                )
                memoryManager.storeMessage(userMsg)
                conversationRepository.insertMessage(userMsg)

                if (isWaitingForUserInput) {
                    _userInputFlow.emit(query)
                    return@launch
                }

                _agentState.value = AgentState.Thinking

                // 0. Check if this is a complex, multi-step query
                //    If so, skip ALL shortcuts and let the LLM planner handle it properly
                val complexity = intentClassifier.classifyComplexity(query)
                val isMultiStep = complexity != QueryComplexity.SIMPLE

                // 1. Alias resolution — bypass LLM for simple, single-action commands ONLY
                if (!isMultiStep) {
                    val alias = AliasResolver.resolve(query)
                    if (alias != null) {
                        executeAliasDirect(alias, query, context)
                        return@launch
                    }

                    // 1b. Alarm shortcut — bypass LLM for simple alarm requests ONLY
                    if (AliasResolver.isAlarmRequest(query)) {
                        val timeStr = AliasResolver.extractAlarmTime(query)
                        if (timeStr != null) {
                            val alarmHint = AliasResolver.ActionHint(
                                "SET_ALARM",
                                mapOf("time" to timeStr, "label" to "Alarm")
                            )
                            executeAliasDirect(alarmHint, query, context)
                            return@launch
                        }
                    }
                }

                // 2. Intent Classification
                val requiresAction = intentClassifier.requiresAction(query)
                if (requiresAction) {
                    generatePlan(userMsg, context)
                } else {
                    executeSimpleQuery(userMsg)
                }
            } catch (e: Exception) {
                _agentState.value = AgentState.Error(e.localizedMessage ?: "Unknown processing error")
            }
        }
    }

    /**
     * Execute an alias-resolved command directly, bypassing the LLM.
     * Builds a single-step Plan and runs it through the normal plan execution pipeline.
     */
    private suspend fun executeAliasDirect(
        alias: AliasResolver.ActionHint,
        originalQuery: String,
        context: Context
    ) {
        try {
            val speechText = humanizePreSpeech(alias.action)
            onSpeakCallback?.invoke(speechText)

            // Save agent response
            val replyMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = speechText,
                sender = ChatMessage.Sender.AGENT,
                modelBadge = "alias"
            )
            conversationRepository.insertMessage(replyMsg)
            memoryManager.storeMessage(replyMsg)

            // Build a single-step plan from the alias
            val plan = Plan(
                planId = UUID.randomUUID().toString(),
                goal = originalQuery,
                estimatedDuration = "instant",
                estimatedSteps = 1,
                steps = listOf(
                    PlanStep(
                        stepId = "s1",
                        order = 1,
                        description = "Execute ${alias.action}",
                        action = alias.action,
                        params = alias.baseParams,
                        fallback = ""
                    )
                )
            )

            planManager.startNewPlan(plan, context)
            executePlanLoop(plan, context)
        } catch (e: Exception) {
            _agentState.value = AgentState.Error("快捷指令执行失败：${e.localizedMessage}")
        }
    }

    private suspend fun executeSimpleQuery(userMsg: ChatMessage) {
        try {
            val provider = llmProviderFactory.getActiveProvider()
            val relevantContext = memoryManager.getRelevantContext(userMsg.text)
            
            val systemPrompt = """
                重要语言规则：你是“龙虾”，默认必须使用简体中文回复。除非用户明确要求英文，否则不要输出英文。失败、权限、下一步建议也必须用中文。
                重要执行规则：用户要求控制手机时，优先执行或说明需要开启的权限，不要只解释。

                You are OpenDroid, a friendly and helpful Android AI assistant.
                Talk like a real person — warm, casual, and natural. Avoid sounding robotic.
                Keep your answers short and to the point, but feel free to be friendly.
                
                You can control this Android device: open apps, set alarms, toggle WiFi/Bluetooth/flashlight, send messages, make calls, and more. If someone asks you to do something, just do it or let them know you can help.
                If the user asks for a long answer, itinerary, plan, guide, list, or multi-day schedule, finish the full response. Do not stop midway.
                
                Never dump raw error messages or technical details. If something goes wrong, say it simply and suggest what to do next.
                
                Context about user and device state:
                $relevantContext
            """.trimIndent()

            val lastMsgs = conversationRepository.getLastMessages(10).map { msg ->
                if (msg.id == userMsg.id) {
                    msg.copy(imageBase64 = userMsg.imageBase64)
                } else {
                    msg
                }
            }

            val replyId = UUID.randomUUID().toString()
            var currentReplyText = ""
            val replyMsg = ChatMessage(
                id = replyId,
                text = currentReplyText,
                sender = ChatMessage.Sender.AGENT,
                modelBadge = provider.name
            )
            conversationRepository.insertMessage(replyMsg)

            try {
                provider.streamComplete(
                    LLMRequest(
                        systemPrompt = systemPrompt,
                        messages = lastMsgs,
                        temperature = 0.5f,
                        maxTokens = LONG_REPLY_MAX_TOKENS,
                        responseFormat = ResponseFormat.TEXT
                    )
                ).collect { chunk ->
                    currentReplyText += chunk
                    conversationRepository.insertMessage(replyMsg.copy(text = currentReplyText))
                }
            } catch (streamError: Exception) {
                if (currentReplyText.isEmpty()) {
                    val response = provider.complete(
                        LLMRequest(
                            systemPrompt = systemPrompt,
                            messages = lastMsgs,
                            temperature = 0.5f,
                            maxTokens = LONG_REPLY_MAX_TOKENS,
                            responseFormat = ResponseFormat.TEXT
                        )
                    )
                    currentReplyText = response.content.trim()
                    conversationRepository.insertMessage(replyMsg.copy(text = currentReplyText))
                }
            }

            val finalReplyMsg = replyMsg.copy(text = currentReplyText)
            memoryManager.storeMessage(finalReplyMsg)
            _agentState.value = AgentState.Speaking(currentReplyText)
            onSpeakCallback?.invoke(currentReplyText)
        } catch (e: Exception) {
            _agentState.value = AgentState.Error("回复生成失败：${e.localizedMessage}")
        }
    }

    private suspend fun generatePlan(userMsg: ChatMessage, context: Context) {
        try {
            val provider = llmProviderFactory.getActiveProvider()
            val relevantContext = memoryManager.getRelevantContext(userMsg.text)
            val sysPrompt = "${PlanningPrompts.PLANNING_SYSTEM_PROMPT}\n\nContext about user and device:\n$relevantContext"
            
            val config = settingsRepository.llmConfig.first()
            val plan = if (config.multiAgentModeEnabled) {
                kotlinx.coroutines.coroutineScope {
                    val plannerDeferred = async(Dispatchers.Default) {
                        provider.complete(
                            LLMRequest(
                                systemPrompt = sysPrompt,
                                messages = listOf(userMsg),
                                temperature = 0.2f,
                                maxTokens = 1500,
                                responseFormat = ResponseFormat.JSON
                            )
                        )
                    }

                    val criticDeferred = async(Dispatchers.Default) {
                        provider.complete(
                            LLMRequest(
                                systemPrompt = PlanningPrompts.CRITIC_SYSTEM_PROMPT,
                                messages = listOf(userMsg),
                                temperature = 0.2f,
                                maxTokens = 1000,
                                responseFormat = ResponseFormat.TEXT
                            )
                        )
                    }

                    val plannerResponse = plannerDeferred.await()
                    val criticResponse = criticDeferred.await()

                    val mergePrompt = """
                        ${PlanningPrompts.MERGE_SYSTEM_PROMPT}
                        
                        User Goal: ${userMsg.text}
                        Initial Plan: ${plannerResponse.content}
                        Critic Safety & Edge Case Report: ${criticResponse.content}
                    """.trimIndent()

                    val mergeResponse = provider.complete(
                        LLMRequest(
                            systemPrompt = mergePrompt,
                            messages = listOf(
                                ChatMessage(
                                    id = UUID.randomUUID().toString(),
                                    text = "Merge the plan and critique into the final JSON plan.",
                                    sender = ChatMessage.Sender.USER,
                                    imageBase64 = userMsg.imageBase64
                                )
                            ),
                            temperature = 0.1f,
                            maxTokens = 1500,
                            responseFormat = ResponseFormat.JSON
                        )
                    )

                    val cleaned = cleanPlanJson(mergeResponse.content)
                    json.decodeFromString<Plan>(cleaned)
                }
            } else {
                val response = provider.complete(
                    LLMRequest(
                        systemPrompt = sysPrompt,
                        messages = listOf(userMsg),
                        temperature = 0.1f,
                        maxTokens = 1500,
                        responseFormat = ResponseFormat.JSON
                    )
                )
                val cleaned = cleanPlanJson(response.content)
                json.decodeFromString<Plan>(cleaned)
            }

            planManager.startNewPlan(plan, context)
            if (config.autoConfirmPlans) {
                executePlanLoop(plan, context)
            } else {
                _agentState.value = AgentState.PlanProposed(plan)
            }
        } catch (e: Exception) {
            fallbackOrError(userMsg, e)
        }
    }

    /**
     * Treat malformed plan JSON as an actionable planning failure. Other provider
     * failures can still degrade to a normal chat response.
     */
    private suspend fun fallbackOrError(userMsg: ChatMessage, cause: Throwable) {
        android.util.Log.e("AgentLoop", "Plan generation failed: ${cause.localizedMessage}", cause)
        val isMalformedPlan = cause is kotlinx.serialization.SerializationException ||
                cause is IllegalArgumentException
        if (isMalformedPlan) {
            val msg = "我理解了你的请求，但这次没能生成可靠的执行计划。你可以换个更具体的说法，或者先试一个更简单的命令。"
            val errMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = msg,
                sender = ChatMessage.Sender.AGENT,
                modelBadge = "系统"
            )
            conversationRepository.insertMessage(errMsg)
            memoryManager.storeMessage(errMsg)
            _agentState.value = AgentState.Speaking(msg)
            onSpeakCallback?.invoke(msg)
        } else {
            executeSimpleQuery(userMsg)
        }
    }

    fun approveProposedPlan(context: Context) {
        scope.launch {
            val plan = planManager.currentPlan.value ?: return@launch
            executePlanLoop(plan, context)
        }
    }

    fun rejectProposedPlan() {
        planManager.clearPlan()
        _agentState.value = AgentState.Idle
    }

    private suspend fun executePlanLoop(plan: Plan, context: Context) {
        planManager.updatePlanStatus(PlanStatus.RUNNING)
        var currentPlanState = planManager.currentPlan.value ?: return

        while (true) {
            val nextStep = planManager.getActiveStep()
            if (nextStep == null) {
                // If there are any failed steps, plan is failed. Otherwise, completed!
                val hasFailed = currentPlanState.steps.any { it.status == StepStatus.FAILED }
                if (hasFailed) {
                    planManager.updatePlanStatus(PlanStatus.FAILED)
                    speakAndSaveSummary(currentPlanState, false)
                } else {
                    planManager.updatePlanStatus(PlanStatus.COMPLETED)
                    speakAndSaveSummary(currentPlanState, true)
                }
                break
            }

            _agentState.value = AgentState.ExecutingPlan(nextStep.description)
            planManager.updateStepStatus(nextStep.stepId, StepStatus.RUNNING)

            // Resolve parameters from prior step results
            val resolvedParams = nextStep.params.mapValues { (_, value) ->
                var newValue = value
                currentPlanState.steps.forEach { completedStep ->
                    if (completedStep.status == StepStatus.COMPLETED && completedStep.result != null) {
                        val refKey = "$" + completedStep.stepId
                        if (newValue.contains(refKey)) {
                            newValue = newValue.replace(refKey, completedStep.result!!)
                        }
                        val doubleRefKey = "$$" + completedStep.stepId
                        if (newValue.contains(doubleRefKey)) {
                            newValue = newValue.replace(doubleRefKey, completedStep.result!!)
                        }
                    }
                }
                newValue
            }

            // Execute the action dispatcher
            var actionResult = try {
                var result = actionDispatcher.execute(nextStep.action, resolvedParams, context)
                
                resolveNeedsInput(result, nextStep.action, resolvedParams, context)
            } catch (e: Exception) {
                android.util.Log.e("AgentLoop", "Exception executing action ${nextStep.action}: ${e.localizedMessage}", e)
                ActionResult(false, null, e.localizedMessage ?: "Unknown execution error")
            }

            if (actionResult.success) {
                planManager.updateStepStatus(
                    nextStep.stepId,
                    StepStatus.COMPLETED,
                    result = actionResult.data ?: "Completed successfully."
                )
            } else if (actionResult is ActionResult.UnknownAction) {
                planManager.updateStepStatus(
                    nextStep.stepId,
                    StepStatus.FAILED,
                    error = actionResult.error ?: "Action execution failed."
                )

                // Update current state of plan to include the failed step status
                currentPlanState = planManager.currentPlan.value ?: break

                // Trigger learning extraction
                reEvalEngine.get().extractLearning(nextStep.action, currentPlanState.goal)

                // Trigger silent replanning
                val completed = currentPlanState.steps.filter { it.status == StepStatus.COMPLETED }
                val remaining = currentPlanState.steps.filter { it.status == StepStatus.PENDING }

                val replan = reEvalEngine.get().replanAfterUnknownAction(
                    originalGoal = currentPlanState.goal,
                    failedStep = nextStep,
                    completedSteps = completed,
                    remainingSteps = remaining,
                    planId = currentPlanState.planId
                )

                if (replan.speech.isNotEmpty()) {
                    onSpeakCallback?.invoke(replan.speech)
                }

                when (replan.decision.uppercase()) {
                    "ABANDON" -> {
                        planManager.updatePlanStatus(PlanStatus.FAILED)
                        speakAndSaveSummary(currentPlanState, false)
                        return
                    }
                    "MODIFY" -> {
                        if (replan.updatedPlan != null) {
                            val mergedSteps = currentPlanState.steps.filter { it.status != StepStatus.PENDING } +
                                    replan.updatedPlan.steps.filter { step ->
                                        currentPlanState.steps.none { it.stepId == step.stepId }
                                    }
                            planManager.startNewPlan(currentPlanState.copy(steps = mergedSteps), context)
                        }
                    }
                    else -> {
                        planManager.updatePlanStatus(PlanStatus.FAILED)
                        speakAndSaveSummary(currentPlanState, false)
                        return
                    }
                }

                // Refresh current state of plan and continue
                currentPlanState = planManager.currentPlan.value ?: break
                continue
            } else {
                // Try fallback action
                if (nextStep.fallback.isNotEmpty() && actionDispatcher.hasAction(nextStep.fallback)) {
                    val fallbackResult = try {
                        actionDispatcher.execute(nextStep.fallback, resolvedParams, context)
                    } catch (e: Exception) {
                        ActionResult(false, null, e.localizedMessage ?: "Unknown execution error")
                    }
                    if (fallbackResult.success) {
                        planManager.updateStepStatus(
                            nextStep.stepId,
                            StepStatus.COMPLETED,
                            result = "Primary failed: ${actionResult.error}. Fallback execution succeeded: ${fallbackResult.data}"
                        )
                    } else {
                        planManager.updateStepStatus(
                            nextStep.stepId,
                            StepStatus.FAILED,
                            error = "Primary failed: ${actionResult.error}. Fallback failed: ${fallbackResult.error}"
                        )
                    }
                } else {
                    planManager.updateStepStatus(
                        nextStep.stepId,
                        StepStatus.FAILED,
                        error = actionResult.error ?: "Action execution failed."
                    )
                }
            }

            // Refresh current state of plan
            currentPlanState = planManager.currentPlan.value ?: break

            // Re-evaluate Plan Loop
            val completed = currentPlanState.steps.filter { it.status == StepStatus.COMPLETED }
            val failed = currentPlanState.steps.filter { it.status == StepStatus.FAILED }
            val remaining = currentPlanState.steps.filter { it.status == StepStatus.PENDING }

            if (failed.isEmpty() && remaining.isEmpty()) {
                continue
            }

            val reEval = reEvalEngine.get().evaluateStepResult(
                originalGoal = currentPlanState.goal,
                completedSteps = completed,
                failedSteps = failed,
                remainingSteps = remaining,
                planId = currentPlanState.planId
            )

            // Speak post-step evaluation speech if any
            if (reEval.speech.isNotEmpty()) {
                onSpeakCallback?.invoke(reEval.speech)
            }

            when (reEval.decision.uppercase()) {
                "ABANDON" -> {
                    planManager.updatePlanStatus(PlanStatus.FAILED)
                    speakAndSaveSummary(currentPlanState, false)
                    return
                }
                "MODIFY" -> {
                    if (reEval.updatedPlan != null) {
                        val mergedSteps = currentPlanState.steps.filter { it.status != StepStatus.PENDING } +
                                reEval.updatedPlan.steps.filter { step ->
                                    currentPlanState.steps.none { it.stepId == step.stepId }
                                }
                        planManager.startNewPlan(currentPlanState.copy(steps = mergedSteps), context)
                    }
                }
                "CONTINUE" -> {
                    // Do nothing, continue to next step
                }
            }
        }
    }

    private data class NeedsInputRetry(
        val result: ActionResult,
        val params: Map<String, String>
    )

    private suspend fun resolveNeedsInput(
        initialResult: ActionResult,
        actionName: String,
        initialParams: Map<String, String>,
        context: Context
    ): ActionResult {
        var result = initialResult
        var params = initialParams

        repeat(MAX_NEEDS_INPUT_PROMPTS) {
            val needsInput = result as? ActionResult.NeedsInput ?: return result
            val retry = if (needsInput.metadata["type"] == "contact_picker") {
                handleContactPicker(needsInput, actionName, params, context)
            } else {
                handleNeedsInput(needsInput, actionName, params, context)
            }
            result = retry.result
            params = retry.params
        }

        return ActionResult.Failure(
            errorMsg = "Too many input prompts for $actionName",
            fallback = "Try the command again with all required details."
        )
    }

    /**
     * Handle contact disambiguation when an action returns NeedsInput with contact_picker metadata.
     * Shows options to user, waits for selection, stores preference, re-executes action.
     */
    private suspend fun handleContactPicker(
        pickerResult: ActionResult.NeedsInput,
        actionName: String,
        originalParams: Map<String, String>,
        context: Context
    ): NeedsInputRetry {
        val meta = pickerResult.metadata
        val matchesJson = meta["matches"] ?: return NeedsInputRetry(pickerResult, originalParams)
        val query = meta["query"] ?: ""

        // Parse the matches back from JSON
        val matches: List<Map<String, String>> = try {
            Json { ignoreUnknownKeys = true }
                .decodeFromString<List<Map<String, String>>>(matchesJson)
        } catch (e: Exception) {
            android.util.Log.e("AgentLoop", "Failed to parse contact matches: ${e.message}")
            return NeedsInputRetry(pickerResult, originalParams)
        }

        if (matches.isEmpty()) return NeedsInputRetry(pickerResult, originalParams)

        // Show picker question to user via chat
        val optionsText = pickerResult.options.joinToString("\n")
        val pickerMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = "${pickerResult.question}\n\n$optionsText",
            sender = ChatMessage.Sender.AGENT,
            modelBadge = "System",
            contactPickerData = matchesJson
        )
        conversationRepository.insertMessage(pickerMsg)
        onSpeakCallback?.invoke(pickerResult.question)

        // Wait for user response
        val userSelection = awaitUserResponse()

        // Save user's response as a chat message
        val userPickMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = userSelection,
            sender = ChatMessage.Sender.USER
        )
        conversationRepository.insertMessage(userPickMsg)

        // Resolve user selection to a contact
        val selectedContact = when {
            // User typed a number: "1", "2", "3"
            userSelection.trim().toIntOrNull() != null -> {
                val index = userSelection.trim().toInt() - 1
                matches.getOrNull(index)
            }

            // User said "first", "second", "third"
            userSelection.lowercase().contains("first") -> matches.getOrNull(0)
            userSelection.lowercase().contains("second") -> matches.getOrNull(1)
            userSelection.lowercase().contains("third") -> matches.getOrNull(2)
            userSelection.lowercase().contains("fourth") -> matches.getOrNull(3)
            userSelection.lowercase().contains("fifth") -> matches.getOrNull(4)

            // User typed the name
            else -> {
                matches.find { contact ->
                    userSelection.contains(contact["name"] ?: "", ignoreCase = true)
                } ?: matches.find { contact ->
                    (contact["name"] ?: "").contains(userSelection.trim(), ignoreCase = true)
                }
            }
        }

        if (selectedContact == null) {
            // Couldn't match — tell user and fail gracefully
            val failMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "我没识别出你选的是谁，请再输入一次联系人姓名。",
                sender = ChatMessage.Sender.AGENT,
                modelBadge = "系统"
            )
            conversationRepository.insertMessage(failMsg)
            return NeedsInputRetry(
                ActionResult.Failure(
                    errorMsg = "未能识别联系人选择",
                    fallback = "请重新输入联系人姓名"
                ),
                originalParams
            )
        }

        val phone = selectedContact["phone"] ?: return NeedsInputRetry(
            ActionResult.Failure(
                errorMsg = "选中的联系人没有电话号码",
                fallback = "请重新选择"
            ),
            originalParams
        )
        val name = selectedContact["name"] ?: "联系人"

        // Remember this choice for next time
        memoryManager.storeContactPreference(
            query = query,
            contact = Contact(name = name, phoneNumber = phone)
        )

        // Confirm selection to user
        val confirmMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = "好的，使用联系人：$name。",
            sender = ChatMessage.Sender.AGENT,
            modelBadge = "系统"
        )
        conversationRepository.insertMessage(confirmMsg)

        // Re-execute the original action with resolved contact
        val resolvedParams = originalParams.toMutableMap()
        resolvedParams["contact"] = phone
        if (meta.containsKey("message")) {
            resolvedParams["message"] = meta["message"]!!
        }

        return NeedsInputRetry(
            actionDispatcher.execute(actionName, resolvedParams, context),
            resolvedParams
        )
    }

    /**
     * Generic missing-parameter prompt. Shows the question, waits for the user's
     * reply, and re-executes the action with the answer injected.
     */
    private suspend fun handleNeedsInput(
        needsInput: ActionResult.NeedsInput,
        actionName: String,
        originalParams: Map<String, String>,
        context: Context
    ): NeedsInputRetry {
        val optionsText = if (needsInput.options.isNotEmpty()) {
            "\n\n" + needsInput.options.joinToString("\n") { "- $it" }
        } else {
            ""
        }
        val promptMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = needsInput.question + optionsText,
            sender = ChatMessage.Sender.AGENT,
            modelBadge = "System"
        )
        conversationRepository.insertMessage(promptMsg)
        onSpeakCallback?.invoke(needsInput.question)

        val answer = awaitUserResponse().trim()
        if (answer.isEmpty()) {
            return NeedsInputRetry(
                ActionResult.Failure(
                    errorMsg = "No value provided for $actionName",
                    fallback = "Try the command again with the missing detail."
                ),
                originalParams
            )
        }

        val userEcho = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = answer,
            sender = ChatMessage.Sender.USER
        )
        conversationRepository.insertMessage(userEcho)

        val paramKey = paramKeyForNeedsInput(needsInput, actionName)
        val newParams = originalParams.toMutableMap().apply { put(paramKey, answer) }
        return NeedsInputRetry(
            actionDispatcher.execute(actionName, newParams, context),
            newParams
        )
    }

    private suspend fun speakAndSaveSummary(plan: Plan, isSuccess: Boolean) {
        val summaryText = if (isSuccess) {
            // Build a natural, human-sounding summary from step results
            val stepSummaries = plan.steps
                .filter { it.status == StepStatus.COMPLETED && !it.result.isNullOrBlank() }
                .mapNotNull { step ->
                    val result = step.result ?: return@mapNotNull null
                    when {
                        result.length > 5 && !result.startsWith("{") -> result
                        else -> null
                    }
                }
            if (stepSummaries.isNotEmpty()) {
                stepSummaries.joinToString(". ")
            } else {
                humanizeGoalDone(plan.goal)
            }
        } else {
            // Log the technical errors but DON'T show them to the user
            val failedSteps = plan.steps.filter { it.status == StepStatus.FAILED }
            failedSteps.forEach { step ->
                android.util.Log.e("AgentLoop",
                    "Step '${step.action}' failed: ${step.error ?: "unknown"}")
            }
            
            // Check if any failed step has a user-friendly error message
            // (e.g. "I've opened the chat... please tap send")
            val userFacingError = failedSteps.firstNotNullOfOrNull { step ->
                step.error?.takeIf { error ->
                    // Include errors that contain actionable guidance for the user
                    error.contains("opened", ignoreCase = true) ||
                    error.contains("please", ignoreCase = true) ||
                    error.contains("check", ignoreCase = true) ||
                    error.contains("tap", ignoreCase = true) ||
                    error.contains("couldn't confirm", ignoreCase = true)
                }
            }
            
            userFacingError ?: humanizeFailure(plan.goal)
        }

        val assistantMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = summaryText,
            sender = ChatMessage.Sender.AGENT,
            modelBadge = "System"
        )
        memoryManager.storeMessage(assistantMsg)
        conversationRepository.insertMessage(assistantMsg)

        _agentState.value = AgentState.Speaking(summaryText)
        onSpeakCallback?.invoke(summaryText)
    }

    private fun cleanPlanJson(raw: String): String {
        var content = raw.trim()
        if (content.startsWith("```json")) {
            content = content.removePrefix("```json")
        }
        if (content.endsWith("```")) {
            content = content.removeSuffix("```")
        }
        content = content.trim()
        try {
            val jsonElement = json.parseToJsonElement(content)
            if (jsonElement is JsonObject) {
                if (jsonElement.containsKey("plan")) {
                    val planElement = jsonElement["plan"]
                    if (planElement != null) {
                        return planElement.toString()
                    }
                }
            }
        } catch (e: Exception) {
            // Silently ignore parsing errors here and let downstream deserialization report them if needed
        }
        return content
    }

    /**
     * Convert raw technical error messages into friendly, user-facing text.
     * Prevents raw Java stack traces, permission denial strings, and
     * intent resolution errors from being spoken to the user.
     */
    private fun formatErrorForUser(action: String, rawError: String): String {
        // Log the technical error for debugging
        android.util.Log.e("AgentLoop", "Action $action error: $rawError")

        // Return only a short, friendly message
        return when {
            rawError.contains("Permission", ignoreCase = true) ||
            rawError.contains("SecurityException", ignoreCase = true) ->
                "这个操作需要权限。请在系统设置里允许相关权限后再试。"

            rawError.contains("ActivityNotFound", ignoreCase = true) ->
                "我没找到需要打开的应用，可能还没有安装。"

            rawError.contains("IOException", ignoreCase = true) ->
                "网络连接不太稳定，请检查网络后再试。"

            else ->
                "这次没有执行成功，可以稍后再试一次。"
        }
    }

    /**
     * Generate a natural pre-execution speech line based on the action.
     */
    private fun humanizePreSpeech(action: String): String {
        return when (action) {
            "TOGGLE_FLASHLIGHT" -> "好的，正在切换手电筒。"
            "SET_ALARM" -> "好的，正在帮你设置闹钟。"
            "SET_TIMER" -> "好的，正在启动计时器。"
            "TAKE_SCREENSHOT" -> "正在截图。"
            "LOCK_SCREEN" -> "正在锁屏。"
            "TOGGLE_WIFI" -> "好的，正在切换 WiFi。"
            "TOGGLE_BLUETOOTH" -> "好的，正在切换蓝牙。"
            "TOGGLE_DND" -> "好的，正在调整勿扰模式。"
            "TOGGLE_HOTSPOT" -> "好的，正在切换热点。"
            "TOGGLE_MOBILE_DATA" -> "好的，正在切换移动数据。"
            "SET_VOLUME" -> "好的，正在调节音量。"
            "SET_BRIGHTNESS" -> "好的，正在调节亮度。"
            "OPEN_APP" -> "好的，正在打开。"
            "ANALYZE_SCREENSHOT" -> "我来看一下当前屏幕。"
            "SET_RINGER_MODE" -> "正在调整响铃模式。"
            "PLAY_MUSIC" -> "好的，正在播放。"
            "MAKE_CALL" -> "正在拨打电话。"
            else -> {
                val readable = action.lowercase().replace("_", " ")
                "好的，正在执行 $readable。"
            }
        }
    }

    /**
     * Generate a natural success message when no step result is available.
     */
    private fun humanizeGoalDone(goal: String): String {
        val lower = goal.lowercase()
        return when {
            lower.contains("alarm") || goal.contains("闹钟") -> "已设置好闹钟。"
            lower.contains("flash") || lower.contains("torch") || goal.contains("手电筒") -> "手电筒已处理。"
            lower.contains("wifi") -> "WiFi 已更新。"
            lower.contains("bluetooth") || goal.contains("蓝牙") -> "蓝牙已切换。"
            lower.contains("volume") || goal.contains("音量") -> "音量已调整。"
            lower.contains("brightness") || goal.contains("亮度") -> "亮度已调整。"
            lower.contains("screenshot") || goal.contains("截图") || goal.contains("截屏") -> "截图完成。"
            lower.contains("timer") || goal.contains("计时") -> "计时器已设置。"
            lower.contains("open") || goal.contains("打开") -> "已打开。"
            lower.contains("call") || goal.contains("打电话") -> "正在拨打电话。"
            lower.contains("message") || lower.contains("whatsapp") || goal.contains("消息") -> "消息已处理。"
            else -> "已完成。"
        }
    }

    /**
     * Generate a natural failure message. Technical details go to logs only.
     */
    private fun humanizeFailure(goal: String): String {
        val lower = goal.lowercase()
        return when {
            lower.contains("alarm") || goal.contains("闹钟") -> "抱歉，闹钟没有设置成功。请检查时钟应用权限后再试。"
            lower.contains("flash") || lower.contains("torch") || goal.contains("手电筒") -> "手电筒没有切换成功，可以再试一次。"
            lower.contains("call") || goal.contains("打电话") -> "电话没有拨出去，可以再试一次。"
            lower.contains("message") || lower.contains("whatsapp") || goal.contains("消息") -> "消息没有发送成功，可以再试一次。"
            lower.contains("wifi") || lower.contains("bluetooth") || goal.contains("蓝牙") -> "这个系统开关没有切换成功，可能需要手动开启权限。"
            lower.contains("brightness") || goal.contains("亮度") -> "亮度没有调整成功，可能需要允许修改系统设置权限。"
            else -> "抱歉，这次没有执行成功，可以再试一次。"
        }
    }
}
