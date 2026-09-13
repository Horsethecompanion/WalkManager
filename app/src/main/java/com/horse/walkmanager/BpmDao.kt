package com.horse.walkmanager

import androidx.room.*

@Dao
interface BpmDao {
    @Query("SELECT * FROM bpm_cache WHERE id = :id LIMIT 1")
    suspend fun getBpm(id: String): BpmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBpm(bpm: BpmEntity)

    @Query("DELETE FROM bpm_cache")
    suspend fun clearAll()
}
