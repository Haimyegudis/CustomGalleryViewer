package com.example.customgalleryviewer.presentation

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.FolderFileCache
import com.example.customgalleryviewer.data.PlaylistWithItems
import com.example.customgalleryviewer.data.SettingsManager
import com.example.customgalleryviewer.repository.MediaRepository
import com.example.customgalleryviewer.util.DeviceMediaScanner
import com.example.customgalleryviewer.util.MediaFolder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    private val cacheManager: com.example.customgalleryviewer.data.MediaCacheManager
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

    // Custom covers stored by bucketId
    private val _folderCovers = mutableStateMapOf<Long, Uri>()

    private val prefs by lazy {
        context.getSharedPreferences("folder_covers", Context.MODE_PRIVATE)
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
                val folders = scanner.getAllMediaFolders()
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
            cached.map { MediaFolder(it.bucketId, it.name, it.path, it.thumbnailUri?.let { u -> Uri.parse(u) }, it.mediaCount) }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveFoldersToCache(folders: List<MediaFolder>) {
        val cached = folders.map { CachedMediaFolder(it.bucketId, it.name, it.path, it.thumbnailUri?.toString(), it.mediaCount) }
        folderCachePrefs.edit().putString("folders", gson.toJson(cached)).apply()
    }

    private data class CachedMediaFolder(
        val bucketId: Long,
        val name: String,
        val path: String,
        val thumbnailUri: String?,
        val mediaCount: Int
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
}
