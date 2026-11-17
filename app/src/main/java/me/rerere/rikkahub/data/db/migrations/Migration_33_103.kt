package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_33_103"

/**
 * 上游 RikkaHub 33 -> 本 fork 的 103。
 *
 * 33 与 103 的表结构只差附件登记：33 仍是 `managed_files`，103 是 `managed_files_cas`
 * （`message_node` 的复合索引已由 [Migration_32_33] 建好）。
 * `upload/<uuid.ext>` 到 `upload/<sha256>` 的重命名、消息与设置里的附件 url 改写需要读文件算哈希，
 * 由 `ManagedFilesArchive.convertLegacyDatabase` 在打开 Room 之前完成；本迁移只负责补齐表结构，
 * 且可重复执行（存档已经被转换过时同样安全）。
 */
val Migration_33_103 = object : Migration(33, 103) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 33 to 103 (attachment cas)")
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `managed_files_cas` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sha256` TEXT NOT NULL, " +
                    "`mime_type` TEXT NOT NULL, " +
                    "`size_bytes` INTEGER NOT NULL, " +
                    "`usages` TEXT NOT NULL DEFAULT '[]')"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_managed_files_cas_sha256` ON `managed_files_cas` (`sha256`)"
            )
            db.execSQL("DROP TABLE IF EXISTS `managed_files`")
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 33 to 103 success")
        } finally {
            db.endTransaction()
        }
    }
}
