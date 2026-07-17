package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val category: String, // "Work", "Study", "Life", "Health", "Shopping", "Personal"
    val dueDate: Long? = null,
    val isCompleted: Boolean = false,
    val isImportant: Boolean = false,
    val calendarEventId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUrl: String? = null
)
