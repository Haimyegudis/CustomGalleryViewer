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

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "dng", "cr2", "nef", "arw")
    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "ts", "m4v", "mpg", "mpeg", "vob")

    suspend fun scanPlaylistItems(
        items: List<PlaylistItemEntity>,
        filter: MediaFilterType
    ): List<Uri> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<Uri>()
        Log.d("FileScanner", "Starting Deep Scan on IO Thread...")

        try {
            for (item in items) {
                if (!isActive) break

                val uri = Uri.parse(item.uriString)
                Log.d("FileScanner", "Analyzing Root: $uri | Type: ${item.type}")

                if (item.type == ItemType.FILE) {
                    resultList.add(uri)
                } else {
                    // ניסיון 1: נתיב פיזי (מהיר)
                    val rawPath = getRawPathFromUri(uri)
                    var scanned = false

                    if (rawPath != null) {
                        val file = File(rawPath)
                        if (file.exists() && file.isDirectory) {
                            Log.d("FileScanner", "Scanning as Native File: $rawPath")
                            scanJavaFileRecursively(file, item.isRecursive, filter, resultList)
                            scanned = true
                        }
                    }

                    // ניסיון 2: USB / SAF (השיטה המשופרת)
                    if (!scanned) {
                        Log.d("FileScanner", "Scanning via DocumentsContract: $uri")
                        scanViaDocumentsContract(uri, item.isRecursive, filter, resultList)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileScanner", "Fatal Scan Error", e)
        }

        Log.d("FileScanner", "Scan Complete. Total Items: ${resultList.size}")
        return@withContext resultList
    }

    // פונקציה חדשה ואמינה לסריקת USB דרך ה-ContentResolver ישירות
    private suspend fun scanViaDocumentsContract(
        treeUri: Uri,
        recursive: Boolean,
        filter: MediaFilterType,
        list: MutableList<Uri>
    ) {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val cursor = try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )
        } catch (e: Exception) {
            Log.e("FileScanner", "Failed to query children: $treeUri", e)
            return
        }

        cursor?.use {
            while (it.moveToNext() && currentCoroutineContext().isActive) {
                val docIdCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                if (docIdCol == -1) continue

                val documentId = it.getString(docIdCol)
                val name = if (nameCol != -1) it.getString(nameCol) else ""
                val mimeType = if (mimeCol != -1) it.getString(mimeCol) else ""

                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (recursive) {
                        // ברקורסיה עלינו לבנות את עץ הילדים החדש
                        // הערה: סריקת USB רקורסיבית עשויה להיות איטית, אבל זו הדרך הנכונה
                        scanViaDocumentsContract(fileUri, true, filter, list)
                    }
                } else {
                    if (isMediaFile(name, mimeType, filter)) {
                        list.add(fileUri)
                    }
                }
            }
        }
    }

    private suspend fun scanJavaFileRecursively(
        folder: File,
        recursive: Boolean,
        filter: MediaFilterType,
        list: MutableList<Uri>
    ) {
        val files = folder.listFiles() ?: return
        val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name }))

        for (file in sortedFiles) {
            if (!currentCoroutineContext().isActive) return

            if (file.isDirectory) {
                if (recursive) scanJavaFileRecursively(file, true, filter, list)
            } else {
                if (isMediaFile(file.name, null, filter)) {
                    list.add(Uri.fromFile(file))
                }
            }
        }
    }

    private fun getRawPathFromUri(uri: Uri): String? {
        val uriString = uri.toString()
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath

        if (uriString.contains("primary%3A")) {
            val split = uriString.split("primary%3A")
            if (split.size > 1) {
                return "$externalStorage/${URLDecoder.decode(split[1], "UTF-8")}"
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