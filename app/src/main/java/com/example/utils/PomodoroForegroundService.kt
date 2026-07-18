package com.example.utils

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PomodoroForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTimerService()
                return START_NOT_STICKY
            }
            else -> startTimerService()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        super.onDestroy()
    }

    private fun startTimerService() {
        val state = PomodoroStore.load(this)
        if (!state.isRunning || state.remainingSeconds <= 0) {
            PomodoroStore.completeFinishedSession(this)
            stopTimerService()
            return
        }

        ServiceCompat.startForeground(
            this,
            PomodoroNotifier.RUNNING_NOTIFICATION_ID,
            PomodoroNotifier.buildRunningNotification(this, state),
            foregroundServiceType()
        )

        if (refreshJob?.isActive == true) return
        refreshJob = serviceScope.launch {
            while (true) {
                val latest = PomodoroStore.load(this@PomodoroForegroundService)
                if (!latest.isRunning || latest.remainingSeconds <= 0) {
                    PomodoroStore.completeFinishedSession(this@PomodoroForegroundService)
                    stopTimerService()
                    break
                }
                PomodoroNotifier.updateRunningNotification(this@PomodoroForegroundService, latest)
                delay(30_000L)
            }
        }
    }

    private fun stopTimerService() {
        refreshJob?.cancel()
        refreshJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
    }

    companion object {
        private const val ACTION_START = "com.example.action.START_POMODORO_FOREGROUND"
        private const val ACTION_STOP = "com.example.action.STOP_POMODORO_FOREGROUND"

        fun start(context: Context) {
            val intent = Intent(context, PomodoroForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PomodoroForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
