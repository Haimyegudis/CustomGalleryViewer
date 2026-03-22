package com.example.customgalleryviewer.data

data class SearchFilters(
    val query: String = "",
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val minDuration: Long? = null,
    val maxDuration: Long? = null
) {
    val hasActiveFilters: Boolean
        get() = dateFrom != null || dateTo != null || minSize != null || maxSize != null || minDuration != null || maxDuration != null
}
