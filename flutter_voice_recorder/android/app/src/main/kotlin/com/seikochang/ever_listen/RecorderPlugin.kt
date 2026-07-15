package com.seikochang.ever_listen

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import java.util.UUID

/** RecorderPlugin
 * MethodChannel + EventChannel plugin for audio recording (Detect, Monitoring, Schedule modes).
 * Manages lifecycle of RecorderService and emits events to Flutter.
 */
class RecorderPlugin: FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel : MethodChannel
    private lateinit var eventChannel: EventChannel
    private var context: Context? = null
    private var eventSink: EventChannel.EventSink? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "ever_listen/recorder")
        channel.setMethodCallHandler(this)
        eventChannel = EventChannel(binding.binaryMessenger, "ever_listen/events")
        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                eventSink = events
                RecorderService.setEventSink(eventSink)
            }

            override fun onCancel(arguments: Any?) {
                eventSink = null
                RecorderService.setEventSink(null)
            }
        })
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startRecording" -> handleStartRecording(call, result)
            "stopRecording" -> handleStopRecording(result)
            "setSensitivity" -> handleSetSensitivity(call, result)
            "setMaxStorageMb" -> handleSetMaxStorage(call, result)
            "scheduleRecording" -> handleScheduleRecording(call, result)
            "cancelSchedule" -> handleCancelSchedule(call, result)
            "getSchedules" -> handleGetSchedules(result)
            "getStatus" -> handleGetStatus(result)
            else -> result.notImplemented()
        }
    }

    private fun handleStartRecording(call: MethodCall, result: MethodChannel.Result) {
        val mode = call.argument<String>("mode") ?: "detect"
        val sensitivity = call.argument<Double>("sensitivity") ?: 0.6
        val maxStorageMb = call.argument<Int>("maxStorageMb") ?: 200
        
        // Check RECORD_AUDIO permission
        if (context?.let { ContextCompat.checkSelfPermission(it, Manifest.permission.RECORD_AUDIO) } 
            != PackageManager.PERMISSION_GRANTED) {
            result.error("PERMISSION_DENIED", "RECORD_AUDIO permission not granted", null)
            return
        }

        try {
            val intent = Intent(context, RecorderService::class.java).apply {
                action = "START_RECORDING"
                putExtra("mode", mode)
                putExtra("sensitivity", sensitivity)
                putExtra("maxStorageMb", maxStorageMb)
            }
            val appContext = context ?: throw IllegalStateException("Plugin is not attached")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            result.success(mapOf("status" to "started", "mode" to mode))
        } catch (e: Exception) {
            result.error("START_FAILED", e.message, null)
        }
    }

    private fun handleStopRecording(result: MethodChannel.Result) {
        try {
            val intent = Intent(context, RecorderService::class.java).apply {
                action = "STOP_RECORDING"
            }
            context?.startService(intent)
            result.success(mapOf("status" to "stopped"))
        } catch (e: Exception) {
            result.error("STOP_FAILED", e.message, null)
        }
    }

    private fun handleSetSensitivity(call: MethodCall, result: MethodChannel.Result) {
        val sensitivity = call.argument<Double>("sensitivity") ?: 0.6
        try {
            val intent = Intent(context, RecorderService::class.java).apply {
                action = "SET_SENSITIVITY"
                putExtra("sensitivity", sensitivity)
            }
            context?.startService(intent)
            result.success(mapOf("sensitivity" to sensitivity))
        } catch (e: Exception) {
            result.error("SET_SENSITIVITY_FAILED", e.message, null)
        }
    }

    private fun handleSetMaxStorage(call: MethodCall, result: MethodChannel.Result) {
        val maxMb = call.argument<Int>("maxStorageMb") ?: 200
        try {
            val intent = Intent(context, RecorderService::class.java).apply {
                action = "SET_MAX_STORAGE"
                putExtra("maxStorageMb", maxMb)
            }
            context?.startService(intent)
            result.success(mapOf("maxStorageMb" to maxMb))
        } catch (e: Exception) {
            result.error("SET_STORAGE_FAILED", e.message, null)
        }
    }

    private fun handleGetStatus(result: MethodChannel.Result) {
        val appContext = context
        if (appContext == null) {
            result.error("NO_CONTEXT", "Plugin is not attached", null)
            return
        }

        val status = RecorderService.statusSnapshot(appContext).toMutableMap()
        status["schedules"] = ScheduleStore.list(appContext).map { it.toMap() }
        result.success(status)
    }

    private fun handleScheduleRecording(call: MethodCall, result: MethodChannel.Result) {
        val appContext = context
        if (appContext == null) {
            result.error("NO_CONTEXT", "Plugin is not attached", null)
            return
        }

        val startTimeMillis = call.argument<Number>("startTimeMillis")?.toLong()
        val endTimeMillis = call.argument<Number>("endTimeMillis")?.toLong()
        if (startTimeMillis == null || endTimeMillis == null || endTimeMillis <= startTimeMillis) {
            result.error("INVALID_SCHEDULE", "Schedule requires startTimeMillis before endTimeMillis", null)
            return
        }

        val schedule = RecordingSchedule(
            id = call.argument<String>("id") ?: UUID.randomUUID().toString(),
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            repeat = call.argument<String>("repeat") ?: "once",
            timezone = call.argument<String>("timezone") ?: "UTC",
            mode = call.argument<String>("mode") ?: "schedule",
            sensitivity = call.argument<Double>("sensitivity") ?: 0.6,
            maxStorageMb = call.argument<Int>("maxStorageMb") ?: 200
        )

        try {
            ScheduleStore.add(appContext, schedule)
            result.success(schedule.toMap())
        } catch (e: SecurityException) {
            result.error("SCHEDULE_PERMISSION_DENIED", e.message, null)
        } catch (e: Exception) {
            result.error("SCHEDULE_FAILED", e.message, null)
        }
    }

    private fun handleCancelSchedule(call: MethodCall, result: MethodChannel.Result) {
        val appContext = context
        val id = call.argument<String>("id")
        if (appContext == null || id == null) {
            result.error("INVALID_ARGUMENT", "cancelSchedule requires an id", null)
            return
        }

        result.success(mapOf("cancelled" to ScheduleStore.cancel(appContext, id), "id" to id))
    }

    private fun handleGetSchedules(result: MethodChannel.Result) {
        val appContext = context
        if (appContext == null) {
            result.error("NO_CONTEXT", "Plugin is not attached", null)
            return
        }
        result.success(ScheduleStore.list(appContext).map { it.toMap() })
    }
}
