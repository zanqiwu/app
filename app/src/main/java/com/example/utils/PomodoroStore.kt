package com.example.utils

import android.content.Context
import kotlin.math.ceil
import kotlin.math.max

data class PomodoroState(
    val totalSeconds: Int = PomodoroStore.DEFAULT_TOTAL_SECONDS,
    val remainingSeconds: Int = PomodoroStore.DEFAULT_TOTAL_SECONDS,
    val isRunning: Boolean = false,
    val endAtMillis: Long = 0L
)

object PomodoroStore {
    const val DEFAULT_TOTAL_SECONDS = 25 * 60

    private const val PREFS_NAME = "pomodoro_timer"
    private const val KEY_TOTAL_SECONDS = "total_seconds"
    private const val KEY_REMAINING_SECONDS = "remaining_seconds"
    private const val KEY_IS_RUNNING = "is_running"
    private const val KEY_END_AT_MILLIS = "end_at_millis"
    private const val KEY_LAST_RECORDED_END_AT_MILLIS = "last_recorded_end_at_millis"
    private const val KEY_FOCUS_SECONDS_PREFIX = "focus_seconds_"
    private const val KEY_FINISH_ALARM_ENABLED = "finish_alarm_enabled"

    fun isFinishAlarmEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FINISH_ALARM_ENABLED, false)

    fun setFinishAlarmEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FINISH_ALARM_ENABLED, enabled)
            .apply()
    }

    fun load(context: Context): PomodoroState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val totalSeconds = prefs.getInt(KEY_TOTAL_SECONDS, DEFAULT_TOTAL_SECONDS).coerceAtLeast(1)
        val isRunning = prefs.getBoolean(KEY_IS_RUNNING, false)
        val endAtMillis = prefs.getLong(KEY_END_AT_MILLIS, 0L)

        if (isRunning && endAtMillis > 0L) {
            val remaining = secondsUntil(endAtMillis)
            if (remaining <= 0) {
                return PomodoroState(
                    totalSeconds = totalSeconds,
                    remainingSeconds = 0,
                    isRunning = true,
                    endAtMillis = endAtMillis
                )
            }
            return PomodoroState(
                totalSeconds = totalSeconds,
                remainingSeconds = remaining,
                isRunning = true,
                endAtMillis = endAtMillis
            )
        }

        val remainingSeconds = prefs
            .getInt(KEY_REMAINING_SECONDS, totalSeconds)
            .coerceIn(0, totalSeconds)
        return PomodoroState(
            totalSeconds = totalSeconds,
            remainingSeconds = remainingSeconds,
            isRunning = false,
            endAtMillis = 0L
        )
    }

    fun setPreset(context: Context, totalSeconds: Int): PomodoroState {
        val total = totalSeconds.coerceAtLeast(1)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TOTAL_SECONDS, total)
            .putInt(KEY_REMAINING_SECONDS, total)
            .putBoolean(KEY_IS_RUNNING, false)
            .putLong(KEY_END_AT_MILLIS, 0L)
            .apply()
        return load(context)
    }

    fun start(context: Context): PomodoroState {
        val current = load(context)
        val remaining = current.remainingSeconds.takeIf { it > 0 } ?: current.totalSeconds
        val endAtMillis = System.currentTimeMillis() + remaining * 1000L
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TOTAL_SECONDS, current.totalSeconds)
            .putInt(KEY_REMAINING_SECONDS, remaining)
            .putBoolean(KEY_IS_RUNNING, true)
            .putLong(KEY_END_AT_MILLIS, endAtMillis)
            .apply()
        return load(context)
    }

    fun pause(context: Context): PomodoroState {
        val current = load(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TOTAL_SECONDS, current.totalSeconds)
            .putInt(KEY_REMAINING_SECONDS, current.remainingSeconds)
            .putBoolean(KEY_IS_RUNNING, false)
            .putLong(KEY_END_AT_MILLIS, 0L)
            .apply()
        return load(context)
    }

    fun reset(context: Context): PomodoroState {
        val totalSeconds = load(context).totalSeconds
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TOTAL_SECONDS, totalSeconds)
            .putInt(KEY_REMAINING_SECONDS, totalSeconds)
            .putBoolean(KEY_IS_RUNNING, false)
            .putLong(KEY_END_AT_MILLIS, 0L)
            .apply()
        return load(context)
    }

    fun markFinished(context: Context, totalSeconds: Int = load(context).totalSeconds): PomodoroState {
        val total = totalSeconds.coerceAtLeast(1)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TOTAL_SECONDS, total)
            .putInt(KEY_REMAINING_SECONDS, total)
            .putBoolean(KEY_IS_RUNNING, false)
            .putLong(KEY_END_AT_MILLIS, 0L)
            .apply()
        return load(context)
    }

    fun completeFinishedSession(context: Context): PomodoroState {
        val current = load(context)
        if (current.isRunning && current.endAtMillis > 0L && current.remainingSeconds <= 0) {
            recordCompletedFocus(context, current.totalSeconds, current.endAtMillis)
            return markFinished(context, current.totalSeconds)
        }
        return current
    }

    fun focusSecondsForDay(context: Context, dayKey: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FOCUS_SECONDS_PREFIX + dayKey, 0).coerceAtLeast(0)
    }

    private fun recordCompletedFocus(context: Context, seconds: Int, endAtMillis: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_LAST_RECORDED_END_AT_MILLIS, 0L) == endAtMillis) return

        val dayKey = DateUtils.dayKey(endAtMillis)
        val key = KEY_FOCUS_SECONDS_PREFIX + dayKey
        val existing = prefs.getInt(key, 0).coerceAtLeast(0)
        prefs.edit()
            .putInt(key, existing + seconds.coerceAtLeast(0))
            .putLong(KEY_LAST_RECORDED_END_AT_MILLIS, endAtMillis)
            .apply()
    }

    private fun secondsUntil(endAtMillis: Long): Int {
        val remainingMillis = endAtMillis - System.currentTimeMillis()
        return max(0, ceil(remainingMillis / 1000.0).toInt())
    }
}
