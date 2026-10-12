package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 附件登记表从 `managed_files`（逐文件一行、自带 created_at 列）换成 `managed_files_cas`
 * （逐 sha256 一行，时间与展示名写进 usages）。
 *
 * 磁盘上 `upload/<uuid.ext>` 到 `upload/<sha256>` 的转换需要读文件算哈希，不是 SQL 能做的，
 * 由导入流程（`ManagedFilesArchive.convertLegacyDatabase`）和启动时的
 * `FilesManager.syncManagedFiles` 完成；本迁移只保证表结构可用且可重复执行。
 */
val Migration_29_100 = object : Migration(29, 100) {
    override fun migrate(db: SupportSQLiteDatabase) {
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
    }
}
