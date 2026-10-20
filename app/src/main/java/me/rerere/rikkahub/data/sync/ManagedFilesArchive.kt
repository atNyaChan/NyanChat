package me.rerere.rikkahub.data.sync

import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import java.io.File
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.data.db.entity.FileUsage
import me.rerere.rikkahub.data.db.entity.FileUsageType
import me.rerere.rikkahub.data.db.entity.isMessageUsage
import me.rerere.rikkahub.data.db.entity.latestDisplayName
import me.rerere.rikkahub.data.files.AttachmentCas
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.attachmentShaFromUrl
import me.rerere.rikkahub.utils.JsonInstant

/**
 * 附件表与 `upload` 目录在「新格式（内容寻址）」与「旧格式（RikkaHub 兼容）」之间的转换。
 *
 * 新格式：附件实体是 `upload/<sha256>`，登记信息在 `managed_files_cas`（含使用记录）。
 * 旧格式：附件是 `upload/<uuid.ext>`，登记信息在 `managed_files`。
 */
internal object ManagedFilesArchive {
    private const val LEGACY_TABLE = "managed_files"
    private const val CAS_TABLE = "managed_files_cas"
    private val ATTACHMENT_TYPES = setOf("image", "document", "video", "audio")

    /** 兼容 ZIP 里数据库声明的 schema 版本：上游 RikkaHub 当前版本（31 = 已带 `usage_record`）。 */
    private const val LEGACY_SCHEMA_VERSION = 31

    /** 本分支的 CAS schema 版本；数据库达到该版本时 `upload` 已是内容寻址，不能再做迁移。 */
    const val CAS_SCHEMA_VERSION = 100

    /** 最低支持的 schema 版本；低于该版本的旧库不再提供迁移链，存档直接拒绝。 */
    const val MIN_SUPPORTED_SCHEMA_VERSION = 30

    /** 读取数据库声明的 schema 版本（`PRAGMA user_version`）；文件不可读时返回 null。 */
    fun readSchemaVersion(databaseFile: File): Int? {
        if (!databaseFile.isFile) return null
        return runCatching {
            SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { database -> database.version }
        }.getOrNull()
    }

    /** 数据库结构是内容寻址的（由本分支写出）时为 true，此时不得迁移 `upload` 目录。 */
    fun isCasSchema(version: Int?): Boolean = version != null && version >= CAS_SCHEMA_VERSION

    /**
     * 把 [filesRoot]/upload 里的普通文件统一成以内容 sha256 命名的实体，相同内容只保留一份。
     * 只在导入旧库（版本 < [CAS_SCHEMA_VERSION]，上游 RikkaHub 为 30 / 31）时调用：旧库里可能仍有
     * 名字已经是 sha256 的文件，这里也按内容重新命名/校验，并返回每个旧文件名到 sha256 的映射
     * 供改写消息/设置里的 url 使用。
     */
    fun normalizeUploadDir(filesRoot: File): Map<String, String> {
        val upload = File(filesRoot, FileFolders.UPLOAD)
        if (!upload.isDirectory) return emptyMap()
        val renamed = linkedMapOf<String, String>()
        upload.listFiles().orEmpty().forEach { file ->
            if (!file.isFile || AttachmentCas.isTemporary(file)) return@forEach
            val name = file.name
            val sha256 = AttachmentCas.sha256(file)
            if (name != sha256) {
                val target = File(upload, sha256)
                if (!target.exists() && !file.renameTo(target)) {
                    file.copyTo(target, overwrite = true)
                }
                file.delete()
            }
            renamed[name] = sha256
        }
        return renamed
    }

    /**
     * 导入：把快照里的旧 `managed_files` 表转成 `managed_files_cas`，并把消息 url 改成
     * `upload/<sha256>`；随后删除旧的 `managed_files` 表与 `room_master_table`
     * （强制 Room 按新 schema 重新校验并写入新的 identity hash）。已经是新格式时不做改动。
     *
     * 这里只把数据库改到 `managed_files_cas`，不写 `user_version`，让 Room 继续按原版本执行
     * 迁移链（30 -> 31 -> 100 -> 101，上游 31 的存档是 31 -> 100 -> 101）。
     */
    fun convertLegacyDatabase(
        databaseFile: File,
        json: Json,
        oldNameToSha: Map<String, String>,
        filesRoot: File,
    ): Boolean {
        if (!databaseFile.isFile) return false
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            .use { database ->
                if (!tableExists(database, LEGACY_TABLE)) return false
                // 建表、改消息、删旧表必须原子完成，中途失败会留下半转换的库
                database.beginTransaction()
                try {
                    val rawDisplayByName = readLegacyDisplayNames(database)
                    val usages = mutableListOf<UsageRecord>()
                    val messageFileNames = mutableMapOf<String, String>()
                    rewriteMessageNodes(database, json) { element, conversationId, nodeId, messageId, messageAt ->
                        val url = (element["url"] as? JsonPrimitive)?.contentOrNull ?: return@rewriteMessageNodes null
                        val oldName = url.substringAfterLast('/')
                        val sha256 = oldNameToSha[oldName] ?: return@rewriteMessageNodes null
                        (element["fileName"] as? JsonPrimitive)?.contentOrNull
                            ?.takeIf { it.isNotBlank() }
                            ?.let { messageFileNames[oldName] = it }
                        if (messageId != null) {
                            usages += UsageRecord(
                                sha256 = sha256,
                                filename = oldName,
                                displayName = "",
                                createdAt = messageAt ?: 0L,
                                conversationId = conversationId,
                                nodeId = nodeId,
                                messageId = messageId,
                            )
                        }
                        "upload/$sha256"
                    }

                    // 旧布局的展示名是逐文件记录的：消息里的 fileName 优先，否则用旧表的值，再否则旧文件名
                    val nameDisplayNames = linkedMapOf<String, String>()
                    (oldNameToSha.keys + rawDisplayByName.keys).forEach { name ->
                        val raw = rawDisplayByName[name]
                        nameDisplayNames[name] = messageFileNames[name]?.takeIf { it.isNotBlank() }
                            ?: raw?.takeIf { it.isNotBlank() && it != name && it != oldNameToSha[name] }
                            ?: name
                    }
                    val namedUsages = usages.map { it.copy(displayName = nameDisplayNames[it.filename] ?: it.filename) }

                    val rowsBySha = readLegacyRows(database, oldNameToSha).toMutableMap()
                    // 磁盘上有内容但旧登记表缺行时, 用文件本身补一行, 避免丢失使用记录
                    val uploadDir = File(filesRoot, FileFolders.UPLOAD)
                    oldNameToSha.forEach { (_, sha256) ->
                        if (rowsBySha.containsKey(sha256)) return@forEach
                        val file = File(uploadDir, sha256)
                        if (!file.isFile) return@forEach
                        rowsBySha[sha256] = LegacyRow(
                            mimeType = FileUtils.guessMimeType(file, sha256),
                            sizeBytes = file.length(),
                            createdAt = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                            displayName = null,
                        )
                    }
                    createCasTable(database)
                    rowsBySha.forEach { (sha256, row) ->
                        val rowUsages = namedUsages.filter { it.sha256 == sha256 }.map { it.toFileUsage() }
                        val finalUsages = rowUsages.ifEmpty {
                            listOf(
                                FileUsage(
                                    type = FileUsageType.NONE,
                                    displayName = row.displayName.orEmpty(),
                                    createdAt = row.createdAt,
                                )
                            )
                        }
                        insertCasRow(database, sha256, row, finalUsages)
                    }
                    database.execSQL("DROP TABLE IF EXISTS `$LEGACY_TABLE`")
                    // schema 已换，丢弃 Room 的 identity 记录，让 Room 按新 schema 重新校验
                    database.execSQL("DROP TABLE IF EXISTS `room_master_table`")
                    database.setTransactionSuccessful()
                    return true
                } finally {
                    database.endTransaction()
                }
            }
    }

    /**
     * 兼容 ZIP 导出：把快照里的 `managed_files_cas` 表转回旧的 `managed_files`，并把消息 url 改写成
     * 上游安装目录下的绝对路径。每份内容按 [filenameBySha] 落一份「基础」文件，消息使用记录各自
     * 按 `FileUsage.filename` 落盘（同一内容被多次使用时复制多份）。
     *
     * 最后把快照声明成上游当前的 schema 版本（[LEGACY_SCHEMA_VERSION]）：改回旧表结构、写
     * `user_version` 并删掉 `room_master_table`，让上游按自己的 schema 重新校验。
     */
    fun prepareLegacyExportDatabase(
        databaseFile: File,
        json: Json,
        upstreamFilesDir: File,
        filenameBySha: Map<String, String>,
        baseShas: Set<String>,
    ): List<Pair<String, String>> {
        if (!databaseFile.isFile) return emptyList()
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            .use { database ->
                if (!tableExists(database, CAS_TABLE)) return emptyList()
                val rows = readCasRows(database)
                // (sha256, conversationId, nodeId, messageId) -> 该次使用在旧布局里的文件名
                val usageFilename = mutableMapOf<String, String>()
                rows.forEach { row ->
                    row.usages.filter { it.isMessageUsage() }.forEach usageLoop@{ usage ->
                        val conv = usage.details["conversationId"] ?: return@usageLoop
                        val node = usage.details["nodeId"] ?: return@usageLoop
                        val msg = usage.details["messageId"] ?: return@usageLoop
                        usageFilename[usageKey(row.sha256, conv, node, msg)] = usage.filename
                    }
                }

                rewriteMessageNodes(database, json) { element, conversationId, nodeId, messageId, _ ->
                    val url = (element["url"] as? JsonPrimitive)?.contentOrNull ?: return@rewriteMessageNodes null
                    val sha256 = attachmentShaFromUrl(url) ?: return@rewriteMessageNodes null
                    val filename = messageId?.let { usageFilename[usageKey(sha256, conversationId, nodeId, it)] }
                        ?: filenameBySha[sha256]
                        ?: return@rewriteMessageNodes null
                    File(upstreamFilesDir, "${FileFolders.UPLOAD}/$filename").toUri().toString()
                }

                val entries = linkedSetOf<LegacyEntry>()
                rows.forEach { row ->
                    val messageUsages = row.usages.filter { it.isMessageUsage() }
                    // 旧布局里每份内容至少落一份「基础」文件；已被消息使用的只在设置（头像等）也引用它时才额外落一份
                    if (messageUsages.isEmpty() || row.sha256 in baseShas) {
                        filenameBySha[row.sha256]?.let { filename ->
                            val baseDisplayName = row.usages.latestDisplayName().takeIf { it.isNotBlank() }
                            entries += LegacyEntry(row.sha256, filename, baseDisplayName)
                        }
                    }
                    messageUsages.forEach { usage ->
                        entries += LegacyEntry(
                            row.sha256,
                            usage.filename,
                            usage.displayName.takeIf { it.isNotBlank() },
                        )
                    }
                }
                createLegacyTable(database)
                val rowsBySha = rows.associateBy { it.sha256 }
                entries.forEach { entry ->
                    val row = rowsBySha[entry.sha256] ?: return@forEach
                    insertLegacyRow(database, entry, row)
                }
                database.execSQL("DROP TABLE IF EXISTS `$CAS_TABLE`")
                // 让上游把这份快照当成它自己的 schema 版本重新校验
                database.execSQL("PRAGMA user_version = $LEGACY_SCHEMA_VERSION")
                database.execSQL("DROP TABLE IF EXISTS `room_master_table`")
                return entries.map { it.sha256 to it.filename }
            }
    }

    /**
     * 导入时把设置里的附件 url 改写成 `upload/<sha256>`。只处理形如 `.../upload/<uuid.ext>`
     * 或 `upload/<uuid.ext>` 的字符串，其它值原样保留。
     */
    fun rewriteSettingsAttachments(text: String, oldNameToSha: Map<String, String>): String {
        if (oldNameToSha.isEmpty()) return text
        val root = runCatching { JsonInstant.parseToJsonElement(text) }.getOrNull() ?: return text
        return JsonInstant.encodeToString(JsonElement.serializer(), rewriteStrings(root, oldNameToSha))
    }

    // ------------------------------------------------------------------ 改写消息

    private fun rewriteMessageNodes(
        database: SQLiteDatabase,
        json: Json,
        rewriteAttachment: (
            element: JsonObject,
            conversationId: String,
            nodeId: String,
            messageId: String?,
            messageAt: Long?,
        ) -> String?,
    ) {
        val updates = mutableListOf<Pair<String, String>>()
        database.rawQuery("SELECT id, conversation_id, messages FROM message_node", null).use { cursor ->
            while (cursor.moveToNext()) {
                val nodeId = cursor.getString(0)
                val conversationId = cursor.getString(1)
                val messages = cursor.getString(2)
                val original = runCatching { json.parseToJsonElement(messages) }.getOrNull() ?: continue
                val (rewritten, changed) = rewriteMessages(original, conversationId, nodeId, rewriteAttachment)
                if (changed) updates += nodeId to rewritten.toString()
            }
        }
        if (updates.isEmpty()) return
        database.beginTransaction()
        try {
            updates.forEach { (id, messages) ->
                database.execSQL("UPDATE message_node SET messages = ? WHERE id = ?", arrayOf(messages, id))
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun rewriteMessages(
        root: JsonElement,
        conversationId: String,
        nodeId: String,
        rewriteAttachment: (
            element: JsonObject,
            conversationId: String,
            nodeId: String,
            messageId: String?,
            messageAt: Long?,
        ) -> String?,
    ): Pair<JsonElement, Boolean> {
        if (root !is JsonArray) return root to false
        var changed = false
        val list = root.map { message ->
            val messageId = (message as? JsonObject)?.let { (it["id"] as? JsonPrimitive)?.contentOrNull }
            val messageAt = (message as? JsonObject)
                ?.get("createdAt")
                ?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?.let(::parseCreatedAt)
            val (rewritten, childChanged) = rewriteNode(
                element = message,
                messageId = messageId,
                messageAt = messageAt,
                conversationId = conversationId,
                nodeId = nodeId,
                rewriteAttachment = rewriteAttachment,
            )
            changed = changed || childChanged
            rewritten
        }
        return JsonArray(list) to changed
    }

    private fun rewriteNode(
        element: JsonElement,
        messageId: String?,
        messageAt: Long?,
        conversationId: String,
        nodeId: String,
        rewriteAttachment: (
            element: JsonObject,
            conversationId: String,
            nodeId: String,
            messageId: String?,
            messageAt: Long?,
        ) -> String?,
    ): Pair<JsonElement, Boolean> = when (element) {
        is JsonArray -> {
            var changed = false
            val list = element.map { child ->
                val (rewritten, childChanged) =
                    rewriteNode(child, messageId, messageAt, conversationId, nodeId, rewriteAttachment)
                changed = changed || childChanged
                rewritten
            }
            JsonArray(list) to changed
        }

        is JsonObject -> {
            var obj = element
            var changed = false
            val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
            if (type in ATTACHMENT_TYPES) {
                val newUrl = rewriteAttachment(obj, conversationId, nodeId, messageId, messageAt)
                if (newUrl != null) {
                    obj = JsonObject(obj + ("url" to JsonPrimitive(newUrl)))
                    changed = true
                }
            }
            var childChanged = false
            val values = obj.mapValues { (key, value) ->
                if (key == "url") value else {
                    val (rewritten, c) = rewriteNode(value, messageId, messageAt, conversationId, nodeId, rewriteAttachment)
                    childChanged = childChanged || c
                    rewritten
                }
            }
            JsonObject(values) to (changed || childChanged)
        }

        else -> element to false
    }

    private fun rewriteStrings(element: JsonElement, oldNameToSha: Map<String, String>): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (_, value) -> rewriteStrings(value, oldNameToSha) })
        is JsonArray -> JsonArray(element.map { rewriteStrings(it, oldNameToSha) })
        is JsonPrimitive -> if (element.isString) JsonPrimitive(mapAttachmentUrl(element.content, oldNameToSha)) else element
    }

    private fun mapAttachmentUrl(value: String, oldNameToSha: Map<String, String>): String {
        // 只改写本地附件 url，避免把 http/content 里碰巧出现的 upload/ 路径段当成附件
        if (value.contains("://") && !value.startsWith("file://")) return value
        val marker = "${FileFolders.UPLOAD}/"
        val index = value.lastIndexOf(marker)
        if (index < 0) return value
        val name = value.substring(index + marker.length).substringBefore('/')
        val sha256 = oldNameToSha[name] ?: return value
        return "${FileFolders.UPLOAD}/$sha256"
    }

    private fun parseCreatedAt(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            LocalDateTime.parse(value).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }.getOrNull()
    }

    // ------------------------------------------------------------------ 旧表 -> 新表

    private data class LegacyRow(
        val mimeType: String,
        val sizeBytes: Long,
        val createdAt: Long,
        val displayName: String?,
    )

    private data class UsageRecord(
        val sha256: String,
        val filename: String,
        val displayName: String,
        val createdAt: Long,
        val conversationId: String,
        val nodeId: String,
        val messageId: String,
    ) {
        fun toFileUsage(): FileUsage = FileUsage(
            type = FileUsageType.MESSAGE,
            filename = filename,
            displayName = displayName,
            createdAt = createdAt,
            details = mapOf(
                "conversationId" to conversationId,
                "nodeId" to nodeId,
                "messageId" to messageId,
            ),
        )
    }

    /** 旧表 `relative_path` 最后一段（旧文件名）到原始 `display_name` 的映射。 */
    private fun readLegacyDisplayNames(database: SQLiteDatabase): Map<String, String> {
        val result = linkedMapOf<String, String>()
        database.rawQuery("SELECT relative_path, display_name FROM `$LEGACY_TABLE`", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0).substringAfterLast('/')
                result[name] = cursor.getString(1) ?: ""
            }
        }
        return result
    }

    private fun readLegacyRows(
        database: SQLiteDatabase,
        oldNameToSha: Map<String, String>,
    ): Map<String, LegacyRow> {
        val rows = linkedMapOf<String, LegacyRow>()
        database.rawQuery(
            "SELECT relative_path, mime_type, size_bytes, created_at, display_name FROM `$LEGACY_TABLE`",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0).substringAfterLast('/')
                val sha256 = oldNameToSha[name] ?: continue
                if (rows.containsKey(sha256)) continue
                rows[sha256] = LegacyRow(
                    mimeType = cursor.getString(1),
                    sizeBytes = cursor.getLong(2),
                    createdAt = cursor.getLong(3),
                    displayName = cursor.getString(4),
                )
            }
        }
        return rows
    }

    private fun createCasTable(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `$CAS_TABLE` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sha256` TEXT NOT NULL, " +
                "`mime_type` TEXT NOT NULL, " +
                "`size_bytes` INTEGER NOT NULL, " +
                "`usages` TEXT NOT NULL DEFAULT '[]')"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_${CAS_TABLE}_sha256` ON `$CAS_TABLE` (`sha256`)"
        )
    }

    private fun insertCasRow(database: SQLiteDatabase, sha256: String, row: LegacyRow, usages: List<FileUsage>) {
        database.execSQL(
            "INSERT OR REPLACE INTO `$CAS_TABLE` " +
                "(sha256, mime_type, size_bytes, usages) VALUES (?, ?, ?, ?)",
            arrayOf<Any?>(
                sha256,
                row.mimeType,
                row.sizeBytes,
                JsonInstant.encodeToString(usages),
            ),
        )
    }

    private fun readCasRows(database: SQLiteDatabase): List<CasRow> {
        val rows = mutableListOf<CasRow>()
        database.rawQuery(
            "SELECT sha256, mime_type, size_bytes, usages FROM `$CAS_TABLE`",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += CasRow(
                    sha256 = cursor.getString(0),
                    mimeType = cursor.getString(1),
                    sizeBytes = cursor.getLong(2),
                    usages = runCatching {
                        JsonInstant.decodeFromString<List<FileUsage>>(cursor.getString(3))
                    }.getOrDefault(emptyList()),
                )
            }
        }
        return rows
    }

    private data class CasRow(
        val sha256: String,
        val mimeType: String,
        val sizeBytes: Long,
        val usages: List<FileUsage>,
    ) {
        /** 旧表没有独立时间列，用最早一次使用的时间，缺失时退回当前时间。 */
        val createdAt: Long
            get() = usages.map { it.createdAt }.filter { it > 0 }.minOrNull() ?: System.currentTimeMillis()
    }

    private data class LegacyEntry(val sha256: String, val filename: String, val displayName: String?)

    private fun createLegacyTable(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `$LEGACY_TABLE` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`folder` TEXT NOT NULL, " +
                "`relative_path` TEXT NOT NULL, " +
                "`display_name` TEXT NOT NULL, " +
                "`mime_type` TEXT NOT NULL, " +
                "`size_bytes` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_${LEGACY_TABLE}_relative_path` " +
                "ON `$LEGACY_TABLE` (`relative_path`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_${LEGACY_TABLE}_folder` ON `$LEGACY_TABLE` (`folder`)"
        )
    }

    private fun insertLegacyRow(database: SQLiteDatabase, entry: LegacyEntry, row: CasRow) {
        val displayName = entry.displayName?.takeIf { it.isNotBlank() } ?: entry.filename
        database.execSQL(
            "INSERT OR REPLACE INTO `$LEGACY_TABLE` " +
                "(folder, relative_path, display_name, mime_type, size_bytes, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                FileFolders.UPLOAD,
                "${FileFolders.UPLOAD}/${entry.filename}",
                displayName,
                row.mimeType,
                row.sizeBytes,
                row.createdAt,
                // updated_at 照抄 created_at
                row.createdAt,
            ),
        )
    }

    private fun usageKey(sha256: String, conversationId: String, nodeId: String, messageId: String): String =
        "$sha256\u0000$conversationId\u0000$nodeId\u0000$messageId"

    private fun tableExists(database: SQLiteDatabase, name: String): Boolean =
        database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(name),
        ).use { it.moveToFirst() }
}
