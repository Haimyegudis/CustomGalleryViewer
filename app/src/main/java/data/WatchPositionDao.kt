package com.example.customgalleryviewer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchPositionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(position: WatchPositionEntity)

    @Query("SELECT * FROM watch_positions WHERE uri = :uri")
    suspend fun getPosition(uri: String): WatchPositionEntity?

    @Query("DELETE FROM watch_positions WHERE uri = :uri")
    suspend fun clearPosition(uri: String)

    @Query("SELECT * FROM watch_positions")
    fun getAllPositionsFlow(): Flow<List<WatchPositionEntity>>
}
