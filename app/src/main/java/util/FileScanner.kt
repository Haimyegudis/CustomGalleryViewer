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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URLDecoder

class FileScanner(
    private val context: Context,
    private val cacheManager: MediaCacheManager
) {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "dng", "cr2", "nef", "arw")
    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "ts", "m4v", "mpg", "mpeg", "vob")

    companion object {
        private const val BATCH_SIZE = 50
        private const val BATCH_DELAY_MS = 100L
    }

    suspend fun scanPlaylistItemsFlow(
        items: List<PlaylistItemEntity>,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        Log.d("FileScanner", "=== STARTING PROGRESSIVE SCAN ===")
        Log.d("FileScanner", "Filter: $filter, Items count: ${items.size}")

        for (item in items) {
            if (!currentCoroutineContext().isActive) break

            val uri = Uri.parse(item.uriString)
            Log.d("FileScanner", "Processing item: ${item.type} - $uri, recursive=${item.isRecursive}")

            if (item.type == ItemType.FILE) {
                emit(listOf(uri))
            } else {
                scanFolderProgressive(uri, item.isRecursive, filter).collect { batch ->
                    Log.d("FileScanner", "Emitting batch of ${batch.size} files")
                    emit(batch)
                }
            }
        }

        Log.d("FileScanner", "=== PROGRESSIVE SCAN COMPLETE ===")
    }

    private suspend fun scanFolderProgressive(
        folderUri: Uri,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        Log.d("FileScanner", "Scanning folder: $folderUri (recursive=$recursive, filter=$filter)")

        // בדיקת Cache
        val cachedFiles = cacheManager.getCachedFiles(folderUri)
        if (cachedFiles != null && cachedFiles.isNotEmpty()) {
            Log.d("FileScanner", "✓ CACHE HIT! Found ${cachedFiles.size} cached files")
            cachedFiles.chunked(BATCH_SIZE).forEach { batch ->
                emit(batch)
                delay(BATCH_DELAY_MS)
            }
            return@flow
        }

        if (cachedFiles != null && cachedFiles.isEmpty()) {
            Log.d("FileScanner", "✗ CACHE EMPTY - invalidating")
            cacheManager.invalidateCache(folderUri)
        }

        Log.d("FileScanner", "✗ CACHE MISS - scanning from scratch")
        val allFiles = mutableListOf<Uri>()

        // אסטרטגיה חדשה: תמיד נסה SAF ראשון
        if (folderUri.scheme == "content") {
            Log.d("FileScanner", "URI is content:// - using SAF scan")

            scanViaSAFProgressive(folderUri, recursive, filter).collect { batch ->
                Log.d("FileScanner", "SAF scan batch: ${batch.size} files")
                allFiles.addAll(batch)
                emit(batch)
            }

            Log.d("FileScanner", "SAF scan complete: ${allFiles.size} total files")

            if (allFiles.isNotEmpty()) {
                cacheManager.cacheFiles(folderUri, allFiles)
            }

            return@flow
        }

        // אם זה לא content URI, נסה MediaStore לתיקיות פנימיות
        val physicalPath = getPhysicalPath(folderUri)
        if (physicalPath != null && isInternalStoragePath(physicalPath)) {
            Log.d("FileScanner", "Using MediaStore scan for internal path: $physicalPath")

            val mediaStoreFiles = MediaStoreScanner.scanInternalStorage(
                context,
                physicalPath,
                filter
            )

            if (mediaStoreFiles.isNotEmpty()) {
                allFiles.addAll(mediaStoreFiles)

                mediaStoreFiles.chunked(BATCH_SIZE).forEach { batch ->
                    emit(batch)
                    delay(BATCH_DELAY_MS)
                }

                cacheManager.cacheFiles(folderUri, allFiles)
                return@flow
            }
        }

        // אם הגענו לפה, אין קבצים
        Log.w("FileScanner", "No files found for: $folderUri")
    }

    private fun isInternalStoragePath(path: String): Boolean {
        val normalizedPath = path.lowercase()
        return normalizedPath.contains("/dcim") ||
                normalizedPath.contains("/pictures") ||
                normalizedPath.contains("/camera") ||
                normalizedPath.contains("/downloads") ||
                normalizedPath.contains("/screenshots")
    }

    private suspend fun scanViaSAFProgressive(
        treeUri: Uri,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        val batch = mutableListOf<Uri>()
        var totalFound = 0

        suspend fun scanRecursive(uri: Uri, depth: Int = 0) {
            if (!currentCoroutineContext().isActive) return
            if (depth > 20) {
                Log.w("FileScanner", "Max recursion depth reached at: $uri")
                return
            }

            try {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)

                Log.d("FileScanner", "SAF scanning: $uri (depth=$depth)")

                val cursor = context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null, null, null
                )

                if (cursor == null) {
                    Log.e("FileScanner", "SAF cursor is null for $uri")
                    return
                }

                cursor.use {
                    val count = cursor.count
                    Log.d("FileScanner", "SAF found $count items at depth $depth")

                    if (count == 0) return@use

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
                            Log.d("FileScanner", "Found folder: $name (recursive=$recursive)")
                            if (recursive) {
                                scanRecursive(fileUri, depth + 1)
                            }
                        } else {
                            val isMedia = isMediaFile(name, mime, filter)
                            Log.v("FileScanner", "File: $name, mime=$mime, isMedia=$isMedia")

                            if (isMedia) {
                                totalFound++
                                batch.add(fileUri)
                                Log.d("FileScanner", "✓ Media file #$totalFound: $name")

                                if (batch.size >= BATCH_SIZE) {
                                    Log.d("FileScanner", "Emitting batch of ${batch.size}")
                                    emit(batch.toList())
                                    batch.clear()
                                    delay(BATCH_DELAY_MS)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FileScanner", "Error scanning SAF at depth $depth: $uri", e)
            }
        }

        scanRecursive(treeUri)

        if (batch.isNotEmpty()) {
            Log.d("FileScanner", "Emitting final batch of ${batch.size}")
            emit(batch.toList())
        }

        Log.d("FileScanner", "SAF scan found total: $totalFound files")
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