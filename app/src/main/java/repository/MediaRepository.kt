package com.example.customgalleryviewer.repository

import android.content.Context
import android.net.Uri
import com.example.customgalleryviewer.data.MediaCacheManager
import com.example.customgalleryviewer.data.PlaylistDao
import com.example.customgalleryviewer.data.PlaylistWithItems
import com.example.customgalleryviewer.util.FileScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val cacheManager: MediaCacheManager,
    @ApplicationContext private val context: Context
) {
    // יצירת instance של FileScanner
    private val fileScanner = FileScanner(context, cacheManager)

    fun getPlaylistsFlow(): Flow<List<PlaylistWithItems>> = playlistDao.getPlaylistsFlow()

    /**
     * מחזיר Flow שפולט באצ'ים של קבצים
     * זה מאפשר לנגן להתחיל להציג מיד את הקבצים הראשונים
     */
    fun getMediaFilesFlow(playlistId: Long): Flow<List<Uri>> = kotlinx.coroutines.flow.flow {
        val playlistWithItems = playlistDao.getPlaylistWithItems(playlistId)

        if (playlistWithItems != null) {
            fileScanner.scanPlaylistItemsFlow(
                items = playlistWithItems.items,
                filter = playlistWithItems.playlist.mediaFilterType
            ).collect { batch ->
                emit(batch)
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    /**
     * ניקוי Cache ידני
     */
    fun clearCache() {
        cacheManager.clearAllCache()
    }

    /**
     * קבלת מידע על Cache
     */
    fun getCacheInfo() = cacheManager.getCacheInfo()
}