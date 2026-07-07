package com.seikochang.ever_listen

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import android.os.Handler
import android.os.Looper

/** RecorderPlugin
 * MethodChannel + EventChannel plugin for audio recording (Detect, Monitoring, Schedule modes).
 * Manages lifecycle of RecorderService and emits events to Flutter.
 */
class RecorderPlugin: FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel : MethodChannel
    private lateinit var eventChannel: EventChannel
    private var context: Context? = null
    private var recorderService: RecorderService? = null
    private var eventSink: EventChannel.EventSink? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "ever_listen/recorder")
        channel.setMethodCallHandler(this)
        eventChannel = EventChannel(binding.binaryMessenger, "ever_listen/events")
        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                eventSink = events
                // Set event sink in service if already running
                recorderService?.setEventSink(eventSink)
            }

            override fun onCancel(arguments: Any?) {
                eventSink = null
                recorderService?.setEventSink(null)
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
            "getStatus" -> handleGetStatus(result)
            else -> result.notImplemented()
        }
    }

    private fun handleStartRecording(call: MethodCall, result: MethodChannel.Result) {
        val mode = call.argument<String>("mode") ?: "detect"
        val sensitivity = call.argument<Double>("sensitivity") ?: 0.6
        
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
            }
            context?.startService(intent)
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
        // TODO: Query RecorderService for current status
        result.success(mapOf(
            "running" to false,
            "mode" to "detect",
            "sensitivity" to 0.6,
            "maxStorageMb" to 200,
            "currentFilePath" to "",
            "storageUsedMb" to 0.0
        ))
    }
}
