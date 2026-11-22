package me.rerere.rikkahub.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabase
import me.rerere.rikkahub.data.db.fts.dropLegacyMessageFts
import java.io.File

internal object DatabaseBackup {
    const val ARCHIVE_DATABASE = "rikka_hub.db"

    /** VACUUM INTO includes committed WAL contents in a consistent, standalone snapshot. */
    fun createSnapshot(database: SupportSQLiteDatabase, destination: File) {
        database.execSQL("VACUUM main INTO ?", arrayOf(destination.absolutePath))
    }

    /**
     * 不经过 Room，直接从库文件拍快照：Room 打不开库（迁移失败、装了更旧的版本）时也能导出。
     * 快照保留盘上原来的 schema 版本，恢复时再走迁移。只读打开，不会改动库文件（日志模式、android_metadata 都不碰）。
     */
    fun createFileSnapshot(databaseFile: File, destination: File) {
        check(!destination.exists()) { "Backup snapshot already exists" }
        require(databaseFile.isFile) { "Database file not found" }
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY) {
            error("Database is corrupt")
        }.use { database ->
            database.execSQL("VACUUM main INTO ?", arrayOf(destination.absolutePath))
        }
    }

    /** Only opens the staged copy, which must already be a standalone snapshot. */
    fun normalize(databaseFile: File) {
        require(databaseFile.isFile && databaseFile.length() > 0) { "Backup database is missing or empty" }
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE) {
            error("Backup database is corrupt")
        }.use { database ->
            // 存档里的 `message_fts` 是旧版用已移除的 `simple` 分词器建的，分词器缺失时
            // `integrity_check` 会失败，所以先删掉这张只承载旧版索引的表（旧索引本来也会被丢弃）。
            dropLegacyMessageFts(database)
            checkpoint(database)
            database.query("PRAGMA integrity_check").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                    "Backup database failed its integrity check"
                }
            }
        }
        removeSidecars(databaseFile)
    }

    fun checkpoint(database: SupportSQLiteDatabase) {
        database.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) {
                "Could not checkpoint the backup database"
            }
        }
    }

    fun removeSidecars(databaseFile: File) {
        // All connections must be closed and committed WAL data checkpointed before removal.
        check(File(databaseFile.path + "-wal").length() == 0L) { "Backup WAL was not fully checkpointed" }
        for (suffix in listOf("-wal", "-shm")) {
            val sidecar = File(databaseFile.path + suffix)
            check(!sidecar.exists() || sidecar.delete()) { "Could not remove staged database sidecar" }
        }
    }
}
