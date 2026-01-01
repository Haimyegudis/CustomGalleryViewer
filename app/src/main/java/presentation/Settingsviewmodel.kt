package com.example.customgalleryviewer.presentation

import androidx.lifecycle.ViewModel
import com.example.customgalleryviewer.data.SettingsManager
import com.example.customgalleryviewer.data.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _currentSortOrder = MutableStateFlow(settingsManager.getSortOrder())
    val currentSortOrder: StateFlow<SortOrder> = _currentSortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        settingsManager.setSortOrder(order)
        _currentSortOrder.value = order
    }
}