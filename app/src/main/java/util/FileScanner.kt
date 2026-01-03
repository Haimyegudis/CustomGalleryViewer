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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

class FileScanner(private val context: Context) {

    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif",
        "dng", "cr2", "nef", "arw", "raw", "tif", "tiff", "svg",
        "jpe", "jfif", "psd", "ico"
    )
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm",
        "ts", "m4v", "mpg", "mpeg", "vob", "m2ts", "mts", "ogv",
        "f4v", "asf", "rm", "rmvb", "divx"
    )

    suspend fun scanPlaylistItems(
        items: List<PlaylistItemEntity>,
        filter: MediaFilterType
    ): List<Uri> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<Uri>()
        Log.d("FileScanner", "=== Starting Deep Scan ===")
        Log.d("FileScanner", "Total items to scan: ${items.size}")
        Log.d("FileScanner", "Filter: $filter")

        try {
            for (item in items) {
                if (!isActive) break

                val uri = Uri.parse(item.uriString)
                Log.d("FileScanner", "")
                Log.d("FileScanner", ">>> Processing item:")
                Log.d("FileScanner", "    URI: $uri")
                Log.d("FileScanner", "    Type: ${item.type}")
                Log.d("FileScanner", "    Recursive: ${item.isRecursive}")

                if (item.type == ItemType.FILE) {
                    Log.d("FileScanner", "    -> Adding as single file")
                    resultList.add(uri)
                } else {
                    var scanned = false
                    var fileCount = 0

                    // Try DocumentFile first
                    try {
                        val docFile = DocumentFile.fromTreeUri(context, uri)
                        if (docFile != null && docFile.exists() && docFile.isDirectory) {
                            Log.d("FileScanner", "    -> Scanning via DocumentFile")
                            fileCount = scanDocumentFile(docFile, item.isRecursive, filter, resultList)
                            scanned = true
                            Log.d("FileScanner", "    -> DocumentFile scan found $fileCount files")
                        }
                    } catch (e: Exception) {
                        Log.w("FileScanner", "    -> DocumentFile failed: ${e.message}")
                    }

                    // Try Native file path
                    if (!scanned) {
                        val rawPath = getRawPathFromUri(uri)
                        if (rawPath != null) {
                            val file = File(rawPath)
                            if (file.exists() && file.isDirectory) {
                                Log.d("FileScanner", "    -> Scanning as Native File: $rawPath")
                                fileCount = scanJavaFileRecursively(file, item.isRecursive, filter, resultList)
                                scanned = true
                                Log.d("FileScanner", "    -> Native scan found $fileCount files")
                            }
                        }
                    }

                    // Try DocumentsContract
                    if (!scanned) {
                        Log.d("FileScanner", "    -> Scanning via DocumentsContract")
                        fileCount = scanViaDocumentsContract(uri, item.isRecursive, filter, resultList)
                        scanned = true
                        Log.d("FileScanner", "    -> DocumentsContract scan found $fileCount files")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileScanner", "=== FATAL SCAN ERROR ===", e)
        }

        Log.d("FileScanner", "")
        Log.d("FileScanner", "=== Scan Complete ===")
        Log.d("FileScanner", "Total media files found: ${resultList.size}")
        return@withContext resultList
    }

    private suspend fun scanDocumentFile(
        folder: DocumentFile,
        recursive: Boolean,
        filter: MediaFilterType,
        list: MutableList<Uri>
    ): Int {
        var count = 0
        try {
            val files = folder.listFiles()
            Log.d("FileScanner", "       DocumentFile children count: ${files.size}")

            for (file in files) {
                if (!currentCoroutineContext().isActive) break

                if (file.isDirectory) {
                    if (recursive) {
                        count += scanDocumentFile(file, true, filter, list)
                    }
                } else {
                    val name = file.name ?: ""
                    val mime = file.type ?: ""
                    if (isMediaFile(name, mime, filter)) {
                        list.add(file.uri)
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileScanner", "       Error scanning DocumentFile", e)
        }
        return count
    }

    private suspend fun scanViaDocumentsContract(
        treeUri: Uri,
        recursive: Boolean,
        filter: MediaFilterType,
        list: MutableList<Uri>
    ): Int {
        var count = 0
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            Log.d("FileScanner", "       Query URI: $childrenUri")

            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )

            cursor?.use {
                val idCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                Log.d("FileScanner", "       Cursor count: ${it.count}")

                while (it.moveToNext() && currentCoroutineContext().isActive) {
                    if (idCol == -1) continue

                    val documentId = it.getString(idCol)
                    val name = if (nameCol != -1) it.getString(nameCol) ?: "" else ""
                    val mimeType = if (mimeCol != -1) it.getString(mimeCol) ?: "" else ""

                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (recursive) {
                            count += scanViaDocumentsContract(fileUri, true, filter, list)
                        }
                    } else {
                        if (isMediaFile(name, mimeType, filter)) {
                            list.add(fileUri)
                            count++
                            if (count <= 5) {
                                Log.d("FileScanner", "       Found: $name ($mimeType)")
                            }
                        }
                    }
                }
            } ?: run {
                Log.e("FileScanner", "       Cursor is null!")
            }
        } catch (e: Exception) {
            Log.e("FileScanner", "       DocumentsContract scan error", e)
        }
        return count
    }

    private suspend fun scanJavaFileRecursively(
        folder: File,
        recursive: Boolean,
        filter: MediaFilterType,
        list: MutableList<Uri>
    ): Int {
        var count = 0
        try {
            val files = folder.listFiles() ?: return 0
            val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name }))

            for (file in sortedFiles) {
                if (!currentCoroutineContext().isActive) return count

                if (file.isDirectory) {
                    if (recursive) {
                        count += scanJavaFileRecursively(file, true, filter, list)
                    }
                } else {
                    if (isMediaFile(file.name, null, filter)) {
                        list.add(Uri.fromFile(file))
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileScanner", "       Error scanning Java File", e)
        }
        return count
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

        val isImageExt = imageExtensions.contains(extension)
        val isVideoExt = videoExtensions.contains(extension)

        val isImageMime = safeMime.startsWith("image/")
        val isVideoMime = safeMime.startsWith("video/")

        val isImage = isImageExt || isImageMime
        val isVideo = isVideoExt || isVideoMime

        return when (filter) {
            MediaFilterType.PHOTOS_ONLY -> isImage
            MediaFilterType.VIDEO_ONLY -> isVideo
            MediaFilterType.MIXED -> isImage || isVideo
        }
    }
}