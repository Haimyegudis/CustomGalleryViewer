package com.example.customgalleryviewer.presentation

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.Playlist
import com.example.customgalleryviewer.data.PlaylistDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditPlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    private var currentPlaylistId: Long = 0

    private val _playlistName = MutableStateFlow("")
    val playlistName: StateFlow<String> = _playlistName.asStateFlow()

    private val _items = MutableStateFlow<List<Uri>>(emptyList())
    val items: StateFlow<List<Uri>> = _items.asStateFlow()

    fun loadPlaylist(playlistId: Long) {
        currentPlaylistId = playlistId
        viewModelScope.launch {
            val playlist = playlistDao.getPlaylistById(playlistId)
            playlist?.let {
                _playlistName.value = it.name
                _items.value = it.uriStrings.map { uriStr -> Uri.parse(uriStr) }
            }
        }
    }

    fun addItems(uris: List<Uri>) {
        val currentItems = _items.value.toMutableList()
        uris.forEach { uri ->
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            if (uri !in currentItems) {
                currentItems.add(uri)
            }
        }
        _items.value = currentItems
    }

    fun addFolder(folderUri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    folderUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri,
                    DocumentsContract.getTreeDocumentId(folderUri)
                )

                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

                val mediaUris = mutableListOf<Uri>()
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val docIdIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(docIdIndex)
                        val mime = cursor.getString(mimeIndex)

                        if (mime?.startsWith("image/") == true || mime?.startsWith("video/") == true) {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                            mediaUris.add(uri)
                        }
                    }
                }

                val currentItems = _items.value.toMutableList()
                mediaUris.forEach { uri ->
                    if (uri !in currentItems) {
                        currentItems.add(uri)
                    }
                }
                _items.value = currentItems
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeItem(uri: Uri) {
        val currentItems = _items.value.toMutableList()
        currentItems.remove(uri)
        _items.value = currentItems
    }

    fun savePlaylist() {
        viewModelScope.launch {
            val playlist = playlistDao.getPlaylistById(currentPlaylistId)
            playlist?.let {
                val updatedPlaylist = it.copy(
                    uriStrings = _items.value.map { uri -> uri.toString() }
                )
                playlistDao.update(updatedPlaylist)
            }
        }
    }
}