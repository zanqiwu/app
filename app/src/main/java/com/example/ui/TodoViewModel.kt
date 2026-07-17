package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TodoItem
import com.example.data.TodoRepository
import com.example.data.SegmentedPlan
import com.example.utils.CalendarSyncHelper
import com.example.widget.TodoAppWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun setCompactMode(compact: Boolean) {
        isCompactMode.value = compact
        sharedPrefs.edit().putBoolean("is_compact_mode", compact).apply()
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TodoRepository(database.todoDao(), database.segmentedPlanDao())
    }

    // Expose filtered items
    val todoItemsState: StateFlow<List<TodoItem>> = combine(
        repository.allItems,
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Statistics Flow for visual indicators (charts or summaries)
    val taskStatsState: StateFlow<TaskStats> = repository.allItems.combine(selectedCategoryFilter) { items, filter ->
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
        
        TaskStats(
            total = total,
            completed = completed,
            pending = pending,
            filteredTotal = filteredTotal,
            filteredCompleted = filteredCompleted
        )
    }.stateIn(
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
            val updated = item.copy(isCompleted = !item.isCompleted)
            if (updated.calendarEventId != null) {
                CalendarSyncHelper.updateTodoInCalendar(getApplication(), updated)
            }
            repository.update(updated)
            
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
}

data class TaskStats(
    val total: Int = 0,
    val completed: Int = 0,
    val pending: Int = 0,
    val filteredTotal: Int = 0,
    val filteredCompleted: Int = 0
)
