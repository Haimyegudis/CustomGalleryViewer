package com.example.customgalleryviewer.repository

import android.net.Uri
import android.util.Log
import com.example.customgalleryviewer.data.CachedMediaFileDao
import com.example.customgalleryviewer.data.CachedMediaFileEntity
import com.example.customgalleryviewer.data.PlaylistItemDao
import com.example.customgalleryviewer.util.FileScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val playlistItemDao: PlaylistItemDao,
    private val cachedMediaFileDao: CachedMediaFileDao,
    private val fileScanner: FileScanner
) {
    // Cache validity: 30 דקות
    private val CACHE_VALIDITY_MS = 30 * 60 * 1000L

    /**
     * מחזיר Flow של קבצי מדיה.
     * אם forceRefresh=false, משתמש ב-cache אם קיים ותקף.
     * אם forceRefresh=true, סורק מחדש ומעדכן cache.
     */
    fun getMediaFilesFlow(playlistId: Long, forceRefresh: Boolean = false): Flow<List<Uri>> = flow {
        withContext(Dispatchers.IO) {
            // בדוק אם יש cache תקף
            val shouldUseCache = !forceRefresh && isCacheValid(playlistId)

            if (shouldUseCache) {
                Log.d("MediaRepository", "Using cached files for playlist $playlistId")
                val cachedFiles = cachedMediaFileDao.getCachedFilesSync(playlistId)
                val uris = cachedFiles.map { Uri.parse(it.uriString) }
                emit(uris)
            } else {
                Log.d("MediaRepository", "Scanning files for playlist $playlistId (forceRefresh=$forceRefresh)")

                // טען את הפריטים מה-playlist
                val items = playlistItemDao.getItemsForPlaylistSync(playlistId)

                if (items.isEmpty()) {
                    Log.w("MediaRepository", "No items in playlist $playlistId")
                    emit(emptyList())
                    return@withContext
                }

                // סרוק קבצים
                val filter = items.firstOrNull()?.filter ?: com.example.customgalleryviewer.data.MediaFilterType.MIXED
                val scannedUris = fileScanner.scanPlaylistItems(items, filter)

                Log.d("MediaRepository", "Scanned ${scannedUris.size} files")

                // שמור ב-cache
                if (scannedUris.isNotEmpty()) {
                    saveToCache(playlistId, scannedUris)
                }

                emit(scannedUris)
            }
        }
    }

    /**
     * בודק אם ה-cache תקף (קיים ולא ישן מדי)
     */
    private suspend fun isCacheValid(playlistId: Long): Boolean {
        val count = cachedMediaFileDao.getCachedFileCount(playlistId)
        if (count == 0) {
            Log.d("MediaRepository", "No cache for playlist $playlistId")
            return false
        }

        val lastCacheTime = cachedMediaFileDao.getLastCacheTime(playlistId) ?: return false
        val age = System.currentTimeMillis() - lastCacheTime
        val isValid = age < CACHE_VALIDITY_MS

        Log.d("MediaRepository", "Cache age: ${age / 1000}s, valid: $isValid")
        return isValid
    }

    /**
     * שומר רשימת URIs ב-cache
     */
    private suspend fun saveToCache(playlistId: Long, uris: List<Uri>) {
        Log.d("MediaRepository", "Saving ${uris.size} files to cache for playlist $playlistId")

        // נקה cache ישן
        cachedMediaFileDao.clearCacheForPlaylist(playlistId)

        // שמור קבצים חדשים
        val entities = uris.map { uri ->
            CachedMediaFileEntity(
                playlistId = playlistId,
                uriString = uri.toString(),
                fileName = uri.lastPathSegment,
                lastModified = System.currentTimeMillis()
            )
        }

        cachedMediaFileDao.insertAll(entities)
        Log.d("MediaRepository", "Cache saved successfully")
    }

    /**
     * מנקה cache לפלייליסט מסוים
     */
    suspend fun clearCache(playlistId: Long) {
        Log.d("MediaRepository", "Clearing cache for playlist $playlistId")
        cachedMediaFileDao.clearCacheForPlaylist(playlistId)
    }

    /**
     * מנקה את כל ה-cache
     */
    suspend fun clearAllCache() {
        // צריך להוסיף פונקציה ב-DAO
        Log.d("MediaRepository", "Clearing all cache")
    }
}