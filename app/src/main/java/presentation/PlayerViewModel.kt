package com.example.customgalleryviewer.presentation

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.SettingsManager
import com.example.customgalleryviewer.data.SortOrder
import com.example.customgalleryviewer.logic.NavigationController
import com.example.customgalleryviewer.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val navigationController: NavigationController,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val currentMedia: StateFlow<Uri?> = navigationController.currentMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadPlaylist(playlistId: Long) {
        navigationController.resetPlaylist()

        viewModelScope.launch(Dispatchers.IO) {
            Log.d("PlayerViewModel", "Start streaming playlist ID: $playlistId")

            repository.getMediaFilesFlow(playlistId).collect { batch ->
                Log.d("PlayerViewModel", "Received batch of ${batch.size} items")

                // Shuffle only if RANDOM mode
                val itemsToAdd = when (settingsManager.getSortOrder()) {
                    SortOrder.RANDOM -> batch.shuffled()
                    else -> batch
                }

                navigationController.appendItems(itemsToAdd)
            }
        }
    }

    fun onNext() {
        navigationController.next()
    }

    fun onPrevious() {
        navigationController.previous()
    }
}