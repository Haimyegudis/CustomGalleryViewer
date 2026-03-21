package com.example.customgalleryviewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val uri: String,
    val addedAt: Long = System.currentTimeMillis()
)
