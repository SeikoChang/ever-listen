package com.seikochang.ever_listen

import java.io.File

/** Filesystem helper for recording retention and circular storage limits. */
class RecordingStorage(private val directory: File) {
    fun ensureDirectory() {
        require(directory.exists() || directory.mkdirs()) {
            "Unable to create recording directory: ${directory.absolutePath}"
        }
    }

    fun files(): List<File> = directory.listFiles()
        ?.filter { it.isFile }
        ?.sortedBy { it.lastModified() }
        ?: emptyList()

    fun totalBytes(): Long = files().sumOf { it.length() }

    fun prune(maxStorageMb: Int, protectedFile: File? = null): List<File> {
        require(maxStorageMb >= 0) { "maxStorageMb must not be negative" }
        ensureDirectory()

        val retained = files().toMutableList()
        var totalBytes = retained.sumOf { it.length() }
        val maxBytes = maxStorageMb.toLong() * 1024L * 1024L
        val deleted = mutableListOf<File>()

        for (candidate in retained.toList()) {
            if (totalBytes <= maxBytes) break
            if (protectedFile != null && candidate.absolutePath == protectedFile.absolutePath) continue

            val size = candidate.length()
            if (candidate.delete()) {
                totalBytes -= size
                deleted += candidate
            }
        }
        return deleted
    }
}
