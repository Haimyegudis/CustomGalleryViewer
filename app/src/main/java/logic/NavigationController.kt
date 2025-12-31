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

    // באפר חכם: מתאים את עצמו לגודל הרשימה
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
            if (wasEmpty) nextInternal()
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

        // הגרלה חדשה
        val nextIndex = generateSmartRandomIndex()
        history.add(nextIndex)
        currentHistoryIndex = history.lastIndex
        updateMediaFromHistory()
    }

    fun previous() {
        synchronized(lock) {
            if (history.isEmpty()) return
            if (currentHistoryIndex <= 0) currentHistoryIndex = history.lastIndex
            else currentHistoryIndex--
            updateMediaFromHistory()
        }
    }

    private fun updateMediaFromHistory() {
        if (currentHistoryIndex >= 0 && currentHistoryIndex < history.size) {
            val idx = history[currentHistoryIndex]
            if (idx < masterList.size) _currentMedia.value = masterList[idx]
        }
    }

    private fun generateSmartRandomIndex(): Int {
        val totalSize = masterList.size
        if (totalSize == 0) return 0

        // אם הרשימה קטנה מאוד (פחות מ-5), סתם רנדום פשוט בלי באפר
        if (totalSize < 5) return (0 until totalSize).random()

        // חישוב באפר דינמי: לא יותר מ-50, אבל גם לא יותר מחצי מהרשימה
        val bufferSize = min(MAX_BUFFER_SIZE, totalSize / 2)

        val recentIndices = history.takeLast(bufferSize).toSet()
        var candidate: Int
        var attempts = 0

        do {
            candidate = (0 until totalSize).random()
            attempts++
        } while (candidate in recentIndices && attempts < 20) // מנסים 20 פעם למצוא משהו חדש

        return candidate
    }
}