package com.example.customgalleryviewer.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaCacheManager
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.data.PlaylistDao
import com.example.customgalleryviewer.data.PlaylistEntity
import com.example.customgalleryviewer.data.PlaylistItemEntity
import com.example.customgalleryviewer.util.FileScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AddPlaylistViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val cacheManager: MediaCacheManager,
    private val settingsManager: com.example.customgalleryviewer.data.SettingsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun createPlaylist(
        name: String,
        selectedUris: List<Pair<Uri, ItemType>>,
        filterType: MediaFilterType,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            // Try to get a thumbnail from file selections first
            var thumbnailUri = selectedUris
                .lastOrNull { it.second == ItemType.FILE }
                ?.first
                ?.toString()

            val playlistId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = name,
                    mediaFilterType = filterType,
                    startVideoMuted = true,
                    autoRotateVideo = true,
                    thumbnailUri = thumbnailUri
                )
            )

            val items = selectedUris.map { (uri, type) ->
                PlaylistItemEntity(
                    playlistId = playlistId,
                    uriString = uri.toString(),
                    type = type,
                    isRecursive = (type == ItemType.FOLDER)
                )
            }

            playlistDao.insertItems(items)

            // If no thumbnail yet (folder-only selection), scan for first file and use it
            if (thumbnailUri == null) {
                launch(Dispatchers.IO) {
                    try {
                        val fileScanner = FileScanner(context, cacheManager, settingsManager.getShowHidden())
                        val playlistWithItems = playlistDao.getPlaylistWithItems(playlistId)
                        if (playlistWithItems != null) {
                            fileScanner.scanPlaylistItemsFlow(
                                items = playlistWithItems.items,
                                filter = filterType
                            ).collect { batch ->
                                if (batch.isNotEmpty()) {
                                    val firstUri = batch.first().toString()
                                    val playlist = playlistWithItems.playlist.copy(thumbnailUri = firstUri)
                                    playlistDao.updatePlaylist(playlist)
                                    return@collect
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
            }

            onComplete()
        }
    }
}
