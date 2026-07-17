package com.example.utils

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.TodoItem
import java.util.TimeZone

object CalendarSyncHelper {

    fun hasCalendarPermission(context: Context): Boolean {
        val writePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        val readPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        return writePermission == PackageManager.PERMISSION_GRANTED && readPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun getPrimaryCalendarId(context: Context): Long {
        var calendarId: Long = 1
        try {
            val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idCol = it.getColumnIndex(CalendarContract.Calendars._ID)
                    val primaryCol = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                    
                    var foundPrimary = false
                    while (!it.isAfterLast) {
                        val id = it.getLong(idCol)
                        val isPrimary = if (primaryCol >= 0) it.getInt(primaryCol) == 1 else false
                        if (isPrimary) {
                            calendarId = id
                            foundPrimary = true
                            break
                        }
                        it.moveToNext()
                    }
                    if (!foundPrimary && it.moveToFirst()) {
                        calendarId = it.getLong(idCol)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return calendarId
    }

    fun addTodoToCalendar(context: Context, item: TodoItem): Long? {
        if (!hasCalendarPermission(context) || item.dueDate == null) return null
        
        return try {
            val cr = context.contentResolver
            val calendarId = getPrimaryCalendarId(context)
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, item.dueDate)
                // Set end date to 1 hour after start date
                put(CalendarContract.Events.DTEND, item.dueDate + 60 * 60 * 1000)
                val prefix = if (item.isCompleted) "[已完成]" else "[待办]"
                put(CalendarContract.Events.TITLE, "$prefix ${item.title}")
                
                val desc = StringBuilder().apply {
                    if (item.description.isNotEmpty()) {
                        append(item.description)
                        append("\n\n")
                    }
                    append("分类标签: ${item.category}\n")
                    if (item.isImportant) {
                        append("优先级: 重要\n")
                    }
                    append("来自「待办清单」应用")
                }.toString()
                
                put(CalendarContract.Events.DESCRIPTION, desc)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            
            val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateTodoInCalendar(context: Context, item: TodoItem): Boolean {
        val eventId = item.calendarEventId ?: return false
        if (!hasCalendarPermission(context)) return false
        
        return try {
            val cr = context.contentResolver
            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            
            if (item.dueDate == null) {
                // If due date was cleared, we remove the calendar event
                cr.delete(updateUri, null, null)
                return true
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, item.dueDate)
                put(CalendarContract.Events.DTEND, item.dueDate + 60 * 60 * 1000)
                val prefix = if (item.isCompleted) "[已完成]" else "[待办]"
                put(CalendarContract.Events.TITLE, "$prefix ${item.title}")
                
                val desc = StringBuilder().apply {
                    if (item.description.isNotEmpty()) {
                        append(item.description)
                        append("\n\n")
                    }
                    append("分类标签: ${item.category}\n")
                    if (item.isImportant) {
                        append("优先级: 重要\n")
                    }
                    append("来自「待办清单」应用")
                }.toString()
                
                put(CalendarContract.Events.DESCRIPTION, desc)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            
            val rows = cr.update(updateUri, values, null, null)
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteTodoFromCalendar(context: Context, eventId: Long): Boolean {
        if (!hasCalendarPermission(context)) return false
        return try {
            val cr = context.contentResolver
            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = cr.delete(deleteUri, null, null)
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
