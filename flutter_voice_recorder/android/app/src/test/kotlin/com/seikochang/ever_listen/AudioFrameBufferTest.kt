package com.seikochang.ever_listen

import org.junit.Assert.*
import org.junit.Test

class AudioFrameBufferTest {

    @Test
    fun testBufferCapacityLimit() {
        val buffer = AudioFrameBuffer(maxFrames = 3)
        assertEquals(0, buffer.size())

        val frame1 = byteArrayOf(1, 2, 3)
        val frame2 = byteArrayOf(4, 5, 6)
        val frame3 = byteArrayOf(7, 8, 9)
        val frame4 = byteArrayOf(10, 11, 12)

        buffer.addFrame(frame1)
        buffer.addFrame(frame2)
        buffer.addFrame(frame3)
        assertEquals(3, buffer.size())

        // Adding a 4th frame should evict frame1
        buffer.addFrame(frame4)
        assertEquals(3, buffer.size())

        val frames = buffer.getFrames()
        assertArrayEquals(frame2, frames[0])
        assertArrayEquals(frame3, frames[1])
        assertArrayEquals(frame4, frames[2])
    }

    @Test
    fun testClear() {
        val buffer = AudioFrameBuffer(maxFrames = 5)
        buffer.addFrame(byteArrayOf(1))
        buffer.addFrame(byteArrayOf(2))
        assertEquals(2, buffer.size())

        buffer.clear()
        assertEquals(0, buffer.size())
        assertTrue(buffer.getFrames().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroCapacity() {
        AudioFrameBuffer(maxFrames = 0)
    }
}
