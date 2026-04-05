package com.agentshell.app.db

import androidx.room.*

@Dao
interface SecretDao {
    @Query("SELECT * FROM secret_entry WHERE scope = :scope")
    suspend fun getByScope(scope: String): List<SecretEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(secret: SecretEntity)

    @Query("DELETE FROM secret_entry WHERE secretId = :secretId")
    suspend fun delete(secretId: String)
}
