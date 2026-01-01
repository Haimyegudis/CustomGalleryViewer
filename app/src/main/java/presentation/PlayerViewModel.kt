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

    // תיקון: שימוש ב-LinkedHashSet כדי למנוע כפילויות אבל לשמור סדר
    private val originalRawSet = LinkedHashSet<Uri>()

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
    }

    fun loadPlaylist(playlistId: Long) {
        if (loadedPlaylistId == playlistId && originalRawSet.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            loadedPlaylistId = playlistId
            currentGallerySort = null
            currentPlaybackSort = null

            // תיקון: ניקוי ה-Set לפני טעינה חדשה
            originalRawSet.clear()

            Log.d("PlayerViewModel", "=== Starting playlist load: $playlistId ===")

            // תיקון: שימוש ב-collect במקום collectLatest כדי לא לאבד באצ'ים
            repository.getMediaFilesFlow(playlistId).collect { batch ->
                Log.d("PlayerViewModel", "Received batch: ${batch.size} files")

                // תיקון: הוספה ל-Set מונעת כפילויות אוטומטית
                val sizeBefore = originalRawSet.size
                originalRawSet.addAll(batch)
                val sizeAfter = originalRawSet.size

                Log.d("PlayerViewModel", "Added ${sizeAfter - sizeBefore} new files (total: $sizeAfter)")

                // עדכון מיידי של הגלריה והפלייבק
                updateGalleryList(settingsManager.getGallerySort())
                updatePlaybackList(settingsManager.getPlaybackSort())

                // התחלת ניגון אם עדיין לא התחלנו
                if (_currentMedia.value == null && _playbackItems.isNotEmpty()) {
                    currentIndex = 0
                    _currentMedia.value = _playbackItems[0]
                    Log.d("PlayerViewModel", "Started playback with first item")
                }
            }

            Log.d("PlayerViewModel", "=== Playlist load complete: ${originalRawSet.size} total files ===")
        }
    }

    private suspend fun updateGalleryList(order: SortOrder) {
        if (originalRawSet.isEmpty()) return
        currentGallerySort = order

        // תיקון: המרת Set ל-List רק פעם אחת
        val sorted = sortList(originalRawSet.toList(), order)
        _galleryItems.value = sorted

        Log.d("PlayerViewModel", "Gallery updated: ${sorted.size} items")
    }

    private suspend fun updatePlaybackList(order: SortOrder) {
        if (originalRawSet.isEmpty()) return
        currentPlaybackSort = order

        // תיקון: המרת Set ל-List רק פעם אחת
        val sorted = sortList(originalRawSet.toList(), order)
        _playbackItems = sorted

        val current = _currentMedia.value
        if (current != null) {
            val newIndex = _playbackItems.indexOf(current)
            if (newIndex != -1) currentIndex = newIndex
        }

        Log.d("PlayerViewModel", "Playback updated: ${sorted.size} items")
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
            _isGalleryMode.value = false
        }
    }

    fun toggleGalleryMode() { _isGalleryMode.value = !_isGalleryMode.value }

    fun setGridColumns(columns: Int) {
        _gridColumns.value = columns
        settingsManager.setGridColumns(columns)
    }
}