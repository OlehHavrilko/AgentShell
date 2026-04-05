package com.agentshell.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "step_table")
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val stepIndex: Int,
    val toolId: String,
    val status: String,
    val startTime: Long,
    val endTime: Long?,
    val input: String?,
    val output: String?
)
