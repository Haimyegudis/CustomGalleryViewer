package com.example.customgalleryviewer.data

import android.content.Context
import android.content.SharedPreferences
import com.example.customgalleryviewer.data.GestureAction
import com.example.customgalleryviewer.data.GestureType
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
        private const val KEY_PLAYBACK_SORT = "playback_sort_order"
        private const val KEY_GALLERY_SORT = "gallery_sort_order"
        private const val KEY_NAV_MODE = "navigation_mode"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_SHOW_HIDDEN = "show_hidden_files"
        private const val KEY_HOME_VIEW_MODE = "home_view_mode"
        private const val KEY_FILES_VIEW_MODE = "files_view_mode"
        private const val KEY_SHUFFLE_ON = "shuffle_on"
        private const val KEY_REPEAT_LIST_ON = "repeat_list_on"
        private const val KEY_MEDIA_FILTER = "media_filter"
        private const val KEY_FOLDER_GRID_COLUMNS = "folder_grid_columns"
        private const val KEY_LOCAL_SORT = "local_sort_order"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_SLIDESHOW_INTERVAL = "slideshow_interval"
        private const val KEY_LOOP_ONE = "loop_one"
        private const val KEY_GESTURE_PREFIX = "gesture_"
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

    // --- Show Hidden Files ---
    private val _showHiddenFlow = MutableStateFlow(getShowHidden())
    val showHiddenFlow: StateFlow<Boolean> = _showHiddenFlow.asStateFlow()

    fun setShowHidden(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
        _showHiddenFlow.value = show
    }

    fun getShowHidden(): Boolean = prefs.getBoolean(KEY_SHOW_HIDDEN, false)

    fun setLocalSort(sort: String) { prefs.edit().putString(KEY_LOCAL_SORT, sort).apply() }
    fun getLocalSort(): String = prefs.getString(KEY_LOCAL_SORT, "default") ?: "default"

    fun setHomeViewMode(mode: String) { prefs.edit().putString(KEY_HOME_VIEW_MODE, mode).apply() }
    fun getHomeViewMode(): String = prefs.getString(KEY_HOME_VIEW_MODE, "hero") ?: "hero"
    fun setFilesViewMode(mode: String) { prefs.edit().putString(KEY_FILES_VIEW_MODE, mode).apply() }
    fun getFilesViewMode(): String = prefs.getString(KEY_FILES_VIEW_MODE, "grid") ?: "grid"

    fun setShuffleOn(on: Boolean) { prefs.edit().putBoolean(KEY_SHUFFLE_ON, on).apply() }
    fun getShuffleOn(): Boolean = prefs.getBoolean(KEY_SHUFFLE_ON, false)
    fun setRepeatListOn(on: Boolean) { prefs.edit().putBoolean(KEY_REPEAT_LIST_ON, on).apply() }
    fun getRepeatListOn(): Boolean = prefs.getBoolean(KEY_REPEAT_LIST_ON, false)

    fun setMediaFilter(filter: String) { prefs.edit().putString(KEY_MEDIA_FILTER, filter).apply() }
    fun getMediaFilter(): String = prefs.getString(KEY_MEDIA_FILTER, "MIXED") ?: "MIXED"
    fun setFolderGridColumns(cols: Int) { prefs.edit().putInt(KEY_FOLDER_GRID_COLUMNS, cols).apply() }
    fun getFolderGridColumns(): Int = prefs.getInt(KEY_FOLDER_GRID_COLUMNS, 3)

    fun setLoopOne(on: Boolean) { prefs.edit().putBoolean(KEY_LOOP_ONE, on).apply() }
    fun getLoopOne(): Boolean = prefs.getBoolean(KEY_LOOP_ONE, false)

    // --- Theme Mode ---
    private val _themeModeFlow = MutableStateFlow(getThemeMode())
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
    }

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "dark") ?: "dark"

    // --- Accent Color ---
    private val _accentColorFlow = MutableStateFlow(getAccentColor())
    val accentColorFlow: StateFlow<String> = _accentColorFlow.asStateFlow()

    fun setAccentColor(color: String) {
        prefs.edit().putString(KEY_ACCENT_COLOR, color).apply()
        _accentColorFlow.value = color
    }

    fun getAccentColor(): String = prefs.getString(KEY_ACCENT_COLOR, "cyan") ?: "cyan"

    // --- Background Wallpaper (Skin) ---
    private val _wallpaperUri = MutableStateFlow(getWallpaper())
    val wallpaperUri: StateFlow<String?> = _wallpaperUri.asStateFlow()

    fun setWallpaper(uri: String?) {
        if (uri == null) prefs.edit().remove("wallpaper_uri").apply()
        else prefs.edit().putString("wallpaper_uri", uri).apply()
        _wallpaperUri.value = uri
    }

    fun getWallpaper(): String? = prefs.getString("wallpaper_uri", null)

    // --- Gesture Settings ---
    private val defaultGestures = mapOf(
        GestureType.DOUBLE_TAP_LEFT to GestureAction.PREVIOUS,
        GestureType.DOUBLE_TAP_RIGHT to GestureAction.NEXT,
        GestureType.DOUBLE_TAP_CENTER to GestureAction.TOGGLE_CONTROLS,
        GestureType.SWIPE_LEFT_VERTICAL to GestureAction.BRIGHTNESS,
        GestureType.SWIPE_RIGHT_VERTICAL to GestureAction.VOLUME,
        GestureType.SWIPE_HORIZONTAL to GestureAction.SEEK,
        GestureType.LONG_PRESS to GestureAction.ACTION_MENU
    )

    fun getGestureAction(gestureType: GestureType): GestureAction {
        val saved = prefs.getString(KEY_GESTURE_PREFIX + gestureType.name, null)
        return if (saved != null) {
            try { GestureAction.valueOf(saved) } catch (_: Exception) { defaultGestures[gestureType] ?: GestureAction.NONE }
        } else {
            defaultGestures[gestureType] ?: GestureAction.NONE
        }
    }

    fun setGestureAction(gestureType: GestureType, action: GestureAction) {
        prefs.edit().putString(KEY_GESTURE_PREFIX + gestureType.name, action.name).apply()
    }

    fun getAllGestureSettings(): Map<GestureType, GestureAction> {
        return GestureType.entries.associateWith { getGestureAction(it) }
    }
}