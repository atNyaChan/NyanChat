package me.rerere.rikkahub.data.db.fts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import java.time.Instant

data class MessageSearchResult(
    val nodeId: String,
    val messageId: String,
    val conversationId: String,
    val title: String,
    val updateAt: Instant,
    val snippet: String,
)

enum class MessageSearchSort(val orderBy: String) {
    RELEVANCE("rank, update_at DESC"),
    NEWEST_FIRST("update_at DESC, rank"),
    OLDEST_FIRST("update_at ASC, rank"),
}

enum class MessageSearchMode {
    TITLE_ONLY,
    EXACT,
    FUZZY,
}

private const val TAG = "MessageFtsManager"

class MessageFtsManager(private val database: AppDatabase) {

    private val db get() = database.openHelper.writableDatabase

    suspend fun indexConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        val conversationId = conversation.id.toString()
        db.execSQL("DELETE FROM message_fts WHERE conversation_id = ?", arrayOf(conversationId))
        conversation.messageNodes.forEach { node ->
            node.messages.forEach { message ->
                val text = message.extractFtsText()
                if (text.isNotBlank()) {
                    db.execSQL(
                        "INSERT INTO message_fts(text, node_id, message_id, conversation_id, title, update_at) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(
                            text,
                            node.id.toString(),
                            message.id.toString(),
                            conversationId,
                            conversation.title,
                            conversation.updateAt.toEpochMilli().toString(),
                        )
                    )
                }
            }
        }
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        db.execSQL("DELETE FROM message_fts WHERE conversation_id = ?", arrayOf(conversationId))
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.execSQL("DELETE FROM message_fts")
    }

    suspend fun search(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
        mode: MessageSearchMode = MessageSearchMode.FUZZY,
    ): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        if (mode != MessageSearchMode.FUZZY) {
            return@withContext searchExact(keyword, sort, mode)
        }
        val results = mutableListOf<MessageSearchResult>()
        val cursor = db.query(
            """
            SELECT node_id, message_id, conversation_id, title, update_at,
                   simple_snippet(message_fts, 0, '[', ']', '...', 30) AS snippet
            FROM message_fts
            WHERE text MATCH jieba_query(?)
            ORDER BY ${sort.orderBy}
            LIMIT 50
            """.trimIndent(),
            arrayOf(keyword)
        )
        Log.i(TAG, "search: $keyword")
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    MessageSearchResult(
                        nodeId = it.getString(0),
                        messageId = it.getString(1),
                        conversationId = it.getString(2),
                        title = it.getString(3),
                        updateAt = Instant.ofEpochMilli(it.getLong(4)),
                        snippet = it.getString(5),
                    )
                )
            }
        }
        results
    }

    private fun searchExact(
        keyword: String,
        sort: MessageSearchSort,
        mode: MessageSearchMode,
    ): List<MessageSearchResult> {
        val titleOnly = mode == MessageSearchMode.TITLE_ONLY
        val orderBy = when (sort) {
            MessageSearchSort.RELEVANCE, MessageSearchSort.NEWEST_FIRST -> "update_at DESC"
            MessageSearchSort.OLDEST_FIRST -> "update_at ASC"
        }
        val groupBy = if (titleOnly) "GROUP BY conversation_id" else ""
        val cursor = db.query(
            """
            SELECT node_id, message_id, conversation_id, title, update_at, text
            FROM message_fts
            WHERE instr(${if (titleOnly) "title" else "text"}, ?) > 0
            $groupBy
            ORDER BY $orderBy
            LIMIT 50
            """.trimIndent(),
            arrayOf(keyword),
        )
        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    add(
                        MessageSearchResult(
                            nodeId = it.getString(0),
                            messageId = it.getString(1),
                            conversationId = it.getString(2),
                            title = it.getString(3),
                            updateAt = Instant.ofEpochMilli(it.getLong(4)),
                            snippet = if (titleOnly) "" else it.getString(5).exactSnippet(keyword),
                        )
                    )
                }
            }
        }
    }
}

private fun String.exactSnippet(keyword: String): String {
    val matchStart = indexOf(keyword)
    if (matchStart < 0) return take(200)
    val start = (matchStart - 60).coerceAtLeast(0)
    val end = (matchStart + keyword.length + 120).coerceAtMost(length)
    return buildString {
        if (start > 0) append("...")
        append(this@exactSnippet, start, matchStart)
        append('[').append(keyword).append(']')
        append(this@exactSnippet, matchStart + keyword.length, end)
        if (end < length) append("...")
    }
}

private fun UIMessage.extractFtsText(): String =
    parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }
        .take(10_000)
