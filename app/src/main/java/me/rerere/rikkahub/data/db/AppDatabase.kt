package me.rerere.rikkahub.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import me.rerere.ai.core.TokenUsage
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.FavoriteDAO
import me.rerere.rikkahub.data.db.dao.FolderDAO
import me.rerere.rikkahub.data.db.dao.GenMediaDAO
import me.rerere.rikkahub.data.db.dao.ManagedFileDAO
import me.rerere.rikkahub.data.db.dao.MediaCreationDAO
import me.rerere.rikkahub.data.db.dao.MemoryConsolidationDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.db.dao.TranslationHistoryDAO
import me.rerere.rikkahub.data.db.dao.UsageRecordDAO
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.FavoriteEntity
import me.rerere.rikkahub.data.db.entity.FolderEntity
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.db.entity.ManagedFileCasEntity
import me.rerere.rikkahub.data.db.entity.MediaCreationNodeEntity
import me.rerere.rikkahub.data.db.entity.MediaCreationRecordEntity
import me.rerere.rikkahub.data.db.entity.MediaCreationSessionEntity
import me.rerere.rikkahub.data.db.entity.MemoryConsolidationEntity
import me.rerere.rikkahub.data.db.entity.MemoryFileEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity
import me.rerere.rikkahub.data.db.entity.TranslationHistoryEntity
import me.rerere.rikkahub.data.db.entity.UsageRecordEntity
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.utils.JsonInstant

/**
 * 应用数据库。
 *
 * 最低支持 schema 版本 30：低于 30 的旧库不再提供迁移链，Room 会直接报错；只有恢复备份时
 * 才由 `AppDatabaseFactory` 注册上游的迁移链
 * （[me.rerere.rikkahub.data.db.migrations.Migration_30_31] 补用量流水表、
 * [me.rerere.rikkahub.data.db.migrations.Migration_31_32] 补 SSH 工作区列、
 * [me.rerere.rikkahub.data.db.migrations.Migration_32_102] 把附件登记换成 `managed_files_cas`）。
 * 100 -> 101 的 [me.rerere.rikkahub.data.db.migrations.Migration_100_101]（用量流水表）与
 * 101 -> 102 的 [me.rerere.rikkahub.data.db.migrations.Migration_101_102]（SSH 工作区列）则总是注册。
 * 因此从旧存档恢复的完整链路是 30 -> 31 -> 32 -> 102，上游 32 的存档直接走 32 -> 102，
 * 本 fork 自己的库则走 100 -> 101 -> 102。
 */
@Database(
    entities = [
        ConversationEntity::class,
        MemoryFileEntity::class,
        MemoryConsolidationEntity::class,
        GenMediaEntity::class,
        MessageNodeEntity::class,
        ManagedFileCasEntity::class,
        FavoriteEntity::class,
        WorkspaceEntity::class,
        FolderEntity::class,
        MediaCreationSessionEntity::class,
        MediaCreationNodeEntity::class,
        MediaCreationRecordEntity::class,
        TranslationHistoryEntity::class,
        UsageRecordEntity::class,
    ],
    version = 102,
)
@TypeConverters(TokenUsageConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO

    abstract fun memoryDao(): MemoryDAO

    abstract fun memoryConsolidationDao(): MemoryConsolidationDAO

    abstract fun genMediaDao(): GenMediaDAO

    abstract fun messageNodeDao(): MessageNodeDAO

    abstract fun managedFileDao(): ManagedFileDAO

    abstract fun favoriteDao(): FavoriteDAO

    abstract fun workspaceDao(): WorkspaceDAO

    abstract fun folderDao(): FolderDAO

    abstract fun mediaCreationDao(): MediaCreationDAO

    abstract fun translationHistoryDao(): TranslationHistoryDAO

    abstract fun usageRecordDao(): UsageRecordDAO
}

object TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String {
        return JsonInstant.encodeToString(usage)
    }

    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? {
        return JsonInstant.decodeFromString(usage)
    }
}
