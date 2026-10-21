package me.rerere.rikkahub.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.fts.rebuildMessageSearchCache
import me.rerere.rikkahub.data.db.migrations.Migration_100_101
import me.rerere.rikkahub.data.db.migrations.Migration_101_102
import me.rerere.rikkahub.data.db.migrations.Migration_30_31
import me.rerere.rikkahub.data.db.migrations.Migration_31_32
import me.rerere.rikkahub.data.db.migrations.Migration_32_102

/** Shared schema and extensions for the app and staged backup validation. */
internal object AppDatabaseFactory {
    /**
     * 构建数据库。[allowLegacyMigration] 为 false（App 自身）时不注册上游旧版本的迁移：
     * 读到 30 / 31 / 32 的旧库会直接由 Room 报错，禁止原地升级。只有恢复存档时先在暂存库里
     * 转换到新 schema，才允许打开并迁移旧版本，因此备份校验传 true。低于 30 的旧库不再有任何迁移链。
     *
     * 上游存档链路是 30 -> 31 -> 32 -> 102（上游 31 的存档走 31 -> 32 -> 102，上游 32 直接走 32 -> 102）；
     * 100 -> 101（用量流水表）与 101 -> 102（SSH 工作区列）总是注册：本 fork 自己的旧库停在 100 / 101，
     * 正常升级也要走这两步。
     */
    fun create(
        context: Context,
        name: String = SQLiteConfiguration.DATABASE_NAME,
        allowLegacyMigration: Boolean = false,
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(Migration_100_101, Migration_101_102)
            .apply {
                if (allowLegacyMigration) {
                    addMigrations(Migration_30_31, Migration_31_32, Migration_32_102)
                }
            }
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
