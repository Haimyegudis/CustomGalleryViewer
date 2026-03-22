package com.example.customgalleryviewer.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.util.ExifGeoExtractor
import com.example.customgalleryviewer.util.GeoMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _geoMedia = MutableStateFlow<List<GeoMedia>>(emptyList())
    val geoMedia: StateFlow<List<GeoMedia>> = _geoMedia.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadGeoMedia()
    }

    private fun loadGeoMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _geoMedia.value = ExifGeoExtractor.extractGeotaggedMedia(context)
            _isLoading.value = false
        }
    }
}
