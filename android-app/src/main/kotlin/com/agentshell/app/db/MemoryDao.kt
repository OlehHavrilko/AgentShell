package com.agentshell.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entry: MemoryEntity)

    @Query("SELECT * FROM memory_entry ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_entry WHERE text LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun search(query: String, limit: Int): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_entry ORDER BY createdAtEpochMs DESC")
    fun getAll(): List<MemoryEntity>

    @Query("SELECT * FROM memory_entry WHERE runId = :runId ORDER BY createdAtEpochMs DESC")
    fun getByRunId(runId: String): List<MemoryEntity>

    @Query("DELETE FROM memory_entry")
    fun clear()
}