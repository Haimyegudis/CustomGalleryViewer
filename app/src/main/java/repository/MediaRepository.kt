package com.example.customgalleryviewer.repository

import android.content.Context
import android.net.Uri
import com.example.customgalleryviewer.data.MediaCacheManager
import com.example.customgalleryviewer.data.PlaylistDao
import com.example.customgalleryviewer.data.PlaylistWithItems
import com.example.customgalleryviewer.data.SettingsManager
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
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context
) {
    private fun createScanner() = FileScanner(context, cacheManager, settingsManager.getShowHidden())

    fun getPlaylistsFlow(): Flow<List<PlaylistWithItems>> = playlistDao.getPlaylistsFlow()

    fun getMediaFilesFlow(playlistId: Long): Flow<List<Uri>> = kotlinx.coroutines.flow.flow {
        val playlistWithItems = playlistDao.getPlaylistWithItems(playlistId)

        if (playlistWithItems != null) {
            createScanner().scanPlaylistItemsFlow(
                items = playlistWithItems.items,
                filter = playlistWithItems.playlist.mediaFilterType
            ).collect { batch ->
                emit(batch)
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getPlaylistWithItems(playlistId: Long) = playlistDao.getPlaylistWithItems(playlistId)

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun hidePlaylist(playlistId: Long, hidden: Boolean) {
        playlistDao.setPlaylistHidden(playlistId, hidden)
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        playlistDao.renamePlaylist(playlistId, name)
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