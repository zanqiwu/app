package com.example.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PomodoroFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_STOP_FEEDBACK -> PomodoroNotifier.stopFinishFeedback(appContext, dismissNotification = true)
            else -> PomodoroNotifier.onTimerFinished(appContext)
        }
    }

    companion object {
        const val ACTION_FINISHED = "com.example.action.POMODORO_FINISHED"
        const val ACTION_STOP_FEEDBACK = "com.example.action.STOP_POMODORO_FEEDBACK"
    }
}
