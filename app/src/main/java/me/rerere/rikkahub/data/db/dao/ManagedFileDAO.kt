package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity

data class ManagedFileWithReference(
    @Embedded val file: ManagedFileEntity,
    val conversationId: String?,
    val nodeId: String?,
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

    @RawQuery
    suspend fun listWithReferencesByFolderRaw(query: SupportSQLiteQuery): List<ManagedFileWithReference>

    @Query("DELETE FROM managed_files WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM managed_files WHERE relative_path = :relativePath")
    suspend fun deleteByPath(relativePath: String): Int

    @Query("DELETE FROM managed_files WHERE folder = :folder")
    suspend fun deleteByFolder(folder: String): Int
}
