package com.charles.ollama.client.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE server_configs ADD COLUMN backendType TEXT NOT NULL DEFAULT 'OLLAMA'")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS installed_litert_models (
                    catalogId TEXT NOT NULL PRIMARY KEY,
                    localFilePath TEXT NOT NULL,
                    expectedBytes INTEGER NOT NULL DEFAULT 0,
                    installedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_threads ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chat_threads ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS prompt_presets (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    text TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Per-thread label/folder + optional Ollama generation parameters.
            db.execSQL("ALTER TABLE chat_threads ADD COLUMN label TEXT")
            db.execSQL("ALTER TABLE chat_threads ADD COLUMN temperature REAL")
            db.execSQL("ALTER TABLE chat_threads ADD COLUMN topP REAL")
            db.execSQL("ALTER TABLE chat_threads ADD COLUMN topK INTEGER")
            db.execSQL("ALTER TABLE chat_threads ADD COLUMN numCtx INTEGER")
            db.execSQL("ALTER TABLE chat_threads ADD COLUMN seed INTEGER")
            // Per-message generation metrics from Ollama.
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN evalCount INTEGER")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN evalDurationNs INTEGER")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN promptEvalCount INTEGER")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN totalDurationNs INTEGER")
        }
    }
}
