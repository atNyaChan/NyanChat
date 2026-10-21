package me.rerere.rikkahub.data.db.fts

import android.database.SQLException
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "LegacyMessageFts"
private const val LEGACY_TABLE = "message_fts"

/**
 * 删除旧版用 `simple` 分词器（libsimple 扩展）建的空 `message_fts` 兼容表。
 *
 * 该扩展已不再随 APK 打包，而这张表既不再被读写、也不再需要重建：
 * - 它留着会让 `PRAGMA integrity_check` 失败（FTS5 的 xIntegrity 需要分词器），从而让
 *   `DatabaseBackup` 的恢复校验拒绝存档；
 * - 降级回旧版时旧版的 `CREATE VIRTUAL TABLE IF NOT EXISTS` 会自行重建同名表，索引本来也要重建。
 *
 * requery 的 SQLite（3.50）在分词器缺失时仍可 `DROP` / `SELECT` / `VACUUM INTO`，
 * 只有完整性检查与 MATCH、写入需要分词器，因此这里直接删表即可。
 */
internal fun dropLegacyMessageFts(db: SupportSQLiteDatabase) {
    val exists = db.query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(LEGACY_TABLE),
    ).use { it.moveToFirst() }
    if (!exists) return
    try {
        db.execSQL("DROP TABLE $LEGACY_TABLE")
    } catch (e: SQLException) {
        // 极老的 SQLite 需要分词器才能 DROP，此时保留该表，不让数据库无法打开。
        Log.w(TAG, "Failed to drop the legacy $LEGACY_TABLE table", e)
    }
}
