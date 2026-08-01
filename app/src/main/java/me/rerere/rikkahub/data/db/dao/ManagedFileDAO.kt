package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity

data class ManagedFileWithReference(
    @Embedded val file: ManagedFileEntity,
    val conversationId: String?,
    val nodeId: String?,
)

data class AttachmentReference(
    val nodeId: String,
    val attachmentUrl: String,
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
        SELECT mn.id AS nodeId, json_extract(part.value, '$.url') AS attachmentUrl
        FROM message_node mn
        JOIN json_each(mn.messages) message
        JOIN json_tree(message.value, '$.parts') part
        WHERE mn.conversation_id = :conversationId
          AND part.type = 'object'
          AND json_extract(part.value, '$.url') IS NOT NULL
        """
    )
    suspend fun listAttachmentReferences(conversationId: String): List<AttachmentReference>

    @Query("DELETE FROM managed_files WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM managed_files WHERE relative_path = :relativePath")
    suspend fun deleteByPath(relativePath: String): Int

    @Query("DELETE FROM managed_files WHERE folder = :folder")
    suspend fun deleteByFolder(folder: String): Int
}
