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

    @Query("SELECT * FROM todo_items WHERE archivedAt IS NULL ORDER BY sortOrder ASC, createdAt DESC")
    fun getActiveTodoItems(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items ORDER BY sortOrder ASC, createdAt DESC")
    fun getAllTodoItemsSync(): List<TodoItem>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getTodoItemById(id: Int): TodoItem?

    @Query("SELECT * FROM todo_items")
    suspend fun getAllTodoItemsOnce(): List<TodoItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodoItem(item: TodoItem): Long

    @Update
    suspend fun updateTodoItem(item: TodoItem)

    @Query("UPDATE todo_items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Int, sortOrder: Long)

    @Query("UPDATE todo_items SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun archiveTodoItem(id: Int, archivedAt: Long)

    @Delete
    suspend fun deleteTodoItem(item: TodoItem)

}
