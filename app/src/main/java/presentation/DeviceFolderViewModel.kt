package com.example.customgalleryviewer.presentation

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.FavoriteDao
import com.example.customgalleryviewer.data.FavoriteEntity
import com.example.customgalleryviewer.data.FolderFileCache
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaCacheManager
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.data.PlaylistItemEntity
import com.example.customgalleryviewer.data.SettingsManager
import com.example.customgalleryviewer.data.SortOrder
import com.example.customgalleryviewer.data.WatchPositionDao
import com.example.customgalleryviewer.data.WatchPositionEntity
import com.example.customgalleryviewer.util.DeviceMediaScanner
import com.example.customgalleryviewer.util.FileScanner
import java.io.File
import coil.ImageLoader
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DeviceFolderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: DeviceMediaScanner,
    private val folderFileCache: FolderFileCache,
    private val settingsManager: SettingsManager,
    private val cacheManager: MediaCacheManager,
    private val watchPositionDao: WatchPositionDao,
    private val favoriteDao: FavoriteDao
) : ViewModel() {

    private val _files = MutableStateFlow<List<Uri>>(emptyList())
    val files: StateFlow<List<Uri>> = _files.asStateFlow()

    private val _currentMedia = MutableStateFlow<Uri?>(null)
    val currentMedia: StateFlow<Uri?> = _currentMedia.asStateFlow()

    private val _isGalleryMode = MutableStateFlow(true)
    val isGalleryMode: StateFlow<Boolean> = _isGalleryMode.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Watch positions for progress display
    private val _watchPositions = MutableStateFlow<Map<String, WatchPositionEntity>>(emptyMap())
    val watchPositions: StateFlow<Map<String, WatchPositionEntity>> = _watchPositions.asStateFlow()

    init {
        viewModelScope.launch {
            watchPositionDao.getAllPositionsFlow().collectLatest { positions ->
                _watchPositions.value = positions.associateBy { it.uri }
            }
        }
    }

    // History-based navigation
    private val history = java.util.Collections.synchronizedList(mutableListOf<Int>())
    private var currentHistoryIndex = -1
    private var playbackList: List<Uri> = emptyList()
    private var loadedBucketId: Long? = null
    private var fileObserver: android.os.FileObserver? = null

    private fun createScanner() = FileScanner(context, cacheManager, settingsManager.getShowHidden())

    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
    }

    fun loadFolder(bucketId: Long, force: Boolean = false) {
        if (!force && loadedBucketId == bucketId && _files.value.isNotEmpty()) return
        loadedBucketId = bucketId

        // Start watching folder for changes (new/deleted files)
        viewModelScope.launch(Dispatchers.IO) {
            val folderPath = scanner.getFolderPath(bucketId) ?: return@launch
            fileObserver?.stopWatching()
            fileObserver = object : android.os.FileObserver(
                File(folderPath),
                CREATE or DELETE or MOVED_FROM or MOVED_TO or CLOSE_WRITE
            ) {
                private var pendingRefresh = false
                override fun onEvent(event: Int, path: String?) {
                    if (pendingRefresh) return
                    pendingRefresh = true
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(500) // debounce
                        pendingRefresh = false
                        quickRefreshFolder(bucketId)
                    }
                }
            }
            fileObserver?.startWatching()
        }
        val startTime = System.currentTimeMillis()

        // Show cached files synchronously - only use file:// URIs
        if (!force) {
            val cached = folderFileCache.getFolderFiles(bucketId)
            Log.w("DeviceFolderVM", "cache lookup took ${System.currentTimeMillis() - startTime}ms, cached=${cached.size}")
            if (cached.isNotEmpty()) {
                _files.value = cached
                playbackList = cached
                _isLoading.value = false
                preWarmCache(cached.take(50))
                // Background refresh - silent, don't update _files unless count changed
                viewModelScope.launch(Dispatchers.IO) {
                    val folderPath = scanner.getFolderPath(bucketId) ?: return@launch
                    val folderUri = android.net.Uri.fromFile(File(folderPath))
                    val allFiles = mutableListOf<Uri>()
                    createScanner().scanPlaylistItemsFlow(
                        items = listOf(PlaylistItemEntity(playlistId = 0, uriString = folderUri.toString(), type = ItemType.FOLDER, isRecursive = false)),
                        filter = MediaFilterType.MIXED
                    ).collect { batch -> allFiles.addAll(batch) }
                    if (allFiles.size != cached.size) {
                        _files.value = allFiles
                        playbackList = allFiles
                        folderFileCache.saveFolderFiles(bucketId, allFiles)
                    }
                }
                return
            }
        }

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val folderPath = scanner.getFolderPath(bucketId)
            Log.w("DeviceFolderVM", "loadFolder bucketId=$bucketId folderPath=$folderPath")
            val scanPath = folderPath ?: run {
                // Fallback: try to get path from MediaStore files
                val contentFiles = scanner.getFilesInFolder(bucketId)
                if (contentFiles.isNotEmpty()) {
                    // Convert content URIs by resolving paths
                    val resolved = contentFiles.mapNotNull { uri ->
                        try {
                            if (uri.scheme == "file") return@mapNotNull uri
                            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                                if (c.moveToFirst()) {
                                    val path = c.getString(0)
                                    if (!path.isNullOrBlank()) android.net.Uri.fromFile(File(path)) else null
                                } else null
                            }
                        } catch (_: Exception) { null }
                    }
                    if (resolved.isNotEmpty()) {
                        _files.value = resolved
                        playbackList = resolved
                        _isLoading.value = false
                        folderFileCache.saveFolderFiles(bucketId, resolved)
                        return@launch
                    }
                }
                null
            }

            if (scanPath != null) {
                val folderUri = android.net.Uri.fromFile(File(scanPath))
                val fileScanner = createScanner()
                val allFiles = mutableListOf<Uri>()
                fileScanner.scanPlaylistItemsFlow(
                    items = listOf(PlaylistItemEntity(playlistId = 0, uriString = folderUri.toString(), type = ItemType.FOLDER, isRecursive = false)),
                    filter = MediaFilterType.MIXED
                ).collect { batch ->
                    allFiles.addAll(batch)
                    _files.value = allFiles.toList()
                    _isLoading.value = false
                }
                playbackList = allFiles
                Log.w("DeviceFolderVM", "scan complete: ${allFiles.size} files, first=${allFiles.firstOrNull()}")
                folderFileCache.saveFolderFiles(bucketId, allFiles)
                // Pre-warm image cache for first 50 files
                preWarmCache(allFiles.take(50))
            }
            _isLoading.value = false
        }
    }

    fun jumpToItem(uri: Uri) {
        val idx = playbackList.indexOf(uri)
        if (idx != -1) {
            history.clear()
            history.add(idx)
            currentHistoryIndex = 0
            _currentMedia.value = uri
            _isGalleryMode.value = false
        }
    }

    fun onNext() {
        val list = playbackList
        if (list.isEmpty()) return

        val isRandom = settingsManager.getPlaybackSort() == SortOrder.RANDOM

        if (currentHistoryIndex < history.lastIndex) {
            currentHistoryIndex++
            updateMediaFromHistory()
        } else {
            val nextIndex = if (isRandom) {
                generateRandomIndex()
            } else {
                val currentIdx = if (history.isEmpty()) -1 else history[currentHistoryIndex]
                (currentIdx + 1) % list.size
            }
            history.add(nextIndex)
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
        val list = playbackList
        if (list.isEmpty()) return
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
                generateRandomIndex()
            } else {
                val currentIdx = history[currentHistoryIndex]
                if (currentIdx - 1 < 0) list.size - 1 else currentIdx - 1
            }
            history.add(0, prevIndex)
            if (history.size > 500) {
                history.removeAt(history.lastIndex)
            }
            currentHistoryIndex = 0
            updateMediaFromHistory()
        }
    }

    private fun updateMediaFromHistory() {
        if (currentHistoryIndex in history.indices) {
            val idx = history[currentHistoryIndex]
            if (idx in playbackList.indices) {
                _currentMedia.value = playbackList[idx]
            }
        }
    }

    private fun generateRandomIndex(): Int {
        val totalSize = playbackList.size
        if (totalSize <= 1) return 0
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

    private fun preWarmCache(uris: List<Uri>) {
        val imageLoader = ImageLoader.Builder(context).build()
        uris.forEach { uri ->
            val request = ImageRequest.Builder(context)
                .data(uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .size(200)
                .memoryCacheKey(uri.toString())
                .diskCacheKey(uri.toString())
                .allowHardware(true)
                .build()
            imageLoader.enqueue(request)
        }
    }

    private val mediaExtensions = setOf("jpg","jpeg","png","webp","bmp","gif","heic","heif","avif","mp4","mkv","avi","mov","flv","wmv","3gp","webm","ts","m4v","mpg","mpeg","m3u8","m3u","m2ts","mts")

    private fun quickRefreshFolder(bucketId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val folderPath = scanner.getFolderPath(bucketId) ?: return@launch
            val dir = File(folderPath)
            if (!dir.exists()) return@launch
            val currentFiles = dir.listFiles()
                ?.filter { it.isFile && !it.isHidden && it.extension.lowercase() in mediaExtensions }
                ?.map { android.net.Uri.fromFile(it) }
                ?: emptyList()
            val oldSet = _files.value.toSet()
            val newSet = currentFiles.toSet()
            if (oldSet != newSet) {
                _files.value = currentFiles
                playbackList = currentFiles
                folderFileCache.saveFolderFiles(bucketId, currentFiles)
            }
        }
    }

    fun removeFileFromList(uri: Uri) {
        _files.value = _files.value.filter { it != uri }
        playbackList = playbackList.filter { it != uri }
    }

    fun setGalleryMode(mode: Boolean) {
        _isGalleryMode.value = mode
    }

    fun setDisplayList(list: List<Uri>) {
        viewModelScope.launch(Dispatchers.Default) {
            playbackList = list
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
}
