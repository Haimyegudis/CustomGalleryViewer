package com.example.customgalleryviewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaFilterType {
    PHOTOS_ONLY, VIDEO_ONLY, MIXED
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),

    val startVideoMuted: Boolean = true,
    val autoRotateVideo: Boolean = true,

    val mediaFilterType: MediaFilterType = MediaFilterType.MIXED
)