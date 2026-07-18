package com.opendroid.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.core.service.OpenDroidService
import com.opendroid.ai.core.voice.SpeechRecognitionEngine
import com.opendroid.ai.data.models.ChatMessage
import com.opendroid.ai.ui.components.ContactPickerCard
import com.opendroid.ai.ui.theme.*
import com.opendroid.ai.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by viewModel.conversationHistory.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    
    val listState = rememberLazyListState()
    var inputQuery by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var transcriptionText by remember { mutableStateOf("") }
    var voiceErrorText by remember { mutableStateOf<String?>(null) }
    var hasRecordAudioPermission by remember { mutableStateOf(isRecordAudioGranted(context)) }
    var voiceStartJob by remember { mutableStateOf<Job?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val speechRecognizer = remember { SpeechRecognitionEngine(context) }

    // Only move to a new message. Long streaming replies remain manually scrollable.
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size - 1)
        }
    }

    fun finishVoiceInput() {
        voiceStartJob?.cancel()
        voiceStartJob = null
        isListening = false
        transcriptionText = ""
        OpenDroidService.resumeAfterUiVoice(context)
    }

    fun beginVoiceRecognition() {
        hasRecordAudioPermission = isRecordAudioGranted(context)
        if (!hasRecordAudioPermission) {
            voiceErrorText = "录音权限未开启，请在系统权限中允许麦克风，或先使用文字输入。"
            return
        }

        voiceStartJob?.cancel()
        isListening = true
        transcriptionText = "正在准备麦克风..."
        voiceErrorText = null
        OpenDroidService.pauseForUiVoice(context)
        voiceStartJob = scope.launch {
            // Let the background wake-word recognizer fully release HyperOS audio input.
            delay(400)
            if (!isRecordAudioGranted(context)) {
                finishVoiceInput()
                voiceErrorText = "录音权限已被系统收回，请重新允许麦克风。"
                return@launch
            }
            transcriptionText = "正在听..."
            speechRecognizer.startListening(
                onResult = { text ->
                    finishVoiceInput()
                    inputQuery = text
                    viewModel.sendMessage(text, context)
                },
                onPartialResult = { partial ->
                    transcriptionText = partial
                },
                onError = { error ->
                    finishVoiceInput()
                    voiceErrorText = friendlyVoiceError(error, isRecordAudioGranted(context))
                }
            )
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordAudioPermission = isGranted || isRecordAudioGranted(context)
        if (hasRecordAudioPermission) {
            voiceErrorText = null
            beginVoiceRecognition()
        } else {
            voiceErrorText = "请先允许录音权限，或直接使用文字输入。"
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasRecordAudioPermission = isRecordAudioGranted(context)
                if (hasRecordAudioPermission && voiceErrorText?.contains("权限") == true) {
                    voiceErrorText = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceStartJob?.cancel()
            speechRecognizer.destroy()
            OpenDroidService.resumeAfterUiVoice(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "龙虾",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            color = AccentNeonGreen,
                            fontSize = 20.sp,
                            letterSpacing = 0.sp
                        )
                        AgentStatusSubtitle(agentState)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.clearChat() }) {
                        Text("清空", color = TextSecondary, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground,
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            ) {
                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                ) {
                    if (history.isEmpty()) {
                        item {
                            UsageHintCard()
                        }
                    }
                    items(history) { msg ->
                        ChatBubble(msg, viewModel, context)
                    }
                    
                    // Show a typing/thinking bubble if thinking
                    if (agentState is AgentState.Thinking) {
                        item {
                            ThinkingBubble()
                        }
                    }
                }

                // If agent proposed a plan, show a modal prompt to approve or reject
                if (agentState is AgentState.PlanProposed) {
                    val proposedPlan = (agentState as AgentState.PlanProposed).plan
                    ProposedPlanPrompt(
                        goal = proposedPlan.goal,
                        stepsCount = proposedPlan.estimatedSteps,
                        onApprove = { viewModel.approvePlan(context) },
                        onReject = { viewModel.rejectPlan() }
                    )
                }
            }

            // Bottom Input Section with Orb overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, DarkBackground),
                            startY = 0f,
                            endY = 50f
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    voiceErrorText?.let { errorText ->
                        Text(
                            text = errorText,
                            color = AccentRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // Floating Orb for Speech
                    FloatingOrb(
                        isListening = isListening,
                        agentState = agentState,
                        onClick = {
                            if (isListening) {
                                if (voiceStartJob?.isActive == true) {
                                    speechRecognizer.cancelListening()
                                    finishVoiceInput()
                                } else {
                                    speechRecognizer.stopListening()
                                }
                            } else {
                                hasRecordAudioPermission = isRecordAudioGranted(context)
                                val audioPerm = if (hasRecordAudioPermission) {
                                    PackageManager.PERMISSION_GRANTED
                                } else {
                                    PackageManager.PERMISSION_DENIED
                                }
                                if (audioPerm == PackageManager.PERMISSION_GRANTED) {
                                    voiceErrorText = null
                                    beginVoiceRecognition()
                                } else {
                                    voiceErrorText = "请先允许录音权限，或直接使用文字输入。"
                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text Input Field / Voice Waveform Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(28.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isListening) {
                            VoiceWaveform(text = transcriptionText)
                        } else {
                            TextField(
                                value = inputQuery,
                                onValueChange = { inputQuery = it },
                                placeholder = { Text("输入你想让手机完成的事...", color = TextSecondary, fontSize = 14.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (inputQuery.isNotBlank()) {
                                        viewModel.sendMessage(inputQuery, context)
                                        inputQuery = ""
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (!isListening && inputQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(inputQuery, context)
                                inputQuery = ""
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AccentNeonGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = DarkBackground
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}

private fun isRecordAudioGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

private fun friendlyVoiceError(error: String, permissionGranted: Boolean): String = when {
    error.contains("not available", ignoreCase = true) ->
        "当前系统没有可用的语音识别服务，请检查系统语音助手设置。"
    error.contains("permission", ignoreCase = true) && permissionGranted ->
        "应用录音权限已开启，但系统语音服务仍无法访问麦克风；请检查控制中心的麦克风总开关和系统语音服务。"
    error.contains("permission", ignoreCase = true) ->
        "录音权限未开启，请在系统权限中允许麦克风。"
    error.contains("network", ignoreCase = true) ->
        "语音识别网络不可用，请检查网络后重试。"
    error.contains("No speech", ignoreCase = true) ||
        error.contains("timeout", ignoreCase = true) ->
        "没有识别到语音，请靠近麦克风后重试。"
    error.contains("disconnected", ignoreCase = true) ||
        error.contains("(11)", ignoreCase = true) ->
        "系统语音识别服务已断开，请稍等一秒后重试。"
    error.contains("busy", ignoreCase = true) ||
        error.contains("too many", ignoreCase = true) ->
        "语音识别服务正忙，请稍等一秒后重试。"
    error.contains("language", ignoreCase = true) ->
        "系统中文语音识别不可用，请在系统语音服务中启用中文。"
    else -> "语音输入失败：$error"
}

@Composable
fun AgentStatusSubtitle(state: AgentState) {
    val text = when (state) {
        is AgentState.Idle -> "已就绪"
        is AgentState.Listening -> "正在听取语音..."
        is AgentState.Thinking -> "正在理解并规划..."
        is AgentState.PlanProposed -> "需要确认计划"
        is AgentState.ExecutingPlan -> "正在执行：${state.currentStepDesc}"
        is AgentState.Speaking -> "回复中：${state.text.take(30)}..."
        is AgentState.Error -> "执行出错"
    }
    
    val color = when (state) {
        is AgentState.Idle -> AccentNeonGreen
        is AgentState.Listening -> AccentRed
        is AgentState.Thinking -> AccentPurple
        is AgentState.PlanProposed -> AccentCyan
        is AgentState.ExecutingPlan -> AccentNeonGreen
        is AgentState.Speaking -> AccentCyan
        is AgentState.Error -> AccentRed
    }

    Text(
        text = text,
        fontSize = 11.sp,
        color = color,
        fontFamily = FontFamily.SansSerif
    )
}

@Composable
fun UsageHintCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "怎么用",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1. 先到“配置”填 API Key 和模型。\n2. 到系统辅助功能里开启“龙虾”。\n3. 回到这里输入任务，执行前先确认计划。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "示例：打开微信给张三发消息 / 打开支付宝付款码 / 打开设置把亮度调高",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = AccentCyan
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, viewModel: ChatViewModel? = null, context: android.content.Context? = null) {
    val isAgent = message.sender == ChatMessage.Sender.AGENT
    val alignment = if (isAgent) Alignment.Start else Alignment.End
    val bubbleColor = if (isAgent) CardBackground else AccentPurple.copy(alpha = 0.25f)
    val textColor = TextPrimary
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // If this is a contact picker message, render the ContactPickerCard instead
    if (isAgent && message.contactPickerData != null) {
        val matches: List<Map<String, String>> = try {
            Json { ignoreUnknownKeys = true }
                .decodeFromString<List<Map<String, String>>>(message.contactPickerData)
        } catch (_: Exception) {
            emptyList()
        }

        if (matches.isNotEmpty()) {
            // Extract query from text ("Which 'dad' do you mean?" → "dad")
            val query = Regex("Which '(.*?)'").find(message.text)?.groupValues?.getOrNull(1) ?: "contact"

            ContactPickerCard(
                query = query,
                matches = matches,
                onContactSelected = { selected ->
                    val index = matches.indexOf(selected) + 1
                    if (viewModel != null && context != null) {
                        viewModel.sendMessage(index.toString(), context)
                    }
                }
            )
            return
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isAgent) Arrangement.Start else Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isAgent) 4.dp else 16.dp,
                            bottomEnd = if (isAgent) 16.dp else 4.dp
                        )
                    )
                    .background(bubbleColor)
                    .border(
                        1.dp,
                        if (isAgent) BorderColor else AccentPurple.copy(alpha = 0.5f),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isAgent) 4.dp else 16.dp,
                            bottomEnd = if (isAgent) 16.dp else 4.dp
                        )
                    )
                    .padding(14.dp)
            ) {
                if (isAgent && message.modelBadge != null) {
                    val displayName = when (message.modelBadge) {
                        "Gemma 4 (On-device)" -> "ON-DEVICE (AI CORE)"
                        "On-Device AI" -> "ON-DEVICE AI"
                        "LiteRT-LM (On-device)" -> "ON-DEVICE (LITERT)"
                        else -> message.modelBadge.uppercase(Locale.getDefault())
                    }
                    Text(
                        text = displayName,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = timeFormat.format(Date(message.timestamp)),
                    fontSize = 9.sp,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun ThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                .background(CardBackground)
                .border(1.dp, BorderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                .padding(14.dp)
                .graphicsLayer(alpha = alpha)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentNeonGreen))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentNeonGreen))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentNeonGreen))
            }
        }
    }
}

@Composable
fun ProposedPlanPrompt(
    goal: String,
    stepsCount: Int,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, AccentCyan, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "计划待确认",
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "计划待确认",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentCyan
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "目标：$goal",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "龙虾已生成 $stepsCount 个步骤。你可以到“计划”页查看细节，确认无误后再执行。",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("取消", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNeonGreen, contentColor = DarkBackground),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("确认执行", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FloatingOrb(
    isListening: Boolean,
    agentState: AgentState,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val colorPulse by animateColorAsState(
        targetValue = when {
            isListening -> AccentRed
            agentState is AgentState.Thinking -> AccentPurple
            agentState is AgentState.ExecutingPlan -> AccentNeonGreen
            agentState is AgentState.Speaking -> AccentCyan
            else -> BorderColor
        },
        animationSpec = tween(500),
        label = "color"
    )

    val shadowSize = if (isListening || agentState !is AgentState.Idle) pulseScale else 1f

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(shadowSize)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(colorPulse, Color.Transparent),
                    radius = 120f
                )
            )
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colorPulse, colorPulse.copy(alpha = 0.6f))
                    )
                )
                .border(2.dp, TextPrimary.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(DarkBackground)
            )
        }
    }
}

@Composable
fun VoiceWaveform(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val heightScale1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val heightScale2 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val heightScale3 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.width(60.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(4.dp).height(heightScale1.dp).clip(CircleShape).background(AccentRed))
            Box(modifier = Modifier.width(4.dp).height(heightScale2.dp).clip(CircleShape).background(AccentRed))
            Box(modifier = Modifier.width(4.dp).height(heightScale3.dp).clip(CircleShape).background(AccentRed))
            Box(modifier = Modifier.width(4.dp).height(heightScale2.dp).clip(CircleShape).background(AccentRed))
            Box(modifier = Modifier.width(4.dp).height(heightScale1.dp).clip(CircleShape).background(AccentRed))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = TextPrimary,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1
        )
    }
}
