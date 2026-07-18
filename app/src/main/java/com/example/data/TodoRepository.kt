package com.example.data

import kotlinx.coroutines.flow.Flow

class TodoRepository(
    private val todoDao: TodoDao,
    private val segmentedPlanDao: SegmentedPlanDao,
    private val snapshotDao: DailyTodoSnapshotDao
) {
    val allItems: Flow<List<TodoItem>> = todoDao.getAllTodoItems()
    val activeItems: Flow<List<TodoItem>> = todoDao.getActiveTodoItems()
    val allSnapshots: Flow<List<DailyTodoSnapshot>> = snapshotDao.getAllSnapshots()

    suspend fun getAllItemsOnce(): List<TodoItem> = todoDao.getAllTodoItemsOnce()

    suspend fun getTodoItemById(id: Int): TodoItem? = todoDao.getTodoItemById(id)

    suspend fun insert(item: TodoItem): Long = todoDao.insertTodoItem(item)

    suspend fun update(item: TodoItem) = todoDao.updateTodoItem(item)

    suspend fun updateAll(items: List<TodoItem>) = todoDao.updateTodoItems(items)

    suspend fun delete(item: TodoItem) = todoDao.deleteTodoItem(item)

    suspend fun deleteCompleted() = todoDao.deleteCompletedItems()

    suspend fun insertSnapshots(snapshots: List<DailyTodoSnapshot>) = snapshotDao.insertSnapshots(snapshots)

    suspend fun upsertSnapshot(snapshot: DailyTodoSnapshot) = snapshotDao.upsertSnapshot(snapshot)

    suspend fun deleteSnapshot(todoId: Int, dayKey: String) = snapshotDao.deleteSnapshot(todoId, dayKey)

    suspend fun markSnapshotCarriedForward(todoId: Int, dayKey: String) =
        snapshotDao.markCarriedForward(todoId, dayKey)

    // Segmented Plan operations
    val allPlans: Flow<List<SegmentedPlan>> = segmentedPlanDao.getAllPlans()

    suspend fun insertPlan(plan: SegmentedPlan): Long = segmentedPlanDao.insertPlan(plan)

    suspend fun updatePlan(plan: SegmentedPlan) = segmentedPlanDao.updatePlan(plan)

    suspend fun deletePlan(plan: SegmentedPlan) = segmentedPlanDao.deletePlan(plan)
}
