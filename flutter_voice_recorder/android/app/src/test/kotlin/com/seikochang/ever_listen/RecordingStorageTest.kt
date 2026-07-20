package com.seikochang.ever_listen

import org.junit.Assert.assertEquals
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
}
