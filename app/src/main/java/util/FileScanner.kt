package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.data.PlaylistItemEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URLDecoder

class FileScanner(private val context: Context) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic")
    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "ts", "m4v")

    fun scanPlaylistItemsFlow(
        items: List<PlaylistItemEntity>,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {

        val buffer = mutableListOf<Uri>()
        Log.d("FileScanner", "Starting Full Access Scan...")

        try {
            for (item in items) {
                // אם המשתמש יצא מהמסך, הפסק סריקה
                if (!currentCoroutineContext().isActive) break

                if (item.type == ItemType.FILE) {
                    // קובץ בודד
                    buffer.add(Uri.parse(item.uriString))
                } else {
                    // המרה חכמה מ-URI של אנדרואיד לנתיב קובץ פיזי
                    val rawPath = getRawPathFromUri(Uri.parse(item.uriString))

                    if (rawPath != null) {
                        Log.d("FileScanner", "Scanning Direct Path: $rawPath")
                        scanJavaFileRecursively(File(rawPath), item.isRecursive, filter, buffer) { batch ->
                            emit(batch) // שולח מנה של תמונות לנגן
                        }
                    } else {
                        Log.e("FileScanner", "Could not resolve path: ${item.uriString}")
                    }
                }
            }

            // שלח את מה שנשאר בבופר
            if (buffer.isNotEmpty()) {
                emit(buffer.toList())
            }
            Log.d("FileScanner", "Scan finished.")

        } catch (e: Exception) {
            Log.e("FileScanner", "Error scanning", e)
        }
    }

    private suspend fun scanJavaFileRecursively(
        folder: File,
        recursive: Boolean,
        filter: MediaFilterType,
        buffer: MutableList<Uri>,
        onEmit: suspend (List<Uri>) -> Unit
    ) {
        val files = folder.listFiles() ?: return

        for (file in files) {
            if (!currentCoroutineContext().isActive) return

            if (file.isDirectory) {
                if (recursive) {
                    scanJavaFileRecursively(file, true, filter, buffer, onEmit)
                }
            } else {
                if (isMediaFile(file.name, "", filter)) {
                    buffer.add(Uri.fromFile(file))

                    // שולח כל 50 תמונות כדי שהנגן יתחיל מיד
                    if (buffer.size >= 50) {
                        onEmit(buffer.toList())
                        buffer.clear()
                    }
                }
            }
        }
    }

    // פונקציית המרה קריטית - ממירה את הג'יבריש של ה-URI לנתיב קובץ אמיתי
    private fun getRawPathFromUri(uri: Uri): String? {
        val uriString = uri.toString()
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath

        // מקרה 1: תיקייה בזיכרון הראשי (Primary)
        if (uriString.contains("primary%3A")) {
            val split = uriString.split("primary%3A")
            if (split.size > 1) {
                val decodedPath = URLDecoder.decode(split[1], "UTF-8")
                return "$externalStorage/$decodedPath"
            }
        }
        // מקרה 2: כבר בפורמט קובץ
        if (uri.scheme == "file") {
            return uri.path
        }

        return null
    }

    private fun isMediaFile(name: String, mimeType: String, filter: MediaFilterType): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()

        val isImage = imageExtensions.contains(extension)
        val isVideo = videoExtensions.contains(extension)

        return when (filter) {
            MediaFilterType.PHOTOS_ONLY -> isImage
            MediaFilterType.VIDEO_ONLY -> isVideo
            MediaFilterType.MIXED -> isImage || isVideo
        }
    }
}