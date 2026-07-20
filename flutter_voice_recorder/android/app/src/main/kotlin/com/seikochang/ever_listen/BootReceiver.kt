package com.seikochang.ever_listen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Device booted. Rescheduling alarms...")
            val now = System.currentTimeMillis()
            val schedules = ScheduleStore.list(context)
            for (schedule in schedules) {
                if (schedule.repeat == "once" && schedule.endTimeMillis <= now) {
                    Log.d(TAG, "Cleaning up expired one-time schedule: ${schedule.id}")
                    ScheduleStore.cancel(context, schedule.id)
                } else {
                    Log.d(TAG, "Rescheduling alarms for schedule: ${schedule.id}")
                    if (schedule.repeat != "once" && schedule.startTimeMillis <= now) {
                        var tempSchedule = schedule
                        val interval = when (schedule.repeat) {
                            "daily" -> 24L * 60L * 60L * 1000L
                            "weekly" -> 7L * 24L * 60L * 60L * 1000L
                            else -> 0L
                        }
                        if (interval > 0L) {
                            while (tempSchedule.startTimeMillis <= now) {
                                tempSchedule = tempSchedule.copy(
                                    startTimeMillis = tempSchedule.startTimeMillis + interval,
                                    endTimeMillis = tempSchedule.endTimeMillis + interval
                                )
                            }
                            Log.d(TAG, "Forwarded recurring schedule ${schedule.id} to start at ${tempSchedule.startTimeMillis}")
                            ScheduleStore.add(context, tempSchedule)
                        } else {
                            ScheduleStore.add(context, schedule)
                        }
                    } else {
                        ScheduleStore.add(context, schedule)
                    }
                }
            }
        }
    }
}
