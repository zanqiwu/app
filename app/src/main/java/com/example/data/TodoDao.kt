package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items ORDER BY sortOrder ASC, createdAt DESC")
    fun getAllTodoItems(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items ORDER BY sortOrder ASC, createdAt DESC")
    fun getAllTodoItemsSync(): List<TodoItem>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getTodoItemById(id: Int): TodoItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodoItem(item: TodoItem): Long

    @Update
    suspend fun updateTodoItem(item: TodoItem)

    @Update
    suspend fun updateTodoItems(items: List<TodoItem>)

    @Delete
    suspend fun deleteTodoItem(item: TodoItem)

    @Query("DELETE FROM todo_items WHERE isCompleted = 1")
    suspend fun deleteCompletedItems()
}
