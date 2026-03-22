package com.example.customgalleryviewer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultEntity): Long

    @Query("SELECT * FROM vault_items ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getById(id: Long): VaultEntity?

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun getCount(): Int
}
