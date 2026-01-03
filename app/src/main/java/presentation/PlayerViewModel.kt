package com.example.customgalleryviewer.presentation

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.SettingsManager
import com.example.customgalleryviewer.data.SortOrder
import com.example.customgalleryviewer.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _currentMedia = MutableStateFlow<Uri?>(null)
    val currentMedia: StateFlow<Uri?> = _currentMedia.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<Uri>>(emptyList())
    val galleryItems: StateFlow<List<Uri>> = _galleryItems.asStateFlow()

    private var _playbackItems: List<Uri> = emptyList()
    private var originalRawList: List<Uri> = emptyList()
    private var currentIndex = 0

    private var loadedPlaylistId: Long? = null
    private var currentGallerySort: SortOrder? = null
    private var currentPlaybackSort: SortOrder? = null

    private val _isGalleryMode = MutableStateFlow(false)
    val isGalleryMode: StateFlow<Boolean> = _isGalleryMode.asStateFlow()

    private val _gridColumns = MutableStateFlow(settingsManager.getGridColumns())
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    init {
        viewModelScope.launch {
            settingsManager.gallerySortFlow.collectLatest { sort ->
                if (originalRawList.isNotEmpty() && sort != currentGallerySort) {
                    updateGalleryList(sort)
                }
            }
        }
        viewModelScope.launch {
            settingsManager.playbackSortFlow.collectLatest { sort ->
                if (originalRawList.isNotEmpty() && sort != currentPlaybackSort) {
                    updatePlaybackList(sort)
                }
            }
        }
    }

    fun loadPlaylist(playlistId: Long) {
        if (loadedPlaylistId == playlistId && originalRawList.isNotEmpty()) {
            Log.d("PlayerViewModel", "loadPlaylist: Already loaded playlist $playlistId with ${originalRawList.size} items")
            return
        }

        Log.d("PlayerViewModel", "loadPlaylist: Loading playlist $playlistId")
        viewModelScope.launch {
            loadedPlaylistId = playlistId
            currentGallerySort = null
            currentPlaybackSort = null

            val tempRaw = mutableListOf<Uri>()
            repository.getMediaFilesFlow(playlistId).collect { batch ->
                Log.d("PlayerViewModel", "loadPlaylist: Received batch of ${batch.size} items. Total so far: ${tempRaw.size + batch.size}")
                tempRaw.addAll(batch)
                originalRawList = ArrayList(tempRaw)

                updateGalleryList(settingsManager.getGallerySort())
                updatePlaybackList(settingsManager.getPlaybackSort())

                if (_currentMedia.value == null && _playbackItems.isNotEmpty()) {
                    currentIndex = 0
                    _currentMedia.value = _playbackItems[0]
                    Log.d("PlayerViewModel", "loadPlaylist: Set current media to ${_playbackItems[0]}")
                }
            }
            Log.d("PlayerViewModel", "loadPlaylist: Finished loading. Total items: ${originalRawList.size}")
        }
    }

    private suspend fun updateGalleryList(order: SortOrder) {
        if (originalRawList.isEmpty()) {
            Log.d("PlayerViewModel", "updateGalleryList: originalRawList is empty")
            return
        }
        currentGallerySort = order
        val sorted = sortList(originalRawList, order)
        _galleryItems.value = sorted
        Log.d("PlayerViewModel", "updateGalleryList: Updated gallery with ${sorted.size} items (sort=$order)")
    }

    private suspend fun updatePlaybackList(order: SortOrder) {
        if (originalRawList.isEmpty()) {
            Log.d("PlayerViewModel", "updatePlaybackList: originalRawList is empty")
            return
        }
        currentPlaybackSort = order
        val sorted = sortList(originalRawList, order)
        _playbackItems = sorted
        Log.d("PlayerViewModel", "updatePlaybackList: Updated playback with ${sorted.size} items (sort=$order)")

        val current = _currentMedia.value
        if (current != null) {
            val newIndex = _playbackItems.indexOf(current)
            if (newIndex != -1) currentIndex = newIndex
        }
    }

    private suspend fun sortList(list: List<Uri>, order: SortOrder): List<Uri> = withContext(Dispatchers.Default) {
        when (order) {
            SortOrder.RANDOM -> list.shuffled()
            SortOrder.BY_NAME -> list.sortedBy { it.lastPathSegment?.lowercase() ?: "" }
            SortOrder.BY_DATE -> list.sortedByDescending {
                try { File(it.path ?: "").lastModified() } catch (e: Exception) { 0L }
            }
        }
    }

    fun onNext() {
        if (_playbackItems.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % _playbackItems.size
            _currentMedia.value = _playbackItems[currentIndex]
        }
    }

    fun onPrevious() {
        if (_playbackItems.isNotEmpty()) {
            currentIndex = if (currentIndex - 1 < 0) _playbackItems.size - 1 else currentIndex - 1
            _currentMedia.value = _playbackItems[currentIndex]
        }
    }

    fun jumpToItem(uri: Uri) {
        val indexInPlayback = _playbackItems.indexOf(uri)
        if (indexInPlayback != -1) {
            currentIndex = indexInPlayback
            _currentMedia.value = uri
        }
    }

    fun toggleGalleryMode() {
        _isGalleryMode.value = !_isGalleryMode.value
        Log.d("PlayerViewModel", "toggleGalleryMode: isGalleryMode=${_isGalleryMode.value}")
    }

    fun setGalleryMode(value: Boolean) {
        _isGalleryMode.value = value
        Log.d("PlayerViewModel", "setGalleryMode: isGalleryMode=$value, galleryItems=${_galleryItems.value.size}, playbackItems=${_playbackItems.size}")
    }

    fun setGridColumns(columns: Int) {
        _gridColumns.value = columns
        settingsManager.setGridColumns(columns)
    }
}