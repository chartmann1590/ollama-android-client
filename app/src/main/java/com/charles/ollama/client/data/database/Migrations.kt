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
}
