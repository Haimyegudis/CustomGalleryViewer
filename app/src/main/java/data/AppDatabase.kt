package com.example.customgalleryviewer.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.Update

data class PlaylistWithItems(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val items: List<PlaylistItemEntity>
)

class Converters {
    @TypeConverter
    fun fromMediaFilter(value: MediaFilterType) = value.name
    @TypeConverter
    fun toMediaFilter(value: String) = MediaFilterType.valueOf(value)

    @TypeConverter
    fun fromItemType(value: ItemType) = value.name
    @TypeConverter
    fun toItemType(value: String) = ItemType.valueOf(value)
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Transaction
    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylists(): List<PlaylistWithItems>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithItems(playlistId: Long): PlaylistWithItems?

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deleteItem(item: PlaylistItemEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteItemsByPlaylistId(playlistId: Long)
}

@Database(entities = [PlaylistEntity::class, PlaylistItemEntity::class], version = 1)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
}