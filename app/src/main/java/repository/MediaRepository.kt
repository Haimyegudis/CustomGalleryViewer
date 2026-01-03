package com.example.customgalleryviewer.repository

import android.net.Uri
import android.util.Log
import com.example.customgalleryviewer.data.CachedMediaFileDao
import com.example.customgalleryviewer.data.CachedMediaFileEntity
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.data.PlaylistDao
import com.example.customgalleryviewer.data.PlaylistItemDao
import com.example.customgalleryviewer.data.PlaylistWithItems
import com.example.customgalleryviewer.util.FileScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
    private val cachedMediaFileDao: CachedMediaFileDao,
    private val fileScanner: FileScanner
) {
    private val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L

    fun getPlaylistsFlow(): Flow<List<PlaylistWithItems>> {
        return playlistDao.getPlaylistsFlow()
    }

    /**
     * PROGRESSIVE LOADING - Shows files immediately as they're found!
     */
    fun getMediaFilesFlow(playlistId: Long, forceRefresh: Boolean = false): Flow<List<Uri>> = flow {
        val shouldUseCache = !forceRefresh && isCacheValid(playlistId)

        if (shouldUseCache) {
            Log.d("MediaRepository", "✓ Using CACHED files")
            val cachedFiles = cachedMediaFileDao.getCachedFilesSync(playlistId)
            val uris = cachedFiles.map { Uri.parse(it.uriString) }
            emit(uris)
        } else {
            Log.d("MediaRepository", "🚀 PROGRESSIVE scan starting...")

            val items = playlistItemDao.getItemsForPlaylistSync(playlistId)
            if (items.isEmpty()) {
                emit(emptyList())
                return@flow
            }

            val playlistWithItems = playlistDao.getPlaylistWithItems(playlistId)
            val filter = playlistWithItems?.playlist?.mediaFilterType ?: MediaFilterType.MIXED

            // PROGRESSIVE: emit files as we find them!
            val allFiles = mutableListOf<Uri>()

            fileScanner.scanPlaylistItemsProgressive(items, filter).collect { uri ->
                allFiles.add(uri)
                // Emit current list every 10 files
                if (allFiles.size % 10 == 0) {
                    emit(allFiles.toList())
                }
            }

            // Final emit
            emit(allFiles)

            Log.d("MediaRepository", "✅ Scan complete: ${allFiles.size} files")

            // Save to cache in background
            if (allFiles.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    launch {
                        saveToCache(playlistId, allFiles)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun isCacheValid(playlistId: Long): Boolean {
        val count = cachedMediaFileDao.getCachedFileCount(playlistId)
        if (count == 0) return false

        val lastCacheTime = cachedMediaFileDao.getLastCacheTime(playlistId) ?: return false
        val age = System.currentTimeMillis() - lastCacheTime
        return age < CACHE_VALIDITY_MS
    }

    private suspend fun saveToCache(playlistId: Long, uris: List<Uri>) {
        Log.d("MediaRepository", "💾 Saving ${uris.size} files to cache...")

        cachedMediaFileDao.clearCacheForPlaylist(playlistId)

        val entities = uris.map { uri ->
            CachedMediaFileEntity(
                playlistId = playlistId,
                uriString = uri.toString(),
                fileName = uri.lastPathSegment,
                lastModified = System.currentTimeMillis()
            )
        }

        // Save in batches
        entities.chunked(500).forEach { batch ->
            cachedMediaFileDao.insertAll(batch)
        }

        Log.d("MediaRepository", "✅ Cache saved")
    }

    suspend fun deletePlaylist(playlistId: Long) {
        withContext(Dispatchers.IO) {
            playlistDao.deletePlaylist(playlistId)
        }
    }

    suspend fun clearCache(playlistId: Long) {
        cachedMediaFileDao.clearCacheForPlaylist(playlistId)
    }
}