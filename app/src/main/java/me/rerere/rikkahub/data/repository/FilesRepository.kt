package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.db.dao.AttachmentReference
import me.rerere.rikkahub.data.db.dao.ManagedFileDAO
import me.rerere.rikkahub.data.db.entity.ManagedFileCasEntity
import kotlinx.coroutines.flow.Flow

class FilesRepository(
    private val dao: ManagedFileDAO,
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

    suspend fun listAttachmentReferences(conversationId: String): List<AttachmentReference> =
        dao.listAttachmentReferences(conversationId)

    suspend fun deleteById(id: Long): Int = dao.deleteById(id)

    suspend fun deleteBySha(sha256: String): Int = dao.deleteBySha(sha256)

    suspend fun deleteAll(): Int = dao.deleteAll()
}
