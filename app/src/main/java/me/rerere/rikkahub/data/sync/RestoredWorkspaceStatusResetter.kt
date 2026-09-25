package me.rerere.rikkahub.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import me.rerere.workspace.WorkspaceShellStatus

internal object RestoredWorkspaceStatusResetter {
    fun resetAfterRestore(context: Context): Int {
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

            // 存档未携带工作区环境时，仅重置本地确实缺少 Rootfs 的工作区；
            // 与本地已有工作区 id 相同且 Rootfs 仍完整的工作区保持可用，避免被完整性检查标记为损坏。
            val workspacesDir = File(context.filesDir, WORKSPACES_DIR)
            val rows = database.rawQuery(
                "SELECT id, root, shell_status FROM workspaces",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            WorkspaceRow(
                                id = cursor.getString(0),
                                root = cursor.getString(1),
                                shellStatus = cursor.getString(2),
                            )
                        )
                    }
                }
            }

            var changed = 0
            for (row in rows) {
                val id = row.id ?: continue
                val targetStatus = if (row.root != null && hasLocalRootfs(workspacesDir, row.root)) {
                    WorkspaceShellStatus.READY.name
                } else {
                    WorkspaceShellStatus.DISABLED.name
                }
                if (row.shellStatus != targetStatus) {
                    database.execSQL(
                        "UPDATE workspaces SET shell_status = ? WHERE id = ?",
                        arrayOf(targetStatus, id),
                    )
                    changed++
                }
            }
            return changed
        }
    }

    private fun hasLocalRootfs(workspacesDir: File, root: String): Boolean {
        if (root == "." || root == ".." || !root.matches(ROOT_PATTERN)) return false
        return File(File(workspacesDir, root), "linux/bin/sh").isFile
    }

    private data class WorkspaceRow(
        val id: String?,
        val root: String?,
        val shellStatus: String?,
    )

    private const val WORKSPACES_DIR = "workspaces"
    private val ROOT_PATTERN = Regex("[A-Za-z0-9._-]+")
}
