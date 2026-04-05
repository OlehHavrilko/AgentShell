package com.agentshell.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RunEntity::class, StepEntity::class, AuditEntity::class, SecretEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun stepDao(): StepDao
    abstract fun auditDao(): AuditDao
    abstract fun secretDao(): SecretDao

    companion object {
        @Volatile private var INSTANCE: AgentDatabase? = null

        fun getInstance(context: Context): AgentDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentDatabase::class.java,
                    "agentshell.db"
                ).build().also { INSTANCE = it }
            }
    }
}
