package com.agentshell.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Query("SELECT * FROM step_table WHERE runId = :runId ORDER BY stepIndex ASC")
    fun stepsForRun(runId: String): Flow<List<StepEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: StepEntity)

    @Delete
    suspend fun delete(step: StepEntity)
}
