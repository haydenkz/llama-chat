package com.llamacpp.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 4,
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

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
