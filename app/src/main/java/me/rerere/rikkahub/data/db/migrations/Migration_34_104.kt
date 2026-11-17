package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_34_104"

/**
 * 上游 RikkaHub 34 -> 本 fork 的 104。
 *
 * 34 与 104 的表结构只差附件登记：34 仍是 `managed_files`，104 是 `managed_files_cas`
 * （`workspaces.storage_mounts` 已由 [Migration_33_34] 建好）。
 * `upload/<uuid.ext>` 到 `upload/<sha256>` 的重命名、消息与设置里的附件 url 改写需要读文件算哈希，
 * 由 `ManagedFilesArchive.convertLegacyDatabase` 在打开 Room 之前完成；本迁移只负责补齐表结构，
 * 且可重复执行（存档已经被转换过时同样安全）。
 */
val Migration_34_104 = object : Migration(34, 104) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 34 to 104 (attachment cas)")
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
            Log.i(TAG, "migrate: migrate from 34 to 104 success")
        } finally {
            db.endTransaction()
        }
    }
}
