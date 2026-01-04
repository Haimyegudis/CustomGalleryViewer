// FileScanner.kt - ULTRA FAST VERSION
// משתמש ב-MediaStore במקום DocumentsContract - פי 10-20 יותר מהיר!

package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

object FileScanner {

    /**
     * סריקה מהירה באמצעות MediaStore
     * פי 10-20 יותר מהיר מ-DocumentsContract!
     */
    fun scanMediaFiles(context: Context, folderUri: Uri): List<Uri> {
        val startTime = System.currentTimeMillis()

        // נסה קודם MediaStore (מהיר!)
        val mediaStoreFiles = tryMediaStoreScan(context, folderUri)
        if (mediaStoreFiles.isNotEmpty()) {
            val duration = System.currentTimeMillis() - startTime
            Log.d("FileScanner", "⚡ MediaStore scan: ${mediaStoreFiles.size} files in ${duration}ms")
            return mediaStoreFiles
        }

        // Fallback ל-DocumentsContract (איטי)
        Log.d("FileScanner", "⚠️ Falling back to DocumentsContract (slow)")
        return scanWithDocumentsContract(context, folderUri)
    }

    /**
     * סריקה מהירה עם MediaStore
     * עובד מצוין עם DCIM, Pictures, Download, Movies
     */
    private fun tryMediaStoreScan(context: Context, folderUri: Uri): List<Uri> {
        val results = mutableListOf<Uri>()

        // חלץ את הנתיב מה-URI
        val folderPath = extractFolderPath(folderUri) ?: return emptyList()

        Log.d("FileScanner", "MediaStore scan for path: $folderPath")

        // סרוק תמונות
        val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        context.contentResolver.query(
            imageUri,
            imageProjection,
            "${MediaStore.Images.Media.DATA} LIKE ?",
            arrayOf("$folderPath%"),
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(dataColumn)

                // ודא שזה בתיקייה הנכונה (לא בתת-תיקיות)
                if (isInFolder(path, folderPath)) {
                    val contentUri = Uri.withAppendedPath(imageUri, id.toString())
                    results.add(contentUri)
                }
            }
        }

        // סרוק סרטונים
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        context.contentResolver.query(
            videoUri,
            videoProjection,
            "${MediaStore.Video.Media.DATA} LIKE ?",
            arrayOf("$folderPath%"),
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(dataColumn)

                if (isInFolder(path, folderPath)) {
                    val contentUri = Uri.withAppendedPath(videoUri, id.toString())
                    results.add(contentUri)
                }
            }
        }

        return results
    }

    /**
     * חילוץ נתיב תיקייה מ-URI
     */
    private fun extractFolderPath(folderUri: Uri): String? {
        val uriString = folderUri.toString()

        // דוגמאות:
        // content://com.android.externalstorage.documents/tree/primary:Download/InstaGold
        // → /storage/emulated/0/Download/InstaGold

        return when {
            uriString.contains("primary:") -> {
                val pathPart = uriString.substringAfter("primary:")
                    .substringBefore("/document")
                    .replace("%2F", "/")
                    .replace("%3A", ":")
                "/storage/emulated/0/$pathPart"
            }
            uriString.contains("home:") -> {
                val pathPart = uriString.substringAfter("home:")
                    .replace("%2F", "/")
                "/storage/emulated/0/$pathPart"
            }
            else -> null
        }
    }

    /**
     * בדוק שהקובץ בתיקייה (ולא בתת-תיקייה)
     */
    private fun isInFolder(filePath: String, folderPath: String): Boolean {
        if (!filePath.startsWith(folderPath)) return false

        val relativePath = filePath.removePrefix(folderPath).removePrefix("/")
        return !relativePath.contains("/")
    }

    /**
     * Fallback: סריקה איטית עם DocumentsContract
     */
    private fun scanWithDocumentsContract(context: Context, folderUri: Uri): List<Uri> {
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<Uri>()

        try {
            val treeUri = folderUri
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex)
                    val mimeType = cursor.getString(mimeIndex)

                    if (isMediaFile(name, mimeType)) {
                        val documentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        results.add(documentUri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileScanner", "DocumentsContract scan failed", e)
        }

        val duration = System.currentTimeMillis() - startTime
        Log.d("FileScanner", "🐌 DocumentsContract scan: ${results.size} files in ${duration}ms")

        return results
    }

    /**
     * בדוק אם זה קובץ מדיה
     */
    private fun isMediaFile(fileName: String, mimeType: String?): Boolean {
        val lowerName = fileName.lowercase(Locale.getDefault())

        // בדיקה לפי סיומת
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif")
        val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm", ".m4v")

        if (imageExtensions.any { lowerName.endsWith(it) }) return true
        if (videoExtensions.any { lowerName.endsWith(it) }) return true

        // בדיקה לפי MIME type
        if (mimeType != null) {
            if (mimeType.startsWith("image/")) return true
            if (mimeType.startsWith("video/")) return true
        }

        return false
    }
}