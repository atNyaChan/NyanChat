package me.rerere.rikkahub.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import me.rerere.workspace.WorkspaceShellStatus

internal object RestoredZipWorkspaceStatusResetter {
    fun resetAfterCompatibleZipRestore(context: Context): Int {
        val databaseFile = context.getDatabasePath("rikka_hub")
        if (!databaseFile.exists()) return 0

        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            val hasWorkspaceTable = database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'workspaces'",
                null,
            ).use { it.moveToFirst() }
            if (!hasWorkspaceTable) return 0

            database.execSQL(
                "UPDATE workspaces SET shell_status = ?",
                arrayOf(WorkspaceShellStatus.DISABLED.name),
            )
            return database.rawQuery("SELECT changes()", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }
}
