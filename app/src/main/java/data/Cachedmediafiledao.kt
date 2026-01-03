package com.example.customgalleryviewer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedMediaFileDao {

    @Query("SELECT * FROM cached_media_files WHERE playlistId = :playlistId ORDER BY fileName ASC")
    fun getCachedFilesForPlaylist(playlistId: Long): Flow<List<CachedMediaFileEntity>>

    @Query("SELECT * FROM cached_media_files WHERE playlistId = :playlistId")
    suspend fun getCachedFilesSync(playlistId: Long): List<CachedMediaFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<CachedMediaFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: CachedMediaFileEntity)

    @Query("DELETE FROM cached_media_files WHERE playlistId = :playlistId")
    suspend fun clearCacheForPlaylist(playlistId: Long)

    @Query("DELETE FROM cached_media_files WHERE playlistId = :playlistId AND uriString NOT IN (:currentUris)")
    suspend fun removeDeletedFiles(playlistId: Long, currentUris: List<String>)

    @Query("SELECT COUNT(*) FROM cached_media_files WHERE playlistId = :playlistId")
    suspend fun getCachedFileCount(playlistId: Long): Int

    @Query("SELECT lastModified FROM cached_media_files WHERE playlistId = :playlistId ORDER BY lastModified DESC LIMIT 1")
    suspend fun getLastCacheTime(playlistId: Long): Long?
}