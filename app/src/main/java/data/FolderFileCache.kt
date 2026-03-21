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
    @Volatile private var folderCacheWarmed = false
    @Volatile private var playlistCacheWarmed = false

    // Preload all folder caches into memory on first access - makes subsequent calls instant
    private fun warmFolderCache() {
        if (folderCacheWarmed) return
        folderCacheWarmed = true
        try {
            prefs.all.forEach { (key, value) ->
                if (key.startsWith("folder_") && value is String && value.isNotEmpty()) {
                    val list = value.split("\n").filter { it.isNotEmpty() }.map { Uri.parse(it) }
                    if (list.isNotEmpty() && list.first().scheme == "file") {
                        memCache[key] = list
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun warmPlaylistCache() {
        if (playlistCacheWarmed) return
        playlistCacheWarmed = true
        try {
            playlistPrefs.all.forEach { (key, value) ->
                if (key.startsWith("playlist_") && value is String && value.isNotEmpty()) {
                    val list = value.split("\n").filter { it.isNotEmpty() }.map { Uri.parse(it) }
                    memCache[key] = list
                }
            }
        } catch (_: Exception) {}
    }

    init {
        // Warm caches on a background thread at singleton creation
        Thread {
            warmFolderCache()
            warmPlaylistCache()
        }.start()
    }

    fun getFolderFiles(bucketId: Long): List<Uri> {
        val key = "folder_$bucketId"
        memCache[key]?.let { return it }
        // Fallback: warm cache if not yet done, then try again
        if (!folderCacheWarmed) { warmFolderCache(); memCache[key]?.let { return it } }
        return emptyList()
    }

    fun saveFolderFiles(bucketId: Long, files: List<Uri>) {
        val key = "folder_$bucketId"
        memCache[key] = files
        saveToDisk(key, files, prefs)
    }

    fun getPlaylistFiles(playlistId: Long): List<Uri> {
        val key = "playlist_$playlistId"
        memCache[key]?.let { return it }
        if (!playlistCacheWarmed) { warmPlaylistCache(); memCache[key]?.let { return it } }
        return emptyList()
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
