package com.example.customgalleryviewer.presentation

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.FolderFileCache
import com.example.customgalleryviewer.data.PlaylistWithItems
import com.example.customgalleryviewer.data.SearchFilters
import com.example.customgalleryviewer.data.SettingsManager
import com.example.customgalleryviewer.data.WatchPositionDao
import com.example.customgalleryviewer.repository.MediaRepository
import com.example.customgalleryviewer.util.DeviceMediaScanner
import com.example.customgalleryviewer.util.MediaFolder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val scanner: DeviceMediaScanner,
    private val folderFileCache: FolderFileCache,
    private val cacheManager: com.example.customgalleryviewer.data.MediaCacheManager,
    private val watchPositionDao: WatchPositionDao
) : ViewModel() {

    val showHidden: StateFlow<Boolean> = settingsManager.showHiddenFlow

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    val playlists: StateFlow<List<PlaylistWithItems>> = repository.getPlaylistsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _deviceFolders = MutableStateFlow<List<MediaFolder>>(emptyList())
    val deviceFolders: StateFlow<List<MediaFolder>> = _deviceFolders.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Uri>>(emptyList())
    val searchResults: StateFlow<List<Uri>> = _searchResults.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val results = scanner.searchMedia(SearchFilters(query = query))
            _searchResults.value = results
        }
    }

    // Recently watched
    private val _recentlyWatched = MutableStateFlow<List<Uri>>(emptyList())
    val recentlyWatched: StateFlow<List<Uri>> = _recentlyWatched.asStateFlow()

    // Recently added
    private val _recentlyAdded = MutableStateFlow<List<Uri>>(emptyList())
    val recentlyAdded: StateFlow<List<Uri>> = _recentlyAdded.asStateFlow()

    // Custom covers stored by bucketId
    private val _folderCovers = mutableStateMapOf<Long, Uri>()

    // Favorite folders
    private val _favoriteFolders = mutableStateMapOf<Long, Boolean>()

    private val prefs by lazy {
        context.getSharedPreferences("folder_covers", Context.MODE_PRIVATE)
    }

    private val favFolderPrefs by lazy {
        context.getSharedPreferences("favorite_folders", Context.MODE_PRIVATE)
    }

    private val folderCachePrefs by lazy {
        context.getSharedPreferences("device_folders_cache", Context.MODE_PRIVATE)
    }

    private val gson = Gson()
    private var foldersLoaded = false

    init {
        // Load saved covers
        prefs.all.forEach { (key, value) ->
            val bucketId = key.toLongOrNull()
            val uriStr = value as? String
            if (bucketId != null && uriStr != null) {
                _folderCovers[bucketId] = Uri.parse(uriStr)
            }
        }

        // Load favorite folders
        favFolderPrefs.all.forEach { (key, value) ->
            val bucketId = key.toLongOrNull()
            if (bucketId != null && value == true) {
                _favoriteFolders[bucketId] = true
            }
        }

        // Load recently watched and recently added
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val watched = watchPositionDao.getRecentlyWatched(10)
                _recentlyWatched.value = watched.map { Uri.parse(it.uri) }
            } catch (_: Exception) {}
            try {
                _recentlyAdded.value = scanner.getRecentlyAddedMedia(10)
            } catch (_: Exception) {}
        }

        // Eagerly load device folders at startup (show cached instantly, refresh in background)
        loadDeviceFolders()

        // Listen for USB/media mount/unmount events to auto-refresh
        val storageReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                viewModelScope.launch {
                    delay(3000) // Wait for mount to complete
                    foldersLoaded = false
                    loadDeviceFolders(force = true)
                }
            }
        }
        // Media mount events require "file" data scheme
        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        try { context.registerReceiver(storageReceiver, mediaFilter) } catch (_: Exception) {}
        // USB events don't use data scheme — separate filter
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try { context.registerReceiver(storageReceiver, usbFilter) } catch (_: Exception) {}
        // Also listen for volume changes (Android 7+)
        val volumeFilter = IntentFilter().apply {
            addAction("android.os.storage.action.VOLUME_STATE_CHANGED")
        }
        try { context.registerReceiver(storageReceiver, volumeFilter) } catch (_: Exception) {}

        // Background preloading of folder files and playlist files
        viewModelScope.launch(Dispatchers.IO) {
            // Preload all playlist files
            try {
                val allPlaylists = repository.getAllPlaylistsOnce()
                allPlaylists.forEach { playlist ->
                    launch {
                        try {
                            val cached = folderFileCache.getPlaylistFiles(playlist.id)
                            if (cached.isEmpty()) {
                                // Pre-scan if no cache exists - accumulate ALL batches
                                val allFiles = mutableListOf<android.net.Uri>()
                                repository.getMediaFilesFlow(playlist.id).collect { batch ->
                                    allFiles.addAll(batch)
                                }
                                if (allFiles.isNotEmpty()) {
                                    folderFileCache.savePlaylistFiles(playlist.id, allFiles)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}

            // Preload device folder files using FileScanner (produces file:// URIs)
            try {
                // Use cached folders if already available, otherwise scan
                val folders = scanner.getCachedFolders() ?: scanner.getAllMediaFolders()
                val fileScanner = com.example.customgalleryviewer.util.FileScanner(context, cacheManager, settingsManager.getShowHidden())
                folders.forEach { folder ->
                    launch {
                        try {
                            val cached = folderFileCache.getFolderFiles(folder.bucketId)
                            if (cached.isEmpty()) {
                                val folderPath = scanner.getFolderPath(folder.bucketId)
                                if (folderPath != null) {
                                    val folderUri = android.net.Uri.fromFile(java.io.File(folderPath))
                                    val allFiles = mutableListOf<android.net.Uri>()
                                    fileScanner.scanPlaylistItemsFlow(
                                        items = listOf(com.example.customgalleryviewer.data.PlaylistItemEntity(
                                            playlistId = 0, uriString = folderUri.toString(),
                                            type = com.example.customgalleryviewer.data.ItemType.FOLDER, isRecursive = false
                                        )),
                                        filter = com.example.customgalleryviewer.data.MediaFilterType.MIXED
                                    ).collect { batch -> allFiles.addAll(batch) }
                                    if (allFiles.isNotEmpty()) {
                                        folderFileCache.saveFolderFiles(folder.bucketId, allFiles)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadDeviceFolders(force: Boolean = false) {
        if (!force && foldersLoaded) return
        foldersLoaded = true

        // Load cached folders instantly
        val cached = loadCachedFolders()
        if (cached.isNotEmpty()) {
            _deviceFolders.value = cached
        }

        // Background refresh
        viewModelScope.launch(Dispatchers.IO) {
            if (cached.isEmpty()) _isScanning.value = true
            val fresh = scanner.getAllMediaFolders()
            _deviceFolders.value = fresh
            _isScanning.value = false
            saveFoldersToCache(fresh)
        }
    }

    private fun loadCachedFolders(): List<MediaFolder> {
        val json = folderCachePrefs.getString("folders", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CachedMediaFolder>>() {}.type
            val cached: List<CachedMediaFolder> = gson.fromJson(json, type)
            cached.map { MediaFolder(it.bucketId, it.name, it.path, it.thumbnailUri?.let { u -> Uri.parse(u) }, it.mediaCount, it.isExternal) }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveFoldersToCache(folders: List<MediaFolder>) {
        val cached = folders.map { CachedMediaFolder(it.bucketId, it.name, it.path, it.thumbnailUri?.toString(), it.mediaCount, it.isExternal) }
        folderCachePrefs.edit().putString("folders", gson.toJson(cached)).apply()
    }

    private data class CachedMediaFolder(
        val bucketId: Long,
        val name: String,
        val path: String,
        val thumbnailUri: String?,
        val mediaCount: Int,
        val isExternal: Boolean = false
    )

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun hidePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.hidePlaylist(playlistId, true)
        }
    }

    fun unhidePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.hidePlaylist(playlistId, false)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, name)
        }
    }

    fun getFolderCoverByBucket(bucketId: Long): Uri? {
        return _folderCovers[bucketId]
    }

    fun setFolderCoverByBucket(bucketId: Long, coverUri: Uri) {
        _folderCovers[bucketId] = coverUri
        prefs.edit().putString(bucketId.toString(), coverUri.toString()).apply()
    }

    fun getFilesInFolder(bucketId: Long): List<Uri> {
        return scanner.getFilesInFolder(bucketId)
    }

    fun isFolderFavorite(bucketId: Long): Boolean {
        return _favoriteFolders[bucketId] == true
    }

    fun toggleFolderFavorite(bucketId: Long) {
        if (_favoriteFolders[bucketId] == true) {
            _favoriteFolders.remove(bucketId)
            favFolderPrefs.edit().remove(bucketId.toString()).apply()
        } else {
            _favoriteFolders[bucketId] = true
            favFolderPrefs.edit().putBoolean(bucketId.toString(), true).apply()
        }
    }
}
