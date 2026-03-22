package com.example.customgalleryviewer.presentation

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.util.DuplicateFinder
import com.example.customgalleryviewer.util.DuplicateGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DuplicateFinderViewModel @Inject constructor(
    private val duplicateFinder: DuplicateFinder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _duplicates = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicates: StateFlow<List<DuplicateGroup>> = _duplicates.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _duplicates.value = duplicateFinder.findDuplicates().filter { it.files.size >= 2 }
            _isScanning.value = false
        }
    }

    fun deleteFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            performDelete(uri)
            scan()
        }
    }

    /** Keep the first file in a group, delete the rest */
    fun keepFirstInGroup(group: DuplicateGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            val toDelete = group.files.drop(1) // keep first, delete rest
            toDelete.forEach { performDelete(it.uri) }
            scan()
        }
    }

    /** Keep the first file in every group, delete all other duplicates */
    fun cleanAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            val current = _duplicates.value
            current.forEach { group ->
                group.files.drop(1).forEach { performDelete(it.uri) }
            }
            scan()
        }
    }

    private fun performDelete(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
            try {
                val path = getPathFromUri(uri)
                if (path != null) File(path).delete()
            } catch (_: Exception) {}
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        try {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
