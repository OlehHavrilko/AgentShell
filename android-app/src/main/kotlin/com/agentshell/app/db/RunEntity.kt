package com.agentshell.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_table")
data class RunEntity(
    @PrimaryKey val runId: String,
    val preset: String?,
    val status: String,
    val startTime: Long,
    val endTime: Long?,
    val tokenCost: Double
)
