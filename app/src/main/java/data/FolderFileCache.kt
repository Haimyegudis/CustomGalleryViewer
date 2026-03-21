package com.example.customgalleryviewer.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderFileCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences("folder_file_cache", Context.MODE_PRIVATE)
    }
    private val playlistPrefs by lazy {
        context.getSharedPreferences("playlist_file_cache", Context.MODE_PRIVATE)
    }

    // In-memory cache for instant access
    private val memCache = java.util.concurrent.ConcurrentHashMap<String, List<Uri>>()

    fun getFolderFiles(bucketId: Long): List<Uri> {
        val key = "folder_$bucketId"
        memCache[key]?.let { cached ->
            // Invalidate stale content:// URIs - only return file:// URIs
            if (cached.isNotEmpty() && cached.first().scheme != "file") {
                memCache.remove(key)
                return emptyList()
            }
            return cached
        }
        val fromDisk = loadFromDisk(key, prefs)
        // Invalidate stale content:// URIs
        if (fromDisk.isNotEmpty() && fromDisk.first().scheme != "file") {
            prefs.edit().remove(key).apply()
            return emptyList()
        }
        return fromDisk
    }

    fun saveFolderFiles(bucketId: Long, files: List<Uri>) {
        val key = "folder_$bucketId"
        memCache[key] = files
        saveToDisk(key, files, prefs)
    }

    fun getPlaylistFiles(playlistId: Long): List<Uri> {
        val key = "playlist_$playlistId"
        memCache[key]?.let { return it }
        return loadFromDisk(key, playlistPrefs)
    }

    fun savePlaylistFiles(playlistId: Long, files: List<Uri>) {
        val key = "playlist_$playlistId"
        memCache[key] = files
        saveToDisk(key, files, playlistPrefs)
    }

    private fun loadFromDisk(key: String, prefs: android.content.SharedPreferences): List<Uri> {
        val data = prefs.getString(key, null) ?: return emptyList()
        return try {
            val list = data.split("\n").filter { it.isNotEmpty() }.map { Uri.parse(it) }
            memCache[key] = list
            list
        } catch (_: Exception) { emptyList() }
    }

    private fun saveToDisk(key: String, files: List<Uri>, prefs: android.content.SharedPreferences) {
        val data = files.joinToString("\n") { it.toString() }
        prefs.edit().putString(key, data).apply()
    }
}
