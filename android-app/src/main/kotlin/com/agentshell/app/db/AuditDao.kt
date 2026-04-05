package com.agentshell.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_event WHERE runId = :runId ORDER BY timestamp ASC")
    fun eventsForRun(runId: String): Flow<List<AuditEntity>>

    @Insert
    suspend fun insert(event: AuditEntity)
}
