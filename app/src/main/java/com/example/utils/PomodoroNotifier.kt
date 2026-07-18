package com.example.utils

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object PomodoroNotifier {
    // A new id is intentional: Android does not let an app restore the importance
    // or lock-screen visibility of an existing channel after it was downgraded.
    const val RUNNING_CHANNEL_ID = "pomodoro_running_channel_v4"
    private const val FINISHED_CHANNEL_ID = "pomodoro_finished_channel"
    const val RUNNING_NOTIFICATION_ID = 2601
    private const val FINISHED_NOTIFICATION_ID = 2602
    private const val FINISH_REQUEST_CODE = 2603

    fun hasNotificationPermission(context: Context): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun isRunningChannelEnabled(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        ensureChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(RUNNING_CHANNEL_ID) ?: return false
        return channel.importance != NotificationManager.IMPORTANCE_NONE &&
            channel.lockscreenVisibility != android.app.Notification.VISIBILITY_SECRET
    }

    fun openRunningChannelSettings(context: Context) {
        ensureChannels(context)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, RUNNING_CHANNEL_ID)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun onTimerStarted(context: Context, state: PomodoroState) {
        ensureChannels(context)
        PomodoroForegroundService.start(context)
        scheduleFinishAlarm(context, state.endAtMillis)
    }

    fun onTimerPaused(context: Context) {
        cancelFinishAlarm(context)
        PomodoroForegroundService.stop(context)
        cancelRunningNotification(context)
    }

    fun onTimerReset(context: Context) {
        cancelFinishAlarm(context)
        PomodoroForegroundService.stop(context)
        cancelRunningNotification(context)
    }

    fun onTimerFinished(context: Context) {
        ensureChannels(context)
        PomodoroStore.completeFinishedSession(context)
        cancelFinishAlarm(context)
        PomodoroForegroundService.stop(context)
        cancelRunningNotification(context)
        playFinishFeedback(context)
        showFinishedNotification(context)
    }

    fun buildRunningNotification(context: Context, state: PomodoroState): android.app.Notification {
        ensureChannels(context)
        val contentView = buildRunningContentView(context, state)
        return NotificationCompat.Builder(context, RUNNING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("番茄钟进行中")
            .setContentText(formatRemainingText(state.remainingSeconds))
            .setContentIntent(mainActivityIntent(context))
            .setWhen(state.endAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCustomContentView(contentView)
            .setCustomBigContentView(contentView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setColor(ContextCompat.getColor(context, R.color.pomodoro_primary))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory("stopwatch")
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .setTicker("番茄钟专注中")
            .setPublicVersion(
                NotificationCompat.Builder(context, RUNNING_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("番茄钟进行中")
                    .setContentText(formatRemainingText(state.remainingSeconds))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setCategory("stopwatch")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
            )
            .build()
    }

    fun updateRunningNotification(context: Context, state: PomodoroState) {
        if (!hasNotificationPermission(context)) return

        try {
            NotificationManagerCompat.from(context).notify(RUNNING_NOTIFICATION_ID, buildRunningNotification(context, state))
        } catch (_: SecurityException) {
            // Android 13+ may revoke notifications at runtime.
        }
    }

    private fun showFinishedNotification(context: Context) {
        if (!hasNotificationPermission(context)) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(context, FINISHED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("番茄钟完成")
            .setContentText("一次专注结束了，休息一下再继续。")
            .setContentIntent(mainActivityIntent(context))
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 180, 500, 180, 700))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(FINISHED_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android 13+ may revoke notifications at runtime.
        }
    }

    private fun cancelRunningNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(RUNNING_NOTIFICATION_ID)
    }

    private fun scheduleFinishAlarm(context: Context, endAtMillis: Long) {
        if (endAtMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = finishPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, endAtMillis, pendingIntent)
        }
    }

    private fun buildRunningContentView(context: Context, state: PomodoroState): RemoteViews {
        val remainingMillis = (state.endAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val chronometerBase = SystemClock.elapsedRealtime() + remainingMillis
        val totalMinutes = (state.totalSeconds / 60).coerceAtLeast(1)
        return RemoteViews(context.packageName, R.layout.notification_pomodoro_timer).apply {
            setTextViewText(R.id.pomodoro_notification_title, "番茄钟专注中")
            setTextViewText(R.id.pomodoro_notification_subtitle, "${totalMinutes} 分钟专注 · 锁屏倒计时")
            setChronometer(R.id.pomodoro_notification_time, chronometerBase, "%s", true)
            setChronometerCountDown(R.id.pomodoro_notification_time, true)
        }
    }

    private fun formatRemainingText(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "剩余 ${String.format("%02d:%02d", mins, secs)}"
    }

    private fun cancelFinishAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = finishPendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun finishPendingIntent(context: Context, flag: Int): PendingIntent? {
        val intent = Intent(context, PomodoroFinishedReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            FINISH_REQUEST_CODE,
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun mainActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            RUNNING_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val runningChannel = NotificationChannel(
            RUNNING_CHANNEL_ID,
            "番茄钟锁屏倒计时",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "以小卡片形式在锁屏和通知栏显示番茄钟剩余时间"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(runningChannel)

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val finishedChannel = NotificationChannel(
            FINISHED_CHANNEL_ID,
            "番茄钟完成提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "番茄钟结束时响铃和震动"
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 180, 500, 180, 700)
        }
        notificationManager.createNotificationChannel(finishedChannel)
    }

    @Suppress("DEPRECATION")
    private fun playFinishFeedback(context: Context) {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, soundUri)?.play()
        } catch (_: Exception) {
            // Notification sound still acts as the main feedback path.
        }

        try {
            val pattern = longArrayOf(0, 500, 180, 500, 180, 700)
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
            // Some devices disable vibration for apps or battery modes.
        }
    }
}
