// PlayerViewModel.kt
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

    private val _filteredGalleryItems = MutableStateFlow<List<Uri>>(emptyList())
    val filteredGalleryItems: StateFlow<List<Uri>> = _filteredGalleryItems.asStateFlow()

    private var _playbackItems: List<Uri> = emptyList()
    private val originalRawSet = LinkedHashSet<Uri>()

    // History navigation
    private val history = mutableListOf<Int>()
    private var currentHistoryIndex = -1

    private var loadedPlaylistId: Long? = null
    private var currentGallerySort: SortOrder? = null
    private var currentPlaybackSort: SortOrder? = null

    private val _isGalleryMode = MutableStateFlow(true)
    val isGalleryMode: StateFlow<Boolean> = _isGalleryMode.asStateFlow()

    private val _gridColumns = MutableStateFlow(settingsManager.getGridColumns())
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            settingsManager.gallerySortFlow.collectLatest { sort ->
                if (originalRawSet.isNotEmpty() && sort != currentGallerySort) {
                    updateGalleryList(sort)
                }
            }
        }
        viewModelScope.launch {
            settingsManager.playbackSortFlow.collectLatest { sort ->
                if (originalRawSet.isNotEmpty() && sort != currentPlaybackSort) {
                    updatePlaybackList(sort)
                }
            }
        }
        viewModelScope.launch {
            searchQuery.collectLatest { query ->
                filterGalleryItems(query)
            }
        }
    }

    fun loadPlaylist(playlistId: Long) {
        if (loadedPlaylistId == playlistId && originalRawSet.isNotEmpty()) {
            Log.d("PlayerViewModel", "Playlist already loaded")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            loadedPlaylistId = playlistId
            currentGallerySort = null
            currentPlaybackSort = null
            originalRawSet.clear()
            history.clear()
            currentHistoryIndex = -1

            Log.d("PlayerViewModel", "Starting to load playlist $playlistId")

            repository.getMediaFilesFlow(playlistId).collect { batch ->
                Log.d("PlayerViewModel", "Received batch of ${batch.size} files")

                val sizeBefore = originalRawSet.size
                originalRawSet.addAll(batch)
                val sizeAfter = originalRawSet.size

                if (sizeAfter > sizeBefore) {
                    updateGalleryList(settingsManager.getGallerySort())
                    updatePlaybackList(settingsManager.getPlaybackSort())

                    if (_currentMedia.value == null && _playbackItems.isNotEmpty()) {
                        val firstIndex = 0
                        history.add(firstIndex)
                        currentHistoryIndex = 0
                        _currentMedia.value = _playbackItems[firstIndex]
                        Log.d("PlayerViewModel", "Set first media: ${_playbackItems[firstIndex]}")
                    }

                    if (_isLoading.value && originalRawSet.size >= 20) {
                        _isLoading.value = false
                        Log.d("PlayerViewModel", "Stopped loading indicator at ${originalRawSet.size} files")
                    }
                }
            }

            _isLoading.value = false
            Log.d("PlayerViewModel", "Finished loading playlist. Total: ${originalRawSet.size} files")
        }
    }

    private suspend fun updateGalleryList(order: SortOrder) {
        if (originalRawSet.isEmpty()) return
        currentGallerySort = order
        val sorted = sortList(originalRawSet.toList(), order)
        _galleryItems.value = sorted
        filterGalleryItems(_searchQuery.value)
        Log.d("PlayerViewModel", "Gallery updated with ${sorted.size} items")
    }

    private suspend fun updatePlaybackList(order: SortOrder) {
        if (originalRawSet.isEmpty()) return
        currentPlaybackSort = order
        val sorted = sortList(originalRawSet.toList(), order)
        _playbackItems = sorted

        val current = _currentMedia.value
        if (current != null) {
            val newIndex = _playbackItems.indexOf(current)
            if (newIndex != -1 && history.isNotEmpty()) {
                history[currentHistoryIndex] = newIndex
            }
        }
        Log.d("PlayerViewModel", "Playback updated with ${sorted.size} items")
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

    private fun filterGalleryItems(query: String) {
        val filtered = if (query.isEmpty()) {
            _galleryItems.value
        } else {
            _galleryItems.value.filter {
                it.lastPathSegment?.contains(query, ignoreCase = true) == true
            }
        }
        _filteredGalleryItems.value = filtered
        Log.d("PlayerViewModel", "Filtered gallery: ${filtered.size} items (query: '$query')")
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onNext() {
        if (_playbackItems.isEmpty()) return

        val isRandom = settingsManager.getPlaybackSort() == SortOrder.RANDOM

        if (currentHistoryIndex < history.lastIndex) {
            // Moving forward in history
            currentHistoryIndex++
            updateMediaFromHistory()
        } else {
            // Need to add new item to history
            val nextIndex = if (isRandom) {
                generateSmartRandomIndex()
            } else {
                val currentIndex = if (history.isEmpty()) -1 else history[currentHistoryIndex]
                (currentIndex + 1) % _playbackItems.size
            }

            history.add(nextIndex)
            currentHistoryIndex = history.lastIndex
            updateMediaFromHistory()
        }
    }

    fun onPrevious() {
        if (_playbackItems.isEmpty()) return
        if (history.isEmpty()) {
            onNext()
            return
        }

        if (currentHistoryIndex > 0) {
            currentHistoryIndex--
            updateMediaFromHistory()
        } else {
            // At start of history, add previous item
            val isRandom = settingsManager.getPlaybackSort() == SortOrder.RANDOM
            val prevIndex = if (isRandom) {
                generateSmartRandomIndex()
            } else {
                val currentIndex = history[currentHistoryIndex]
                if (currentIndex - 1 < 0) _playbackItems.size - 1 else currentIndex - 1
            }

            history.add(0, prevIndex)
            currentHistoryIndex = 0
            updateMediaFromHistory()
        }
    }

    private fun updateMediaFromHistory() {
        if (currentHistoryIndex >= 0 && currentHistoryIndex < history.size) {
            val idx = history[currentHistoryIndex]
            if (idx >= 0 && idx < _playbackItems.size) {
                _currentMedia.value = _playbackItems[idx]
            }
        }
    }

    private fun generateSmartRandomIndex(): Int {
        val totalSize = _playbackItems.size
        if (totalSize == 0) return 0
        if (totalSize == 1) return 0
        if (totalSize < 5) return (0 until totalSize).random()

        val bufferSize = kotlin.math.min(50, totalSize / 2)
        val recentIndices = history.takeLast(bufferSize).toSet()
        var candidate: Int
        var attempts = 0

        do {
            candidate = (0 until totalSize).random()
            attempts++
        } while (candidate in recentIndices && attempts < 20)

        return candidate
    }

    fun jumpToItem(uri: Uri) {
        val indexInPlayback = _playbackItems.indexOf(uri)
        if (indexInPlayback != -1) {
            if (currentHistoryIndex < history.lastIndex) {
                history.add(indexInPlayback)
                currentHistoryIndex = history.lastIndex
            } else {
                history.add(indexInPlayback)
                currentHistoryIndex = history.lastIndex
            }
            _currentMedia.value = uri
            _isGalleryMode.value = false
        }
    }

    fun toggleGalleryMode() {
        _isGalleryMode.value = !_isGalleryMode.value
    }

    fun setGalleryMode(mode: Boolean) {
        _isGalleryMode.value = mode
    }

    fun setGridColumns(columns: Int) {
        _gridColumns.value = columns
        settingsManager.setGridColumns(columns)
    }
}