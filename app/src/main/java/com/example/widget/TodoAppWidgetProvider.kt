package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TodoAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_ITEM_CLICK = "com.example.widget.ACTION_ITEM_CLICK"
        const val EXTRA_TODO_ID = "com.example.widget.EXTRA_TODO_ID"
        const val EXTRA_TODO_COMPLETED = "com.example.widget.EXTRA_TODO_COMPLETED"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Setup the RemoteViewsService intent for the ListView
            val serviceIntent = Intent(context, TodoWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty_view)

            // Dynamic header stats
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val allItems = db.todoDao().getAllTodoItemsSync()
                    val activeCount = allItems.count { !it.isCompleted }
                    val totalCount = allItems.size
                    
                    val statsText = if (totalCount == 0) "无任务" else "$activeCount / $totalCount 待办"
                    views.setTextViewText(R.id.widget_stats, statsText)
                    
                    // Push individual updates
                    appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Click template to open app on clicking title/header
            val titleIntent = Intent(context, MainActivity::class.java)
            val titlePendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                titleIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, titlePendingIntent)

            // Click template for individual list items
            val clickIntent = Intent(context, TodoAppWidgetProvider::class.java).apply {
                action = ACTION_ITEM_CLICK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            
            val clickPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent)

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, TodoAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            
            // Notify data change to the ListView adapter
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
            
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_ITEM_CLICK) {
            val todoId = intent.getIntExtra(EXTRA_TODO_ID, -1)
            val completed = intent.getBooleanExtra(EXTRA_TODO_COMPLETED, false)
            
            if (todoId != -1) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val item = db.todoDao().getTodoItemById(todoId)
                        if (item != null) {
                            // Toggle state
                            val willComplete = !item.isCompleted
                            val updatedItem = item.copy(
                                isCompleted = willComplete,
                                completedAt = if (willComplete) System.currentTimeMillis() else null
                            )
                            
                            // If it is synced with calendar, update in calendar too
                            if (updatedItem.calendarEventId != null) {
                                com.example.utils.CalendarSyncHelper.updateTodoInCalendar(context, updatedItem)
                            }
                            
                            db.todoDao().updateTodoItem(updatedItem)
                            
                            // Re-broadcast updates to widgets immediately
                            updateAllWidgets(context)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
