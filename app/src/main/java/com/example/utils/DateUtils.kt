package com.example.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

object DateUtils {
    private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun todayKey(): String = dayKey(System.currentTimeMillis())

    fun yesterdayKey(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return dayKey(cal.timeInMillis)
    }

    fun dayKey(millis: Long): String = synchronized(dayFormatter) {
        dayFormatter.format(Date(millis))
    }

    fun isSameDay(millis: Long?, key: String): Boolean {
        return millis != null && dayKey(millis) == key
    }

    fun startOfDayMillis(key: String): Long {
        val parsed = synchronized(dayFormatter) { dayFormatter.parse(key) } ?: Date()
        return Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun endOfDayMillis(key: String): Long = startOfDayMillis(key) + DAY_MILLIS - 1

    fun millisUntilNextDay(): Long {
        val now = System.currentTimeMillis()
        val nextMidnight = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 1)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return max(1_000L, nextMidnight - now)
    }

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
}
