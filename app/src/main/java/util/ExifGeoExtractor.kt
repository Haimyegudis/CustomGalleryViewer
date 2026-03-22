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

    // In-memory cache
    @Volatile
    private var cachedResult: List<GeoMedia>? = null

    fun extractGeotaggedMedia(context: Context, forceRefresh: Boolean = false): List<GeoMedia> {
        if (!forceRefresh) cachedResult?.let { return it }

        val result = mutableListOf<GeoMedia>()

        // Query images — only those with non-zero lat/long via EXIF
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME
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

                    if (path.isNotEmpty()) {
                        try {
                            val exif = ExifInterface(path)
                            val latLong = exif.latLong
                            if (latLong != null && latLong[0] != 0.0 && latLong[1] != 0.0) {
                                val uri = android.content.ContentUris.withAppendedId(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                                )
                                result.add(GeoMedia(uri, latLong[0], latLong[1], name))
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting geo data", e)
        }

        cachedResult = result
        Log.i(TAG, "Found ${result.size} geotagged images")
        return result
    }
}
