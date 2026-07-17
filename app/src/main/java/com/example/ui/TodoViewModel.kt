package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TodoItem
import com.example.data.TodoRepository
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

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TodoRepository(database.todoDao())
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

    fun insertTodo(title: String, description: String, category: String, isImportant: Boolean, dueDate: Long?, syncToCalendar: Boolean = false) {
        viewModelScope.launch {
            if (title.isNotBlank()) {
                var calendarEventId: Long? = null
                val tempItem = TodoItem(
                    title = title.trim(),
                    description = description.trim(),
                    category = category,
                    isImportant = isImportant,
                    dueDate = dueDate
                )
                
                if (syncToCalendar && dueDate != null) {
                    calendarEventId = CalendarSyncHelper.addTodoToCalendar(getApplication(), tempItem)
                }

                val item = tempItem.copy(calendarEventId = calendarEventId)
                repository.insert(item)
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
    val aiError = MutableStateFlow<String?>(null)

    fun generateTodoWithAi(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            aiLoading.value = true
            aiError.value = null
            try {
                val results = com.example.utils.GeminiManager.generateTodoItems(prompt)
                aiGeneratedItems.value = results
            } catch (e: Exception) {
                e.printStackTrace()
                aiError.value = e.message ?: "生成失败，请检查网络或配置"
            } finally {
                aiLoading.value = false
            }
        }
    }

    fun importAiTodoItems(items: List<com.example.utils.GeneratedTodoItem>) {
        viewModelScope.launch {
            items.forEach { item ->
                val todo = TodoItem(
                    title = item.title,
                    description = item.description,
                    category = item.category,
                    isImportant = item.isImportant
                )
                repository.insert(todo)
            }
            TodoAppWidgetProvider.updateAllWidgets(getApplication())
            clearAiGeneratedItems()
        }
    }

    fun clearAiGeneratedItems() {
        aiGeneratedItems.value = emptyList()
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
