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
import android.util.Log
import io.flutter.plugin.common.EventChannel
import java.util.concurrent.atomic.AtomicReference

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
    private var mode: String = "detect"
    private var sensitivity: Double = 0.6
    private var maxStorageMb: Int = 200
    private var currentOutputFile: File? = null
    private var outputFileWriter: AudioFileWriter? = null
    private var scheduledSession = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "RecorderService"

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE_MS = 30
        private const val FRAME_SIZE_SAMPLES = (SAMPLE_RATE * FRAME_SIZE_MS) / 1000  // 480 samples
        private const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2  // 16-bit = 2 bytes
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        private const val PRE_ROLL_DURATION_MS = 1500  // 1.5 seconds pre-roll buffer
        private const val MONITOR_CHUNK_DURATION_MS = 60_000L
        private val eventSinkRef = AtomicReference<EventChannel.EventSink?>()

        @Volatile private var statusRunning = false
        @Volatile private var statusMode = "detect"
        @Volatile private var statusSensitivity = 0.6
        @Volatile private var statusMaxStorageMb = 200
        @Volatile private var statusCurrentFilePath = ""
        @Volatile private var statusStorageUsedMb = 0.0

        fun setEventSink(sink: EventChannel.EventSink?) {
            eventSinkRef.set(sink)
        }

        fun statusSnapshot(context: Context): Map<String, Any?> {
            updateStorageUsed(context)
            return mapOf(
                "running" to statusRunning,
                "mode" to statusMode,
                "sensitivity" to statusSensitivity,
                "maxStorageMb" to statusMaxStorageMb,
                "currentFilePath" to statusCurrentFilePath,
                "storageUsedMb" to statusStorageUsedMb
            )
        }

        private fun updateStorageUsed(context: Context) {
            val outputDir = File(context.getExternalFilesDir(null), "recordings")
            statusStorageUsedMb = outputDir.listFiles()
                ?.filter { it.isFile }
                ?.sumOf { it.length() }
                ?.toDouble()
                ?.div(1024.0 * 1024.0) ?: 0.0
        }
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
        
        vadProcessor = try {
            WebRTCVADProcessor()
        } catch (_: Throwable) {
            MockVADProcessor()
        }
        vadProcessor?.setSensitivity(sensitivity)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY
        
        when (action) {
            "START_RECORDING" -> {
                mode = intent.getStringExtra("mode") ?: "detect"
                sensitivity = intent.getDoubleExtra("sensitivity", 0.6)
                maxStorageMb = intent.getIntExtra("maxStorageMb", maxStorageMb)
                scheduledSession = intent.getBooleanExtra("scheduled", false)
                startRecording()
            }
            "STOP_RECORDING" -> {
                scheduledSession = intent.getBooleanExtra("scheduled", scheduledSession)
                stopRecording()
            }
            "SET_SENSITIVITY" -> {
                sensitivity = intent.getDoubleExtra("sensitivity", 0.6)
                vadProcessor?.setSensitivity(sensitivity)
                statusSensitivity = sensitivity
            }
            "SET_MAX_STORAGE" -> {
                maxStorageMb = intent.getIntExtra("maxStorageMb", 200)
                statusMaxStorageMb = maxStorageMb
                enforceStorageLimit()
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
        statusRunning = true
        statusMode = mode
        statusSensitivity = sensitivity
        statusMaxStorageMb = maxStorageMb
        
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
                statusRunning = false
                outputFileWriter = null
                stopForeground(true)
                emitEvent("error", mapOf("message" to "AudioRecord initialization failed"))
                return
            }
            
            isRecording = true
            audioRecord?.startRecording()
            
            // Start audio capture thread
            recordingThread = thread(name = "AudioCaptureThread") {
                audioCaptureLoop()
            }
            
            emitEvent("recordingStarted", mapOf("mode" to mode))
            if (scheduledSession) {
                emitEvent("scheduleStarted", mapOf("mode" to mode))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            statusRunning = false
            try {
                audioRecord?.release()
            } catch (_: Exception) {
            }
            audioRecord = null
            outputFileWriter = null
            stopForeground(true)
            emitEvent("error", mapOf("message" to e.message))
        }
    }

    private fun audioCaptureLoop() {
        val frameBuffer = ByteArray(FRAME_SIZE_BYTES)
        var frameCount = 0
        var speechActive = false
        var silenceFrameCount = 0
        var monitorChunkStartedAt = 0L
        
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
                
                if (mode == "monitor" || mode == "schedule") {
                    if (currentOutputFile == null) {
                        currentOutputFile = outputFileWriter?.createNewFile("${mode}_${System.currentTimeMillis()}.wav")
                        statusCurrentFilePath = currentOutputFile?.absolutePath ?: ""
                        monitorChunkStartedAt = System.currentTimeMillis()
                    }

                    outputFileWriter?.writeFrame(frameBuffer)

                    if (System.currentTimeMillis() - monitorChunkStartedAt >= MONITOR_CHUNK_DURATION_MS) {
                        rotateCurrentFile()
                        enforceStorageLimit()
                        monitorChunkStartedAt = System.currentTimeMillis()
                    }

                    if (frameCount % 100 == 0) {
                        Log.d(TAG, "Processed $frameCount frames in $mode mode")
                    }
                    continue
                }

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
                        statusCurrentFilePath = currentOutputFile?.absolutePath ?: ""
                        
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
                            rotateCurrentFile()
                            enforceStorageLimit()
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
            if (mode == "monitor" || mode == "schedule") {
                rotateCurrentFile()
                enforceStorageLimit()
            }
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
            statusCurrentFilePath = ""
            statusRunning = false
            enforceStorageLimit()
            
            // Stop foreground notification
            stopForeground(true)
            
            emitEvent("recordingStopped", mapOf())
            if (scheduledSession) {
                emitEvent("scheduleEnded", mapOf("mode" to mode))
                scheduledSession = false
            }
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

    private fun emitEvent(eventType: String, data: Map<String, Any?>) {
        handler.post {
            try {
                eventSinkRef.get()?.success(mapOf(
                    "type" to eventType,
                    "data" to data,
                    "timestamp" to System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting event", e)
            }
        }
    }

    private fun rotateCurrentFile() {
        val file = currentOutputFile
        outputFileWriter?.closeCurrentFile()
        if (file != null) {
            emitEvent("fileReady", mapOf("filePath" to file.absolutePath))
        }
        currentOutputFile = null
        statusCurrentFilePath = ""
    }

    private fun enforceStorageLimit() {
        val outputDir = File(getExternalFilesDir(null), "recordings")
        val files = outputDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?.toMutableList() ?: return

        var totalBytes = files.sumOf { it.length() }
        val maxBytes = maxStorageMb.toLong() * 1024L * 1024L

        while (totalBytes > maxBytes && files.isNotEmpty()) {
            val oldest = files.removeAt(0)
            val size = oldest.length()
            if (oldest.absolutePath != currentOutputFile?.absolutePath && oldest.delete()) {
                totalBytes -= size
                emitEvent("storagePruned", mapOf("filePath" to oldest.absolutePath))
            } else {
                break
            }
        }

        statusStorageUsedMb = totalBytes.toDouble() / (1024.0 * 1024.0)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        if (isRecording) {
            stopRecording()
        }
        statusRunning = false
    }
}
