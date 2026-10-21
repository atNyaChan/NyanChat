package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_101_102"

/**
 * 新增工作区类型与 SSH 连接配置列，支持远程 SSH 工作区。
 *
 * 旧工作区都是本机 PRoot 工作区，`type` 默认 `LOCAL`；`ssh_config` 为空表示没有远程配置。
 * 对应上游的 `31 -> 32`；上游存档由 [Migration_31_32] 补齐同样的列后直接走 [Migration_32_102]。
 */
val Migration_101_102 = object : Migration(101, 102) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 101 to 102 (ssh workspaces)")
        db.beginTransaction()
        try {
            db.execSQL("ALTER TABLE `workspaces` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'LOCAL'")
            db.execSQL("ALTER TABLE `workspaces` ADD COLUMN `ssh_config` TEXT")
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 101 to 102 success")
        } finally {
            db.endTransaction()
        }
    }
}
