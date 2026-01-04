// MediaCacheManager.kt
package com.example.customgalleryviewer.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class CachedFolder(
    val folderUri: String,
    val files: List<String>,
    val lastScanned: Long,
    val fileCount: Int
)

@Singleton
class MediaCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("media_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L
    }

    fun getCachedFiles(folderUri: Uri): List<Uri>? {
        val key = getFolderKey(folderUri)
        val json = prefs.getString(key, null) ?: return null

        return try {
            val cached = gson.fromJson<CachedFolder>(json, CachedFolder::class.java)
            val isValid = (System.currentTimeMillis() - cached.lastScanned) < CACHE_VALIDITY_MS

            if (isValid) {
                cached.files.map { Uri.parse(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun cacheFiles(folderUri: Uri, files: List<Uri>) {
        val key = getFolderKey(folderUri)
        val cached = CachedFolder(
            folderUri = folderUri.toString(),
            files = files.map { it.toString() },
            lastScanned = System.currentTimeMillis(),
            fileCount = files.size
        )

        prefs.edit()
            .putString(key, gson.toJson(cached))
            .apply()
    }

    fun invalidateCache(folderUri: Uri) {
        val key = getFolderKey(folderUri)
        prefs.edit().remove(key).apply()
    }

    fun clearAllCache() {
        prefs.edit().clear().apply()
    }

    fun getCacheInfo(): Map<String, CachedFolder> {
        val result = mutableMapOf<String, CachedFolder>()

        prefs.all.forEach { (key, value) ->
            if (value is String) {
                try {
                    val cached = gson.fromJson<CachedFolder>(value, CachedFolder::class.java)
                    result[key] = cached
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        return result
    }

    private fun getFolderKey(folderUri: Uri): String {
        val uriStr = folderUri.toString()
        val deviceId = extractDeviceId(uriStr)
        val pathHash = uriStr.hashCode().toString()
        return "cache_${deviceId}_${pathHash}"
    }

    private fun extractDeviceId(uriStr: String): String {
        val regex = Regex("""tree/([^%/:]+)""")
        val match = regex.find(uriStr)
        return match?.groupValues?.getOrNull(1) ?: "unknown"
    }
}