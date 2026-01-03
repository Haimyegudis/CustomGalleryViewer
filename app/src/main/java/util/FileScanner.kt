package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.data.PlaylistItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "FileScanner"

    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif",
        "dng", "cr2", "nef", "arw", "raw", "tif", "tiff", "svg"
    )
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm",
        "ts", "m4v", "mpg", "mpeg", "vob", "m2ts", "mts", "ogv"
    )

    /**
     * PROGRESSIVE SCAN - emits files as they're found!
     */
    fun scanPlaylistItemsProgressive(
        items: List<PlaylistItemEntity>,
        filter: MediaFilterType
    ): Flow<Uri> = flow {
        Log.d(TAG, "🚀 PROGRESSIVE scan started for ${items.size} items, filter: $filter")
        val startTime = System.currentTimeMillis()
        var count = 0

        for (item in items) {
            if (!currentCoroutineContext().isActive) break

            val uri = Uri.parse(item.uriString)

            if (item.type == ItemType.FILE) {
                emit(uri)
                count++
            } else {
                // Try DocumentFile
                try {
                    val docFile = DocumentFile.fromTreeUri(context, uri)
                    if (docFile != null && docFile.exists() && docFile.isDirectory) {
                        scanDocumentFileProgressive(docFile, item.isRecursive, filter).collect { fileUri ->
                            emit(fileUri)
                            count++
                            if (count % 50 == 0) {
                                Log.d(TAG, "📦 Emitted $count files...")
                            }
                        }
                        continue
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "DocumentFile error: ${e.message}")
                }

                // Try native file
                val rawPath = getRawPathFromUri(uri)
                if (rawPath != null) {
                    val file = File(rawPath)
                    if (file.exists() && file.isDirectory) {
                        scanJavaFileProgressive(file, item.isRecursive, filter).collect { fileUri ->
                            emit(fileUri)
                            count++
                            if (count % 50 == 0) {
                                Log.d(TAG, "📦 Emitted $count files...")
                            }
                        }
                    }
                }
            }
        }

        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        Log.d(TAG, "✅ PROGRESSIVE scan complete: $count files in ${duration}s")
    }

    /**
     * OLD METHOD - for cache saving (runs in background)
     */
    suspend fun scanPlaylistItems(
        items: List<PlaylistItemEntity>,
        filter: MediaFilterType
    ): List<Uri> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<Uri>()

        for (item in items) {
            if (!isActive) break
            val uri = Uri.parse(item.uriString)

            if (item.type == ItemType.FILE) {
                resultList.add(uri)
            } else {
                try {
                    val docFile = DocumentFile.fromTreeUri(context, uri)
                    if (docFile != null && docFile.exists() && docFile.isDirectory) {
                        scanDocumentFile(docFile, item.isRecursive, filter, resultList)
                    }
                } catch (e: Exception) {
                    val rawPath = getRawPathFromUri(uri)
                    if (rawPath != null) {
                        val file = File(rawPath)
                        if (file.exists() && file.isDirectory) {
                            scanJavaFileRecursively(file, item.isRecursive, filter, resultList)
                        }
                    }
                }
            }
        }

        return@withContext resultList
    }

    private fun scanDocumentFileProgressive(
        folder: DocumentFile,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<Uri> = flow {
        try {
            val files = folder.listFiles()
            for (file in files) {
                if (!currentCoroutineContext().isActive) break

                if (file.isDirectory && recursive) {
                    scanDocumentFileProgressive(file, true, filter).collect { emit(it) }
                } else if (!file.isDirectory) {
                    val name = file.name ?: ""
                    val mimeType = file.type ?: ""
                    if (isMediaFile(name, mimeType, filter)) {
                        emit(file.uri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan error: ${e.message}")
        }
    }

    private fun scanJavaFileProgressive(
        folder: File,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<Uri> = flow {
        try {
            val files = folder.listFiles() ?: return@flow
            for (file in files.sortedWith(compareBy({ !it.isDirectory }, { it.name }))) {
                if (!currentCoroutineContext().isActive) break

                if (file.isDirectory && recursive) {
                    scanJavaFileProgressive(file, true, filter).collect { emit(it) }
                } else if (!file.isDirectory) {
                    if (isMediaFile(file.name, null, filter)) {
                        emit(Uri.fromFile(file))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan error: ${e.message}")
        }
    }

    private suspend fun scanDocumentFile(
        folder: DocumentFile,
        recursive: Boolean,
        filter: MediaFilterType,
        list: MutableList<Uri>
    ) {
        try {
            val files = folder.listFiles()
            for (file in files) {
                if (!currentCoroutineContext().isActive) break

                if (file.isDirectory && recursive) {
                    scanDocumentFile(file, true, filter, list)
                } else if (!file.isDirectory) {
                    val name = file.name ?: ""
                    val mimeType = file.type ?: ""
                    if (isMediaFile(name, mimeType, filter)) {
                        list.add(file.uri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan error: ${e.message}")
        }
    }

    private suspend fun scanJavaFileRecursively(
        folder: File,
        recursive: Boolean,
        filter: MediaFilterType,
        list: MutableList<Uri>
    ) {
        try {
            val files = folder.listFiles() ?: return
            val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name }))

            for (file in sorted) {
                if (!currentCoroutineContext().isActive) break

                if (file.isDirectory && recursive) {
                    scanJavaFileRecursively(file, true, filter, list)
                } else if (!file.isDirectory) {
                    if (isMediaFile(file.name, null, filter)) {
                        list.add(Uri.fromFile(file))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan error: ${e.message}")
        }
    }

    private fun getRawPathFromUri(uri: Uri): String? {
        val uriString = uri.toString()
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath

        if (uriString.contains("primary%3A")) {
            val split = uriString.split("primary%3A")
            if (split.size > 1) {
                val decoded = URLDecoder.decode(split[1], "UTF-8")
                return "$externalStorage/$decoded"
            }
        }

        if (uri.scheme == "file") {
            return uri.path
        }

        return null
    }

    private fun isMediaFile(name: String?, mimeType: String?, filter: MediaFilterType): Boolean {
        val safeName = name?.lowercase() ?: ""
        val safeMime = mimeType?.lowercase() ?: ""
        val extension = safeName.substringAfterLast('.', "")

        val isImage = imageExtensions.contains(extension) || safeMime.startsWith("image/")
        val isVideo = videoExtensions.contains(extension) || safeMime.startsWith("video/")

        return when (filter) {
            MediaFilterType.PHOTOS_ONLY -> isImage
            MediaFilterType.VIDEO_ONLY -> isVideo
            MediaFilterType.MIXED -> isImage || isVideo
        }
    }
}