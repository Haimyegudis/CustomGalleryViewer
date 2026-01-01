package com.example.customgalleryviewer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    // תיקון: מחזיר PlaylistWithItems (כולל ספירת פריטים) ולא סתם Entity
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY id DESC")
    fun getPlaylistsFlow(): Flow<List<PlaylistWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Transaction
    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylists(): List<PlaylistWithItems>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithItems(playlistId: Long): PlaylistWithItems?

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteItemsByPlaylistId(playlistId: Long)
}