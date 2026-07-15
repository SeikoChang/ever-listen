package com.seikochang.ever_listen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ScheduleRecorderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra("scheduleId") ?: return
        val schedule = ScheduleStore.get(context, scheduleId) ?: return

        when (intent.action) {
            ACTION_START -> {
                val serviceIntent = Intent(context, RecorderService::class.java).apply {
                    action = "START_RECORDING"
                    putExtra("mode", schedule.mode)
                    putExtra("sensitivity", schedule.sensitivity)
                    putExtra("maxStorageMb", schedule.maxStorageMb)
                    putExtra("scheduled", true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            ACTION_STOP -> {
                val serviceIntent = Intent(context, RecorderService::class.java).apply {
                    action = "STOP_RECORDING"
                    putExtra("scheduled", true)
                }
                context.startService(serviceIntent)
                ScheduleStore.rescheduleNext(context, scheduleId)
            }
        }
    }

    companion object {
        const val ACTION_START = "com.seikochang.ever_listen.SCHEDULE_START"
        const val ACTION_STOP = "com.seikochang.ever_listen.SCHEDULE_STOP"
    }
}
