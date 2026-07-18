package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DailyTodoSnapshot
import com.example.data.TodoItem
import com.example.data.TodoRepository
import com.example.data.SegmentedPlan
import com.example.utils.CalendarSyncHelper
import com.example.utils.DateUtils
import com.example.utils.PomodoroNotifier
import com.example.utils.PomodoroState
import com.example.utils.PomodoroStore
import com.example.widget.TodoAppWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RolloverCandidate(
    val todo: TodoItem,
    val archiveDay: String
)

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TodoRepository

    // Current category filter
    val selectedCategoryFilter = MutableStateFlow("全部") // "全部", "工作", "学习", "生活", "健康", "购物", "个人", "重要"

    // Search query
    val searchQuery = MutableStateFlow("")

    // Background style selection
    private val sharedPrefs = application.getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE)
    val backgroundStyle = MutableStateFlow(sharedPrefs.getString("background_style", "default") ?: "default")

    fun setBackgroundStyle(style: String) {
        backgroundStyle.value = style
        sharedPrefs.edit().putString("background_style", style).apply()
    }

    // Compact mode preference
    val isCompactMode = MutableStateFlow(sharedPrefs.getBoolean("is_compact_mode", false))

    val currentDayKeyState = MutableStateFlow(DateUtils.todayKey())
    val pomodoroState = MutableStateFlow(PomodoroStore.load(application))
    val pomodoroRunningState: StateFlow<Boolean> = pomodoroState
        .map { it.isRunning }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, pomodoroState.value.isRunning)

    fun setCompactMode(compact: Boolean) {
        isCompactMode.value = compact
        sharedPrefs.edit().putBoolean("is_compact_mode", compact).apply()
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TodoRepository(
            database.todoDao(),
            database.segmentedPlanDao(),
            database.dailyTodoSnapshotDao()
        )
        startDateTicker()
        startPomodoroTicker()
    }

    // Expose filtered items
    val todoItemsState: StateFlow<List<TodoItem>> = combine(
        repository.activeItems,
        selectedCategoryFilter,
        searchQuery
    ) { items, filter, query ->
        items.filter { item ->
            // Search query filter
            val matchesQuery = query.isEmpty() || 
                item.title.contains(query, ignoreCase = true) || 
                item.description.contains(query, ignoreCase = true)

            // Category filter
            val matchesCategory = when (filter) {
                "全部" -> true
                "重要" -> item.isImportant
                else -> item.category == filter
            }

            matchesQuery && matchesCategory
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Statistics Flow for visual indicators (charts or summaries)
    val taskStatsState: StateFlow<TaskStats> = combine(
        repository.activeItems,
        selectedCategoryFilter,
        currentDayKeyState
    ) { items, filter, todayKey ->
        val total = items.size
        val completed = items.count { it.isCompleted }
        val pending = total - completed
        
        val filteredItems = items.filter { item ->
            when (filter) {
                "全部" -> true
                "重要" -> item.isImportant
                else -> item.category == filter
            }
        }
        val filteredTotal = filteredItems.size
        val filteredCompleted = filteredItems.count { it.isCompleted }
        val todayCompleted = filteredItems.count { item ->
            item.isCompleted && DateUtils.isSameDay(item.completedAt ?: item.dueDate ?: item.createdAt, todayKey)
        }
        val todayPending = filteredItems.count { !it.isCompleted }
        val yesterdayKey = DateUtils.yesterdayKey()
        val yesterdayUnfinished = filteredItems.count { item ->
            !item.isCompleted && DateUtils.isSameDay(item.dueDate ?: item.createdAt, yesterdayKey)
        }
        
        TaskStats(
            total = total,
            completed = completed,
            pending = pending,
            filteredTotal = filteredTotal,
            filteredCompleted = filteredCompleted,
            todayTotal = todayPending + todayCompleted,
            todayCompleted = todayCompleted,
            yesterdayUnfinished = yesterdayUnfinished
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskStats()
    )

    // Segmented plans Flow
    val segmentedPlansState: StateFlow<List<SegmentedPlan>> = repository.allPlans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val dailySnapshotsState: StateFlow<List<DailyTodoSnapshot>> = repository.allSnapshots
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingRolloverState = MutableStateFlow<List<RolloverCandidate>>(emptyList())

    init {
        prepareDailyRollover()
    }

    private fun prepareDailyRollover() {
        viewModelScope.launch(Dispatchers.IO) {
            val todayKey = DateUtils.todayKey()
            val todayStart = DateUtils.startOfDayMillis(todayKey)
            val snapshots = mutableListOf<DailyTodoSnapshot>()
            val candidates = mutableListOf<RolloverCandidate>()

            repository.getAllItemsOnce().forEach { item ->
                if (item.archivedAt != null) return@forEach

                if (item.isCompleted) {
                    val completedAt = item.completedAt ?: item.dueDate ?: item.createdAt
                    if (completedAt < todayStart) {
                        snapshots += item.toDailySnapshot(
                            dayKey = DateUtils.dayKey(completedAt),
                            wasCompleted = true
                        )
                    }
                } else {
                    val assignedAt = item.dueDate ?: item.createdAt
                    if (assignedAt < todayStart) {
                        val archiveDay = DateUtils.dayKey(assignedAt)
                        snapshots += item.toDailySnapshot(dayKey = archiveDay, wasCompleted = false)
                        candidates += RolloverCandidate(item, archiveDay)
                    }
                }
            }

            if (snapshots.isNotEmpty()) repository.insertSnapshots(snapshots)

            val handledKey = sharedPrefs.getString("rollover_handled_day", null)
            if (handledKey != todayKey && candidates.isNotEmpty()) {
                pendingRolloverState.value = candidates
            } else if (candidates.isEmpty()) {
                sharedPrefs.edit().putString("rollover_handled_day", todayKey).apply()
            }
        }
    }

    fun confirmDailyRollover(selectedTodoIds: Set<Int>) {
        val candidates = pendingRolloverState.value
        if (candidates.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val todayKey = DateUtils.todayKey()
            val todayStart = DateUtils.startOfDayMillis(todayKey)
            val archivedAt = System.currentTimeMillis()
            val context = getApplication<Application>()

            candidates.forEach { candidate ->
                val item = candidate.todo
                if (item.id in selectedTodoIds) {
                    repository.markSnapshotCarriedForward(item.id, candidate.archiveDay)
                    repository.update(item.copy(dueDate = todayStart, archivedAt = null))
                } else {
                    repository.update(item.copy(archivedAt = archivedAt))
                    com.example.utils.AlarmScheduler.cancelAlarm(context, item)
                }
            }

            sharedPrefs.edit().putString("rollover_handled_day", todayKey).apply()
            pendingRolloverState.value = emptyList()
            TodoAppWidgetProvider.updateAllWidgets(context)
        }
    }

    fun restoreSnapshotToToday(snapshot: DailyTodoSnapshot) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getTodoItemById(snapshot.todoId)
            val todayStart = DateUtils.startOfDayMillis(DateUtils.todayKey())
            if (existing != null) {
                repository.update(
                    existing.copy(
                        isCompleted = false,
                        completedAt = null,
                        dueDate = todayStart,
                        archivedAt = null
                    )
                )
            } else {
                repository.insert(
                    TodoItem(
                        title = snapshot.title,
                        description = snapshot.description,
                        category = snapshot.category,
                        dueDate = todayStart,
                        isImportant = snapshot.isImportant
                    )
                )
            }
            repository.markSnapshotCarriedForward(snapshot.todoId, snapshot.dayKey)
            TodoAppWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    private fun TodoItem.toDailySnapshot(dayKey: String, wasCompleted: Boolean) = DailyTodoSnapshot(
        todoId = id,
        dayKey = dayKey,
        title = title,
        description = description,
        category = category,
        isImportant = isImportant,
        wasCompleted = wasCompleted,
        completedAt = completedAt,
        originalDueDate = dueDate,
        originalCreatedAt = createdAt
    )

    fun insertSegmentedPlan(title: String, planType: String, targetDate: String) {
        viewModelScope.launch {
            if (title.isNotBlank()) {
                val plan = SegmentedPlan(
                    title = title.trim(),
                    planType = planType,
                    targetDate = targetDate
                )
                repository.insertPlan(plan)
            }
        }
    }

    fun toggleSegmentedPlan(plan: SegmentedPlan) {
        viewModelScope.launch {
            repository.updatePlan(plan.copy(isCompleted = !plan.isCompleted))
        }
    }

    fun deleteSegmentedPlan(plan: SegmentedPlan) {
        viewModelScope.launch {
            repository.deletePlan(plan)
        }
    }

    fun insertTodo(
        title: String,
        description: String,
        category: String,
        isImportant: Boolean,
        dueDate: Long?,
        locationName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        imageUrl: String? = null,
        syncToCalendar: Boolean = false,
        alarmTime: Long? = null,
        hasAlarm: Boolean = false
    ) {
        viewModelScope.launch {
            if (title.isNotBlank()) {
                var calendarEventId: Long? = null
                val tempItem = TodoItem(
                    title = title.trim(),
                    description = description.trim(),
                    category = category,
                    isImportant = isImportant,
                    dueDate = dueDate,
                    locationName = locationName?.trim()?.ifEmpty { null },
                    latitude = latitude,
                    longitude = longitude,
                    imageUrl = imageUrl,
                    alarmTime = alarmTime,
                    hasAlarm = hasAlarm
                )
                
                if (syncToCalendar && dueDate != null) {
                    calendarEventId = CalendarSyncHelper.addTodoToCalendar(getApplication(), tempItem)
                }

                val item = tempItem.copy(calendarEventId = calendarEventId)
                val rowId = repository.insert(item)
                val finalItem = item.copy(id = rowId.toInt())
                
                if (finalItem.hasAlarm && finalItem.alarmTime != null) {
                    com.example.utils.AlarmScheduler.scheduleAlarm(getApplication(), finalItem)
                }
                
                TodoAppWidgetProvider.updateAllWidgets(getApplication())
            }
        }
    }

    fun updateTodo(item: TodoItem, syncToCalendar: Boolean = false) {
        viewModelScope.launch {
            var updatedItem = item
            val context = getApplication<Application>()
            
            if (item.calendarEventId != null) {
                // Already synced, update it
                CalendarSyncHelper.updateTodoInCalendar(context, item)
            } else if (syncToCalendar && item.dueDate != null) {
                // Not synced yet, but user wants to sync now
                val eventId = CalendarSyncHelper.addTodoToCalendar(context, item)
                updatedItem = item.copy(calendarEventId = eventId)
            }
            
            repository.update(updatedItem)
            
            if (updatedItem.hasAlarm && updatedItem.alarmTime != null && !updatedItem.isCompleted) {
                com.example.utils.AlarmScheduler.scheduleAlarm(context, updatedItem)
            } else {
                com.example.utils.AlarmScheduler.cancelAlarm(context, updatedItem)
            }
            
            TodoAppWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun toggleComplete(item: TodoItem) {
        viewModelScope.launch {
            val willComplete = !item.isCompleted
            val updated = item.copy(
                isCompleted = willComplete,
                completedAt = if (willComplete) System.currentTimeMillis() else null
            )
            if (updated.calendarEventId != null) {
                CalendarSyncHelper.updateTodoInCalendar(getApplication(), updated)
            }
            repository.update(updated)

            val todayKey = DateUtils.todayKey()
            if (willComplete) {
                repository.upsertSnapshot(updated.toDailySnapshot(todayKey, wasCompleted = true))
            } else {
                repository.deleteSnapshot(updated.id, todayKey)
            }
            
            val context = getApplication<android.app.Application>()
            if (updated.isCompleted) {
                com.example.utils.AlarmScheduler.cancelAlarm(context, updated)
            } else if (updated.hasAlarm && updated.alarmTime != null) {
                com.example.utils.AlarmScheduler.scheduleAlarm(context, updated)
            }
            
            TodoAppWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun toggleImportant(item: TodoItem) {
        viewModelScope.launch {
            val updated = item.copy(isImportant = !item.isImportant)
            if (updated.calendarEventId != null) {
                CalendarSyncHelper.updateTodoInCalendar(getApplication(), updated)
            }
            repository.update(updated)
            TodoAppWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun deleteTodo(item: TodoItem) {
        viewModelScope.launch {
            item.calendarEventId?.let { eventId ->
                CalendarSyncHelper.deleteTodoFromCalendar(getApplication(), eventId)
            }
            com.example.utils.AlarmScheduler.cancelAlarm(getApplication(), item)
            repository.delete(item)
            TodoAppWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun deleteCompleted() {
        viewModelScope.launch {
            val itemsToDelete = todoItemsState.value.filter { it.isCompleted }
            itemsToDelete.forEach { item ->
                item.calendarEventId?.let { eventId ->
                    CalendarSyncHelper.deleteTodoFromCalendar(getApplication(), eventId)
                }
                com.example.utils.AlarmScheduler.cancelAlarm(getApplication(), item)
            }
            repository.deleteCompleted()
            TodoAppWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun setCategoryFilter(category: String) {
        selectedCategoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateTodoOrder(orderedItems: List<TodoItem>) {
        viewModelScope.launch {
            repository.updateAll(
                orderedItems.mapIndexed { index, item ->
                    item.copy(sortOrder = index.toLong() * 1_000L)
                }
            )
            TodoAppWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun insertTodayPlanTodo(title: String) {
        insertTodo(
            title = title,
            description = "",
            category = "个人",
            isImportant = false,
            dueDate = null
        )
    }

    fun setPomodoroPreset(minutes: Int) {
        val context = getApplication<Application>()
        PomodoroNotifier.onTimerReset(context)
        pomodoroState.value = PomodoroStore.setPreset(context, minutes * 60)
    }

    fun togglePomodoro() {
        val context = getApplication<Application>()
        val current = PomodoroStore.load(context)
        if (current.isRunning) {
            val paused = PomodoroStore.pause(context)
            PomodoroNotifier.onTimerPaused(context)
            pomodoroState.value = paused
        } else {
            val started = PomodoroStore.start(context)
            PomodoroNotifier.onTimerStarted(context, started)
            pomodoroState.value = started
        }
    }

    fun resetPomodoro() {
        val context = getApplication<Application>()
        val reset = PomodoroStore.reset(context)
        PomodoroNotifier.onTimerReset(context)
        pomodoroState.value = reset
    }

    private fun startDateTicker() {
        viewModelScope.launch {
            var lastDay = currentDayKeyState.value
            while (true) {
                val today = DateUtils.todayKey()
                currentDayKeyState.value = today
                if (today != lastDay) {
                    lastDay = today
                    prepareDailyRollover()
                }
                delay(DateUtils.millisUntilNextDay())
            }
        }
    }

    private fun startPomodoroTicker() {
        viewModelScope.launch {
            while (true) {
                val context = getApplication<Application>()
                val latest = PomodoroStore.load(context)
                if (latest.isRunning && latest.remainingSeconds <= 0) {
                    pomodoroState.value = PomodoroStore.completeFinishedSession(context)
                } else {
                    pomodoroState.value = latest
                }
                delay(if (latest.isRunning) 1_000L else 3_000L)
            }
        }
    }

    // AI Assistant States
    val aiLoading = MutableStateFlow(false)
    val aiGeneratedItems = MutableStateFlow<List<com.example.utils.GeneratedTodoItem>>(emptyList())
    val aiGeneratedActions = MutableStateFlow<List<com.example.utils.GeneratedAction>>(emptyList())
    val aiGeneratedArt = MutableStateFlow<com.example.utils.GeneratedImageArt?>(null)
    val aiGeneratedSong = MutableStateFlow<com.example.utils.GeneratedSong?>(null)
    val aiError = MutableStateFlow<String?>(null)
    val aiApiKey = MutableStateFlow("")

    init {
        aiApiKey.value = com.example.utils.GeminiManager.getApiKey(getApplication())
    }

    fun saveAiApiKey(key: String) {
        com.example.utils.GeminiManager.saveApiKey(getApplication(), key)
        aiApiKey.value = key
    }

    fun generateTodoWithAi(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            aiLoading.value = true
            aiError.value = null
            aiGeneratedActions.value = emptyList()
            try {
                val response = com.example.utils.GeminiManager.generateTodoItems(getApplication(), prompt)
                aiGeneratedItems.value = response.todoItems
                aiGeneratedActions.value = response.actions
            } catch (e: Exception) {
                e.printStackTrace()
                aiError.value = e.message ?: "生成失败，请检查网络或配置"
            } finally {
                aiLoading.value = false
            }
        }
    }

    fun generateArtWithAi(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            aiLoading.value = true
            aiError.value = null
            aiGeneratedArt.value = null
            try {
                val result = com.example.utils.GeminiManager.generateImageArt(getApplication(), prompt)
                aiGeneratedArt.value = result
            } catch (e: Exception) {
                e.printStackTrace()
                aiError.value = e.message ?: "图片生成失败，请检查密钥或网络"
            } finally {
                aiLoading.value = false
            }
        }
    }

    fun generateSongWithAi(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            aiLoading.value = true
            aiError.value = null
            aiGeneratedSong.value = null
            try {
                val result = com.example.utils.GeminiManager.generateSong(getApplication(), prompt)
                aiGeneratedSong.value = result
            } catch (e: Exception) {
                e.printStackTrace()
                aiError.value = e.message ?: "歌曲生成失败，请检查密钥或网络"
            } finally {
                aiLoading.value = false
            }
        }
    }

    fun clearAiMultimedia() {
        aiGeneratedArt.value = null
        aiGeneratedSong.value = null
        aiError.value = null
    }

    fun importAiTodoItems(items: List<com.example.utils.GeneratedTodoItem>) {
        viewModelScope.launch {
            items.forEach { item ->
                val todo = TodoItem(
                    title = item.title,
                    description = item.description,
                    category = item.category,
                    isImportant = item.isImportant,
                    locationName = item.locationName,
                    latitude = item.latitude,
                    longitude = item.longitude,
                    imageUrl = item.imageUrl
                )
                repository.insert(todo)
            }
            TodoAppWidgetProvider.updateAllWidgets(getApplication())
            clearAiGeneratedItems()
        }
    }

    fun importProceduralArtAsTodo(title: String, category: String, art: com.example.utils.GeneratedImageArt) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val localPath = com.example.utils.saveProceduralArtToCache(context, art)
            if (localPath != null) {
                val todo = TodoItem(
                    title = title.ifBlank { "AI 艺术画作: ${art.style}" },
                    description = "基于灵感「${art.prompt}」智能生成的艺术画作封面",
                    category = category,
                    isImportant = false,
                    imageUrl = localPath
                )
                repository.insert(todo)
                TodoAppWidgetProvider.updateAllWidgets(context)
            }
        }
    }

    fun clearAiGeneratedItems() {
        aiGeneratedItems.value = emptyList()
        aiGeneratedActions.value = emptyList()
        aiError.value = null
    }

    // Expose all items flow for weekly report selection
    val allTodoItems: StateFlow<List<TodoItem>> = repository.activeItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allTodoRecords: StateFlow<List<TodoItem>> = repository.allItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val completedTodoItems: StateFlow<List<TodoItem>> = repository.allItems
        .map { items -> items.filter(TodoItem::isCompleted) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Weekly Report states
    val weeklyReportContent = MutableStateFlow<String?>(null)
    val weeklyReportLoading = MutableStateFlow(false)
    val weeklyReportError = MutableStateFlow<String?>(null)

    fun generateWeeklyReport(context: android.content.Context, tasks: List<TodoItem>, customPrompt: String, summaryStyle: String) {
        viewModelScope.launch {
            weeklyReportLoading.value = true
            weeklyReportError.value = null
            weeklyReportContent.value = null
            try {
                if (tasks.isEmpty()) {
                    throw IllegalArgumentException("请选择或提供至少一个已完成的任务来进行周报生成！")
                }
                
                val taskListString = tasks.mapIndexed { index, item ->
                    "- [${item.category}] ${item.title}" + if (item.description.isNotBlank()) " (${item.description})" else ""
                }.joinToString("\n")
                
                val styleDesc = when (summaryStyle) {
                    "detailed" -> "详细且富有条理（包括任务背景、实施细节、后续计划）"
                    "concise" -> "精炼且重点突出（只提核心产出、关键数据，字数控制在200字以内）"
                    "work" -> "专业职场风格（使用书面的、职业化的词汇，突出KPI与业务价值）"
                    else -> "标准周报格式（按分类整理本周工作，条理清晰）"
                }

                val prompt = """
                    请根据以下本周完成的任务，撰写一份结构完整、逻辑清晰的周报。
                    
                    ### 本周完成的任务列表:
                    $taskListString
                    
                    ### 周报撰写风格与要求:
                    - 撰写风格: $styleDesc
                    - 用户附加自定义偏好/指令: ${if (customPrompt.isBlank()) "无" else customPrompt}
                    
                    ### 周报结构规范 (请根据风格适当微调):
                    1. ✨ 本周工作总结：按分类或模块梳理已完成的任务及其产出/业务价值。
                    2. 📈 核心成果亮点：挑选1-2个最重要或最有成效的成果进行重点说明。
                    3. 🔮 下周工作计划：基于本周的成果，合理推测并规划下周需要跟进或启动的工作。
                    4. 💡 个人反思与建议（可选）：简短说明本周遇到的挑战、解决方案或改进建议。
                    
                    请直接输出周报内容，不需要任何前言或总结性的寒暄。
                """.trimIndent()
                
                val request = com.example.utils.GeminiRequest(
                    contents = listOf(
                        com.example.utils.GeminiContent(
                            parts = listOf(com.example.utils.GeminiPart(text = prompt))
                        )
                    ),
                    generationConfig = com.example.utils.GeminiGenerationConfig(
                        temperature = 0.6f
                    )
                )
                
                val response = com.example.utils.GeminiManager.generateWithFallback(context, request, "gemini-3.5-flash")
                val resultText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (resultText != null) {
                    weeklyReportContent.value = resultText
                } else {
                    weeklyReportError.value = "AI 生成的内容为空"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                weeklyReportError.value = e.message ?: "生成周报时发生未知错误"
            } finally {
                weeklyReportLoading.value = false
            }
        }
    }
    
    fun clearWeeklyReport() {
        weeklyReportContent.value = null
        weeklyReportError.value = null
        weeklyReportLoading.value = false
    }
}

data class TaskStats(
    val total: Int = 0,
    val completed: Int = 0,
    val pending: Int = 0,
    val filteredTotal: Int = 0,
    val filteredCompleted: Int = 0,
    val todayTotal: Int = 0,
    val todayCompleted: Int = 0,
    val yesterdayUnfinished: Int = 0
)
