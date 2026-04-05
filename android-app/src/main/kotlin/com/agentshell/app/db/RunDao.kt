package com.agentshell.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Query("SELECT * FROM run_table ORDER BY startTime DESC")
    fun allRuns(): Flow<List<RunEntity>>

    @Query("SELECT * FROM run_table WHERE runId = :id")
    suspend fun getById(id: String): RunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: RunEntity)

    @Update
    suspend fun update(run: RunEntity)

    @Delete
    suspend fun delete(run: RunEntity)
}
