package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.ManagedFileDAO
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.db.entity.ManagedFileCasEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity

class FilesRepository(
    private val dao: ManagedFileDAO,
    private val messageNodeDao: MessageNodeDAO,
) {
    suspend fun insert(file: ManagedFileCasEntity): ManagedFileCasEntity {
        val id = dao.insert(file)
        return file.copy(id = id)
    }

    suspend fun update(file: ManagedFileCasEntity) {
        dao.update(file)
    }

    suspend fun getById(id: Long): ManagedFileCasEntity? = dao.getById(id)

    suspend fun getBySha(sha256: String): ManagedFileCasEntity? = dao.getBySha(sha256)

    suspend fun listAllNow(): List<ManagedFileCasEntity> = dao.listAllNow()

    fun listAll(): Flow<List<ManagedFileCasEntity>> = dao.listAll()

    suspend fun listConversationIdsByLatest(): List<String> = dao.listConversationIdsByLatest()

    /** 附件索引重建时读取会话的消息节点（JSON 里是相对 filesDir 的落盘 url）。 */
    suspend fun listMessageNodes(conversationId: String): List<MessageNodeEntity> =
        messageNodeDao.getNodesOfConversation(conversationId)

    suspend fun deleteById(id: Long): Int = dao.deleteById(id)

    suspend fun deleteBySha(sha256: String): Int = dao.deleteBySha(sha256)

    suspend fun deleteAll(): Int = dao.deleteAll()
}
