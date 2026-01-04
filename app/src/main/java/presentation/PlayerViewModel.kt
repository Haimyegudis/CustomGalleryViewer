// PlayerViewModel.kt - SUPER FAST VERSION
// תיקונים: 1) גלריה מהירה עם progressive updates, 2) ללא re-sorting מיותר

package com.example.customgalleryviewer.presentation

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.MediaCacheManager
import com.example.customgalleryviewer.data.SettingsManager
import com.example.customgalleryviewer.data.SortOrder
import com.example.customgalleryviewer.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val settingsManager: SettingsManager,
    private val mediaCacheManager: MediaCacheManager
) : ViewModel() {

    private val _currentMedia = MutableStateFlow<Uri?>(null)
    val currentMedia: StateFlow<Uri?> = _currentMedia.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<Uri>>(emptyList())
    val galleryItems: StateFlow<List<Uri>> = _galleryItems.asStateFlow()

    private val _filteredGalleryItems = MutableStateFlow<List<Uri>>(emptyList())
    val filteredGalleryItems: StateFlow<List<Uri>> = _filteredGalleryItems.asStateFlow()

    private var _playbackItems: List<Uri> = emptyList()
    private val originalRawSet = LinkedHashSet<Uri>()

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

    fun loadPlaylist(playlistId: Long, forceRefresh: Boolean = false) {
        if (loadedPlaylistId == playlistId && originalRawSet.isNotEmpty() && !forceRefresh) {
            Log.d("PlayerViewModel", "Playlist already loaded")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            loadedPlaylistId = playlistId

            if (forceRefresh) {
                mediaCacheManager.removeAllCache()
                Log.d("PlayerViewModel", "Cleared all cache for refresh")
            }

            currentGallerySort = null
            currentPlaybackSort = null
            originalRawSet.clear()
            history.clear()
            currentHistoryIndex = -1

            Log.d("PlayerViewModel", "Starting to load playlist $playlistId")

            // Get folder URI from somewhere - you need to pass this!
            // For now, assuming you have a way to get it
            // If you have a database, get it from there
            // Otherwise, pass folderUri directly to this function

            repository.getMediaFilesFlow(playlistId).collect { batch ->
                Log.d("PlayerViewModel", "Received batch of ${batch.size} files")

                val sizeBefore = originalRawSet.size
                originalRawSet.addAll(batch)
                val sizeAfter = originalRawSet.size

                if (sizeAfter > sizeBefore) {
                    // עדכון PROGRESSIVE - מעדכן את הגלריה מיד!
                    updateGalleryListProgressive(settingsManager.getGallerySort())
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

    fun refreshPlaylist() {
        loadedPlaylistId?.let { playlistId ->
            Log.d("PlayerViewModel", "Refreshing playlist $playlistId")
            loadPlaylist(playlistId, forceRefresh = true)
        }
    }

    // PROGRESSIVE UPDATE - מעדכן מיד ללא המתנה!
    private fun updateGalleryListProgressive(order: SortOrder) {
        if (originalRawSet.isEmpty()) return
        currentGallerySort = order

        // עדכון מיידי עם הקבצים הקיימים (ללא מיון!)
        val currentList = originalRawSet.toList()
        _galleryItems.value = currentList
        filterGalleryItems(_searchQuery.value)

        // מיון ברקע - לא חוסם!
        viewModelScope.launch(Dispatchers.Default) {
            val sorted = sortList(currentList, order)
            withContext(Dispatchers.Main) {
                _galleryItems.value = sorted
                filterGalleryItems(_searchQuery.value)
                Log.d("PlayerViewModel", "Gallery sorted: ${sorted.size} items, order=$order")
            }
        }
    }

    private suspend fun updateGalleryList(order: SortOrder) {
        if (originalRawSet.isEmpty()) return
        currentGallerySort = order

        val sorted = withContext(Dispatchers.Default) {
            sortList(originalRawSet.toList(), order)
        }

        _galleryItems.value = sorted
        filterGalleryItems(_searchQuery.value)
        Log.d("PlayerViewModel", "Gallery updated with ${sorted.size} items, order=$order")
    }

    private suspend fun updatePlaybackList(order: SortOrder) {
        if (originalRawSet.isEmpty()) return
        currentPlaybackSort = order

        val sorted = withContext(Dispatchers.Default) {
            sortList(originalRawSet.toList(), order)
        }

        _playbackItems = sorted

        val current = _currentMedia.value
        if (current != null) {
            val newIndex = _playbackItems.indexOf(current)
            if (newIndex != -1 && history.isNotEmpty() && currentHistoryIndex < history.size) {
                history[currentHistoryIndex] = newIndex
            }
        }
        Log.d("PlayerViewModel", "Playback updated with ${sorted.size} items, order=$order")
    }

    private fun sortList(list: List<Uri>, order: SortOrder): List<Uri> {
        return when (order) {
            SortOrder.RANDOM -> list.shuffled()

            SortOrder.BY_NAME -> list.sortedBy { uri ->
                try {
                    val name = uri.lastPathSegment ?: ""
                    java.net.URLDecoder.decode(name, "UTF-8")
                        .substringAfterLast('/')
                        .substringAfterLast(':')
                        .lowercase()
                } catch (e: Exception) {
                    uri.toString().lowercase()
                }
            }

            SortOrder.BY_DATE -> list.sortedWith(compareByDescending { uri ->
                getFileModifiedTime(uri)
            })
        }
    }

    private fun getFileModifiedTime(uri: Uri): Long {
        return try {
            if (uri.scheme == "content") {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.MediaStore.MediaColumns.DATE_MODIFIED),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dateIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                        if (dateIndex >= 0) {
                            val timestamp = cursor.getLong(dateIndex)
                            if (timestamp > 0) {
                                return timestamp * 1000
                            }
                        }
                    }
                }
            }

            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    return file.lastModified()
                }
            }

            if (uri.scheme == "content") {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dateIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        if (dateIndex >= 0) {
                            return cursor.getLong(dateIndex)
                        }
                    }
                }
            }

            0L
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error getting modified time for $uri", e)
            0L
        }
    }

    private fun filterGalleryItems(query: String) {
        val filtered = if (query.isEmpty()) {
            _galleryItems.value
        } else {
            _galleryItems.value.filter {
                val name = it.lastPathSegment ?: ""
                val decoded = try {
                    java.net.URLDecoder.decode(name, "UTF-8")
                } catch (e: Exception) {
                    name
                }
                decoded.contains(query, ignoreCase = true)
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
            currentHistoryIndex++
            updateMediaFromHistory()
        } else {
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
            if (settingsManager.getPlaybackSort() == SortOrder.RANDOM) {
                Log.d("PlayerViewModel", "Jumping to item in RANDOM mode - resetting history")
                history.clear()
                history.add(indexInPlayback)
                currentHistoryIndex = 0
            } else {
                if (currentHistoryIndex < history.lastIndex) {
                    history.add(indexInPlayback)
                    currentHistoryIndex = history.lastIndex
                } else {
                    history.add(indexInPlayback)
                    currentHistoryIndex = history.lastIndex
                }
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