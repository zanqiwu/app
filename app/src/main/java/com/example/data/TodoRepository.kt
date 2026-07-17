package com.example.data

import kotlinx.coroutines.flow.Flow

class TodoRepository(private val todoDao: TodoDao) {
    val allItems: Flow<List<TodoItem>> = todoDao.getAllTodoItems()

    suspend fun getTodoItemById(id: Int): TodoItem? = todoDao.getTodoItemById(id)

    suspend fun insert(item: TodoItem): Long = todoDao.insertTodoItem(item)

    suspend fun update(item: TodoItem) = todoDao.updateTodoItem(item)

    suspend fun delete(item: TodoItem) = todoDao.deleteTodoItem(item)

    suspend fun deleteCompleted() = todoDao.deleteCompletedItems()
}
