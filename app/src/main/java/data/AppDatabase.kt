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
    entities = [PlaylistEntity::class, PlaylistItemEntity::class, WatchPositionEntity::class, FavoriteEntity::class, VaultEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun watchPositionDao(): WatchPositionDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun vaultDao(): VaultDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS watch_positions (uri TEXT NOT NULL PRIMARY KEY, position INTEGER NOT NULL, duration INTEGER NOT NULL, updatedAt INTEGER NOT NULL DEFAULT 0)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS favorites (uri TEXT NOT NULL PRIMARY KEY, addedAt INTEGER NOT NULL DEFAULT 0)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS vault_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, originalPath TEXT NOT NULL, vaultPath TEXT NOT NULL, fileName TEXT NOT NULL, isVideo INTEGER NOT NULL, addedAt INTEGER NOT NULL DEFAULT 0)")
            }
        }
    }
}