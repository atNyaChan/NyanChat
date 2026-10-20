package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 附件登记表从 `managed_files`（逐文件一行、自带 created_at 列）换成 `managed_files_cas`
 * （逐 sha256 一行，时间与展示名写进 usages）。
 *
 * 磁盘上 `upload/<uuid.ext>` 到 `upload/<sha256>` 的转换需要读文件算哈希，不是 SQL 能做的，
 * 只在导入旧存档时由 `ManagedFilesArchive.convertLegacyDatabase` 完成。App 自身禁止原地升级：
 * `AppDatabaseFactory` 在 App 运行路径下不注册本迁移，读到 31（或 30）的旧库会直接由 Room 报错；
 * 本迁移只在备份校验/恢复时注册（`allowLegacyMigration = true`），保证表结构可用且可重复执行。
 *
 * 上游 RikkaHub 当前的 schema 版本是 31（已带 `usage_record` 与 `translation_history`），30 的存档
 * 会先走 [Migration_30_31] 补上 `usage_record` 再到本迁移，之后由 [Migration_100_101] 补完本 fork 的
 * 版本号。最低支持 schema 版本为 30，低于 30 的存档不再迁移。
 */
val Migration_31_100 = object : Migration(31, 100) {
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
