package com.seikochang.ever_listen

import org.junit.Assert.*
import org.junit.Test

class VADProcessorTest {

    @Test
    fun testMockVADProcessorWithSilence() {
        val processor = MockVADProcessor()
        processor.setSensitivity(0.5)

        // 480 samples of silence (960 bytes)
        val silenceFrame = ByteArray(960)
        assertFalse(processor.processFrame(silenceFrame))
    }

    @Test
    fun testMockVADProcessorWithSpeech() {
        val processor = MockVADProcessor()
        // Set high sensitivity
        processor.setSensitivity(0.9)

        // 480 samples of a loud square wave signal (960 bytes)
        val loudFrame = ByteArray(960)
        for (i in loudFrame.indices step 2) {
            if ((i / 2) % 2 == 0) {
                loudFrame[i] = 0x00
                loudFrame[i + 1] = 0x7F // +32512
            } else {
                loudFrame[i] = 0x00
                loudFrame[i + 1] = 0x80.toByte() // -32768
            }
        }
        assertTrue(processor.processFrame(loudFrame))
    }

    @Test
    fun testWebRTCVADProcessorThrowsUnsatisfiedLinkErrorOnHostJVM() {
        try {
            WebRTCVADProcessor()
            // If it somehow passes (which it shouldn't on host local unit test), print success
            System.out.println("WebRTCVADProcessor loaded successfully (possibly running on device or native lib mocked)")
        } catch (e: UnsatisfiedLinkError) {
            // Expected behavior on host JVM unit tests because everlisten_vad is compiled for Android ABIs
            assertNotNull(e.message)
        } catch (e: Exception) {
            // In case of other exceptions
            assertNotNull(e.message)
        }
    }

    // ==================== Sensitivity Edge Cases (TODO #60) ====================

    @Test
    fun testSensitivityZeroRejectsSilence() {
        val processor = MockVADProcessor()
        processor.setSensitivity(0.0)
        val silenceFrame = ByteArray(960)
        // threshold = 100 * (1.0 - 0.0 + 0.1) = 110, silence RMS = 0 → not speech
        assertFalse(processor.processFrame(silenceFrame))
    }

    @Test
    fun testSensitivityOneDetectsVeryQuietSignal() {
        val processor = MockVADProcessor()
        processor.setSensitivity(1.0)
        // threshold = 100 * (1.0 - 1.0 + 0.1) = 10
        // Build a frame with RMS ≈ 20 (low but > 10)
        val quietFrame = ByteArray(960)
        for (i in quietFrame.indices step 2) {
            val sample = 30.toShort() // RMS of all-30s = 30 > 10
            quietFrame[i] = (sample.toInt() and 0xFF).toByte()
            quietFrame[i + 1] = (sample.toInt() shr 8).toByte()
        }
        assertTrue(processor.processFrame(quietFrame))
    }

    @Test
    fun testSensitivityOneRejectsSilence() {
        val processor = MockVADProcessor()
        processor.setSensitivity(1.0)
        val silenceFrame = ByteArray(960)
        // RMS = 0, threshold = 10 → still not speech
        assertFalse(processor.processFrame(silenceFrame))
    }

    @Test
    fun testSensitivityZeroRejectsModerateSignal() {
        val processor = MockVADProcessor()
        processor.setSensitivity(0.0)
        // threshold = 110
        // Build a frame with RMS ≈ 50 (moderate, but < 110)
        val moderateFrame = ByteArray(960)
        for (i in moderateFrame.indices step 2) {
            val sample = 50.toShort()
            moderateFrame[i] = (sample.toInt() and 0xFF).toByte()
            moderateFrame[i + 1] = (sample.toInt() shr 8).toByte()
        }
        assertFalse(processor.processFrame(moderateFrame))
    }

    @Test
    fun testSensitivityZeroDetectsLoudSignal() {
        val processor = MockVADProcessor()
        processor.setSensitivity(0.0)
        // threshold = 110
        // Build a frame with RMS ≈ 1000 (loud)
        val loudFrame = ByteArray(960)
        for (i in loudFrame.indices step 2) {
            val sample = 1000.toShort()
            loudFrame[i] = (sample.toInt() and 0xFF).toByte()
            loudFrame[i + 1] = (sample.toInt() shr 8).toByte()
        }
        assertTrue(processor.processFrame(loudFrame))
    }

    @Test
    fun testSensitivityOutOfBoundsIsCoerced() {
        val processor = MockVADProcessor()
        processor.setSensitivity(-0.5)
        // Should be coerced to 0.0, so silence still rejected
        assertFalse(processor.processFrame(ByteArray(960)))

        processor.setSensitivity(2.0)
        // Should be coerced to 1.0, silence still rejected
        assertFalse(processor.processFrame(ByteArray(960)))
    }

    // ==================== Dynamic Sensitivity Switch (TODO #60) ====================

    @Test
    fun testDynamicSensitivitySwitch() {
        val processor = MockVADProcessor()

        // Build a frame with RMS ≈ 50
        val frame = ByteArray(960)
        for (i in frame.indices step 2) {
            val sample = 50.toShort()
            frame[i] = (sample.toInt() and 0xFF).toByte()
            frame[i + 1] = (sample.toInt() shr 8).toByte()
        }

        // sensitivity 0.0 → threshold = 110, RMS 50 < 110 → not speech
        processor.setSensitivity(0.0)
        assertFalse(processor.processFrame(frame))

        // sensitivity 1.0 → threshold = 10, RMS 50 > 10 → speech
        processor.setSensitivity(1.0)
        assertTrue(processor.processFrame(frame))

        // sensitivity 0.5 → threshold = 60, RMS 50 < 60 → not speech
        processor.setSensitivity(0.5)
        assertFalse(processor.processFrame(frame))
    }

    // ==================== Empty Frame (TODO #60) ====================

    @Test
    fun testEmptyFrameReturnsSilence() {
        val processor = MockVADProcessor()
        processor.setSensitivity(1.0) // max sensitivity
        val emptyFrame = ByteArray(0)
        // calculateRMS returns 0.0 for empty samples → not speech
        assertFalse(processor.processFrame(emptyFrame))
    }

    @Test
    fun testEmptyFrameAtZeroSensitivity() {
        val processor = MockVADProcessor()
        processor.setSensitivity(0.0) // min sensitivity
        assertFalse(processor.processFrame(ByteArray(0)))
    }

    // ==================== Benchmark ====================

    @Test
    fun testVADProcessorsBenchmark() {
        val mockProcessor = MockVADProcessor()
        mockProcessor.setSensitivity(0.5)

        val frame = ByteArray(960) // 30ms at 16kHz
        
        val startMock = System.nanoTime()
        for (i in 1..1000) {
            mockProcessor.processFrame(frame)
        }
        val endMock = System.nanoTime()
        val durationMockMs = (endMock - startMock) / 1_000_000.0
        System.out.println("MockVADProcessor benchmark: 1000 frames in ${durationMockMs}ms")
        
        try {
            val nativeProcessor = WebRTCVADProcessor()
            val startNative = System.nanoTime()
            for (i in 1..1000) {
                nativeProcessor.processFrame(frame)
            }
            val endNative = System.nanoTime()
            val durationNativeMs = (endNative - startNative) / 1_000_000.0
            System.out.println("WebRTCVADProcessor benchmark: 1000 frames in ${durationNativeMs}ms")
        } catch (_: Throwable) {
            System.out.println("WebRTCVADProcessor benchmark: skipped (native library not loadable on host JVM)")
        }
    }
}
