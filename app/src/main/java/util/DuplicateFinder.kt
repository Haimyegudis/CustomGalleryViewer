package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class DuplicateGroup(
    val hash: String,
    val files: List<DuplicateFile>
)

data class DuplicateFile(
    val uri: Uri,
    val path: String,
    val name: String,
    val size: Long
)

@Singleton
class DuplicateFinder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun findDuplicates(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val allFiles = mutableListOf<DuplicateFile>()

        // Query images
        queryMediaFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, allFiles)
        // Query videos
        queryMediaFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, allFiles)

        // Group by size first
        val sizeGroups = allFiles.groupBy { it.size }.filter { it.value.size > 1 }

        // Then compare MD5 of first 64KB
        val duplicates = mutableListOf<DuplicateGroup>()
        for ((_, files) in sizeGroups) {
            val hashGroups = files.groupBy { computePartialHash(it.path) }
                .filter { it.value.size > 1 && it.key.isNotEmpty() }
            for ((hash, group) in hashGroups) {
                duplicates.add(DuplicateGroup(hash, group))
            }
        }
        duplicates
    }

    private fun queryMediaFiles(contentUri: Uri, result: MutableList<DuplicateFile>) {
        try {
            context.contentResolver.query(
                contentUri,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE
                ),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val path = if (dataIdx >= 0) cursor.getString(dataIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    if (path.isNotEmpty() && size > 0) {
                        val uri = android.content.ContentUris.withAppendedId(contentUri, id)
                        result.add(DuplicateFile(uri, path, name, size))
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun computePartialHash(path: String): String {
        return try {
            val file = File(path)
            if (!file.exists()) return ""
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(65536) // 64KB
            FileInputStream(file).use { fis ->
                val read = fis.read(buffer)
                if (read > 0) md.update(buffer, 0, read)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "" }
    }
}
