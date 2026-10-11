package me.rerere.rikkahub.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
    )

    /** 用 [relocate] 改写 [databaseFile] 里全部附件 part 的 url；只有真的变化才写回。 */
    fun rewriteDatabase(databaseFile: File, json: Json, relocate: (String) -> String): Int {
        if (!databaseFile.exists()) return 0

        val updates = mutableListOf<Pair<String, String>>()
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
        }
        return updates.size
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
