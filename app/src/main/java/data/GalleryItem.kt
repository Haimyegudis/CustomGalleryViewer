package com.example.customgalleryviewer.data

import android.net.Uri

sealed class GalleryItem {
    data class MediaFile(val uri: Uri) : GalleryItem()
    data class Folder(
        val uri: Uri,
        val name: String,
        val thumbnailUri: Uri? = null,
        val childCount: Int = 0
    ) : GalleryItem()
}
