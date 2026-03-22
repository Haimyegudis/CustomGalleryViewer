package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface

data class GeoMedia(
    val uri: Uri,
    val latitude: Double,
    val longitude: Double,
    val name: String
)

object ExifGeoExtractor {
    private const val TAG = "ExifGeoExtractor"

    fun extractGeotaggedMedia(context: Context): List<GeoMedia> {
        val result = mutableListOf<GeoMedia>()

        // Query images with GPS data
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.LATITUDE,
                    MediaStore.Images.Media.LONGITUDE
                ),
                null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val dataIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val path = if (dataIdx >= 0) cursor.getString(dataIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )

                    if (path.isNotEmpty()) {
                        try {
                            val exif = ExifInterface(path)
                            val latLong = exif.latLong
                            if (latLong != null && latLong[0] != 0.0 && latLong[1] != 0.0) {
                                result.add(GeoMedia(uri, latLong[0], latLong[1], name))
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting geo data", e)
        }

        return result
    }
}
