package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaCacheManager
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.data.PlaylistItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

class FileScanner(
    private val context: Context,
    private val cacheManager: MediaCacheManager
) {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "dng", "cr2", "nef", "arw")
    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "ts", "m4v", "mpg", "mpeg", "vob")

    companion object {
        private const val BATCH_SIZE = 50 // שולח 50 קבצים בכל פעם
        private const val BATCH_DELAY_MS = 100L // המתנה קצרה בין באצ'ים
    }

    /**
     * טעינה הדרגתית - מחזיר Flow שפולט באצ'ים
     */
    suspend fun scanPlaylistItemsFlow(
        items: List<PlaylistItemEntity>,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        Log.d("FileScanner", "=== STARTING PROGRESSIVE SCAN ===")

        for (item in items) {
            if (!currentCoroutineContext().isActive) break

            val uri = Uri.parse(item.uriString)

            if (item.type == ItemType.FILE) {
                // קובץ בודד - שולח מיד
                emit(listOf(uri))
            } else {
                // תיקייה - מטעין בבאצ'ים
                scanFolderProgressive(uri, item.isRecursive, filter).collect { batch ->
                    emit(batch)
                }
            }
        }

        Log.d("FileScanner", "=== PROGRESSIVE SCAN COMPLETE ===")
    }

    /**
     * סריקת תיקייה הדרגתית עם Cache
     */
    private suspend fun scanFolderProgressive(
        folderUri: Uri,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        Log.d("FileScanner", "Scanning folder: $folderUri")

        // בדיקת Cache תחילה
        val cachedFiles = cacheManager.getCachedFiles(folderUri)
        if (cachedFiles != null) {
            Log.d("FileScanner", "✓ CACHE HIT! Found ${cachedFiles.size} cached files")

            // שולח את הקבצים המטמונים בבאצ'ים
            cachedFiles.chunked(BATCH_SIZE).forEach { batch ->
                emit(batch)
                delay(BATCH_DELAY_MS)
            }
            return@flow
        }

        // אין Cache - צריך לסרוק
        Log.d("FileScanner", "✗ CACHE MISS - scanning from scratch")
        val allFiles = mutableListOf<Uri>()

        // ניסיון 1: קובץ פיזי
        val physicalPath = getPhysicalPath(folderUri)
        if (physicalPath != null) {
            val folder = File(physicalPath)
            if (folder.exists() && folder.isDirectory) {
                Log.d("FileScanner", "Using physical scan: $physicalPath")
                scanPhysicalFolderProgressive(folder, recursive, filter).collect { batch ->
                    allFiles.addAll(batch)
                    emit(batch)
                }

                // שמירה ב-Cache
                cacheManager.cacheFiles(folderUri, allFiles)
                return@flow
            }
        }

        // ניסיון 2: SAF/USB
        Log.d("FileScanner", "Using SAF scan")
        scanViaSAFProgressive(folderUri, recursive, filter).collect { batch ->
            allFiles.addAll(batch)
            emit(batch)
        }

        // שמירה ב-Cache
        cacheManager.cacheFiles(folderUri, allFiles)
    }

    /**
     * סריקה פיזית הדרגתית
     */
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
                    if (isMediaFile(file.name, null, filter)) {
                        batch.add(Uri.fromFile(file))

                        // שולח באצ'
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

        // שולח את מה שנשאר
        if (batch.isNotEmpty()) {
            emit(batch.toList())
        }
    }

    /**
     * סריקת SAF הדרגתית
     */
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
                            if (isMediaFile(name, mime, filter)) {
                                batch.add(fileUri)

                                // שולח באצ'
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

        // שולח את מה שנשאר
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

    private fun isMediaFile(name: String?, mime: String?, filter: MediaFilterType): Boolean {
        val safeName = name?.lowercase() ?: ""
        val safeMime = mime?.lowercase() ?: ""
        val ext = safeName.substringAfterLast('.', "")

        val isImageByExt = imageExtensions.contains(ext)
        val isVideoByExt = videoExtensions.contains(ext)
        val isImageByMime = safeMime.startsWith("image/")
        val isVideoByMime = safeMime.startsWith("video/")

        val isImage = isImageByExt || isImageByMime
        val isVideo = isVideoByExt || isVideoByMime

        return when (filter) {
            MediaFilterType.PHOTOS_ONLY -> isImage
            MediaFilterType.VIDEO_ONLY -> isVideo
            MediaFilterType.MIXED -> isImage || isVideo
        }
    }
}