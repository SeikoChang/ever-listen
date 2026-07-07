package com.seikochang.ever_listen

import android.util.Log
import kotlin.math.sqrt

/**
 * VADProcessor: Interface for voice activity detection.
 * Implementations will use WebRTC VAD (native library) or mock/RMS-based fallback.
 */
interface VADProcessor {
    fun processFrame(frameBytes: ByteArray): Boolean  // true if speech detected
    fun setSensitivity(sensitivity: Double)  // 0.0 = least sensitive, 1.0 = most sensitive
}

/**
 * MockVADProcessor: Simple RMS-based VAD for initial testing before native library integration.
 * Detects speech by checking if RMS energy exceeds a sensitivity-dependent threshold.
 */
class MockVADProcessor : VADProcessor {
    private var sensitivity = 0.6
    private val TAG = "MockVADProcessor"

    override fun setSensitivity(sensitivity: Double) {
        this.sensitivity = sensitivity.coerceIn(0.0, 1.0)
        Log.d(TAG, "Sensitivity set to $this.sensitivity")
    }

    override fun processFrame(frameBytes: ByteArray): Boolean {
        // Convert byte array to short array (16-bit PCM)
        val shortArray = ShortArray(frameBytes.size / 2)
        for (i in shortArray.indices) {
            val b1 = frameBytes[2 * i].toInt() and 0xFF
            val b2 = (frameBytes[2 * i + 1].toInt() shl 8) or b1
            shortArray[i] = b2.toShort()
        }

        // Calculate RMS (Root Mean Square) energy
        val rms = calculateRMS(shortArray)

        // Threshold: lower sensitivity means higher threshold (less sensitive)
        // Higher sensitivity means lower threshold (more sensitive)
        val threshold = 100.0 * (1.0 - sensitivity + 0.1)  // Range: ~10 to 110

        val isSpeech = rms > threshold
        
        // Uncomment for debug logging (verbose)
        // Log.d(TAG, "RMS: $rms, Threshold: $threshold, Speech: $isSpeech")

        return isSpeech
    }

    private fun calculateRMS(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        
        var sum = 0.0
        for (sample in samples) {
            sum += (sample.toInt() * sample.toInt()).toDouble()
        }
        
        val meanSquare = sum / samples.size
        return sqrt(meanSquare)
    }
}

/**
 * WebRTCVADProcessor: Real VAD using WebRTC C library via JNI.
 * TODO: Implement after compiling libeverlisten_vad.so
 */
class WebRTCVADProcessor : VADProcessor {
    private val TAG = "WebRTCVADProcessor"
    private var vadHandle: Long = 0
    private var aggressiveness: Int = 2  // 0-3, default 2

    init {
        try {
            System.loadLibrary("everlisten_vad")
            vadHandle = VADNativeBridge.initVad(16000, aggressiveness)
            Log.d(TAG, "WebRTC VAD initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC VAD", e)
            throw e
        }
    }

    override fun setSensitivity(sensitivity: Double) {
        // Map 0.0-1.0 to aggressiveness 0-3
        aggressiveness = (sensitivity * 3).toInt().coerceIn(0, 3)
        Log.d(TAG, "Aggressiveness set to $aggressiveness")
        
        try {
            VADNativeBridge.destroyVad(vadHandle)
            vadHandle = VADNativeBridge.initVad(16000, aggressiveness)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting sensitivity", e)
        }
    }

    override fun processFrame(frameBytes: ByteArray): Boolean {
        return try {
            VADNativeBridge.processFrame(vadHandle, frameBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
            false
        }
    }

    fun destroy() {
        try {
            if (vadHandle != 0L) {
                VADNativeBridge.destroyVad(vadHandle)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying VAD", e)
        }
    }
}

/**
 * VADNativeBridge: JNI bridge to WebRTC VAD C library.
 * TODO: Implement after compiling native library
 */
object VADNativeBridge {
    init {
        try {
            System.loadLibrary("everlisten_vad")
        } catch (e: UnsatisfiedLinkError) {
            // Library not available yet; will fall back to MockVADProcessor
        }
    }

    external fun initVad(sampleRate: Int, aggressiveness: Int): Long
    external fun processFrame(vadHandle: Long, frameBytes: ByteArray): Boolean
    external fun destroyVad(vadHandle: Long): Boolean
}
