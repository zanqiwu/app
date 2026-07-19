package com.example.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PomodoroStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("pomodoro_timer", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun finishAlarmIsOffByDefaultAndCanBeChanged() {
        assertFalse(PomodoroStore.isFinishAlarmEnabled(context))

        PomodoroStore.setFinishAlarmEnabled(context, true)
        assertTrue(PomodoroStore.isFinishAlarmEnabled(context))

        PomodoroStore.setFinishAlarmEnabled(context, false)
        assertFalse(PomodoroStore.isFinishAlarmEnabled(context))
    }
}
