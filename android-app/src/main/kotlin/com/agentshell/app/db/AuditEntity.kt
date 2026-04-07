package com.agentshell.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_event")
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val type: String,
    val timestamp: Long,
    val payload: String?
)
