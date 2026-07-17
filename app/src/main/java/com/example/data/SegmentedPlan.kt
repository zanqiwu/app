package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "segmented_plans")
data class SegmentedPlan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val planType: String, // "DAILY", "WEEKLY", "MONTHLY"
    val title: String,
    val targetDate: String, // e.g. "2026-07-17" for daily, "2026-W29" for weekly, "2026-07" for monthly
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
