package me.rerere.rikkahub.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceType
import java.io.File

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
            // 旧存档没有 type 列, 其中的工作区都是本地工作区
            val hasTypeColumn = database.rawQuery("PRAGMA table_info(workspaces)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == "type") {
                        found = true
                        break
                    }
                }
                found
            }
            val typeExpression = if (hasTypeColumn) "type" else "'LOCAL'"
            val rows = database.rawQuery(
                "SELECT id, root, shell_status, $typeExpression FROM workspaces",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            WorkspaceRow(
                                id = cursor.getString(0),
                                root = cursor.getString(1),
                                shellStatus = cursor.getString(2),
                                type = cursor.getString(3),
                            )
                        )
                    }
                }
            }

            var changed = 0
            for (row in rows) {
                val id = row.id ?: continue
                // 远程工作区没有本机 Rootfs, 状态与本地目录无关
                if (row.type == WorkspaceType.SSH.name) continue
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
        if (!WorkspaceManager.isValidWorkspaceRoot(root)) return false
        return WorkspaceManager.isUsableRootfs(File(File(workspacesDir, root), "linux"))
    }

    private data class WorkspaceRow(
        val id: String?,
        val root: String?,
        val shellStatus: String?,
        val type: String?,
    )

    private const val WORKSPACES_DIR = "workspaces"
}
