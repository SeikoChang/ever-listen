package com.seikochang.ever_listen

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject

data class RecordingSchedule(
    val id: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val repeat: String,
    val timezone: String,
    val mode: String,
    val sensitivity: Double,
    val maxStorageMb: Int
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "startTimeMillis" to startTimeMillis,
        "endTimeMillis" to endTimeMillis,
        "repeat" to repeat,
        "timezone" to timezone,
        "mode" to mode,
        "sensitivity" to sensitivity,
        "maxStorageMb" to maxStorageMb
    )
}

object ScheduleStore {
    private const val PREFS_NAME = "ever_listen_schedules"
    private const val KEY_IDS = "schedule_ids"
    private const val ACTION_START = "com.seikochang.ever_listen.SCHEDULE_START"
    private const val ACTION_STOP = "com.seikochang.ever_listen.SCHEDULE_STOP"
    private const val CURRENT_SCHEMA_VERSION = 2

    fun add(context: Context, schedule: RecordingSchedule) {
        get(context, schedule.id)?.let { cancelAlarms(context, it) }
        save(context, schedule)
        setAlarms(context, schedule)
    }

    fun cancel(context: Context, id: String): Boolean {
        val schedule = get(context, id) ?: return false
        cancelAlarms(context, schedule)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = ids(context).filterNot { it == id }.toSet()
        prefs.edit().remove(key(id)).putStringSet(KEY_IDS, ids).apply()
        return true
    }

    fun list(context: Context): List<RecordingSchedule> = ids(context).mapNotNull { get(context, it) }

    fun get(context: Context, id: String): RecordingSchedule? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key(id), null)
            ?: return null
        val schedule = if (raw.trimStart().startsWith("{")) {
            decodeJson(id, raw)
        } else {
            decodeLegacy(id, raw)
        } ?: return null

        // Migrate the legacy delimiter format on first successful read.
        if (!raw.trimStart().startsWith("{")) save(context, schedule)
        return schedule
    }

    fun rescheduleNext(context: Context, id: String) {
        val current = get(context, id) ?: return
        val interval = when (current.repeat) {
            "daily" -> 24L * 60L * 60L * 1000L
            "weekly" -> 7L * 24L * 60L * 60L * 1000L
            else -> {
                cancel(context, id)
                return
            }
        }

        val next = current.copy(
            startTimeMillis = current.startTimeMillis + interval,
            endTimeMillis = current.endTimeMillis + interval
        )
        save(context, next)
        setAlarms(context, next)
    }

    private fun save(context: Context, schedule: RecordingSchedule) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = ids(context).plus(schedule.id).toSet()
        val encoded = JSONObject().apply {
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("id", schedule.id)
            put("startTimeMillis", schedule.startTimeMillis)
            put("endTimeMillis", schedule.endTimeMillis)
            put("repeat", schedule.repeat)
            put("timezone", schedule.timezone)
            put("mode", schedule.mode)
            put("sensitivity", schedule.sensitivity)
            put("maxStorageMb", schedule.maxStorageMb)
        }.toString()
        prefs.edit().putStringSet(KEY_IDS, ids).putString(key(schedule.id), encoded).apply()
    }

    private fun decodeJson(id: String, raw: String): RecordingSchedule? = try {
        val json = JSONObject(raw)
        if (json.optInt("schemaVersion", -1) != CURRENT_SCHEMA_VERSION) return null
        RecordingSchedule(
            id = id,
            startTimeMillis = json.getLong("startTimeMillis"),
            endTimeMillis = json.getLong("endTimeMillis"),
            repeat = json.getString("repeat"),
            timezone = json.getString("timezone"),
            mode = json.getString("mode"),
            sensitivity = json.getDouble("sensitivity"),
            maxStorageMb = json.getInt("maxStorageMb")
        )
    } catch (_: Exception) {
        null
    }

    private fun decodeLegacy(id: String, raw: String): RecordingSchedule? {
        val parts = raw.split("|")
        if (parts.size != 8 || parts[7] != "v1") return null
        return RecordingSchedule(
            id = id,
            startTimeMillis = parts[0].toLongOrNull() ?: return null,
            endTimeMillis = parts[1].toLongOrNull() ?: return null,
            repeat = parts[2],
            timezone = parts[3],
            mode = parts[4],
            sensitivity = parts[5].toDoubleOrNull() ?: 0.6,
            maxStorageMb = parts[6].toIntOrNull() ?: 200
        )
    }

    private fun ids(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_IDS, emptySet()) ?: emptySet()

    private fun key(id: String) = "schedule_$id"

    private fun setAlarms(context: Context, schedule: RecordingSchedule) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setAlarm(alarmManager, schedule.startTimeMillis, intent(context, schedule, ACTION_START, 1))
        setAlarm(alarmManager, schedule.endTimeMillis, intent(context, schedule, ACTION_STOP, 2))
    }

    private fun setAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // Boot recovery should not crash if the user revoked exact-alarm access.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and the set call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarms(context: Context, schedule: RecordingSchedule) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(intent(context, schedule, ACTION_START, 1))
        alarmManager.cancel(intent(context, schedule, ACTION_STOP, 2))
    }

    private fun intent(
        context: Context,
        schedule: RecordingSchedule,
        action: String,
        requestOffset: Int
    ): PendingIntent {
        val intent = Intent(context, ScheduleRecorderReceiver::class.java).apply {
            this.action = action
            putExtra("scheduleId", schedule.id)
        }
        val immutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
        return PendingIntent.getBroadcast(context, schedule.id.hashCode() + requestOffset, intent, flags)
    }
}
