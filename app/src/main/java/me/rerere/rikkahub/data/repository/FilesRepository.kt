package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import androidx.sqlite.db.SimpleSQLiteQuery
import me.rerere.rikkahub.data.db.dao.ManagedFileDAO
import me.rerere.rikkahub.data.db.dao.ManagedFileWithReference
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity

class FilesRepository(
    private val dao: ManagedFileDAO,
) {
    suspend fun insert(file: ManagedFileEntity): ManagedFileEntity {
        val id = dao.insert(file)
        return file.copy(id = id)
    }

    suspend fun update(file: ManagedFileEntity) {
        dao.update(file)
    }

    suspend fun getById(id: Long): ManagedFileEntity? = dao.getById(id)

    suspend fun getByPath(relativePath: String): ManagedFileEntity? = dao.getByPath(relativePath)

    fun listByFolder(folder: String): Flow<List<ManagedFileEntity>> = dao.listByFolder(folder)

    suspend fun listWithReferencesByFolder(folder: String): List<ManagedFileWithReference> =
        dao.listWithReferencesByFolderRaw(
            SimpleSQLiteQuery(
                """
                WITH attachment_references AS (
                    SELECT mn.id AS nodeId,
                           mn.conversation_id AS conversationId,
                           json_extract(part.value, '$.url') AS attachmentUrl,
                           conversation.update_at AS conversationUpdateAt
                    FROM message_node mn
                    JOIN conversationentity conversation ON conversation.id = mn.conversation_id
                    JOIN json_each(mn.messages) message
                    JOIN json_tree(message.value, '$.parts') part
                    WHERE part.type = 'object'
                      AND json_extract(part.value, '$.url') IS NOT NULL
                ),
                latest_references AS (
                    SELECT mf.id AS fileId,
                           reference.conversationId AS conversationId,
                           reference.nodeId AS nodeId,
                           MAX(reference.conversationUpdateAt) AS latestUpdate
                    FROM managed_files mf
                    JOIN attachment_references reference
                      ON instr(
                           reference.attachmentUrl,
                           substr(mf.relative_path, instr(mf.relative_path, '/') + 1)
                         ) > 0
                    WHERE mf.folder = ?
                    GROUP BY mf.id
                )
                SELECT mf.*, reference.conversationId, reference.nodeId
                FROM managed_files mf
                LEFT JOIN latest_references reference ON reference.fileId = mf.id
                WHERE mf.folder = ?
                ORDER BY mf.created_at DESC
                """.trimIndent(),
                arrayOf(folder, folder),
            )
        )

    suspend fun deleteById(id: Long): Int = dao.deleteById(id)

    suspend fun deleteByPath(relativePath: String): Int = dao.deleteByPath(relativePath)

    suspend fun deleteByFolder(folder: String): Int = dao.deleteByFolder(folder)
}
