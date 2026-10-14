package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.utils.JsonInstant

/**
 * 内容寻址的聊天附件登记表。
 *
 * 附件实体直接以 sha256 命名保存在 `files/upload/<sha256>`，本表一行对应一份内容（一个 sha256）。
 * 展示名、MIME 与大小等文件属性都不在本表保存：展示名与时间逐使用记录在 [usages] 里
 * （同一份内容被多个附件/消息使用时可能不同），MIME 与大小在读取时按需推导。
 */
@Entity(
    tableName = "managed_files_cas",
    indices = [Index(value = ["sha256"], unique = true)]
)
data class ManagedFileCasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("sha256")
    val sha256: String,
    @ColumnInfo("mime_type")
    val mimeType: String,
    @ColumnInfo("size_bytes")
    val sizeBytes: Long,
    @ColumnInfo("usages", defaultValue = "[]")
    val usages: String = "[]",
)

/**
 * 附件的一次使用。[type] 见 [FileUsageType]。
 *
 * - `message`：消息里的附件。`filename` / `displayName` 是该次使用在旧版（RikkaHub 兼容）布局里
 *   的 `uuid.ext` 文件名与展示名，`details` 记录 `{conversationId, nodeId, messageId}`，
 *   [createdAt] 是该消息的时间。新增附件时会先写入一条三个定位字段都为空的临时 `message`
 *   占位记录，用来携带展示名并避免刚加的附件被当成未使用；发送成消息后由真实消息记录取代。
 * - `assistant_background` / `assistant_avatar` / `user_avatar`：设置里的附件，`details` 记录
 *   `{assistantId}`（用户头像为空）。
 * - `none`：没有被任何消息或设置引用的附件，`details` 为空，[createdAt] 记录该内容落盘的时间。
 *
 * 一份内容只要还有 `message` / 设置类使用就不会保留 `none` 记录；反之只有 `none` 记录。
 */
@Serializable
data class FileUsage(
    val type: String,
    val filename: String = "",
    @SerialName("display_name")
    val displayName: String = "",
    @SerialName("created_at")
    val createdAt: Long = 0,
    val details: Map<String, String> = emptyMap(),
)

object FileUsageType {
    const val MESSAGE = "message"
    const val ASSISTANT_BACKGROUND = "assistant_background"
    const val ASSISTANT_AVATAR = "assistant_avatar"
    const val USER_AVATAR = "user_avatar"

    /** 设置类使用类型：助手头像/聊天背景、用户头像。 */
    val SETTINGS_TYPES = setOf(ASSISTANT_BACKGROUND, ASSISTANT_AVATAR, USER_AVATAR)

    /** 没有被任何使用引用的附件；每个 sha256 只有一条，用来记录落盘时间与展示名。 */
    const val NONE = "none"
}

fun ManagedFileCasEntity.decodeUsages(): List<FileUsage> =
    runCatching { JsonInstant.decodeFromString<List<FileUsage>>(usages) }.getOrDefault(emptyList())

fun ManagedFileCasEntity.encodeUsages(list: List<FileUsage>): ManagedFileCasEntity =
    copy(usages = JsonInstant.encodeToString(list))

/** 真实消息使用记录：`message` 类型，且会话/节点/消息三个定位字段都非空。 */
fun FileUsage.isMessageUsage(): Boolean =
    type == FileUsageType.MESSAGE &&
        !details["conversationId"].isNullOrBlank() &&
        !details["nodeId"].isNullOrBlank() &&
        !details["messageId"].isNullOrBlank()

/** 设置类使用记录：助手头像/聊天背景、用户头像。 */
fun FileUsage.isSettingsUsage(): Boolean = type in FileUsageType.SETTINGS_TYPES

/** 新增附件时写入的临时 `message` 占位记录：定位字段为空，发送成真实消息后由 [isMessageUsage] 的记录取代。 */
fun FileUsage.isPendingUsage(): Boolean =
    type == FileUsageType.MESSAGE && !isMessageUsage()

/** 一组使用记录里时间最晚那条的展示名（新增附件写到最晚的记录上，输入框也从它读取）。 */
fun List<FileUsage>.latestDisplayName(): String =
    maxByOrNull { it.createdAt }?.displayName.orEmpty()
