package com.seikochang.ever_listen

import android.util.Log

/**
 * AudioFrameBuffer: Circular buffer for storing audio frames (pre-roll).
 * Maintains the last N frames for pre-roll capture (e.g., 1.5s of audio before speech detection).
 */
class AudioFrameBuffer(private val maxFrames: Int) {
    private val frames = mutableListOf<ByteArray>()
    private val TAG = "AudioFrameBuffer"

    fun addFrame(frame: ByteArray) {
        if (frames.size >= maxFrames) {
            // Remove oldest frame
            frames.removeAt(0)
        }
        frames.add(frame.copyOf())
    }

    fun getFrames(): List<ByteArray> {
        return frames.toList()
    }

    fun clear() {
        frames.clear()
        Log.d(TAG, "Buffer cleared")
    }

    fun size(): Int = frames.size
}
