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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread
import java.io.File
import android.util.Log
import androidx.annotation.VisibleForTesting
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
    @VisibleForTesting
    var audioRecordFactory: () -> AudioRecord = {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            BUFFER_SIZE
        )
    }
    @Volatile private var isRecording = false
    private var recordingThread: Thread? = null
    private var vadProcessor: VADProcessor? = null
    private var frameBuffer: AudioFrameBuffer? = null
    @VisibleForTesting var mode: String = "detect"
    @VisibleForTesting var sensitivity: Double = 0.6
    @VisibleForTesting var maxStorageMb: Int = 200
    private var currentOutputFile: File? = null
    private var outputFileWriter: AudioFileWriter? = null
    @VisibleForTesting var scheduledSession = false
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "RecorderService"

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE_MS = 30
        private const val FRAME_SIZE_SAMPLES = (SAMPLE_RATE * FRAME_SIZE_MS) / 1000  // 480 samples
        private const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2  // 16-bit = 2 bytes
        private val MIN_BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        private val BUFFER_SIZE = maxOf(FRAME_SIZE_BYTES * 2, MIN_BUFFER_SIZE.takeIf { it > 0 } ?: FRAME_SIZE_BYTES * 2)
        private const val PRE_ROLL_DURATION_MS = 1500  // 1.5 seconds pre-roll buffer
        private const val MONITOR_CHUNK_DURATION_MS = 60_000L
        private val eventSinkRef = AtomicReference<EventChannel.EventSink?>()

        // Test-only: provides a context when the service's mBase is null (direct construction)
        @setparam:VisibleForTesting
        @get:VisibleForTesting
        var testContext: Context? = null

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
            statusStorageUsedMb = RecordingStorage(recordingDirectory(context)).totalBytes()
                .toDouble()
                .div(1024.0 * 1024.0)
        }

        private fun recordingDirectory(context: Context): File =
            context.getExternalFilesDir(null)?.resolve("recordings")
                ?: context.filesDir.resolve("recordings")
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
        val action = intent?.action ?: return START_NOT_STICKY
        
        when (action) {
            "START_RECORDING" -> {
                mode = intent.getStringExtra("mode") ?: "detect"
                sensitivity = intent.getDoubleExtra("sensitivity", 0.6).coerceIn(0.0, 1.0)
                maxStorageMb = intent.getIntExtra("maxStorageMb", maxStorageMb).coerceAtLeast(1)
                scheduledSession = intent.getBooleanExtra("scheduled", false)
                startRecording()
            }
            "STOP_RECORDING" -> {
                scheduledSession = intent.getBooleanExtra("scheduled", scheduledSession)
                stopRecording()
            }
            "SET_SENSITIVITY" -> {
                sensitivity = intent.getDoubleExtra("sensitivity", 0.6).coerceIn(0.0, 1.0)
                vadProcessor?.setSensitivity(sensitivity)
                statusSensitivity = sensitivity
            }
            "SET_MAX_STORAGE" -> {
                maxStorageMb = intent.getIntExtra("maxStorageMb", 200).coerceAtLeast(1)
                statusMaxStorageMb = maxStorageMb
                enforceStorageLimit()
            }
        }
        
        return START_STICKY
    }

    @VisibleForTesting internal fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return
        }

        Log.d(TAG, "Starting recording in $mode mode, sensitivity: $sensitivity")
        if (mode !in setOf("detect", "monitor", "schedule")) {
            emitEvent("error", mapOf("message" to "Unsupported recording mode: $mode"))
            stopSelf()
            return
        }

        frameBuffer?.clear()
        currentOutputFile = null
        statusCurrentFilePath = ""
        statusRunning = true
        statusMode = mode
        statusSensitivity = sensitivity
        statusMaxStorageMb = maxStorageMb
        
        // Create output directory
        val outputDir = recordingDirectory(testContext ?: this)
        val storage = RecordingStorage(outputDir)
        try {
            storage.ensureDirectory()
        } catch (e: IllegalArgumentException) {
            statusRunning = false
            emitEvent("error", mapOf("message" to e.message))
            stopSelf()
            return
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
        acquireWakeLock()
        
        // Initialize AudioRecord
        try {
            audioRecord = audioRecordFactory()
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                statusRunning = false
                outputFileWriter = null
            stopForeground(true)
            releaseWakeLock()
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
            releaseWakeLock()
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
                val bytesRead = audioRecord!!.read(frameBuffer, 0, FRAME_SIZE_BYTES, AudioRecord.READ_BLOCKING)

                if (bytesRead != FRAME_SIZE_BYTES) {
                    if (bytesRead < 0) {
                        Log.e(TAG, "AudioRecord read failed: $bytesRead")
                        emitEvent("error", mapOf("message" to "AudioRecord read failed", "code" to bytesRead))
                        break
                    }
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

    @VisibleForTesting internal fun stopRecording() {
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
            
            // Finalize any open output file and notify Flutter.
            rotateCurrentFile()
            outputFileWriter = null
            currentOutputFile = null
            statusCurrentFilePath = ""
            statusRunning = false
            enforceStorageLimit()
            
            // Stop foreground notification
            stopForeground(true)
            releaseWakeLock()
            
            emitEvent("recordingStopped", mapOf())
            if (scheduledSession) {
                emitEvent("scheduleEnded", mapOf("mode" to mode))
                scheduledSession = false
            }
            stopSelf()
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
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

    @VisibleForTesting internal fun enforceStorageLimit() {
        val storage = RecordingStorage(recordingDirectory(testContext ?: this))
        storage.prune(maxStorageMb, currentOutputFile).forEach { file ->
            emitEvent("storagePruned", mapOf("filePath" to file.absolutePath))
        }
        statusStorageUsedMb = storage.totalBytes().toDouble() / (1024.0 * 1024.0)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EverListen::RecorderService"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        if (isRecording) {
            stopRecording()
        }
        (vadProcessor as? WebRTCVADProcessor)?.destroy()
        vadProcessor = null
        releaseWakeLock()
        statusRunning = false
    }
}
