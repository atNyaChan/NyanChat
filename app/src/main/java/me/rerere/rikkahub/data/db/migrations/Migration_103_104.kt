package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_103_104"

/**
 * 本 fork 103 -> 104：`workspaces` 增加 `storage_mounts` 列，
 * 对应上游的 `33 -> 34`（[Migration_33_34]）。
 */
val Migration_103_104 = object : Migration(103, 104) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 103 to 104 (workspace storage mounts)")
        db.beginTransaction()
        try {
            db.execSQL(
                "ALTER TABLE `workspaces` ADD COLUMN `storage_mounts` TEXT NOT NULL DEFAULT '[]'"
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 103 to 104 success")
        } finally {
            db.endTransaction()
        }
    }
}
