package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.FolderDAO
import me.rerere.rikkahub.data.db.entity.FolderEntity
import me.rerere.rikkahub.data.model.Folder
import java.time.Instant
import kotlin.uuid.Uuid

class FolderRepository(
    private val folderDAO: FolderDAO,
    private val conversationDAO: ConversationDAO,
) {
    fun getFoldersOfAssistant(assistantId: Uuid): Flow<List<Folder>> {
        return folderDAO.getFoldersOfAssistant(assistantId.toString())
            .map { list -> list.map { it.toFolder() } }
    }

    suspend fun getFolderById(id: Uuid): Folder? {
        return folderDAO.getFolderById(id.toString())?.toFolder()
    }

    suspend fun createFolder(assistantId: Uuid, name: String): Folder {
        val folder = Folder(
            assistantId = assistantId,
            name = name,
            createAt = Instant.now(),
        )
        folderDAO.insert(folder.toEntity())
        return folder
    }

    suspend fun renameFolder(id: Uuid, name: String) {
        folderDAO.rename(id.toString(), name)
    }

    /**
     * 删除文件夹，先把归属该文件夹的会话 folder_id 清空，再删除文件夹本身（不影响会话）。
     */
    suspend fun deleteFolder(id: Uuid) {
        conversationDAO.clearFolder(id.toString())
        folderDAO.deleteById(id.toString())
    }

    /**
     * 移动文件夹在助手内分组中的排序位置。
     *
     * @param forward true 表示往前移（更靠前），false 表示往后移（更靠后）。
     *                已处于最前/最后时不做任何操作。
     */
    suspend fun moveFolder(id: Uuid, forward: Boolean) {
        val target = folderDAO.getFolderById(id.toString()) ?: return
        val folders = folderDAO.getFoldersOfAssistant(target.assistantId).first()
        val index = folders.indexOfFirst { it.id == target.id }
        if (index < 0) return
        val newIndex = if (forward) index - 1 else index + 1
        if (newIndex < 0 || newIndex >= folders.size) return

        val reordered = folders.toMutableList().apply { add(newIndex, removeAt(index)) }
        reordered.forEachIndexed { i, folder ->
            folderDAO.updateSortIndex(folder.id, i)
        }
    }
}

private fun FolderEntity.toFolder(): Folder = Folder(
    id = Uuid.parse(id),
    assistantId = Uuid.parse(assistantId),
    name = name,
    sortIndex = sortIndex,
    createAt = Instant.ofEpochMilli(createAt),
)

private fun Folder.toEntity(): FolderEntity = FolderEntity(
    id = id.toString(),
    assistantId = assistantId.toString(),
    name = name,
    sortIndex = sortIndex,
    createAt = createAt.toEpochMilli(),
)
