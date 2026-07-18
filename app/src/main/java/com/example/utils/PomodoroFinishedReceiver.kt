package com.example.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PomodoroFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PomodoroNotifier.onTimerFinished(context.applicationContext)
    }
}
