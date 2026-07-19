package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class TodoRepository(
    private val database: AppDatabase
) {
    private val todoDao = database.todoDao()
    private val segmentedPlanDao = database.segmentedPlanDao()
    private val snapshotDao = database.dailyTodoSnapshotDao()

    val allItems: Flow<List<TodoItem>> = todoDao.getAllTodoItems()
    val activeItems: Flow<List<TodoItem>> = todoDao.getActiveTodoItems()
    val allSnapshots: Flow<List<DailyTodoSnapshot>> = snapshotDao.getAllSnapshots()

    suspend fun getAllItemsOnce(): List<TodoItem> = todoDao.getAllTodoItemsOnce()

    suspend fun getTodoItemById(id: Int): TodoItem? = todoDao.getTodoItemById(id)

    suspend fun insert(item: TodoItem): Long = todoDao.insertTodoItem(item)

    suspend fun update(item: TodoItem) = todoDao.updateTodoItem(item)

    suspend fun updateSortOrders(orderedIds: List<Int>) = database.withTransaction {
        orderedIds.forEachIndexed { index, id ->
            todoDao.updateSortOrder(id, index.toLong() * 1_000L)
        }
    }

    suspend fun setCompletion(
        item: TodoItem,
        snapshot: DailyTodoSnapshot?,
        snapshotDayKey: String
    ) = database.withTransaction {
        todoDao.updateTodoItem(item)
        if (snapshot != null) {
            snapshotDao.upsertSnapshot(snapshot)
        } else {
            snapshotDao.deleteSnapshot(item.id, snapshotDayKey)
        }
    }

    suspend fun archiveWithSnapshot(
        itemId: Int,
        archivedAt: Long,
        snapshot: DailyTodoSnapshot
    ) = database.withTransaction {
        val current = todoDao.getTodoItemById(itemId)
        if (current?.isCompleted == true) {
            snapshotDao.upsertSnapshot(snapshot.copy(
                title = current.title,
                description = current.description,
                category = current.category,
                isImportant = current.isImportant,
                completedAt = current.completedAt,
                originalDueDate = current.dueDate,
                originalCreatedAt = current.createdAt
            ))
            todoDao.archiveTodoItem(itemId, archivedAt)
        } else if (current != null) {
            todoDao.deleteTodoItem(current)
        } else {
            // A concurrent completion may have removed the row already. Keep its snapshot.
            snapshotDao.upsertSnapshot(snapshot)
        }
    }

    suspend fun archiveCompletedWithSnapshots(
        items: List<TodoItem>,
        snapshots: List<DailyTodoSnapshot>,
        archivedAt: Long
    ) = database.withTransaction {
        snapshots.forEach { snapshotDao.upsertSnapshot(it) }
        items.forEach { todoDao.archiveTodoItem(it.id, archivedAt) }
    }

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
