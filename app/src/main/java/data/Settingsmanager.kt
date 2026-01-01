package com.example.customgalleryviewer.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gallery_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PLAYBACK_SORT = "playback_sort_order" // שינינו שם לבהירות
        private const val KEY_GALLERY_SORT = "gallery_sort_order"   // חדש
        private const val KEY_NAV_MODE = "navigation_mode"
        private const val KEY_GRID_COLUMNS = "grid_columns"
    }

    // --- Playback Sort (Random / Name / Date) ---
    private val _playbackSortFlow = MutableStateFlow(getPlaybackSort())
    val playbackSortFlow: StateFlow<SortOrder> = _playbackSortFlow.asStateFlow()

    fun setPlaybackSort(order: SortOrder) {
        prefs.edit().putString(KEY_PLAYBACK_SORT, order.name).apply()
        _playbackSortFlow.value = order
    }

    fun getPlaybackSort(): SortOrder {
        val orderName = prefs.getString(KEY_PLAYBACK_SORT, SortOrder.RANDOM.name)
        return try {
            SortOrder.valueOf(orderName ?: SortOrder.RANDOM.name)
        } catch (e: Exception) {
            SortOrder.RANDOM
        }
    }

    // --- Gallery Sort (Name / Date only) ---
    private val _gallerySortFlow = MutableStateFlow(getGallerySort())
    val gallerySortFlow: StateFlow<SortOrder> = _gallerySortFlow.asStateFlow()

    fun setGallerySort(order: SortOrder) {
        // בגלריה לא נאפשר RANDOM, אם נשלח בטעות נשנה ל-NAME
        val validOrder = if (order == SortOrder.RANDOM) SortOrder.BY_NAME else order
        prefs.edit().putString(KEY_GALLERY_SORT, validOrder.name).apply()
        _gallerySortFlow.value = validOrder
    }

    fun getGallerySort(): SortOrder {
        val orderName = prefs.getString(KEY_GALLERY_SORT, SortOrder.BY_DATE.name) // ברירת מחדל: תאריך
        return try {
            SortOrder.valueOf(orderName ?: SortOrder.BY_DATE.name)
        } catch (e: Exception) {
            SortOrder.BY_DATE
        }
    }

    // --- Navigation Mode & Grid ---
    fun setNavigationMode(mode: String) {
        prefs.edit().putString(KEY_NAV_MODE, mode).apply()
    }

    fun getNavigationMode(): String = prefs.getString(KEY_NAV_MODE, "TAP") ?: "TAP"

    fun setGridColumns(columns: Int) {
        prefs.edit().putInt(KEY_GRID_COLUMNS, columns).apply()
    }

    fun getGridColumns(): Int = prefs.getInt(KEY_GRID_COLUMNS, 3)
}