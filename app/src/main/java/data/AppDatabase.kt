package com.example.customgalleryviewer.data

import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

// המחלקה שהייתה חסרה וגרמה לקריסה
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

// הגדרת המסד - ללא ה-Interface בתוכו (כי הוא בקובץ נפרד)
@Database(entities = [PlaylistEntity::class, PlaylistItemEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
}