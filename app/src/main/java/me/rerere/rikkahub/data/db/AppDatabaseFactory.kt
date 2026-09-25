package me.rerere.rikkahub.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.fts.rebuildMessageSearchCache

/** Shared schema and extensions for the app and staged backup validation. */
internal object AppDatabaseFactory {
    fun create(context: Context, name: String = SQLiteConfiguration.DATABASE_NAME): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.beginTransaction()
                    try {
                        val cacheTableExists = db.query(
                            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'message_search_cache'"
                        ).use { it.moveToFirst() }
                        val cacheHasMessageAt = cacheTableExists &&
                            db.query("PRAGMA table_info(message_search_cache)").use { cursor ->
                                val nameIndex = cursor.getColumnIndex("name")
                                var found = false
                                while (cursor.moveToNext()) {
                                    if (nameIndex >= 0 && cursor.getString(nameIndex) == "message_at") {
                                        found = true
                                        break
                                    }
                                }
                                found
                            }
                        if (cacheTableExists && !cacheHasMessageAt) {
                            // Additive migration keeps the legacy update_at column so older builds can still read the cache.
                            db.execSQL("ALTER TABLE message_search_cache ADD COLUMN message_at TEXT")
                        }
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS message_search_cache(
                                text TEXT,
                                node_id TEXT,
                                message_id TEXT,
                                conversation_id TEXT,
                                title TEXT,
                                message_at TEXT,
                                update_at TEXT
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE INDEX IF NOT EXISTS index_message_search_cache_conversation_id
                            ON message_search_cache(conversation_id)
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE INDEX IF NOT EXISTS index_message_search_cache_message_at
                            ON message_search_cache(message_at)
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE INDEX IF NOT EXISTS index_message_search_cache_update_at
                            ON message_search_cache(update_at)
                            """.trimIndent()
                        )

                        // 旧版重建索引时不会写 message_at，若整表 message_at 全为空则静默重建补上消息时间。
                        val hasAnyMessageAt = cacheHasMessageAt &&
                            db.query("SELECT 1 FROM message_search_cache WHERE message_at IS NOT NULL LIMIT 1")
                                .use { it.moveToFirst() }
                        val hasRowsWithoutMessageAt = cacheHasMessageAt &&
                            !hasAnyMessageAt &&
                            db.query("SELECT 1 FROM message_search_cache LIMIT 1").use { it.moveToFirst() }

                        val legacyTableSql = db.query(
                            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'message_fts'"
                        ).use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                        val hasLegacyEntries = if (legacyTableSql != null) {
                            db.query(
                                "SELECT 1 FROM message_fts LIMIT 1"
                            ).use { it.moveToFirst() }
                        } else {
                            false
                        }

                        if (legacyTableSql != null) db.execSQL("DROP TABLE message_fts")
                        db.execSQL(
                            """
                            CREATE VIRTUAL TABLE message_fts USING fts5(
                                text,
                                node_id UNINDEXED,
                                message_id UNINDEXED,
                                conversation_id UNINDEXED,
                                title UNINDEXED,
                                update_at UNINDEXED,
                                tokenize = 'simple'
                            )
                            """.trimIndent()
                        )
                        if (hasLegacyEntries ||
                            (cacheTableExists && !cacheHasMessageAt) ||
                            hasRowsWithoutMessageAt
                        ) {
                            rebuildMessageSearchCache(db)
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            })
            .openHelperFactory(SQLiteConfiguration.openHelperFactory(context))
            .build()
}
