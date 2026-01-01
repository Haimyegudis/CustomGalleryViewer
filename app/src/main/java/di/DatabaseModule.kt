package com.example.customgalleryviewer.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.customgalleryviewer.data.AppDatabase
import com.example.customgalleryviewer.data.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // הוספת עמודה חדשה thumbnailUri
            database.execSQL("ALTER TABLE playlists ADD COLUMN thumbnailUri TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "gallery_db"
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration() // במקרה של בעיה, תמחק ותיצור מחדש
            .build()
    }

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao {
        return database.playlistDao()
    }
}