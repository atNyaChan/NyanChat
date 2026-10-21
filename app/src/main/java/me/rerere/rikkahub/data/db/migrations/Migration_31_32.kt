package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_31_32"

/**
 * 上游 RikkaHub 31 -> 32：工作区新增类型与 SSH 连接配置列，支持远程 SSH 工作区。
 *
 * 旧工作区都是本机 PRoot 工作区，`type` 默认 `LOCAL`；`ssh_config` 为空表示没有远程配置。
 * 本迁移只用于恢复上游 31 的存档；本 fork 自己的库从 101 -> 102 走 [Migration_101_102]。
 */
val Migration_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 31 to 32 (ssh workspaces)")
        db.beginTransaction()
        try {
            addColumnIfMissing(db, "workspaces", "type", "TEXT NOT NULL DEFAULT 'LOCAL'")
            addColumnIfMissing(db, "workspaces", "ssh_config", "TEXT")
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 31 to 32 success")
        } finally {
            db.endTransaction()
        }
    }
}

/** 迁移可能需要重复执行（例如存档已部分转换过），已存在的列直接跳过。 */
private fun addColumnIfMissing(
    db: SupportSQLiteDatabase,
    table: String,
    column: String,
    definition: String,
) {
    val exists = db.query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        var found = false
        while (cursor.moveToNext()) {
            if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                found = true
                break
            }
        }
        found
    }
    if (!exists) {
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
    }
}
