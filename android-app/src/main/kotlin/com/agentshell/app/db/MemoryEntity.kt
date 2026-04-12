package com.agentshell.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_entry")
data class MemoryEntity(
    @PrimaryKey val memoryId: String,
    val runId: String,
    val agentId: String,
    val text: String,
    val tags: String,
    val score: Double,
    val createdAtEpochMs: Long,
    val decayHalfLifeMs: Long,
)