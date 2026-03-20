package com.example.customgalleryviewer.data

import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

@Database(
    entities = [PlaylistEntity::class, PlaylistItemEntity::class],
    version = 3,  // Added isHidden field
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}