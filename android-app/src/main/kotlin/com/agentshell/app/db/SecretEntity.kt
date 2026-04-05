package com.agentshell.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secret_entry")
data class SecretEntity(
    @PrimaryKey val secretId: String,
    val encryptedValue: ByteArray,
    val ttlEpochSec: Long,
    val scope: String
)
