package com.example.customgalleryviewer.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.util.DeviceMediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceFolderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: DeviceMediaScanner
) : ViewModel() {

    private val _files = MutableStateFlow<List<Uri>>(emptyList())
    val files: StateFlow<List<Uri>> = _files.asStateFlow()

    private val _currentMedia = MutableStateFlow<Uri?>(null)
    val currentMedia: StateFlow<Uri?> = _currentMedia.asStateFlow()

    private val _isGalleryMode = MutableStateFlow(true)
    val isGalleryMode: StateFlow<Boolean> = _isGalleryMode.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentIndex = -1
    private var loadedBucketId: Long? = null

    fun loadFolder(bucketId: Long) {
        if (loadedBucketId == bucketId) return
        loadedBucketId = bucketId
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val mediaFiles = scanner.getFilesInFolder(bucketId)
            _files.value = mediaFiles
            _isLoading.value = false
        }
    }

    fun jumpToItem(uri: Uri) {
        currentIndex = _files.value.indexOf(uri)
        _currentMedia.value = uri
        _isGalleryMode.value = false
    }

    fun onNext() {
        val list = _files.value
        if (list.isEmpty()) return
        currentIndex = (currentIndex + 1) % list.size
        _currentMedia.value = list[currentIndex]
    }

    fun onPrevious() {
        val list = _files.value
        if (list.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
        _currentMedia.value = list[currentIndex]
    }

    fun setGalleryMode(mode: Boolean) {
        _isGalleryMode.value = mode
    }
}
