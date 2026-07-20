package com.seikochang.ever_listen

import android.app.AlarmManager
import android.content.Context
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
class ScheduleStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Clear SharedPreferences before each test
        val prefs = context.getSharedPreferences("ever_listen_schedules", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @Test
    fun testAddAndGetSchedule() {
        val schedule = RecordingSchedule(
            id = "test-schedule-1",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            repeat = "once",
            timezone = "UTC",
            mode = "detect",
            sensitivity = 0.6,
            maxStorageMb = 200
        )

        ScheduleStore.add(context, schedule)

        val retrieved = ScheduleStore.get(context, "test-schedule-1")
        assertNotNull(retrieved)
        assertEquals(schedule.id, retrieved?.id)
        assertEquals(schedule.startTimeMillis, retrieved?.startTimeMillis)
        assertEquals(schedule.endTimeMillis, retrieved?.endTimeMillis)
        assertEquals(schedule.repeat, retrieved?.repeat)
        assertEquals(schedule.timezone, retrieved?.timezone)
        assertEquals(schedule.mode, retrieved?.mode)
        assertEquals(schedule.sensitivity, retrieved!!.sensitivity, 0.001)
        assertEquals(schedule.maxStorageMb, retrieved?.maxStorageMb)
    }

    @Test
    fun testCancelSchedule() {
        val schedule = RecordingSchedule(
            id = "test-schedule-2",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            repeat = "once",
            timezone = "UTC",
            mode = "detect",
            sensitivity = 0.6,
            maxStorageMb = 200
        )

        ScheduleStore.add(context, schedule)
        assertTrue(ScheduleStore.list(context).isNotEmpty())

        val cancelled = ScheduleStore.cancel(context, "test-schedule-2")
        assertTrue(cancelled)
        assertTrue(ScheduleStore.list(context).isEmpty())
    }

    @Test
    fun testRescheduleNextDaily() {
        val schedule = RecordingSchedule(
            id = "test-schedule-3",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            repeat = "daily",
            timezone = "UTC",
            mode = "detect",
            sensitivity = 0.6,
            maxStorageMb = 200
        )

        ScheduleStore.add(context, schedule)
        ScheduleStore.rescheduleNext(context, "test-schedule-3")

        val nextSchedule = ScheduleStore.get(context, "test-schedule-3")
        assertNotNull(nextSchedule)
        // 24 hours in millis = 24 * 60 * 60 * 1000 = 86400000
        assertEquals(1000L + 86400000L, nextSchedule?.startTimeMillis)
        assertEquals(2000L + 86400000L, nextSchedule?.endTimeMillis)
    }

    @Test
    fun testAlarmsAreSet() {
        val schedule = RecordingSchedule(
            id = "test-schedule-4",
            startTimeMillis = 5000L,
            endTimeMillis = 10000L,
            repeat = "once",
            timezone = "UTC",
            mode = "detect",
            sensitivity = 0.6,
            maxStorageMb = 200
        )

        ScheduleStore.add(context, schedule)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = shadowOf(alarmManager)
        
        val nextScheduledAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull(nextScheduledAlarm)
    }
}
