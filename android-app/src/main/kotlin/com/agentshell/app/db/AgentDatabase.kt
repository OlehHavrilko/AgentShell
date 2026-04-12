package com.agentshell.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RunEntity::class,
        StepEntity::class,
        AuditEntity::class,
        SecretEntity::class,
        MemoryEntity::class,
        MessageEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun stepDao(): StepDao
    abstract fun auditDao(): AuditDao
    abstract fun secretDao(): SecretDao
    abstract fun memoryDao(): MemoryDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: AgentDatabase? = null

        // v1→v2: payload column changed to nullable (recreate table since SQLite
        //        does not support ALTER COLUMN).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS audit_event_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        runId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        payload TEXT
                    )"""
                )
                db.execSQL(
                    "INSERT INTO audit_event_new SELECT id, runId, type, timestamp, payload FROM audit_event"
                )
                db.execSQL("DROP TABLE audit_event")
                db.execSQL("ALTER TABLE audit_event_new RENAME TO audit_event")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS memory_entry (
                        memoryId TEXT NOT NULL,
                        runId TEXT NOT NULL,
                        agentId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        score REAL NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        decayHalfLifeMs INTEGER NOT NULL,
                        PRIMARY KEY(memoryId)
                    )"""
                )

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS chat_message (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )"""
                )
            }
        }

        fun getInstance(context: Context): AgentDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentDatabase::class.java,
                    "agentshell.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
