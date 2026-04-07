package com.agentshell.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RunEntity::class, StepEntity::class, AuditEntity::class, SecretEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun stepDao(): StepDao
    abstract fun auditDao(): AuditDao
    abstract fun secretDao(): SecretDao

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

        fun getInstance(context: Context): AgentDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentDatabase::class.java,
                    "agentshell.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
