package com.seikochang.ever_listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordingStorageTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun prunesOldestFilesUntilLimit() {
        val directory = tempFolder.newFolder("recordings")
        val oldest = File(directory, "old.wav").apply { writeBytes(ByteArray(1024 * 1024)) }
        val newest = File(directory, "new.wav").apply { writeBytes(ByteArray(8)) }
        oldest.setLastModified(1L)
        newest.setLastModified(2L)

        val deleted = RecordingStorage(directory).prune(maxStorageMb = 1)

        assertEquals(listOf(oldest), deleted)
        assertTrue(newest.exists())
    }

    @Test
    fun protectsCurrentFileAndDeletesOtherCandidates() {
        val directory = tempFolder.newFolder("recordings")
        val protected = File(directory, "current.wav").apply { writeBytes(ByteArray(8)) }
        val removable = File(directory, "old.wav").apply { writeBytes(ByteArray(8)) }
        protected.setLastModified(1L)
        removable.setLastModified(2L)

        val deleted = RecordingStorage(directory).prune(maxStorageMb = 0, protectedFile = protected)

        assertEquals(listOf(removable), deleted)
        assertTrue(protected.exists())
    }

    // ==================== TODO #61: totalBytes(), ensureDirectory(), empty directory ====================

    @Test
    fun totalBytes_returnsSumOfAllFiles() {
        val directory = tempFolder.newFolder("recordings")
        File(directory, "a.wav").writeBytes(ByteArray(1000))
        File(directory, "b.wav").writeBytes(ByteArray(2000))
        File(directory, "c.wav").writeBytes(ByteArray(3000))

        val storage = RecordingStorage(directory)
        assertEquals(6000L, storage.totalBytes())
    }

    @Test
    fun totalBytes_returnsZeroForEmptyDirectory() {
        val directory = tempFolder.newFolder("recordings")
        val storage = RecordingStorage(directory)
        assertEquals(0L, storage.totalBytes())
    }

    @Test
    fun ensureDirectory_createsIfNotExists() {
        val directory = File(tempFolder.root, "new_recordings")
        assertFalse(directory.exists())

        RecordingStorage(directory).ensureDirectory()

        assertTrue(directory.exists())
        assertTrue(directory.isDirectory)
    }

    @Test
    fun ensureDirectory_noopIfExists() {
        val directory = tempFolder.newFolder("recordings")
        // Should not throw
        RecordingStorage(directory).ensureDirectory()
        assertTrue(directory.exists())
    }

    @Test
    fun prune_onEmptyDirectoryReturnsNoDeletions() {
        val directory = tempFolder.newFolder("recordings")
        val storage = RecordingStorage(directory)
        val deleted = storage.prune(maxStorageMb = 1)
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun prune_noDeletionWhenUnderLimit() {
        val directory = tempFolder.newFolder("recordings")
        File(directory, "a.wav").writeBytes(ByteArray(100))
        File(directory, "b.wav").writeBytes(ByteArray(200))

        val storage = RecordingStorage(directory)
        val deleted = storage.prune(maxStorageMb = 10)
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun prune_withNegativeMaxStorageThrows() {
        val directory = tempFolder.newFolder("recordings")
        val storage = RecordingStorage(directory)
        try {
            storage.prune(maxStorageMb = -1)
            org.junit.Assert.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("not be negative"))
        }
    }
}
