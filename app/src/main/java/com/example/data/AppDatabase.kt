package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TodoItem::class, SegmentedPlan::class, DailyTodoSnapshot::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun segmentedPlanDao(): SegmentedPlanDao
    abstract fun dailyTodoSnapshotDao(): DailyTodoSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "todo_database"
                )
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .addMigrations(MIGRATION_8_9)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE todo_items ADD COLUMN completedAt INTEGER")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE todo_items ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE todo_items SET sortOrder = -createdAt")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE todo_items ADD COLUMN archivedAt INTEGER")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_todo_snapshots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        todoId INTEGER NOT NULL,
                        dayKey TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        category TEXT NOT NULL,
                        isImportant INTEGER NOT NULL,
                        wasCompleted INTEGER NOT NULL,
                        completedAt INTEGER,
                        originalDueDate INTEGER,
                        originalCreatedAt INTEGER NOT NULL,
                        carriedForward INTEGER NOT NULL,
                        archivedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_daily_todo_snapshots_todoId_dayKey " +
                        "ON daily_todo_snapshots(todoId, dayKey)"
                )
            }
        }
    }
}
