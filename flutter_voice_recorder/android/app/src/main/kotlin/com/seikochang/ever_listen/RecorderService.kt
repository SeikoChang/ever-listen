package com.seikochang.ever_listen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log
import io.flutter.plugin.common.EventChannel

/**
 * RecorderService: Foreground service for continuous audio capture in Detect or Monitoring mode.
 * - Captures 16-bit PCM mono at 16 kHz
 * - Processes frames (30 ms) through VAD
 * - Maintains pre-roll circular buffer
 * - Emits events: speechStarted, speechEnded, fileReady
 */
class RecorderService : Service() {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var vadProcessor: VADProcessor? = null
    private var frameBuffer: AudioFrameBuffer? = null
    private var eventSink: EventChannel.EventSink? = null
    private var mode: String = "detect"
    private var sensitivity: Double = 0.6
    private var maxStorageMb: Int = 200
    private var currentOutputFile: File? = null
    private var outputFileWriter: AudioFileWriter? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "RecorderService"

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE_MS = 30
        private const val FRAME_SIZE_SAMPLES = (SAMPLE_RATE * FRAME_SIZE_MS) / 1000  // 480 samples
        private const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2  // 16-bit = 2 bytes
        private const val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        private const val PRE_ROLL_DURATION_MS = 1500  // 1.5 seconds pre-roll buffer
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize frame buffer (pre-roll circular buffer)
        val preRollFrames = (PRE_ROLL_DURATION_MS / FRAME_SIZE_MS)
        frameBuffer = AudioFrameBuffer(preRollFrames)
        
        // Initialize VAD processor (stub for now, will use native library later)
        vadProcessor = MockVADProcessor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY
        
        when (action) {
            "START_RECORDING" -> {
                mode = intent.getStringExtra("mode") ?: "detect"
                sensitivity = intent.getDoubleExtra("sensitivity", 0.6)
                startRecording()
            }
            "STOP_RECORDING" -> stopRecording()
            "SET_SENSITIVITY" -> {
                sensitivity = intent.getDoubleExtra("sensitivity", 0.6)
                vadProcessor?.setSensitivity(sensitivity)
            }
            "SET_MAX_STORAGE" -> {
                maxStorageMb = intent.getIntExtra("maxStorageMb", 200)
            }
        }
        
        return START_STICKY
    }

    private fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return
        }

        Log.d(TAG, "Starting recording in $mode mode, sensitivity: $sensitivity")
        
        // Create output directory
        val outputDir = File(getExternalFilesDir(null), "recordings")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        
        // Initialize audio file writer
        outputFileWriter = AudioFileWriter(
            outputDir = outputDir,
            sampleRate = SAMPLE_RATE,
            channelCount = 1,
            bitsPerSample = 16
        )
        
        // Show foreground notification
        showNotification()
        
        // Initialize AudioRecord
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                emitEvent("error", mapOf("message" to "AudioRecord initialization failed"))
                return
            }
            
            isRecording = true
            audioRecord?.startRecording()
            
            // Start audio capture thread
            recordingThread = thread(name = "AudioCaptureThread") {
                audioCapturLoop()
            }
            
            emitEvent("recordingStarted", mapOf("mode" to mode))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            emitEvent("error", mapOf("message" to e.message))
        }
    }

    private fun audioCapturLoop() {
        val frameBuffer = ByteArray(FRAME_SIZE_BYTES)
        var frameCount = 0
        var speechActive = false
        var silenceFrameCount = 0
        
        try {
            while (isRecording && audioRecord != null) {
                // Read frame from microphone
                val bytesRead = audioRecord!!.read(frameBuffer, 0, FRAME_SIZE_BYTES)
                
                if (bytesRead != FRAME_SIZE_BYTES) {
                    Log.w(TAG, "Expected $FRAME_SIZE_BYTES bytes, got $bytesRead")
                    continue
                }
                
                frameCount++
                
                // Add frame to pre-roll buffer
                this.frameBuffer?.addFrame(frameBuffer.copyOf())
                
                // Process frame through VAD
                val isSpeech = vadProcessor?.processFrame(frameBuffer) ?: false
                
                if (isSpeech) {
                    silenceFrameCount = 0
                    
                    if (!speechActive) {
                        // Speech started
                        speechActive = true
                        Log.d(TAG, "Speech detected at frame $frameCount")
                        emitEvent("speechStarted", mapOf("frame" to frameCount))
                        
                        // Create output file for this speech segment
                        currentOutputFile = outputFileWriter?.createNewFile("speech_${System.currentTimeMillis()}.wav")
                        
                        // Write pre-roll frames to file
                        this.frameBuffer?.getFrames()?.forEach { frame ->
                            outputFileWriter?.writeFrame(frame)
                        }
                    }
                    
                    // Write current frame to file
                    outputFileWriter?.writeFrame(frameBuffer)
                } else {
                    if (speechActive) {
                        silenceFrameCount++
                        
                        // Write silence frame to file
                        outputFileWriter?.writeFrame(frameBuffer)
                        
                        // Check if silence timeout reached (e.g., 500ms silence = ~17 frames @ 30ms)
                        val silenceThresholdFrames = 17
                        if (silenceFrameCount >= silenceThresholdFrames) {
                            // Speech ended
                            speechActive = false
                            Log.d(TAG, "Speech ended at frame $frameCount")
                            emitEvent("speechEnded", mapOf("frame" to frameCount))
                            
                            // Close current file
                            outputFileWriter?.closeCurrentFile()
                            currentOutputFile?.let { file ->
                                emitEvent("fileReady", mapOf("filePath" to file.absolutePath))
                            }
                            currentOutputFile = null
                        }
                    }
                }
                
                // Log progress every 100 frames (~3 seconds)
                if (frameCount % 100 == 0) {
                    Log.d(TAG, "Processed $frameCount frames, speech active: $speechActive")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in audio capture loop", e)
            emitEvent("error", mapOf("message" to e.message))
        } finally {
            Log.d(TAG, "Audio capture loop ended after $frameCount frames")
        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Recording not in progress")
            return
        }
        
        Log.d(TAG, "Stopping recording")
        isRecording = false
        
        try {
            // Stop audio capture
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            // Wait for recording thread to finish
            recordingThread?.join(5000)  // timeout 5 seconds
            recordingThread = null
            
            // Close output file
            outputFileWriter?.closeCurrentFile()
            outputFileWriter = null
            currentOutputFile = null
            
            // Stop foreground notification
            stopForeground(true)
            
            emitEvent("recordingStopped", mapOf())
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            emitEvent("error", mapOf("message" to e.message))
        }
    }

    private fun showNotification() {
        val channelId = "EverListenRecording"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "Ever Listen Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ever Listen")
            .setContentText("Recording in $mode mode...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        
        startForeground(1, notification)
    }

    fun setEventSink(sink: EventChannel.EventSink?) {
        this.eventSink = sink
    }

    private fun emitEvent(eventType: String, data: Map<String, Any?>) {
        handler.post {
            try {
                eventSink?.success(mapOf(
                    "type" to eventType,
                    "data" to data,
                    "timestamp" to System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting event", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        if (isRecording) {
            stopRecording()
        }
    }
}
