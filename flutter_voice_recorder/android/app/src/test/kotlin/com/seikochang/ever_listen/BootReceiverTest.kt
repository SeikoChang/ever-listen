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

    // ==================== TODO #63: Recurring Schedule Rescheduling ====================

    @Test
    fun testBootReceiverReschedulesDailyRecurring() {
        val now = System.currentTimeMillis()
        val dailySchedule = RecordingSchedule(
            id = "daily-1",
            startTimeMillis = now - 2 * 24L * 60L * 60L * 1000L, // 2 days ago
            endTimeMillis = now - 2 * 24L * 60L * 60L * 1000L + 3600000L,
            repeat = "daily",
            timezone = "UTC",
            mode = "detect",
            sensitivity = 0.6,
            maxStorageMb = 200
        )
        ScheduleStore.add(context, dailySchedule)

        val receiver = BootReceiver()
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, bootIntent)

        val activeSchedules = ScheduleStore.list(context)
        assertEquals(1, activeSchedules.size)
        val rescheduled = activeSchedules[0]
        // Should be forwarded to a future time (at least 1 day ahead)
        assertTrue(rescheduled.startTimeMillis > now)
        assertEquals("daily-1", rescheduled.id)
    }

    @Test
    fun testBootReceiverReschedulesWeeklyRecurring() {
        val now = System.currentTimeMillis()
        val weeklySchedule = RecordingSchedule(
            id = "weekly-1",
            startTimeMillis = now - 8L * 24L * 60L * 60L * 1000L, // 8 days ago
            endTimeMillis = now - 8L * 24L * 60L * 60L * 1000L + 3600000L,
            repeat = "weekly",
            timezone = "UTC",
            mode = "monitor",
            sensitivity = 0.7,
            maxStorageMb = 300
        )
        ScheduleStore.add(context, weeklySchedule)

        val receiver = BootReceiver()
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, bootIntent)

        val activeSchedules = ScheduleStore.list(context)
        assertEquals(1, activeSchedules.size)
        val rescheduled = activeSchedules[0]
        // Should be forwarded to at least 1 week ahead
        assertTrue(rescheduled.startTimeMillis > now)
        assertEquals("weekly-1", rescheduled.id)
        // Sensitivity and mode should be preserved
        assertEquals(0.7, rescheduled.sensitivity, 0.001)
        assertEquals("monitor", rescheduled.mode)
    }

    @Test
    fun testBootReceiverWithEmptyScheduleList() {
        // No schedules added — should not crash
        val receiver = BootReceiver()
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, bootIntent)

        assertTrue(ScheduleStore.list(context).isEmpty())
    }

    @Test
    fun testBootReceiverWithFutureDailySchedule() {
        val now = System.currentTimeMillis()
        val dailySchedule = RecordingSchedule(
            id = "daily-future",
            startTimeMillis = now + 86400000L, // 1 day from now
            endTimeMillis = now + 86400000L + 3600000L,
            repeat = "daily",
            timezone = "UTC",
            mode = "detect",
            sensitivity = 0.6,
            maxStorageMb = 200
        )
        ScheduleStore.add(context, dailySchedule)

        val receiver = BootReceiver()
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, bootIntent)

        val activeSchedules = ScheduleStore.list(context)
        assertEquals(1, activeSchedules.size)
        // Future daily schedule should not be forwarded, just rescheduled as-is
        val rescheduled = activeSchedules[0]
        assertEquals(dailySchedule.startTimeMillis, rescheduled.startTimeMillis)
    }
}
