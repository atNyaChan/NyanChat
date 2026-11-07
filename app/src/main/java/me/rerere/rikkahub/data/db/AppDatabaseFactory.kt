package me.rerere.rikkahub.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.fts.dropLegacyMessageFts
import me.rerere.rikkahub.data.db.fts.rebuildMessageSearchCache
import me.rerere.rikkahub.data.db.migrations.Migration_100_101
import me.rerere.rikkahub.data.db.migrations.Migration_101_102
import me.rerere.rikkahub.data.db.migrations.Migration_30_31
import me.rerere.rikkahub.data.db.migrations.Migration_31_32
import me.rerere.rikkahub.data.db.migrations.Migration_32_102

/** Shared schema and legacy cleanup for the app and staged backup validation. */
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
                        // 旧版 `message_fts`（simple 分词器）依赖已移除的 libsimple 扩展，
                        // 留着会让备份校验的 integrity_check 失败，所以打开数据库时直接删掉；
                        // 旧版的索引本来也要重建。
                        dropLegacyMessageFts(db)
                        val cacheTableExists = db.query(
                            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'message_search_cache'"
                        ).use { it.moveToFirst() }
                        val cacheColumns = if (cacheTableExists) {
                            db.query("PRAGMA table_info(message_search_cache)").use { cursor ->
                                val nameIndex = cursor.getColumnIndex("name")
                                buildSet {
                                    while (cursor.moveToNext()) {
                                        if (nameIndex >= 0) add(cursor.getString(nameIndex))
                                    }
                                }
                            }
                        } else {
                            emptySet()
                        }
                        val cacheHasMessageAt = "message_at" in cacheColumns
                        val contentColumns = listOf("user_text", "body_text", "reasoning_text", "tool_text")
                        // 缓存只是派生数据：结构不符（旧版合并正文列 / 缺少分类列 / 缺消息时间）时整表丢掉重建，
                        // 不做逐列补列，也不保留已废弃的 `text` 列。
                        val cacheSchemaCurrent = cacheTableExists &&
                            cacheHasMessageAt &&
                            contentColumns.all(cacheColumns::contains) &&
                            "text" !in cacheColumns
                        if (cacheTableExists && !cacheSchemaCurrent) {
                            db.execSQL("DROP TABLE message_search_cache")
                        }
                        // title / update_at 是早期版本写入的会话标题与更新时间，现在不再写入也不再读取，列本身保留
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS message_search_cache(
                                node_id TEXT,
                                message_id TEXT,
                                conversation_id TEXT,
                                title TEXT,
                                message_at TEXT,
                                update_at TEXT,
                                user_text TEXT,
                                body_text TEXT,
                                reasoning_text TEXT,
                                tool_text TEXT
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
                        // update_at 已不再参与读取与排序，旧库留下的这索引只会拖慢写入
                        db.execSQL("DROP INDEX IF EXISTS index_message_search_cache_update_at")

                        // 旧版重建索引时不会写 message_at，若整表 message_at 全为空则静默重建补上消息时间。
                        val hasAnyMessageAt = cacheSchemaCurrent &&
                            db.query("SELECT 1 FROM message_search_cache WHERE message_at IS NOT NULL LIMIT 1")
                                .use { it.moveToFirst() }
                        val hasRowsWithoutMessageAt = cacheSchemaCurrent &&
                            !hasAnyMessageAt &&
                            db.query("SELECT 1 FROM message_search_cache LIMIT 1").use { it.moveToFirst() }

                        // 缓存表刚建/重建（包括刚恢复的上游旧库存档）或旧行缺消息时间时全量重建；
                        // 旧表 `message_fts` 里的内容从不作为重建来源。
                        if (!cacheSchemaCurrent || hasRowsWithoutMessageAt) {
                            rebuildMessageSearchCache(db)
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            })
            .openHelperFactory(SQLiteConfiguration.openHelperFactory())
            .build()
}
