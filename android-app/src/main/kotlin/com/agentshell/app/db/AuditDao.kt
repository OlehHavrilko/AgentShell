package com.agentshell.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_event WHERE runId = :runId ORDER BY timestamp ASC")
    fun eventsForRun(runId: String): Flow<List<AuditEntity>>

    @Query("SELECT * FROM audit_event ORDER BY timestamp DESC LIMIT :limit")
    fun recentEvents(limit: Int): Flow<List<AuditEntity>>

    @Query("SELECT * FROM audit_event WHERE payload LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 100")
    fun searchEvents(query: String): Flow<List<AuditEntity>>

    @Insert
    suspend fun insert(event: AuditEntity)
}
