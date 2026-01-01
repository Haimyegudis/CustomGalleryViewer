package com.example.customgalleryviewer.logic

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class NavigationController @Inject constructor() {

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
            masterList.addAll(newItems)
            if (wasEmpty && masterList.isNotEmpty()) {
                nextInternal()
            }
        }
    }

    fun next() {
        synchronized(lock) { nextInternal() }
    }

    private fun nextInternal() {
        if (masterList.isEmpty()) return

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

    fun previous() {
        synchronized(lock) {
            if (masterList.isEmpty()) return
            if (history.isEmpty()) {
                nextInternal()
                return
            }

            if (currentHistoryIndex > 0) {
                currentHistoryIndex--
            } else {
                currentHistoryIndex = history.lastIndex
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