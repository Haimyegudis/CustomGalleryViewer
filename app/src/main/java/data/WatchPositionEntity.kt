package com.example.customgalleryviewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_positions")
data class WatchPositionEntity(
    @PrimaryKey val uri: String,
    val position: Long,
    val duration: Long,
    val updatedAt: Long = System.currentTimeMillis()
)
