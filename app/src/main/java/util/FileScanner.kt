package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.data.PlaylistItemEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URLDecoder

class FileScanner(
    private val context: Context
) {
    // ⭐ רשימות סיומות מהירות - זה החלק החשוב!
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif",
        "dng", "cr2", "nef", "arw", "orf", "rw2", "raw", "raf"
    )
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm",
        "ts", "m4v", "mpg", "mpeg", "vob", "m2ts", "mts", "ogv"
    )

    companion object {
        private const val BATCH_SIZE = 50
        private const val BATCH_DELAY_MS = 100L
    }

    suspend fun scanPlaylistItemsFlow(
        items: List<PlaylistItemEntity>,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        Log.d("FileScanner", "=== STARTING SCAN ===")

        for (item in items) {
            if (!currentCoroutineContext().isActive) break

            val uri = Uri.parse(item.uriString)

            if (item.type == ItemType.FILE) {
                if (isMediaFileByExtension(uri.toString(), filter)) {
                    emit(listOf(uri))
                }
            } else {
                scanFolderProgressive(uri, item.isRecursive, filter).collect { batch ->
                    emit(batch)
                }
            }
        }

        Log.d("FileScanner", "=== SCAN COMPLETE ===")
    }

    private suspend fun scanFolderProgressive(
        folderUri: Uri,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        Log.d("FileScanner", "Scanning folder: $folderUri")

        val physicalPath = getPhysicalPath(folderUri)
        if (physicalPath != null) {
            val folder = File(physicalPath)
            if (folder.exists() && folder.isDirectory) {
                Log.d("FileScanner", "Using physical scan: $physicalPath")
                scanPhysicalFolderProgressive(folder, recursive, filter).collect { batch ->
                    emit(batch)
                }
                return@flow
            }
        }

        Log.d("FileScanner", "Using SAF scan")
        scanViaSAFProgressive(folderUri, recursive, filter).collect { batch ->
            emit(batch)
        }
    }

    private suspend fun scanPhysicalFolderProgressive(
        folder: File,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        val batch = mutableListOf<Uri>()

        suspend fun scanRecursive(dir: File) {
            if (!currentCoroutineContext().isActive) return

            val files = dir.listFiles() ?: return

            for (file in files) {
                if (!currentCoroutineContext().isActive) return

                if (file.isDirectory) {
                    if (recursive) {
                        scanRecursive(file)
                    }
                } else {
                    // ⭐ בדיקה מהירה לפי סיומת
                    if (isMediaFileByExtension(file.name, filter)) {
                        batch.add(Uri.fromFile(file))

                        if (batch.size >= BATCH_SIZE) {
                            emit(batch.toList())
                            batch.clear()
                            delay(BATCH_DELAY_MS)
                        }
                    }
                }
            }
        }

        scanRecursive(folder)

        if (batch.isNotEmpty()) {
            emit(batch.toList())
        }
    }

    private suspend fun scanViaSAFProgressive(
        treeUri: Uri,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        val batch = mutableListOf<Uri>()

        suspend fun scanRecursive(uri: Uri) {
            if (!currentCoroutineContext().isActive) return

            try {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)

                val cursor = context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null, null, null
                ) ?: return

                cursor.use {
                    while (cursor.moveToNext() && currentCoroutineContext().isActive) {
                        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                        if (idIdx == -1) continue

                        val documentId = cursor.getString(idIdx)
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                        val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) ?: "" else ""

                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (recursive) {
                                scanRecursive(fileUri)
                            }
                        } else {
                            // ⭐ בדיקה מהירה
                            if (isMediaFileByExtension(name, filter) || isMediaFileByMime(mime, filter)) {
                                batch.add(fileUri)

                                if (batch.size >= BATCH_SIZE) {
                                    emit(batch.toList())
                                    batch.clear()
                                    delay(BATCH_DELAY_MS)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FileScanner", "Error scanning SAF: $uri", e)
            }
        }

        scanRecursive(treeUri)

        if (batch.isNotEmpty()) {
            emit(batch.toList())
        }
    }

    private fun getPhysicalPath(uri: Uri): String? {
        val uriString = uri.toString()
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath

        if (uriString.contains("primary%3A") || uriString.contains("primary:")) {
            val decoded = URLDecoder.decode(uriString, "UTF-8")
            val parts = decoded.split(Regex("primary[:%]"))
            if (parts.size > 1) {
                return "$externalStorage/${parts[1]}"
            }
        }

        if (uri.scheme == "file") {
            return uri.path
        }

        return null
    }

    // ⭐ הפונקציה החשובה - בדיקה מהירה לפי סיומת!
    private fun isMediaFileByExtension(fileName: String, filter: MediaFilterType): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return false

        val isImage = imageExtensions.contains(ext)
        val isVideo = videoExtensions.contains(ext)

        return when (filter) {
            MediaFilterType.PHOTOS_ONLY -> isImage
            MediaFilterType.VIDEO_ONLY -> isVideo
            MediaFilterType.MIXED -> isImage || isVideo
        }
    }

    private fun isMediaFileByMime(mime: String, filter: MediaFilterType): Boolean {
        val safeMime = mime.lowercase()

        val isImage = safeMime.startsWith("image/")
        val isVideo = safeMime.startsWith("video/")

        return when (filter) {
            MediaFilterType.PHOTOS_ONLY -> isImage
            MediaFilterType.VIDEO_ONLY -> isVideo
            MediaFilterType.MIXED -> isImage || isVideo
        }
    }
}