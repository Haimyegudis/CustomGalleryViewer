package com.example.customgalleryviewer.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gallery_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SORT_ORDER = "sort_order"
    }

    fun setSortOrder(order: SortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    fun getSortOrder(): SortOrder {
        val orderName = prefs.getString(KEY_SORT_ORDER, SortOrder.RANDOM.name)
        return try {
            SortOrder.valueOf(orderName ?: SortOrder.RANDOM.name)
        } catch (e: Exception) {
            SortOrder.RANDOM
        }
    }
}