package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_33_34"

/**
 * 上游 RikkaHub 33 -> 34：`workspaces` 增加 `storage_mounts` 列。
 */
val Migration_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 33 to 34 (workspace storage mounts)")
        db.beginTransaction()
        try {
            db.execSQL(
                "ALTER TABLE `workspaces` ADD COLUMN `storage_mounts` TEXT NOT NULL DEFAULT '[]'"
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 33 to 34 success")
        } finally {
            db.endTransaction()
        }
    }
}
