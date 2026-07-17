package com.example.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.data.AppDatabase
import com.example.data.TodoItem

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TodoWidgetFactory(applicationContext)
    }
}

class TodoWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var todoList: List<TodoItem> = ArrayList()

    override fun onCreate() {
        // Factory initialized
    }

    override fun onDataSetChanged() {
        // Runs on binder thread. Safely query Room synchronously!
        try {
            val db = AppDatabase.getDatabase(context)
            todoList = db.todoDao().getAllTodoItemsSync()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        todoList = emptyList()
    }

    override fun getCount(): Int {
        return todoList.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, com.example.R.layout.widget_item_layout)
        
        if (position >= todoList.size) return views
        val item = todoList[position]

        // Set status text (emoji checkbox)
        val statusText = if (item.isCompleted) "☑️" else "⚪"
        views.setTextViewText(com.example.R.id.widget_item_status, statusText)

        // Set title and apply a visual styling if completed
        views.setTextViewText(com.example.R.id.widget_item_title, item.title)
        
        // Category tag
        views.setTextViewText(com.example.R.id.widget_item_category, item.category)
        views.setViewVisibility(com.example.R.id.widget_item_category, View.VISIBLE)

        // Show/Hide importance star
        if (item.isImportant) {
            views.setViewVisibility(com.example.R.id.widget_item_important, View.VISIBLE)
        } else {
            views.setViewVisibility(com.example.R.id.widget_item_important, View.GONE)
        }

        // Create a fillInIntent to identify which item was clicked
        val fillInIntent = Intent().apply {
            val extras = Bundle().apply {
                putInt(TodoAppWidgetProvider.EXTRA_TODO_ID, item.id)
                putBoolean(TodoAppWidgetProvider.EXTRA_TODO_COMPLETED, item.isCompleted)
            }
            putExtras(extras)
        }
        
        // Apply click fill-in intent to the status text (for toggling completion) and item body
        views.setOnClickFillInIntent(com.example.R.id.widget_item_root, fillInIntent)
        views.setOnClickFillInIntent(com.example.R.id.widget_item_status, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        if (position < todoList.size) {
            return todoList[position].id.toLong()
        }
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}
