package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ManagedFileCasEntity

data class AttachmentReference(
    val nodeId: String,
    val messageId: String,
    val attachmentUrl: String,
    val fileName: String?,
    /** 该消息的 `createdAt`（kotlinx LocalDateTime 序列化后的字符串），解析失败时为 null。 */
    val messageAt: String?,
)

@Dao
interface ManagedFileDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(file: ManagedFileCasEntity): Long

    @Update
    suspend fun update(file: ManagedFileCasEntity)

    @Query("SELECT * FROM managed_files_cas WHERE id = :id")
    suspend fun getById(id: Long): ManagedFileCasEntity?

    @Query("SELECT * FROM managed_files_cas WHERE sha256 = :sha256")
    suspend fun getBySha(sha256: String): ManagedFileCasEntity?

    @Query("SELECT * FROM managed_files_cas")
    suspend fun listAllNow(): List<ManagedFileCasEntity>

    @Query("SELECT * FROM managed_files_cas")
    fun listAll(): Flow<List<ManagedFileCasEntity>>

    @Query("SELECT id FROM conversationentity ORDER BY update_at DESC")
    suspend fun listConversationIdsByLatest(): List<String>

    @Query(
        """
        SELECT DISTINCT
               mn.id AS nodeId,
               IFNULL(json_extract(message.value, '$.id'), '') AS messageId,
               json_extract(part.value, '$.url') AS attachmentUrl,
               json_extract(part.value, '$.fileName') AS fileName,
               json_extract(message.value, '$.createdAt') AS messageAt
        FROM message_node mn
        JOIN json_each(mn.messages) message
        JOIN json_tree(message.value, '$.parts') part
        WHERE mn.conversation_id = :conversationId
          AND part.type = 'object'
          AND json_extract(part.value, '$.url') IS NOT NULL
        """
    )
    suspend fun listAttachmentReferences(conversationId: String): List<AttachmentReference>

    @Query("DELETE FROM managed_files_cas WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM managed_files_cas WHERE sha256 = :sha256")
    suspend fun deleteBySha(sha256: String): Int

    @Query("DELETE FROM managed_files_cas")
    suspend fun deleteAll(): Int
}
