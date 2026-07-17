package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentedPlanDao {
    @Query("SELECT * FROM segmented_plans ORDER BY createdAt DESC")
    fun getAllPlans(): Flow<List<SegmentedPlan>>

    @Query("SELECT * FROM segmented_plans WHERE planType = :type AND targetDate = :date ORDER BY id DESC")
    fun getPlansByDate(type: String, date: String): Flow<List<SegmentedPlan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: SegmentedPlan): Long

    @Update
    suspend fun updatePlan(plan: SegmentedPlan)

    @Delete
    suspend fun deletePlan(plan: SegmentedPlan)
}
