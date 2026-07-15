package com.seikochang.ever_listen

/**
 * JNI bridge to native VAD (for example WebRTC VAD).
 *
 * The native library is optional during early development. Callers should catch
 * UnsatisfiedLinkError and fall back to an in-Kotlin VAD implementation.
 */
object VADNativeBridge {
    init {
        try {
            System.loadLibrary("everlisten_vad")
        } catch (_: UnsatisfiedLinkError) {
            // Native VAD is not bundled yet; MockVADProcessor is used instead.
        }
    }

    external fun initVad(sampleRate: Int, aggressiveness: Int): Long
    external fun processFrame(vadHandle: Long, frameBytes: ByteArray): Boolean
    external fun destroyVad(vadHandle: Long): Boolean
}
