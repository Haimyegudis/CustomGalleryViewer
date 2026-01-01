package com.example.customgalleryviewer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.CachedFolder
import com.example.customgalleryviewer.data.MediaCacheManager
import com.example.customgalleryviewer.data.SettingsManager
import com.example.customgalleryviewer.data.SortOrder
import com.example.customgalleryviewer.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val cacheManager: MediaCacheManager,
    private val repository: MediaRepository
) : ViewModel() {

    // Playback Sort
    private val _playbackSort = MutableStateFlow(settingsManager.getPlaybackSort())
    val playbackSort: StateFlow<SortOrder> = _playbackSort.asStateFlow()

    fun setPlaybackSort(order: SortOrder) {
        settingsManager.setPlaybackSort(order)
        _playbackSort.value = order
    }

    // Gallery Sort
    private val _gallerySort = MutableStateFlow(settingsManager.getGallerySort())
    val gallerySort: StateFlow<SortOrder> = _gallerySort.asStateFlow()

    fun setGallerySort(order: SortOrder) {
        settingsManager.setGallerySort(order)
        _gallerySort.value = order
    }

    // Navigation Mode
    private val _navigationMode = MutableStateFlow(settingsManager.getNavigationMode())
    val navigationMode: StateFlow<String> = _navigationMode.asStateFlow()

    fun setNavigationMode(mode: String) {
        settingsManager.setNavigationMode(mode)
        _navigationMode.value = mode
    }

    // Cache Info
    private val _cacheInfo = MutableStateFlow<Map<String, CachedFolder>>(emptyMap())
    val cacheInfo: StateFlow<Map<String, CachedFolder>> = _cacheInfo.asStateFlow()

    init {
        loadCacheInfo()
    }

    private fun loadCacheInfo() {
        viewModelScope.launch {
            _cacheInfo.value = repository.getCacheInfo()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            loadCacheInfo()
        }
    }
}