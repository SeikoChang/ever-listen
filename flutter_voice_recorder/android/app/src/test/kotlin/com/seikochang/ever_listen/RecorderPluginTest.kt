package com.seikochang.ever_listen

import android.content.Context
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.mockito.kotlin.*
import android.content.Intent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RecorderPluginTest {

    private lateinit var context: Context
    private lateinit var plugin: RecorderPlugin

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ever_listen_schedules", Context.MODE_PRIVATE)
            .edit().clear().apply()

        val binding = mock<io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding>()
        whenever(binding.applicationContext).thenReturn(context)
        whenever(binding.binaryMessenger).thenReturn(mock())

        plugin = RecorderPlugin()
        plugin.onAttachedToEngine(binding)

        // Provide context for RecorderService (service's mBase is null when created via startService)
        RecorderService.testContext = context

        // Bypass Robolectric startForegroundService (throws during service lifecycle)
        plugin.testServiceStarter = { intent: Intent ->
            // No-op: we test plugin behavior, not service lifecycle
        }

        // Grant RECORD_AUDIO permission for plugin tests
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
    }

    @After
    fun tearDown() {
        RecorderService.testContext = null
        plugin.testServiceStarter = null
        plugin.onDetachedFromEngine(mock())
    }

    // ==================== startRecording (TODO #45) ====================

    @Test
    fun startRecording_validModeSucceeds() {
        val result = captureResult()
        plugin.onMethodCall(MethodCall("startRecording", mapOf("mode" to "detect")), result)
        assertTrue(result.called)
        assertEquals("started", (result.value as Map<*, *>)["status"])
    }

    @Test
    fun startRecording_invalidModeReturnsError() {
        val result = captureResult()
        plugin.onMethodCall(MethodCall("startRecording", mapOf("mode" to "invalid")), result)
        assertTrue(result.called)
        assertEquals("INVALID_MODE", result.errorCode)
    }

    @Test
    fun startRecording_permissionDeniedReturnsError() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.RECORD_AUDIO)
        val result = captureResult()
        plugin.onMethodCall(MethodCall("startRecording", mapOf("mode" to "detect")), result)
        assertTrue(result.called)
        assertEquals("PERMISSION_DENIED", result.errorCode)
    }

    // ==================== stopRecording (TODO #46) ====================

    @Test
    fun stopRecording_succeeds() {
        val result = captureResult()
        plugin.onMethodCall(MethodCall("stopRecording", null), result)
        assertTrue(result.called)
        assertEquals("stopped", (result.value as Map<*, *>)["status"])
    }

    // ==================== scheduleRecording (TODO #47) ====================

    @Test
    fun scheduleRecording_validInputSucceeds() {
        val now = System.currentTimeMillis()
        val args = mapOf(
            "startTimeMillis" to (now + 500000L),
            "endTimeMillis" to (now + 1000000L),
            "repeat" to "once",
            "timezone" to "UTC",
            "mode" to "schedule",
            "sensitivity" to 0.7,
            "maxStorageMb" to 150
        )
        val result = captureResult()
        plugin.onMethodCall(MethodCall("scheduleRecording", args), result)
        assertTrue(result.called)
        val schedule = result.value as Map<*, *>
        assertEquals("once", schedule["repeat"])
        assertEquals(150, schedule["maxStorageMb"])
    }

    @Test
    fun scheduleRecording_pastStartTimeReturnsError() {
        val now = System.currentTimeMillis()
        val args = mapOf("startTimeMillis" to (now - 100000L), "endTimeMillis" to (now + 1000000L))
        val result = captureResult()
        plugin.onMethodCall(MethodCall("scheduleRecording", args), result)
        assertTrue(result.called)
        assertEquals("INVALID_SCHEDULE", result.errorCode)
    }

    @Test
    fun scheduleRecording_endBeforeStartReturnsError() {
        val now = System.currentTimeMillis()
        val args = mapOf("startTimeMillis" to (now + 1000000L), "endTimeMillis" to (now + 500000L))
        val result = captureResult()
        plugin.onMethodCall(MethodCall("scheduleRecording", args), result)
        assertTrue(result.called)
        assertEquals("INVALID_SCHEDULE", result.errorCode)
    }

    @Test
    fun scheduleRecording_invalidRepeatReturnsError() {
        val now = System.currentTimeMillis()
        val args = mapOf("startTimeMillis" to (now + 500000L), "endTimeMillis" to (now + 1000000L), "repeat" to "monthly")
        val result = captureResult()
        plugin.onMethodCall(MethodCall("scheduleRecording", args), result)
        assertTrue(result.called)
        assertEquals("INVALID_SCHEDULE", result.errorCode)
    }

    // ==================== cancelSchedule (TODO #48) ====================

    @Test
    fun cancelSchedule_validIdCancels() {
        val now = System.currentTimeMillis()
        ScheduleStore.add(context, RecordingSchedule(
            id = "test-cancel", startTimeMillis = now + 500000L, endTimeMillis = now + 1000000L,
            repeat = "once", timezone = "UTC", mode = "detect", sensitivity = 0.6, maxStorageMb = 200
        ))
        val result = captureResult()
        plugin.onMethodCall(MethodCall("cancelSchedule", mapOf("id" to "test-cancel")), result)
        assertTrue(result.called)
        val res = result.value as Map<*, *>
        assertTrue(res["cancelled"] as Boolean)
        assertTrue(ScheduleStore.list(context).isEmpty())
    }

    @Test
    fun cancelSchedule_missingIdReturnsError() {
        val result = captureResult()
        plugin.onMethodCall(MethodCall("cancelSchedule", mapOf<String, Any>()), result)
        assertTrue(result.called)
        assertEquals("INVALID_ARGUMENT", result.errorCode)
    }

    // ==================== getSchedules (TODO #49) ====================

    @Test
    fun getSchedules_emptyReturnsEmptyList() {
        val result = captureResult()
        plugin.onMethodCall(MethodCall("getSchedules", null), result)
        assertTrue(result.called)
        assertTrue((result.value as List<*>).isEmpty())
    }

    @Test
    fun getSchedules_populatedReturnsList() {
        val now = System.currentTimeMillis()
        ScheduleStore.add(context, RecordingSchedule(
            id = "test-list", startTimeMillis = now + 500000L, endTimeMillis = now + 1000000L,
            repeat = "daily", timezone = "UTC", mode = "monitor", sensitivity = 0.5, maxStorageMb = 100
        ))
        val result = captureResult()
        plugin.onMethodCall(MethodCall("getSchedules", null), result)
        assertTrue(result.called)
        assertEquals(1, (result.value as List<*>).size)
    }

    // ==================== requestPermissions (TODO #50) ====================

    @Test
    fun requestPermissions_noActivityReturnsError() {
        val result = captureResult()
        plugin.onMethodCall(MethodCall("requestPermissions", null), result)
        assertTrue(result.called)
        assertEquals("NO_ACTIVITY", result.errorCode)
    }

    // ==================== getStatus (TODO #51) ====================

    @Test
    fun getStatus_returnsStatusSnapshot() {
        val result = captureResult()
        plugin.onMethodCall(MethodCall("getStatus", null), result)
        assertTrue(result.called)
        val status = result.value as Map<*, *>
        assertNotNull(status["running"])
        assertNotNull(status["mode"])
        assertNotNull(status["schedules"])
    }

    // ==================== setSensitivity / setMaxStorageMb (TODO #52) ====================

    @Test
    fun setSensitivity_succeeds() {
        val result = captureResult()
        plugin.onMethodCall(MethodCall("setSensitivity", mapOf("sensitivity" to 0.8)), result)
        assertTrue(result.called)
        assertEquals(0.8, (result.value as Map<*, *>)["sensitivity"])
    }

    @Test
    fun setMaxStorageMb_succeeds() {
        val result = captureResult()
        plugin.onMethodCall(MethodCall("setMaxStorageMb", mapOf("maxStorageMb" to 500)), result)
        assertTrue(result.called)
        assertEquals(500, (result.value as Map<*, *>)["maxStorageMb"])
    }

    @Test
    fun unknownMethod_returnsNotImplemented() {
        val result = captureResult()
        plugin.onMethodCall(MethodCall("unknownMethod", null), result)
        assertTrue(result.called)
    }

    // ==================== Test Helpers ====================

    private fun captureResult(): CapturedResult = CapturedResult()

    private class CapturedResult : MethodChannel.Result {
        var called = false
        var value: Any? = null
        var errorCode: String? = null
        override fun success(result: Any?) { called = true; value = result }
        override fun error(code: String, message: String?, details: Any?) { called = true; errorCode = code }
        override fun notImplemented() { called = true }
    }
}
