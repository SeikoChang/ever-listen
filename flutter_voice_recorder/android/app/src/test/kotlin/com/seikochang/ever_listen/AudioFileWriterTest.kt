package com.seikochang.ever_listen

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioFileWriterTest {
    @get:Rule
    val tempFolder = TemporaryFolder()
    
    private lateinit var outputDir: File
    private lateinit var writer: AudioFileWriter

    @Before
    fun setUp() {
        outputDir = tempFolder.newFolder("recordings")
        writer = AudioFileWriter(
            outputDir = outputDir,
            sampleRate = 16000,
            channelCount = 1,
            bitsPerSample = 16
        )
    }

    @Test
    fun testWAVFileCreationAndHeader() {
        val file = writer.createNewFile("test.wav")
        assertTrue(file.exists())

        // Write some dummy PCM frames (e.g. 100 bytes of zeros)
        val dummyFrame = ByteArray(100)
        writer.writeFrame(dummyFrame)
        writer.closeCurrentFile()

        // Read the file and parse the WAV header
        val fileBytes = FileInputStream(file).use { it.readBytes() }
        assertEquals(44 + 100, fileBytes.size) // 44 bytes header + 100 bytes data

        val buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Verify "RIFF"
        val riff = ByteArray(4)
        buffer.get(riff)
        assertEquals("RIFF", String(riff))

        // Verify ChunkSize
        val chunkSize = buffer.int
        assertEquals(36 + 100, chunkSize)

        // Verify "WAVE"
        val wave = ByteArray(4)
        buffer.get(wave)
        assertEquals("WAVE", String(wave))

        // Verify "fmt "
        val fmt = ByteArray(4)
        buffer.get(fmt)
        assertEquals("fmt ", String(fmt))

        // Verify Subchunk1Size (16 for PCM)
        val subchunk1Size = buffer.int
        assertEquals(16, subchunk1Size)

        // AudioFormat (1 for PCM)
        val audioFormat = buffer.short
        assertEquals(1.toShort(), audioFormat)

        // NumChannels (1)
        val numChannels = buffer.short
        assertEquals(1.toShort(), numChannels)

        // SampleRate (16000)
        val sampleRate = buffer.int
        assertEquals(16000, sampleRate)

        // ByteRate (16000 * 1 * 2 = 32000)
        val byteRate = buffer.int
        assertEquals(32000, byteRate)

        // BlockAlign (1 * 2 = 2)
        val blockAlign = buffer.short
        assertEquals(2.toShort(), blockAlign)

        // BitsPerSample (16)
        val bitsPerSample = buffer.short
        assertEquals(16.toShort(), bitsPerSample)

        // Verify "data"
        val data = ByteArray(4)
        buffer.get(data)
        assertEquals("data", String(data))

        // Verify Subchunk2Size (100)
        val subchunk2Size = buffer.int
        assertEquals(100, subchunk2Size)
    }
}
