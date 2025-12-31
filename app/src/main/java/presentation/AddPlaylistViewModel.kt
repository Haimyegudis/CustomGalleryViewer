package com.example.customgalleryviewer.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.data.PlaylistDao
import com.example.customgalleryviewer.data.PlaylistEntity
import com.example.customgalleryviewer.data.PlaylistItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddPlaylistViewModel @Inject constructor(
    private val playlistDao: PlaylistDao
) : ViewModel() {

    fun createPlaylist(
        name: String,
        selectedUris: List<Pair<Uri, ItemType>>, // Pair of URI and Type (File/Folder)
        filterType: MediaFilterType,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            // 1. צור את הפלייליסט
            val playlistId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = name,
                    mediaFilterType = filterType,
                    startVideoMuted = true,
                    autoRotateVideo = true
                )
            )

            // 2. צור את הפריטים
            val items = selectedUris.map { (uri, type) ->
                PlaylistItemEntity(
                    playlistId = playlistId,
                    uriString = uri.toString(),
                    type = type,
                    isRecursive = (type == ItemType.FOLDER) // ברירת מחדל: רקורסיבי לתיקיות
                )
            }

            playlistDao.insertItems(items)
            onComplete()
        }
    }
}