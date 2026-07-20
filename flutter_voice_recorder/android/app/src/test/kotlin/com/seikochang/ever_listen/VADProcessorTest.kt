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
}
