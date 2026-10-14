package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ManagedFileCasEntity

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

    @Query("DELETE FROM managed_files_cas WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM managed_files_cas WHERE sha256 = :sha256")
    suspend fun deleteBySha(sha256: String): Int

    @Query("DELETE FROM managed_files_cas")
    suspend fun deleteAll(): Int
}
