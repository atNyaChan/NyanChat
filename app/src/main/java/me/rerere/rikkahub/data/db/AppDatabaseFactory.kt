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
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS message_search_cache(
                                text TEXT,
                                node_id TEXT,
                                message_id TEXT,
                                conversation_id TEXT,
                                title TEXT,
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
                            CREATE INDEX IF NOT EXISTS index_message_search_cache_update_at
                            ON message_search_cache(update_at)
                            """.trimIndent()
                        )

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
                        if (hasLegacyEntries) rebuildMessageSearchCache(db)
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            })
            .openHelperFactory(SQLiteConfiguration.openHelperFactory(context))
            .build()
}
