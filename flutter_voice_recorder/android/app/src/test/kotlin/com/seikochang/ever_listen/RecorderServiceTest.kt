package com.seikochang.ever_listen

import android.content.Context
import android.media.AudioRecord
import io.flutter.plugin.common.EventChannel
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPackageManager
import android.content.pm.PackageManager
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import org.mockito.kotlin.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecorderServiceTest {

    private lateinit var context: Context
    private lateinit var service: RecorderService
    private lateinit var mockAudioRecord: AudioRecord
    private var capturedEvents: MutableList<Map<*, *>> = mutableListOf()
    private val frameQueue = ArrayBlockingQueue<ByteArray>(200)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        capturedEvents.clear()
        frameQueue.clear()

        // Grant RECORD_AUDIO permission via Robolectric shadow
        ShadowPackageManager.shadowOf(context.packageManager).setPermission(
            android.Manifest.permission.RECORD_AUDIO,
            PackageManager.PERMISSION_GRANTED
        )

        // Reset shared preferences
        context.getSharedPreferences("ever_listen_schedules", Context.MODE_PRIVATE)
            .edit().clear().apply()

        // Set up event sink
        RecorderService.setEventSink(object : EventChannel.EventSink {
            override fun success(event: Any?) {
                if (event is Map<*, *>) capturedEvents.add(event.toMutableMap())
            }
            override fun error(code: String, message: String, details: Any) {}
            override fun endOfStream() {}
        })

        // Create service and inject mock AudioRecord
        service = RecorderService()
        service.onCreate()

        mockAudioRecord = mock<AudioRecord>()
        whenever(mockAudioRecord.state).thenReturn(AudioRecord.STATE_INITIALIZED)
        whenever(mockAudioRecord.read(any<ByteArray>(), any<Int>(), any<Int>(), any<Int>())).then { invocation ->
            val buffer = invocation.getArgument<ByteArray>(0)
            val offset = invocation.getArgument<Int>(1)
            val size = invocation.getArgument<Int>(2)
            val frame = frameQueue.poll()
            if (frame == null) -1
            else {
                val copyLen = minOf(frame.size, size)
                frame.copyInto(buffer, offset, 0, copyLen)
                copyLen
            }
        }
        whenever(mockAudioRecord.startRecording()).then { }
        whenever(mockAudioRecord.stop()).then { }
        whenever(mockAudioRecord.release()).then { }

        service.audioRecordFactory = { mockAudioRecord }
    }

    @After
    fun tearDown() {
        service.onDestroy()
        RecorderService.setEventSink(null)
        cleanUpFiles()
    }

    // ==================== Lifecycle (TODO #53) ====================

    @Test
    fun startRecording_initializesService() {
        frameQueue.add(silenceFrame())
        service.startRecording()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        waitLoop()

        val status = RecorderService.statusSnapshot(context)
        assertTrue(status["running"] as Boolean)
    }

    @Test
    fun stopRecording_stopsService() {
        frameQueue.add(silenceFrame())
        service.startRecording()
        waitLoop()

        service.stopRecording()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val status = RecorderService.statusSnapshot(context)
        assertFalse(status["running"] as Boolean)
    }

    // ==================== Detect Mode (TODO #54) ====================

    @Test
    fun detectMode_createsFileOnSpeech() {
        enqueueDetectFrames()
        service.startRecording()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        waitLoop()

        val files = recordingFiles()
        assertTrue("Expected file in detect mode, found ${files.size}", files.isNotEmpty())
        assertTrue("File should have data, got ${files.sumOf { it.length() }} bytes", files.sumOf { it.length() } > 44)
    }

    @Test
    fun detectMode_emitsSpeechEvents() {
        enqueueDetectFrames()
        service.startRecording()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        waitLoop()

        val types = capturedEvents.map { it["type"] }
        assertTrue("Expected speechStarted: $types", types.contains("speechStarted"))
        assertTrue("Expected speechEnded: $types", types.contains("speechEnded"))
        assertTrue("Expected fileReady: $types", types.contains("fileReady"))
    }

    // ==================== Monitor Mode (TODO #55) ====================

    @Test
    fun monitorMode_writesAllFrames() {
        repeat(50) { frameQueue.add(silenceFrame()) }
        service.startRecording()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        waitLoop()

        val files = recordingFiles()
        assertTrue("Expected file in monitor mode", files.isNotEmpty())
        val total = files.sumOf { it.length() }
        assertTrue("Expected >= ${50 * 960 + 44} bytes, got $total", total >= 50 * 960 + 44)
    }

    @Test
    fun monitorMode_emitsFileReady() {
        repeat(10) { frameQueue.add(silenceFrame()) }
        service.startRecording()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        waitLoop()

        val types = capturedEvents.map { it["type"] }
        assertTrue(types.contains("recordingStarted"))
        assertTrue(types.contains("fileReady"))
    }

    // ==================== Schedule Mode (TODO #56) ====================

    @Test
    fun scheduleMode_emitsScheduleEvents() {
        repeat(10) { frameQueue.add(silenceFrame()) }
        service.scheduledSession = true
        service.startRecording()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        waitLoop()

        val types = capturedEvents.map { it["type"] }
        assertTrue("Expected scheduleStarted: $types", types.contains("scheduleStarted"))
        assertTrue(types.contains("fileReady"))
    }

    // ==================== Invalid Mode (TODO #59) ====================

    @Test
    fun invalidMode_emitsErrorAndStops() {
        service.mode = "invalid_mode"
        service.startRecording()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val types = capturedEvents.map { it["type"] }
        assertTrue("Expected error: $types", types.contains("error"))
        assertFalse(RecorderService.statusSnapshot(context)["running"] as Boolean)
    }

    // ==================== Storage Enforcement (TODO #58) ====================

    @Test
    fun enforceStorageLimit_viaSetMaxStorage() {
        val dir = context.getExternalFilesDir(null)?.resolve("recordings")
            ?: context.filesDir.resolve("recordings")
        dir.mkdirs()
        File(dir, "big1.wav").writeText("a".repeat(512 * 1024))
        File(dir, "big2.wav").writeText("b".repeat(512 * 1024))

        service.maxStorageMb = 0
        service.enforceStorageLimit()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue((dir.listFiles()?.filter { it.isFile } ?: emptyList()).isEmpty())
    }

    // ==================== Test Helpers ====================

    private fun enqueueDetectFrames() {
        repeat(10) { frameQueue.add(silenceFrame()) }
        repeat(20) { frameQueue.add(speechFrame()) }
        repeat(20) { frameQueue.add(silenceFrame()) }
        repeat(5) { frameQueue.add(silenceFrame()) }
    }

    private fun silenceFrame() = ByteArray(960)

    private fun speechFrame(): ByteArray {
        val frame = ByteArray(960)
        for (i in frame.indices step 2) {
            if ((i / 2) % 2 == 0) { frame[i] = 0x00; frame[i + 1] = 0x7F.toByte() }
            else { frame[i] = 0x00; frame[i + 1] = 0x80.toByte() }
        }
        return frame
    }

    private fun recordingFiles(): List<File> {
        val dir = context.getExternalFilesDir(null)?.resolve("recordings")
            ?: context.filesDir.resolve("recordings")
        return dir.listFiles()?.filter { it.isFile } ?: emptyList()
    }

    private fun cleanUpFiles() {
        val dir = context.getExternalFilesDir(null)?.resolve("recordings")
            ?: context.filesDir.resolve("recordings")
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun waitLoop() {
        Thread.sleep(3000)
    }
}