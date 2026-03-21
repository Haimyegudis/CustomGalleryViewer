// PlayerViewModel.kt
package com.example.customgalleryviewer.presentation

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.GalleryItem
import com.example.customgalleryviewer.data.MediaCacheManager
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.data.SettingsManager
import com.example.customgalleryviewer.data.SortOrder
import com.example.customgalleryviewer.repository.MediaRepository
import com.example.customgalleryviewer.util.FileScanner
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

import com.example.customgalleryviewer.data.FavoriteDao
import com.example.customgalleryviewer.data.FavoriteEntity
import com.example.customgalleryviewer.data.FolderFileCache
import com.example.customgalleryviewer.data.WatchPositionDao
import com.example.customgalleryviewer.data.WatchPositionEntity
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsManager: SettingsManager,
    private val cacheManager: MediaCacheManager,
    private val folderFileCache: FolderFileCache,
    private val watchPositionDao: WatchPositionDao,
    private val favoriteDao: FavoriteDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private fun createScanner() = FileScanner(appContext, cacheManager, settingsManager.getShowHidden())

    private val _currentMedia = MutableStateFlow<Uri?>(null)
    val currentMedia: StateFlow<Uri?> = _currentMedia.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<Uri>>(emptyList())
    val galleryItems: StateFlow<List<Uri>> = _galleryItems.asStateFlow()

    private val _filteredGalleryItems = MutableStateFlow<List<Uri>>(emptyList())
    val filteredGalleryItems: StateFlow<List<Uri>> = _filteredGalleryItems.asStateFlow()

    // Folder browsing
    private val _browseItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val browseItems: StateFlow<List<GalleryItem>> = _browseItems.asStateFlow()

    private val _folderStack = MutableStateFlow<List<Pair<Uri, String>>>(emptyList())
    val folderStack: StateFlow<List<Pair<Uri, String>>> = _folderStack.asStateFlow()

    private val _isBrowseMode = MutableStateFlow(false)
    val isBrowseMode: StateFlow<Boolean> = _isBrowseMode.asStateFlow()

    private var _playbackItems: List<Uri> = emptyList()
    private val originalRawSet = java.util.Collections.synchronizedSet(LinkedHashSet<Uri>())

    // History navigation
    private val history = java.util.Collections.synchronizedList(mutableListOf<Int>())
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

    private val _mediaFilter = MutableStateFlow(MediaFilterType.MIXED)
    val mediaFilter: StateFlow<MediaFilterType> = _mediaFilter.asStateFlow()

    private val _isShuffleOn = MutableStateFlow(settingsManager.getShuffleOn())
    val isShuffleOn: StateFlow<Boolean> = _isShuffleOn.asStateFlow()

    private val _isRepeatListOn = MutableStateFlow(settingsManager.getRepeatListOn())
    val isRepeatListOn: StateFlow<Boolean> = _isRepeatListOn.asStateFlow()

    // Watch positions for progress display
    private val _watchPositions = MutableStateFlow<Map<String, com.example.customgalleryviewer.data.WatchPositionEntity>>(emptyMap())
    val watchPositions: StateFlow<Map<String, com.example.customgalleryviewer.data.WatchPositionEntity>> = _watchPositions.asStateFlow()

    init {
        viewModelScope.launch {
            watchPositionDao.getAllPositionsFlow().collectLatest { positions ->
                _watchPositions.value = positions.associateBy { it.uri }
            }
        }
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
                filterGalleryItems(query, _mediaFilter.value)
            }
        }
        viewModelScope.launch {
            _mediaFilter.collectLatest { filter ->
                filterGalleryItems(_searchQuery.value, filter)
            }
        }
    }

    // Store playlist folder items for browse mode
    private var playlistFolderItems = listOf<com.example.customgalleryviewer.data.PlaylistItemEntity>()

    private val _playlistFolders = MutableStateFlow<List<Pair<Uri, String>>>(emptyList())
    val playlistFolders: StateFlow<List<Pair<Uri, String>>> = _playlistFolders.asStateFlow()

    fun forceReload(playlistId: Long) {
        loadedPlaylistId = null
        originalRawSet.clear()
        loadPlaylist(playlistId)
    }

    fun loadPlaylist(playlistId: Long) {
        if (loadedPlaylistId == playlistId && originalRawSet.isNotEmpty()) {
            Log.d("PlayerViewModel", "Playlist already loaded")
            return
        }

        loadedPlaylistId = playlistId
        currentGallerySort = null
        currentPlaybackSort = null
        originalRawSet.clear()
        history.clear()
        currentHistoryIndex = -1

        // Show cached files instantly
        val cached = folderFileCache.getPlaylistFiles(playlistId)
        if (cached.isNotEmpty()) {
            originalRawSet.addAll(cached)
            viewModelScope.launch {
                updateGalleryList(settingsManager.getGallerySort())
                updatePlaybackList(settingsManager.getPlaybackSort())
                if (_currentMedia.value == null && _playbackItems.isNotEmpty()) {
                    history.add(0)
                    currentHistoryIndex = 0
                    _currentMedia.value = _playbackItems[0]
                }
                _isLoading.value = false
            }
        }

        // Background scan for fresh data
        viewModelScope.launch {
            if (cached.isEmpty()) _isLoading.value = true

            Log.d("PlayerViewModel", "Starting to load playlist $playlistId")

            // Load playlist items for folder browsing
            val playlistData = withContext(Dispatchers.IO) {
                repository.getPlaylistWithItems(playlistId)
            }
            if (playlistData != null) {
                playlistFolderItems = playlistData.items.filter {
                    it.type == com.example.customgalleryviewer.data.ItemType.FOLDER
                }
                _playlistFolders.value = playlistFolderItems.map { item ->
                    val uri = Uri.parse(item.uriString)
                    val name = uri.lastPathSegment?.substringAfterLast(':') ?: uri.lastPathSegment ?: "Folder"
                    uri to name
                }
            }

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

            // Save to cache after scan completes
            folderFileCache.savePlaylistFiles(playlistId, originalRawSet.toList())

            _isLoading.value = false
            Log.d("PlayerViewModel", "Finished loading playlist. Total: ${originalRawSet.size} files")
        }
    }

    fun getPlaylistFolders(): List<Pair<Uri, String>> {
        return playlistFolderItems.map { item ->
            val uri = Uri.parse(item.uriString)
            val name = uri.lastPathSegment?.substringAfterLast(':') ?: uri.lastPathSegment ?: "Folder"
            uri to name
        }
    }

    private suspend fun updateGalleryList(order: SortOrder) {
        if (originalRawSet.isEmpty()) return
        currentGallerySort = order
        val sorted = sortList(originalRawSet.toList(), order)
        _galleryItems.value = sorted
        filterGalleryItems(_searchQuery.value, _mediaFilter.value)
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
            SortOrder.RANDOM -> list
            SortOrder.BY_NAME -> list.sortedBy { it.lastPathSegment?.lowercase() ?: "" }
            SortOrder.BY_DATE -> list.sortedByDescending {
                try { File(it.path ?: "").lastModified() } catch (e: Exception) { 0L }
            }
            SortOrder.BY_SIZE -> list.sortedByDescending { getFileSize(it) }
            SortOrder.BY_DURATION -> list.sortedByDescending { getVideoDuration(it) }
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            if (uri.scheme == "file") {
                File(uri.path ?: "").length()
            } else {
                appContext.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use {
                    if (it.moveToFirst()) it.getLong(0) else 0L
                } ?: 0L
            }
        } catch (e: Exception) { 0L }
    }

    private fun getVideoDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(appContext, uri)
            }
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L }
        finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun filterGalleryItems(query: String, filter: MediaFilterType) {
        var items = _galleryItems.value

        // Apply media type filter
        if (filter != MediaFilterType.MIXED) {
            items = items.filter { uri ->
                val isVideoFile = isVideo(uri.toString())
                when (filter) {
                    MediaFilterType.VIDEO_ONLY -> isVideoFile
                    MediaFilterType.PHOTOS_ONLY -> !isVideoFile
                    else -> true
                }
            }
        }

        // Apply search query
        if (query.isNotEmpty()) {
            items = items.filter {
                it.lastPathSegment?.contains(query, ignoreCase = true) == true
            }
        }

        _filteredGalleryItems.value = items
        Log.d("PlayerViewModel", "Filtered gallery: ${items.size} items (query: '$query', filter: $filter)")
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setMediaFilter(filter: MediaFilterType) {
        _mediaFilter.value = filter
    }

    fun onNext() {
        if (_playbackItems.isEmpty()) return

        val isRandom = _isShuffleOn.value || settingsManager.getPlaybackSort() == SortOrder.RANDOM

        if (currentHistoryIndex < history.lastIndex) {
            // Moving forward in history - don't trim forward history
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
            // Cap history at 500
            if (history.size > 500) {
                history.removeAt(0)
            } else {
                currentHistoryIndex = history.lastIndex
            }
            if (currentHistoryIndex >= history.size) currentHistoryIndex = history.lastIndex
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
            // Cap history at 500 - remove from end
            if (history.size > 500) {
                history.removeAt(history.lastIndex)
            }
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
            history.clear()
            history.add(indexInPlayback)
            currentHistoryIndex = 0
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

    fun toggleShuffle() {
        _isShuffleOn.value = !_isShuffleOn.value
        settingsManager.setShuffleOn(_isShuffleOn.value)
        if (_isShuffleOn.value) {
            viewModelScope.launch {
                updatePlaybackList(SortOrder.RANDOM)
            }
        } else {
            viewModelScope.launch {
                updatePlaybackList(settingsManager.getPlaybackSort())
            }
        }
    }

    fun toggleRepeatList() {
        _isRepeatListOn.value = !_isRepeatListOn.value
        settingsManager.setRepeatListOn(_isRepeatListOn.value)
    }

    fun removeFromList(uri: Uri) {
        originalRawSet.remove(uri)
        viewModelScope.launch {
            updateGalleryList(settingsManager.getGallerySort())
            updatePlaybackList(settingsManager.getPlaybackSort())
        }
    }

    // --- Watch position ---

    fun saveWatchPosition(uri: Uri, position: Long, duration: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            watchPositionDao.savePosition(
                WatchPositionEntity(uri = uri.toString(), position = position, duration = duration)
            )
        }
    }

    suspend fun getWatchPosition(uri: Uri): Long {
        return withContext(Dispatchers.IO) {
            watchPositionDao.getPosition(uri.toString())?.position ?: 0L
        }
    }

    // --- Favorites ---

    private val _favoriteUris = MutableStateFlow<Set<String>>(emptySet())
    val favoriteUris: StateFlow<Set<String>> = _favoriteUris.asStateFlow()

    init {
        viewModelScope.launch {
            favoriteDao.getAllFavoriteUrisFlow().collectLatest { uris ->
                _favoriteUris.value = uris.toSet()
            }
        }
    }

    fun toggleFavorite(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val uriStr = uri.toString()
            if (favoriteDao.isFavorite(uriStr)) {
                favoriteDao.removeFavorite(uriStr)
            } else {
                favoriteDao.addFavorite(FavoriteEntity(uri = uriStr))
            }
        }
    }

    fun isFavorite(uri: Uri): Boolean {
        return _favoriteUris.value.contains(uri.toString())
    }

    // --- Folder browsing ---

    fun enterBrowseMode(folderUri: Uri, folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBrowseMode.value = true
            _folderStack.value = listOf(folderUri to folderName)
            val items = createScanner().browseFolderLevel(folderUri, _mediaFilter.value)
            _browseItems.value = items
        }
    }

    fun openSubfolder(folderUri: Uri, folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val stack = _folderStack.value.toMutableList()
            stack.add(folderUri to folderName)
            _folderStack.value = stack

            // Try physical path first
            val items = createScanner().browseFolderLevel(folderUri, _mediaFilter.value)
            _browseItems.value = items
        }
    }

    fun navigateBackInBrowse(): Boolean {
        val stack = _folderStack.value
        if (stack.size <= 1) {
            _isBrowseMode.value = false
            _browseItems.value = emptyList()
            _folderStack.value = emptyList()
            return false // back to flat gallery
        }

        val newStack = stack.dropLast(1)
        _folderStack.value = newStack
        val parentUri = newStack.last().first
        viewModelScope.launch(Dispatchers.IO) {
            val items = createScanner().browseFolderLevel(parentUri, _mediaFilter.value)
            _browseItems.value = items
        }
        return true // stayed in browse mode
    }

    fun exitBrowseMode() {
        _isBrowseMode.value = false
        _browseItems.value = emptyList()
        _folderStack.value = emptyList()
    }

    fun getFolderCover(folderUri: Uri): Uri? = cacheManager.getFolderCover(folderUri)

    fun setFolderCover(folderUri: Uri, coverUri: Uri) {
        cacheManager.setFolderCover(folderUri, coverUri)
        // Refresh browse items to show new cover
        val stack = _folderStack.value
        if (stack.isNotEmpty()) {
            val currentFolder = stack.last().first
            viewModelScope.launch(Dispatchers.IO) {
                val items = createScanner().browseFolderLevel(currentFolder, _mediaFilter.value)
                _browseItems.value = items
            }
        }
    }

    fun getMediaFilesInFolder(folderUri: Uri): List<Uri> {
        val items = createScanner().browseFolderLevel(folderUri, MediaFilterType.MIXED)
        return items.filterIsInstance<GalleryItem.MediaFile>().map { it.uri }
    }
}