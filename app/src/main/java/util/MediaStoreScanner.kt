package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.customgalleryviewer.data.MediaFilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
object MediaStoreScanner {

    suspend fun scanInternalStorage(
        context: Context,
        folderPath: String,
        filter: MediaFilterType
    ): List<Uri> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<Uri>()

        Log.d("MediaStoreScanner", "Scanning MediaStore for path: $folderPath")

        // סריקת תמונות
        if (filter == MediaFilterType.PHOTOS_ONLY || filter == MediaFilterType.MIXED) {
            val imageUris = scanImages(context, folderPath)
            mediaList.addAll(imageUris)
            Log.d("MediaStoreScanner", "Found ${imageUris.size} images")
        }

        // סריקת וידאו
        if (filter == MediaFilterType.VIDEO_ONLY || filter == MediaFilterType.MIXED) {
            val videoUris = scanVideos(context, folderPath)
            mediaList.addAll(videoUris)
            Log.d("MediaStoreScanner", "Found ${videoUris.size} videos")
        }

        Log.d("MediaStoreScanner", "Total found: ${mediaList.size} files")
        mediaList
    }

    private fun scanImages(context: Context, folderPath: String): List<Uri> {
        val images = mutableListOf<Uri>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath%")

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(dataColumn)

                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                images.add(uri)

                Log.v("MediaStoreScanner", "Found image: $path")
            }
        }

        return images
    }

    private fun scanVideos(context: Context, folderPath: String): List<Uri> {
        val videos = mutableListOf<Uri>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME
        )

        val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath%")

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(dataColumn)

                val uri = Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                videos.add(uri)

                Log.v("MediaStoreScanner", "Found video: $path")
            }
        }

        return videos
    }
}