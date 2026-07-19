package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodoRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completionSurvivesReorderAndDeleteIsArchived() = runBlocking {
        val firstId = repository.insert(TodoItem(title = "first", category = "work")).toInt()
        val secondId = repository.insert(TodoItem(title = "second", category = "work")).toInt()
        val original = repository.getTodoItemById(firstId)!!
        val completedAt = System.currentTimeMillis()
        val completed = original.copy(isCompleted = true, completedAt = completedAt)
        val snapshot = completed.toSnapshot("2026-07-19")

        repository.setCompletion(completed, snapshot, "2026-07-19")
        repository.updateSortOrders(listOf(secondId, firstId))

        assertTrue(repository.getTodoItemById(firstId)!!.isCompleted)
        assertEquals(1, repository.allSnapshots.first().size)

        repository.archiveWithSnapshot(firstId, completedAt + 1, snapshot)

        assertFalse(repository.activeItems.first().any { it.id == firstId })
        val archived = repository.allSnapshots.first().single()
        assertEquals("first", archived.title)
        assertTrue(archived.wasCompleted)
    }

    private fun TodoItem.toSnapshot(dayKey: String) = DailyTodoSnapshot(
        todoId = id,
        dayKey = dayKey,
        title = title,
        description = description,
        category = category,
        isImportant = isImportant,
        wasCompleted = true,
        completedAt = completedAt,
        originalDueDate = dueDate,
        originalCreatedAt = createdAt
    )
}
