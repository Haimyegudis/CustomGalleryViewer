package com.example.customgalleryviewer.repository

import android.content.Context
import android.net.Uri
import com.example.customgalleryviewer.data.PlaylistDao
import com.example.customgalleryviewer.data.PlaylistWithItems
import com.example.customgalleryviewer.util.FileScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    @ApplicationContext private val context: Context
) {
    private val fileScanner = FileScanner(context)

    // תיקון: מחזיר רשימה מורחבת (עם פריטים)
    fun getPlaylistsFlow(): Flow<List<PlaylistWithItems>> = playlistDao.getPlaylistsFlow()

    fun getMediaFilesFlow(playlistId: Long): Flow<List<Uri>> = flow {
        val playlistWithItems = playlistDao.getPlaylistWithItems(playlistId)

        if (playlistWithItems != null) {
            val allFiles = fileScanner.scanPlaylistItems(
                items = playlistWithItems.items,
                filter = playlistWithItems.playlist.mediaFilterType
            )
            emit(allFiles)
        } else {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    // פונקציית מחיקה שהייתה חסרה
    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }
}