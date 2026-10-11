package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity

data class AttachmentReference(
    val nodeId: String,
    val messageId: String,
    val attachmentUrl: String,
)

/** 消息库里一条带文件名的附件引用: [attachmentUrl] 为附件 url, [fileName] 为 part 记录的展示文件名。 */
data class AttachmentMessageFileName(
    val attachmentUrl: String,
    val fileName: String,
)

@Dao
interface ManagedFileDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: ManagedFileEntity): Long

    @Update
    suspend fun update(file: ManagedFileEntity)

    @Query("SELECT * FROM managed_files WHERE id = :id")
    suspend fun getById(id: Long): ManagedFileEntity?

    @Query("SELECT * FROM managed_files WHERE relative_path = :relativePath")
    suspend fun getByPath(relativePath: String): ManagedFileEntity?

    @Query("SELECT * FROM managed_files WHERE folder = :folder ORDER BY created_at DESC")
    fun listByFolder(folder: String): Flow<List<ManagedFileEntity>>

    @Query("SELECT id FROM conversationentity ORDER BY update_at DESC")
    suspend fun listConversationIdsByLatest(): List<String>

    @Query(
        """
        SELECT mn.id AS nodeId,
               IFNULL(json_extract(message.value, '$.id'), '') AS messageId,
               json_extract(part.value, '$.url') AS attachmentUrl
        FROM message_node mn
        JOIN json_each(mn.messages) message
        JOIN json_tree(message.value, '$.parts') part
        WHERE mn.conversation_id = :conversationId
          AND part.type = 'object'
          AND json_extract(part.value, '$.url') IS NOT NULL
        """
    )
    suspend fun listAttachmentReferences(conversationId: String): List<AttachmentReference>

    /**
     * 列出消息库里全部带 `fileName` 的附件 part, 用于在 `managed_files` 缺少展示名时回退读取。
     * 只统计真正记录了文件名的 part (目前是文档), 图片/视频/音频没有该字段。
     */
    @Query(
        """
        SELECT DISTINCT
               json_extract(part.value, '$.url') AS attachmentUrl,
               json_extract(part.value, '$.fileName') AS fileName
        FROM message_node mn
        JOIN json_each(mn.messages) message
        JOIN json_tree(message.value, '$.parts') part
        WHERE part.type = 'object'
          AND json_extract(part.value, '$.url') IS NOT NULL
          AND json_extract(part.value, '$.fileName') IS NOT NULL
        """
    )
    suspend fun listAttachmentMessageFileNames(): List<AttachmentMessageFileName>

    @Query("DELETE FROM managed_files WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM managed_files WHERE relative_path = :relativePath")
    suspend fun deleteByPath(relativePath: String): Int

    @Query("DELETE FROM managed_files WHERE folder = :folder")
    suspend fun deleteByFolder(folder: String): Int
}
