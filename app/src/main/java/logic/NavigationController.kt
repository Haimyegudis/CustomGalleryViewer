package com.example.customgalleryviewer.logic

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.customgalleryviewer.data.SettingsManager
import com.example.customgalleryviewer.data.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class NavigationController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {

    private val masterList = mutableListOf<Uri>()
    private val history = mutableListOf<Int>()
    private var currentHistoryIndex = -1
    private val MAX_BUFFER_SIZE = 50

    private val _currentMedia = MutableStateFlow<Uri?>(null)
    val currentMedia: StateFlow<Uri?> = _currentMedia.asStateFlow()
    private val lock = Any()

    fun resetPlaylist() {
        synchronized(lock) {
            masterList.clear()
            history.clear()
            currentHistoryIndex = -1
            _currentMedia.value = null
        }
    }

    fun appendItems(newItems: List<Uri>) {
        if (newItems.isEmpty()) return
        synchronized(lock) {
            val wasEmpty = masterList.isEmpty()

            // Sort items based on playback settings (תיקון: שימוש ב-getPlaybackSort)
            val sortedItems = when (settingsManager.getPlaybackSort()) {
                SortOrder.BY_NAME -> sortByName(newItems)
                SortOrder.BY_DATE -> sortByDate(newItems)
                SortOrder.RANDOM -> newItems
            }

            masterList.addAll(sortedItems)
            if (wasEmpty && masterList.isNotEmpty()) {
                nextInternal()
            }
        }
    }

    private fun sortByName(items: List<Uri>): List<Uri> {
        return items.sortedBy { uri ->
            getFileName(uri)
        }
    }

    private fun sortByDate(items: List<Uri>): List<Uri> {
        return items.sortedByDescending { uri ->
            getFileDate(uri)
        }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = ""
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = it.getString(nameIndex) ?: ""
                    }
                }
            }
        }
        if (fileName.isEmpty()) {
            fileName = uri.lastPathSegment ?: ""
        }
        return fileName
    }

    private fun getFileDate(uri: Uri): Long {
        var date = 0L
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val dateIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    if (dateIndex >= 0) {
                        date = it.getLong(dateIndex)
                    }
                }
            }
        }
        return date
    }

    fun next() {
        synchronized(lock) { nextInternal() }
    }

    private fun nextInternal() {
        if (masterList.isEmpty()) return

        // תיקון: שימוש ב-getPlaybackSort
        when (settingsManager.getPlaybackSort()) {
            SortOrder.RANDOM -> {
                if (currentHistoryIndex < history.lastIndex) {
                    currentHistoryIndex++
                    updateMediaFromHistory()
                    return
                }

                val nextIndex = generateSmartRandomIndex()
                history.add(nextIndex)
                currentHistoryIndex = history.lastIndex
                updateMediaFromHistory()
            }
            SortOrder.BY_NAME, SortOrder.BY_DATE -> {
                // Sequential navigation
                val currentIndex = if (history.isEmpty()) -1 else history[currentHistoryIndex]
                val nextIndex = (currentIndex + 1) % masterList.size

                if (currentHistoryIndex < history.lastIndex) {
                    currentHistoryIndex++
                } else {
                    history.add(nextIndex)
                    currentHistoryIndex = history.lastIndex
                }
                updateMediaFromHistory()
            }
        }
    }

    fun previous() {
        synchronized(lock) {
            if (masterList.isEmpty()) return
            if (history.isEmpty()) {
                nextInternal()
                return
            }

            // תיקון: שימוש ב-getPlaybackSort
            when (settingsManager.getPlaybackSort()) {
                SortOrder.RANDOM -> {
                    if (currentHistoryIndex > 0) {
                        currentHistoryIndex--
                    } else {
                        currentHistoryIndex = history.lastIndex
                    }
                }
                SortOrder.BY_NAME, SortOrder.BY_DATE -> {
                    val currentIndex = history[currentHistoryIndex]
                    val prevIndex = if (currentIndex - 1 < 0) masterList.size - 1 else currentIndex - 1

                    if (currentHistoryIndex > 0) {
                        currentHistoryIndex--
                    } else {
                        history.add(0, prevIndex)
                        currentHistoryIndex = 0
                    }
                }
            }
            updateMediaFromHistory()
        }
    }

    private fun updateMediaFromHistory() {
        if (currentHistoryIndex >= 0 && currentHistoryIndex < history.size) {
            val idx = history[currentHistoryIndex]
            if (idx >= 0 && idx < masterList.size) {
                _currentMedia.value = masterList[idx]
            }
        }
    }

    private fun generateSmartRandomIndex(): Int {
        val totalSize = masterList.size
        if (totalSize == 0) return 0
        if (totalSize == 1) return 0

        if (totalSize < 5) return (0 until totalSize).random()

        val bufferSize = min(MAX_BUFFER_SIZE, totalSize / 2)
        val recentIndices = history.takeLast(bufferSize).toSet()
        var candidate: Int
        var attempts = 0

        do {
            candidate = (0 until totalSize).random()
            attempts++
        } while (candidate in recentIndices && attempts < 20)

        return candidate
    }
}