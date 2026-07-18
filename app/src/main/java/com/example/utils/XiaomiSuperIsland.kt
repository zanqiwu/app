package com.example.utils

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.example.BuildConfig
import org.json.JSONObject
import kotlin.math.ceil

object XiaomiSuperIsland {
    private const val FOCUS_PARAM_KEY = "miui.focus.param"
    private const val FOCUS_PICS_KEY = "miui.focus.pics"
    private const val TIMER_PIC_KEY = "miui.focus.pic_timer"

    fun isOs3Supported(context: Context): Boolean = focusProtocolVersion(context) >= 3

    // Xiaomi documents this provider call as potentially slow. Call it off the UI thread.
    fun hasFocusPermission(context: Context): Boolean {
        return runCatching {
            val extras = Bundle().apply { putString("package", context.packageName) }
            context.contentResolver.call(
                Uri.parse("content://miui.statusbar.notification.public"),
                "canShowFocus",
                null,
                extras
            )?.getBoolean("canShowFocus", false) == true
        }.getOrDefault(false)
    }

    fun addTimerData(context: Context, notification: Notification, state: PomodoroState): Notification {
        if (!isOs3Supported(context) || !hasFocusPermission(context)) return notification

        val minutesLeft = ceil(state.remainingSeconds / 60.0).toInt().coerceAtLeast(1)
        val remaining = String.format(
            java.util.Locale.CHINA,
            "%02d:%02d",
            state.remainingSeconds / 60,
            state.remainingSeconds % 60
        )
        val percent = if (state.totalSeconds > 0) {
            ((state.totalSeconds - state.remainingSeconds) * 100 / state.totalSeconds).coerceIn(0, 100)
        } else {
            0
        }

        val textInfo = JSONObject()
            .put("frontTitle", "专注中")
            .put("title", "$percent%")
            .put("content", "剩余 $remaining")
            .put("useHighLight", true)
        val picInfo = JSONObject()
            .put("type", 1)
            .put("pic", TIMER_PIC_KEY)
        val bigIsland = JSONObject()
            .put(
                "imageTextInfoLeft",
                JSONObject()
                    .put("type", 1)
                    .put("picInfo", picInfo)
                    .put("miui.focus.paramtextInfo", textInfo)
            )
            .put("picInfo", picInfo)
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandTimeout", state.remainingSeconds.coerceAtLeast(1))
            .put("dismissIsland", false)
            .put("highlightColor", "#4F6DAA")
            .put("bigIslandArea", bigIsland)
            .put("smallIslandArea", JSONObject().put("picInfo", picInfo))
        val paramV2 = JSONObject()
            .put("protocol", 1)
            .put("business", BuildConfig.XIAOMI_ISLAND_BUSINESS)
            .put("islandFirstFloat", true)
            .put("enableFloat", false)
            .put("timeout", minutesLeft + 1)
            .put("updatable", true)
            .put("reopen", "reopen")
            .put("filterWhenNoPermission", false)
            .put("aodTitle", "番茄钟 · 剩余 $remaining")
            .put("aodPic", TIMER_PIC_KEY)
            .put("param_island", island)
            .put(
                "baseInfo",
                JSONObject()
                    .put("title", "番茄钟专注中")
                    .put("content", "剩余 $remaining")
                    .put("colorTitle", "#4F6DAA")
                    .put("type", 2)
            )

        val pics = Bundle().apply {
            putParcelable(
                TIMER_PIC_KEY,
                Icon.createWithResource(context, android.R.drawable.ic_lock_idle_alarm)
            )
        }
        notification.extras.putBundle(FOCUS_PICS_KEY, pics)
        notification.extras.putString(
            FOCUS_PARAM_KEY,
            JSONObject().put("param_v2", paramV2).toString()
        )
        return notification
    }

    private fun focusProtocolVersion(context: Context): Int {
        return runCatching {
            Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
        }.getOrDefault(0)
    }
}
