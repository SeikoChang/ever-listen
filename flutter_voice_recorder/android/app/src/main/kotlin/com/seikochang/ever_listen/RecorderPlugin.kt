package com.seikochang.ever_listen

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.app.Activity
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import java.util.UUID

/** RecorderPlugin
 * MethodChannel + EventChannel plugin for audio recording (Detect, Monitoring, Schedule modes).
 * Manages lifecycle of RecorderService and emits events to Flutter.
 */
class RecorderPlugin: FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware {
    private lateinit var channel : MethodChannel
    private lateinit var eventChannel: EventChannel
    private var context: Context? = null
    private var activity: Activity? = null
    private var eventSink: EventChannel.EventSink? = null
    private var permissionResult: MethodChannel.Result? = null

    // Test-only: allows tests to intercept startService calls
    @setparam:VisibleForTesting
    @get:VisibleForTesting
    var testServiceStarter: ((Intent) -> Unit)? = null

    companion object {
        private const val REQUEST_RECORD_AUDIO = 4101
        private const val REQUEST_NOTIFICATIONS = 4102
    }

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
            "requestPermissions" -> handleRequestPermissions(result)
            "getStatus" -> handleGetStatus(result)
            else -> result.notImplemented()
        }
    }

    private fun handleStartRecording(call: MethodCall, result: MethodChannel.Result) {
        val mode = call.argument<String>("mode") ?: "detect"
        val sensitivity = (call.argument<Number>("sensitivity")?.toDouble() ?: 0.6).coerceIn(0.0, 1.0)
        val maxStorageMb = (call.argument<Number>("maxStorageMb")?.toInt() ?: 200).coerceAtLeast(1)

        if (mode !in setOf("detect", "monitor", "schedule")) {
            result.error("INVALID_MODE", "mode must be detect, monitor, or schedule", null)
            return
        }
        
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
            if (testServiceStarter != null) {
                testServiceStarter!!.invoke(intent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        if (startTimeMillis <= System.currentTimeMillis()) {
            result.error("INVALID_SCHEDULE", "Schedule startTimeMillis must be in the future", null)
            return
        }

        val repeat = call.argument<String>("repeat") ?: "once"
        if (repeat !in setOf("once", "daily", "weekly")) {
            result.error("INVALID_SCHEDULE", "repeat must be once, daily, or weekly", null)
            return
        }

        // Grant exact alarm permission in tests
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                activity?.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${appContext.packageName}")
                    }
                )
                result.error(
                    "SCHEDULE_EXACT_ALARM_REQUIRED",
                    "Exact alarm permission is required for scheduled recording",
                    mapOf("settingsAction" to Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                )
                return
            }
        }

        val schedule = RecordingSchedule(
            id = call.argument<String>("id") ?: UUID.randomUUID().toString(),
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            repeat = repeat,
            timezone = call.argument<String>("timezone") ?: "UTC",
            mode = call.argument<String>("mode") ?: "schedule",
            sensitivity = (call.argument<Number>("sensitivity")?.toDouble() ?: 0.6).coerceIn(0.0, 1.0),
            maxStorageMb = (call.argument<Number>("maxStorageMb")?.toInt() ?: 200).coerceAtLeast(1)
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

    private fun handleRequestPermissions(result: MethodChannel.Result) {
        val currentActivity = activity
        val appContext = context
        if (currentActivity == null || appContext == null) {
            result.error("NO_ACTIVITY", "An attached Android activity is required", null)
            return
        }

        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissions.isEmpty()) {
            result.success(mapOf("microphone" to true, "notifications" to true))
            return
        }

        permissionResult = result
        currentActivity.requestPermissions(permissions.toTypedArray(), REQUEST_RECORD_AUDIO)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener { requestCode, permissions, grantResults ->
            onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_RECORD_AUDIO) return false
        val microphoneGranted = ContextCompat.checkSelfPermission(
            context ?: return false,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context!!, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        permissionResult?.success(mapOf("microphone" to microphoneGranted, "notifications" to notificationsGranted))
        permissionResult = null
        return true
    }
}
