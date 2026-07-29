package me.rerere.rikkahub.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.data.files.FileFolders
import java.io.File

internal object RestoredAttachmentUrlRewriter {
    fun rewrite(context: Context, json: Json): Int {
        val databaseFile = context.getDatabasePath("rikka_hub")
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
                    val rewritten = rewriteElement(context, original)
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

    private fun rewriteElement(context: Context, element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { rewriteElement(context, it) })
        is JsonObject -> {
            val rewritten = element.mapValues { (_, value) ->
                rewriteElement(context, value)
            }.toMutableMap()
            val type = (rewritten["type"] as? JsonPrimitive)?.contentOrNull
            if (type in ATTACHMENT_TYPES) {
                val oldUrl = (rewritten["url"] as? JsonPrimitive)?.contentOrNull
                val newUrl = oldUrl?.let { relocateUrl(context, it) }
                if (newUrl != null && newUrl != oldUrl) {
                    rewritten["url"] = JsonPrimitive(newUrl)
                }
            }
            JsonObject(rewritten)
        }

        else -> element
    }

    private fun relocateUrl(context: Context, url: String): String? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme != "file") return null
        val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
        val restoredFile = File(context.filesDir, "${FileFolders.UPLOAD}/$fileName")
        return if (restoredFile.isFile) Uri.fromFile(restoredFile).toString() else null
    }

    private val ATTACHMENT_TYPES = setOf("image", "document", "video", "audio")
}
