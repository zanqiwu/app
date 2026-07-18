package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTodoSnapshotDao {
    @Query("SELECT * FROM daily_todo_snapshots ORDER BY dayKey DESC, id DESC")
    fun getAllSnapshots(): Flow<List<DailyTodoSnapshot>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSnapshots(snapshots: List<DailyTodoSnapshot>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: DailyTodoSnapshot)

    @Query("DELETE FROM daily_todo_snapshots WHERE todoId = :todoId AND dayKey = :dayKey")
    suspend fun deleteSnapshot(todoId: Int, dayKey: String)

    @Query("UPDATE daily_todo_snapshots SET carriedForward = 1 WHERE todoId = :todoId AND dayKey = :dayKey")
    suspend fun markCarriedForward(todoId: Int, dayKey: String)
}
