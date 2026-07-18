package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_todo_snapshots",
    indices = [Index(value = ["todoId", "dayKey"], unique = true)]
)
data class DailyTodoSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val todoId: Int,
    val dayKey: String,
    val title: String,
    val description: String,
    val category: String,
    val isImportant: Boolean,
    val wasCompleted: Boolean,
    val completedAt: Long? = null,
    val originalDueDate: Long? = null,
    val originalCreatedAt: Long,
    val carriedForward: Boolean = false,
    val archivedAt: Long = System.currentTimeMillis()
)
