package com.seikochang.ever_listen

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioFileWriter: Writes PCM frames to WAV files.
 * Handles WAV header formatting, frame buffering, and file finalization.
 */
class AudioFileWriter(
    private val outputDir: File,
    private val sampleRate: Int,
    private val channelCount: Int,
    private val bitsPerSample: Int
) {
    private var currentFile: File? = null
    private var currentFileWriter: RandomAccessFile? = null
    private var currentFileBytesWritten: Int = 0
    private val TAG = "AudioFileWriter"
    private val bytesPerSample = bitsPerSample / 8
    private val byteRate = sampleRate * channelCount * bytesPerSample
    private val blockAlign = channelCount * bytesPerSample

    fun createNewFile(filename: String): File {
        closeCurrentFile()
        
        val file = File(outputDir, filename)
        currentFile = file
        currentFileWriter = RandomAccessFile(file, "rw")
        currentFileBytesWritten = 0
        
        // Write WAV header (placeholder, will be updated on close)
        writeWAVHeader(0)
        
        Log.d(TAG, "Created new audio file: ${file.absolutePath}")
        return file
    }

    fun writeFrame(frameBytes: ByteArray) {
        val writer = currentFileWriter ?: return
        try {
            writer.write(frameBytes)
            currentFileBytesWritten += frameBytes.size
        } catch (e: Exception) {
            Log.e(TAG, "Error writing frame", e)
        }
    }

    fun closeCurrentFile() {
        val file = currentFile
        val writer = currentFileWriter
        
        if (writer != null && file != null) {
            try {
                // Update WAV header with actual data size
                writer.seek(0)
                writeWAVHeader(currentFileBytesWritten)
                writer.close()
                
                Log.d(TAG, "Closed audio file: ${file.absolutePath}, size: ${file.length()} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing file", e)
            }
        }
        
        currentFile = null
        currentFileWriter = null
        currentFileBytesWritten = 0
    }

    private fun writeWAVHeader(dataSize: Int) {
        val writer = currentFileWriter ?: return
        
        try {
            val buffer = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                
                // RIFF header
                put("RIFF".toByteArray())
                putInt(36 + dataSize)  // file size - 8
                put("WAVE".toByteArray())
                
                // fmt subchunk
                put("fmt ".toByteArray())
                putInt(16)  // subchunk size
                putShort(1)  // audio format (1 = PCM)
                putShort(channelCount.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort(blockAlign.toShort())
                putShort(bitsPerSample.toShort())
                
                // data subchunk
                put("data".toByteArray())
                putInt(dataSize)
            }
            
            writer.write(buffer.array())
        } catch (e: Exception) {
            Log.e(TAG, "Error writing WAV header", e)
        }
    }
}
