package com.seikochang.ever_listen

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BootReceiverTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("ever_listen_schedules", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @Test
    fun testBootReceiverReschedulesActiveAlarms() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = shadowOf(alarmManager)

        val now = System.currentTimeMillis()
        
        // 1. Add a future one-time schedule
        val futureSchedule = RecordingSchedule(
            id = "future-1",
            startTimeMillis = now + 500000L,
            endTimeMillis = now + 1000000L,
            repeat = "once",
            timezone = "UTC",
            mode = "detect",
            sensitivity = 0.6,
            maxStorageMb = 200
        )
        ScheduleStore.add(context, futureSchedule)

        // 2. Add an expired one-time schedule
        val expiredSchedule = RecordingSchedule(
            id = "expired-1",
            startTimeMillis = now - 1000000L,
            endTimeMillis = now - 500000L,
            repeat = "once",
            timezone = "UTC",
            mode = "detect",
            sensitivity = 0.6,
            maxStorageMb = 200
        )
        ScheduleStore.add(context, expiredSchedule)

        assertEquals(2, ScheduleStore.list(context).size)

        // Trigger BootReceiver
        val receiver = BootReceiver()
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, bootIntent)

        // Verify expired one-time schedule was cleaned up
        val activeSchedules = ScheduleStore.list(context)
        assertEquals(1, activeSchedules.size)
        assertEquals("future-1", activeSchedules[0].id)

        // Verify future alarms are set
        val alarms = shadowAlarmManager.scheduledAlarms
        assertTrue(alarms.isNotEmpty())
    }
}
