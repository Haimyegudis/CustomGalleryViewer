package com.example.customgalleryviewer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.PlaylistDao
import com.example.customgalleryviewer.data.PlaylistEntity
import com.example.customgalleryviewer.data.PlaylistWithItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlistDao: PlaylistDao
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<PlaylistWithItems>>(emptyList())
    val playlists: StateFlow<List<PlaylistWithItems>> = _playlists

    init {
        loadPlaylists()
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            _playlists.value = playlistDao.getAllPlaylists()
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(playlist)
            loadPlaylists() // רענון
        }
    }
}