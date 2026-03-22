package com.example.customgalleryviewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val vaultPath: String,
    val fileName: String,
    val isVideo: Boolean,
    val addedAt: Long = System.currentTimeMillis()
)
