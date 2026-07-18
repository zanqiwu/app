package com.example.data

import kotlinx.coroutines.flow.Flow

class TodoRepository(
    private val todoDao: TodoDao,
    private val segmentedPlanDao: SegmentedPlanDao
) {
    val allItems: Flow<List<TodoItem>> = todoDao.getAllTodoItems()

    suspend fun getTodoItemById(id: Int): TodoItem? = todoDao.getTodoItemById(id)

    suspend fun insert(item: TodoItem): Long = todoDao.insertTodoItem(item)

    suspend fun update(item: TodoItem) = todoDao.updateTodoItem(item)

    suspend fun updateAll(items: List<TodoItem>) = todoDao.updateTodoItems(items)

    suspend fun delete(item: TodoItem) = todoDao.deleteTodoItem(item)

    suspend fun deleteCompleted() = todoDao.deleteCompletedItems()

    // Segmented Plan operations
    val allPlans: Flow<List<SegmentedPlan>> = segmentedPlanDao.getAllPlans()

    suspend fun insertPlan(plan: SegmentedPlan): Long = segmentedPlanDao.insertPlan(plan)

    suspend fun updatePlan(plan: SegmentedPlan) = segmentedPlanDao.updatePlan(plan)

    suspend fun deletePlan(plan: SegmentedPlan) = segmentedPlanDao.deletePlan(plan)
}
