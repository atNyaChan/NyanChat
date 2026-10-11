package me.rerere.rikkahub.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.data.files.AttachmentCas
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.LocalFileUrls
import java.io.File

/**
 * 改写消息库里附件 part 的 url。
 *
 * 导入存档时用：把备份里的旧绝对路径（可能来自别的包名）按文件名找回当前安装的 `upload/<name>`，
 * 统一改写成落盘形式（相对 filesDir）。已经是相对形式的原样保留。
 *
 * 兼容旧版 ZIP 导出时也复用同一套遍历，只是 [rewriteDatabase] 的 [relocate] 换成改写成上游绝对 url。
 */
internal object RestoredAttachmentUrlRewriter {
    fun rewrite(context: Context, json: Json): Int = rewriteDatabase(
        databaseFile = context.getDatabasePath("rikka_hub"),
        json = json,
        relocate = { url -> LocalFileUrls.toStored(context.filesDir, url) },
        // 导入时顺带把 managed_files 里缺失/uuid 形式的展示名回填成消息库记录的文件名
        onAttachmentFileNames = { database, fileNames ->
            backfillManagedFileDisplayNames(
                database = database,
                uploadDir = AttachmentCas.uploadDir(context.filesDir),
                messageFileNames = fileNames,
            )
        },
    )

    /**
     * 用 [relocate] 改写 [databaseFile] 里全部附件 part 的 url；只有真的变化才写回。
     * [onAttachmentFileNames] 非空时收集 `upload` 软链接名到消息库 `fileName` 的映射并在最后回调，
     * 供导入流程回填 `managed_files` 的展示名（导出路径不传，避免多余遍历）。
     */
    fun rewriteDatabase(
        databaseFile: File,
        json: Json,
        relocate: (String) -> String,
        onAttachmentFileNames: ((SQLiteDatabase, Map<String, String>) -> Unit)? = null,
    ): Int {
        if (!databaseFile.exists()) return 0

        val updates = mutableListOf<Pair<String, String>>()
        val attachmentFileNames = mutableMapOf<String, String>()
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            database.rawQuery("SELECT id, messages FROM message_node", null).use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("id")
                val messagesIndex = cursor.getColumnIndexOrThrow("messages")
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)
                    val original = runCatching {
                        json.parseToJsonElement(cursor.getString(messagesIndex))
                    }.getOrNull() ?: continue
                    val rewritten = rewriteElement(original, relocate)
                    if (rewritten != original) {
                        updates += id to rewritten.toString()
                    }
                    if (onAttachmentFileNames != null) {
                        collectAttachmentFileNames(rewritten, attachmentFileNames)
                    }
                }
            }

            database.beginTransaction()
            try {
                updates.forEach { (id, messages) ->
                    database.execSQL(
                        "UPDATE message_node SET messages = ? WHERE id = ?",
                        arrayOf(messages, id),
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }

            onAttachmentFileNames?.invoke(database, attachmentFileNames)
        }
        return updates.size
    }

    /** 收集附件 part 里记录的展示文件名, 以 url 的最后一段（软链接名）为键。 */
    private fun collectAttachmentFileNames(element: JsonElement, out: MutableMap<String, String>) {
        when (element) {
            is JsonArray -> element.forEach { collectAttachmentFileNames(it, out) }
            is JsonObject -> {
                val type = (element["type"] as? JsonPrimitive)?.contentOrNull
                if (type in ATTACHMENT_TYPES) {
                    val url = (element["url"] as? JsonPrimitive)?.contentOrNull
                    val fileName = (element["fileName"] as? JsonPrimitive)?.contentOrNull
                    if (!url.isNullOrBlank() && !fileName.isNullOrBlank()) {
                        url.toUri().lastPathSegment?.let { out[it] = fileName }
                    }
                }
                element.values.forEach { collectAttachmentFileNames(it, out) }
            }

            else -> Unit
        }
    }

    /**
     * 导入后回填 `managed_files` 的展示名：消息库记录了真实文件名、而该表里没有记录或只有软链接名
     * （缺失、空白或等于软链接名）时，改写为消息库的文件名；缺失的软链接直接补一条记录。
     * 图片/视频/音频没有 `fileName`，保持软链接名。
     */
    private fun backfillManagedFileDisplayNames(
        database: SQLiteDatabase,
        uploadDir: File,
        messageFileNames: Map<String, String>,
    ) {
        if (messageFileNames.isEmpty()) return

        val existing = linkedMapOf<String, Pair<Long, String>>()
        database.rawQuery(
            "SELECT id, relative_path, display_name FROM managed_files WHERE folder = ?",
            arrayOf(FileFolders.UPLOAD),
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val pathIndex = cursor.getColumnIndexOrThrow("relative_path")
            val nameIndex = cursor.getColumnIndexOrThrow("display_name")
            while (cursor.moveToNext()) {
                val linkName = cursor.getString(pathIndex).substringAfterLast('/')
                existing[linkName] = cursor.getLong(idIndex) to cursor.getString(nameIndex)
            }
        }

        val now = System.currentTimeMillis()
        database.beginTransaction()
        try {
            existing.forEach { (linkName, row) ->
                val resolved = messageFileNames[linkName] ?: return@forEach
                val (id, displayName) = row
                if (displayName.isBlank() || displayName == linkName) {
                    database.execSQL(
                        "UPDATE managed_files SET display_name = ?, updated_at = ? WHERE id = ?",
                        arrayOf<Any?>(resolved, now, id),
                    )
                }
            }
            uploadDir.listFiles().orEmpty().forEach { file ->
                if (existing.containsKey(file.name)) return@forEach
                val resolved = messageFileNames[file.name] ?: return@forEach
                database.execSQL(
                    "INSERT OR REPLACE INTO managed_files " +
                        "(folder, relative_path, display_name, mime_type, size_bytes, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        FileFolders.UPLOAD,
                        "${FileFolders.UPLOAD}/${file.name}",
                        resolved,
                        FileUtils.guessMimeType(file, resolved),
                        file.length(),
                        file.lastModified().takeIf { it > 0 } ?: now,
                        now,
                    ),
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun rewriteElement(
        element: JsonElement,
        relocate: (String) -> String,
    ): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { rewriteElement(it, relocate) })
        is JsonObject -> {
            val rewritten = element.mapValues { (_, value) ->
                rewriteElement(value, relocate)
            }.toMutableMap()
            val type = (rewritten["type"] as? JsonPrimitive)?.contentOrNull
            if (type in ATTACHMENT_TYPES) {
                val oldUrl = (rewritten["url"] as? JsonPrimitive)?.contentOrNull
                val newUrl = oldUrl?.let(relocate)
                if (newUrl != null && newUrl != oldUrl) {
                    rewritten["url"] = JsonPrimitive(newUrl)
                }
            }
            JsonObject(rewritten)
        }

        else -> element
    }

    private val ATTACHMENT_TYPES = setOf("image", "document", "video", "audio")
}
