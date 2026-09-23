package com.llamacpp.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        /** Adds the per-message stats columns (model, tokens, duration…). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN model TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN tokenCount INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN durationMs REAL")
                db.execSQL("ALTER TABLE messages ADD COLUMN promptTokenCount INTEGER")
            }
        }

        /** Adds tool-calling columns. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCallsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCallId TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolName TEXT")
            }
        }

        /** Adds the reasoning-summary column. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN thinkingSummary TEXT")
            }
        }

        /**
         * Replaces the reasoning-summary text with a measured thinking duration.
         * SQLite on minSdk cannot DROP COLUMN, so the table is rebuilt.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE messages_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "conversationId TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, " +
                        "reasoning TEXT NOT NULL, imagesJson TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "model TEXT, tokenCount INTEGER, durationMs REAL, promptTokenCount INTEGER, " +
                        "tokensPerSecond REAL, toolCallsJson TEXT NOT NULL, toolCallId TEXT, toolName TEXT, " +
                        "thinkingMs INTEGER, error TEXT)",
                )
                db.execSQL(
                    "INSERT INTO messages_new (id, conversationId, role, content, reasoning, imagesJson, createdAt, " +
                        "model, tokenCount, durationMs, promptTokenCount, tokensPerSecond, toolCallsJson, toolCallId, " +
                        "toolName, error) " +
                        "SELECT id, conversationId, role, content, reasoning, imagesJson, createdAt, model, tokenCount, " +
                        "durationMs, promptTokenCount, tokensPerSecond, toolCallsJson, toolCallId, toolName, error " +
                        "FROM messages",
                )
                db.execSQL("DROP TABLE messages")
                db.execSQL("ALTER TABLE messages_new RENAME TO messages")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
