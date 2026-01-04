// MediaCacheManager.kt - SMART CACHE
// בודק timestamps - רק סורק אם היו שינויים!

package com.example.customgalleryviewer.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

data class CachedFolder(
    val folderUri: String,
    val files: List<String>,
    val lastScanned: Long,
    val fileCount: Int,
    val folderModifiedTime: Long  // חדש! זמן שינוי אחרון בתיקייה
)

@Singleton
class MediaCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("media_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val CACHE_VALIDITY_MS = 30 * 24 * 60 * 60 * 1000L  // 30 יום
    }

    /**
     * קבל קבצים מ-cache - רק אם לא היו שינויים!
     */
    fun getCachedFiles(folderUri: Uri): List<Uri>? {
        val key = getFolderKey(folderUri)
        val json = prefs.getString(key, null)

        if (json == null) {
            Log.d("MediaCache", "❌ No cache entry for key: $key")
            return null
        }

        return try {
            val cached = gson.fromJson<CachedFolder>(json, CachedFolder::class.java)
            val age = System.currentTimeMillis() - cached.lastScanned
            val isValid = age < CACHE_VALIDITY_MS

            if (!isValid) {
                Log.d("MediaCache", "❌ Expired cache: age ${age / 1000 / 60} minutes")
                return null
            }

            // בדוק אם היו שינויים בתיקייה!
            val currentModifiedTime = getFolderModifiedTime(folderUri)
            if (currentModifiedTime > cached.folderModifiedTime) {
                Log.d("MediaCache", "🔄 Folder changed! Cache: ${cached.folderModifiedTime}, Current: $currentModifiedTime")
                return null
            }

            Log.d("MediaCache", "✅ Valid cache: ${cached.fileCount} files, age: ${age / 1000 / 60} minutes")
            cached.files.map { Uri.parse(it) }
        } catch (e: Exception) {
            Log.e("MediaCache", "Error parsing cache for $key", e)
            null
        }
    }

    /**
     * שמור קבצים ב-cache עם timestamp של התיקייה
     */
    fun cacheFiles(folderUri: Uri, files: List<Uri>) {
        val key = getFolderKey(folderUri)
        val folderModifiedTime = getFolderModifiedTime(folderUri)

        val cached = CachedFolder(
            folderUri = normalizeUri(folderUri).toString(),
            files = files.map { it.toString() },
            lastScanned = System.currentTimeMillis(),
            fileCount = files.size,
            folderModifiedTime = folderModifiedTime
        )

        val json = gson.toJson(cached)
        prefs.edit().putString(key, json).apply()
        Log.d("MediaCache", "✅ Cached ${files.size} files with timestamp: $folderModifiedTime")
    }

    /**
     * קבל זמן שינוי אחרון של התיקייה
     * משתמש ב-MediaStore לבדיקה מהירה!
     */
    private fun getFolderModifiedTime(folderUri: Uri): Long {
        val folderPath = extractFolderPath(folderUri) ?: return 0L

        var latestTime = 0L

        try {
            // בדוק תמונות
            val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                imageUri,
                arrayOf(MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.DATA),
                "${MediaStore.Images.Media.DATA} LIKE ?",
                arrayOf("$folderPath%"),
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val modifiedTime = cursor.getLong(0) * 1000
                    if (modifiedTime > latestTime) latestTime = modifiedTime
                }
            }

            // בדוק סרטונים
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                videoUri,
                arrayOf(MediaStore.Video.Media.DATE_MODIFIED, MediaStore.Video.Media.DATA),
                "${MediaStore.Video.Media.DATA} LIKE ?",
                arrayOf("$folderPath%"),
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val modifiedTime = cursor.getLong(0) * 1000
                    if (modifiedTime > latestTime) latestTime = modifiedTime
                }
            }
        } catch (e: Exception) {
            Log.e("MediaCache", "Error getting folder modified time", e)
        }

        return latestTime
    }

    private fun extractFolderPath(folderUri: Uri): String? {
        val uriString = folderUri.toString()

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

    fun removeCacheEntry(folderUri: Uri) {
        val key = getFolderKey(folderUri)
        prefs.edit().remove(key).apply()
        Log.d("MediaCache", "Removed cache entry: $key")
    }

    fun removeAllCache() {
        prefs.edit().clear().apply()
        Log.d("MediaCache", "Cleared all cache")
    }

    fun clearAllCache() {
        removeAllCache()
    }

    fun getCacheInfo(): Map<String, CachedFolder> {
        val result = mutableMapOf<String, CachedFolder>()
        val all = prefs.all

        for ((key, value) in all) {
            if (value is String) {
                try {
                    val cached = gson.fromJson<CachedFolder>(value, CachedFolder::class.java)
                    result[key] = cached
                } catch (e: Exception) {
                    Log.e("MediaCache", "Error parsing cache entry: $key", e)
                }
            }
        }

        Log.d("MediaCache", "Cache info: ${result.size} entries")
        return result
    }

    private fun getFolderKey(folderUri: Uri): String {
        val uriStr = folderUri.toString()
        val deviceId = extractDeviceId(uriStr)
        val pathPart = extractPath(uriStr)
        val key = "cache_${deviceId}_${pathPart.hashCode()}"
        Log.v("MediaCache", "Generated key: $key for URI: $uriStr")
        return key
    }

    private fun extractDeviceId(uriStr: String): String {
        val regex = Regex("""tree/([^%/:]+)""")
        val match = regex.find(uriStr)
        return match?.groupValues?.getOrNull(1) ?: "primary"
    }

    private fun extractPath(uriStr: String): String {
        val parts = uriStr.split(":")
        return if (parts.size > 1) {
            parts.last().replace("/", "_")
        } else {
            uriStr.substringAfterLast("/")
        }
    }

    private fun normalizeUri(uri: Uri): Uri {
        val uriString = uri.toString()
        return if (uriString.contains("%3A")) {
            Uri.parse(URLDecoder.decode(uriString, "UTF-8"))
        } else {
            uri
        }
    }
}