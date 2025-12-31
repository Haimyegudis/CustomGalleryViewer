package com.example.customgalleryviewer.repository

import android.content.Context
import android.net.Uri
import com.example.customgalleryviewer.data.PlaylistDao
import com.example.customgalleryviewer.util.FileScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    @ApplicationContext private val context: Context
) {
    private val fileScanner = FileScanner(context)

    suspend fun getAllPlaylists() = playlistDao.getAllPlaylists()

    // פונקציה שמחזירה Flow של רשימות (Chunks)
    fun getMediaFilesFlow(playlistId: Long): Flow<List<Uri>> = flow {
        val playlistWithItems = playlistDao.getPlaylistWithItems(playlistId)

        if (playlistWithItems != null) {
            fileScanner.scanPlaylistItemsFlow(
                items = playlistWithItems.items,
                filter = playlistWithItems.playlist.mediaFilterType
            ).collect { batch ->
                emit(batch)
            }
        }
    }
}