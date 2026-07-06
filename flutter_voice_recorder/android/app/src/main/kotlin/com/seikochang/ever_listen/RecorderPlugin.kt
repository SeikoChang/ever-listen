package com.seikochang.ever_listen

import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel

/** RecorderPlugin
 * Minimal MethodChannel + EventChannel plugin skeleton. Implement native audio capture, VAD, file writing and background behavior.
 */
class RecorderPlugin: FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel : MethodChannel
    private lateinit var eventChannel: EventChannel
    private var context: Context? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "ever_listen/recorder")
        channel.setMethodCallHandler(this)
        eventChannel = EventChannel(binding.binaryMessenger, "ever_listen/events")
        // TODO: set up EventChannel.StreamHandler to emit events like 'speechStarted', 'speechEnded', 'fileReady'
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startRecording" -> {
                val mode = call.argument<String>("mode") ?: "detect"
                // TODO: start service / native audio + VAD
                result.success(null)
            }
            "stopRecording" -> {
                // TODO: stop service / native audio
                result.success(null)
            }
            "setSensitivity" -> {
                val s = call.argument<Double>("sensitivity") ?: 0.6
                // TODO: pass sensitivity to VAD
                result.success(null)
            }
            "setMaxStorageMb" -> {
                val mb = call.argument<Int>("maxStorageMb") ?: 200
                // TODO: configure storage manager
                result.success(null)
            }
            "getStatus" -> {
                // TODO: return status map
                result.success(mapOf("running" to false))
            }
            else -> result.notImplemented()
        }
    }
}
