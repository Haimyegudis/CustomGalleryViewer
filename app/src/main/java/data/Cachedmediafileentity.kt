package com.example.customgalleryviewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "cached_media_files",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("uriString")]
)
data class CachedMediaFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val playlistId: Long,

    val uriString: String,

    val lastModified: Long = System.currentTimeMillis(),

    // Optional: לשמור מידע נוסף
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null
)