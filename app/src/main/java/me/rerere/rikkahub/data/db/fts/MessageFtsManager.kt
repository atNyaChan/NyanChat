package me.rerere.rikkahub.data.db.fts

import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

data class MessageSearchResult(
    val nodeId: String,
    val messageId: String,
    val conversationId: String,
    val title: String,
    val updateAt: Instant,
    val snippet: String,
)

enum class MessageSearchSort {
    RELEVANCE,
    NEWEST_FIRST,
    OLDEST_FIRST,
}

enum class MessageSearchMode {
    TITLE_ONLY,
    EXACT,
    FUZZY,
}

enum class MessageAttachmentState {
    EXISTS,
    MISSING,
}

class MessageFtsManager(private val database: AppDatabase) {

    private val db get() = database.openHelper.writableDatabase

    suspend fun indexConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        val conversationId = conversation.id.toString()
        db.execSQL("DELETE FROM message_search_cache WHERE conversation_id = ?", arrayOf(conversationId))
        conversation.messageNodes.forEach { node ->
            node.messages.forEach { message ->
                val text = message.extractFtsText()
                if (text.isNotBlank()) {
                    db.execSQL(
                        """
                        INSERT INTO message_search_cache(
                            text, node_id, message_id, conversation_id, title, update_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
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
        db.execSQL("DELETE FROM message_search_cache WHERE conversation_id = ?", arrayOf(conversationId))
    }

    suspend fun rebuildAll() = withContext(Dispatchers.IO) {
        rebuildMessageSearchCache(db)
    }

    suspend fun search(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
        mode: MessageSearchMode = MessageSearchMode.FUZZY,
        limit: Int = 50,
        offset: Int = 0,
    ): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        if (mode != MessageSearchMode.FUZZY) {
            return@withContext searchExact(keyword, sort, mode, limit, offset)
        }
        val terms = keyword.split(Regex("\\s+")).filter(String::isNotEmpty)
        if (terms.isEmpty()) return@withContext emptyList()
        val orderBy = when (sort) {
            MessageSearchSort.RELEVANCE, MessageSearchSort.NEWEST_FIRST -> "update_at DESC"
            MessageSearchSort.OLDEST_FIRST -> "update_at ASC"
        }
        val cursor = db.query(
            """
            SELECT node_id, message_id, conversation_id, title, update_at, text
            FROM message_search_cache
            WHERE instr(text, ?) > 0
            ORDER BY $orderBy
            """.trimIndent(),
            arrayOf(terms.first())
        )
        val matches = buildList {
            cursor.use {
                while (it.moveToNext()) {
                    val text = it.getString(5)
                    text.findOrderedTerms(terms)?.let { ranges ->
                        add(
                            ranges.orderedTermGapCount() to MessageSearchResult(
                                nodeId = it.getString(0),
                                messageId = it.getString(1),
                                conversationId = it.getString(2),
                                title = it.getString(3),
                                updateAt = Instant.ofEpochMilli(it.getLong(4)),
                                snippet = text.orderedSnippet(ranges),
                            )
                        )
                    }
                }
            }
        }
        val ordered = if (sort == MessageSearchSort.RELEVANCE) {
            matches.sortedWith(compareBy<Pair<Int, MessageSearchResult>> { it.first }
                .thenByDescending { it.second.updateAt })
        } else {
            matches
        }
        ordered.drop(offset).take(limit).map { it.second }
    }

    suspend fun searchByModel(
        modelId: Uuid,
        sort: MessageSearchSort = MessageSearchSort.NEWEST_FIRST,
        limit: Int = 50,
        offset: Int = 0,
    ): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        val orderBy = when (sort) {
            MessageSearchSort.RELEVANCE, MessageSearchSort.NEWEST_FIRST -> "c.update_at DESC"
            MessageSearchSort.OLDEST_FIRST -> "c.update_at ASC"
        }
        val cursor = db.query(
            """
            SELECT mn.id, j.value, mn.conversation_id, c.title, c.update_at
            FROM message_node mn
            JOIN conversationentity c ON c.id = mn.conversation_id,
                 json_each(mn.messages) j
            WHERE json_extract(j.value, '$.role') = 'assistant'
              AND json_extract(j.value, '$.modelId') = ?
            ORDER BY $orderBy
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf<Any?>(modelId.toString(), limit, offset),
        )
        buildList {
            cursor.use {
                while (it.moveToNext()) {
                    val message = JsonInstant.decodeFromString<UIMessage>(it.getString(1))
                    add(
                        MessageSearchResult(
                            nodeId = it.getString(0),
                            messageId = message.id.toString(),
                            conversationId = it.getString(2),
                            title = it.getString(3),
                            updateAt = Instant.ofEpochMilli(it.getLong(4)),
                            snippet = message.extractFtsText(),
                        )
                    )
                }
            }
        }
    }

    suspend fun countByModel(modelId: Uuid): Int = withContext(Dispatchers.IO) {
        val cursor = db.query(
            """
            SELECT COUNT(*)
            FROM message_node mn, json_each(mn.messages) j
            WHERE json_extract(j.value, '$.role') = 'assistant'
              AND json_extract(j.value, '$.modelId') = ?
            """.trimIndent(),
            arrayOf(modelId.toString()),
        )
        cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    suspend fun getUsedModelIds(): List<Uuid> = withContext(Dispatchers.IO) {
        val cursor = db.query(
            """
            SELECT DISTINCT json_extract(j.value, '$.modelId')
            FROM message_node mn, json_each(mn.messages) j
            WHERE json_extract(j.value, '$.role') = 'assistant'
              AND json_extract(j.value, '$.modelId') IS NOT NULL
            """.trimIndent()
        )
        buildList {
            cursor.use {
                while (it.moveToNext()) {
                    runCatching { Uuid.parse(it.getString(0)) }.getOrNull()?.let(::add)
                }
            }
        }
    }

    suspend fun searchManuallyEdited(
        sort: MessageSearchSort = MessageSearchSort.NEWEST_FIRST,
        limit: Int = 50,
        offset: Int = 0,
    ): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        val orderBy = when (sort) {
            MessageSearchSort.RELEVANCE, MessageSearchSort.NEWEST_FIRST -> "c.update_at DESC"
            MessageSearchSort.OLDEST_FIRST -> "c.update_at ASC"
        }
        val cursor = db.query(
            """
            SELECT mn.id, j.value, mn.conversation_id, c.title, c.update_at
            FROM message_node mn
            JOIN conversationentity c ON c.id = mn.conversation_id,
                 json_each(mn.messages) j
            WHERE json_extract(j.value, '$.role') = 'assistant'
              AND json_extract(j.value, '$.modelId') IS NULL
            ORDER BY $orderBy
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf(limit, offset),
        )
        buildList {
            cursor.use {
                while (it.moveToNext()) {
                    val message = JsonInstant.decodeFromString<UIMessage>(it.getString(1))
                    add(
                        MessageSearchResult(
                            nodeId = it.getString(0),
                            messageId = message.id.toString(),
                            conversationId = it.getString(2),
                            title = it.getString(3),
                            updateAt = Instant.ofEpochMilli(it.getLong(4)),
                            snippet = message.extractFtsText(),
                        )
                    )
                }
            }
        }
    }

    suspend fun countManuallyEdited(): Int = withContext(Dispatchers.IO) {
        val cursor = db.query(
            """
            SELECT COUNT(*)
            FROM message_node mn, json_each(mn.messages) j
            WHERE json_extract(j.value, '$.role') = 'assistant'
              AND json_extract(j.value, '$.modelId') IS NULL
            """.trimIndent()
        )
        cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    suspend fun searchByAttachmentState(
        state: MessageAttachmentState,
        sort: MessageSearchSort = MessageSearchSort.NEWEST_FIRST,
        limit: Int = 50,
        offset: Int = 0,
    ): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        loadAttachmentMessages(state, sort).drop(offset).take(limit)
    }

    suspend fun countByAttachmentState(state: MessageAttachmentState): Int = withContext(Dispatchers.IO) {
        loadAttachmentMessages(state, MessageSearchSort.NEWEST_FIRST).size
    }

    suspend fun countSearch(keyword: String, mode: MessageSearchMode): Int = withContext(Dispatchers.IO) {
        if (mode == MessageSearchMode.FUZZY) {
            val terms = keyword.split(Regex("\\s+")).filter(String::isNotEmpty)
            if (terms.isEmpty()) return@withContext 0
            val cursor = db.query(
                "SELECT text FROM message_search_cache WHERE instr(text, ?) > 0",
                arrayOf(terms.first()),
            )
            return@withContext cursor.use {
                var count = 0
                while (it.moveToNext()) {
                    if (it.getString(0).findOrderedTerms(terms) != null) count++
                }
                count
            }
        } else {
            val column = if (mode == MessageSearchMode.TITLE_ONLY) "title" else "text"
            val countTarget = if (mode == MessageSearchMode.TITLE_ONLY) {
                "COUNT(DISTINCT conversation_id)"
            } else {
                "COUNT(*)"
            }
            val cursor = db.query(
                "SELECT $countTarget FROM message_search_cache WHERE instr($column, ?) > 0",
                arrayOf(keyword),
            )
            return@withContext cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }
    }

    private fun searchExact(
        keyword: String,
        sort: MessageSearchSort,
        mode: MessageSearchMode,
        limit: Int,
        offset: Int,
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
            FROM message_search_cache
            WHERE instr(${if (titleOnly) "title" else "text"}, ?) > 0
            $groupBy
            ORDER BY $orderBy
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf<Any?>(keyword, limit, offset),
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

    private fun loadAttachmentMessages(
        state: MessageAttachmentState,
        sort: MessageSearchSort,
    ): List<MessageSearchResult> {
        val orderBy = when (sort) {
            MessageSearchSort.RELEVANCE, MessageSearchSort.NEWEST_FIRST -> "c.update_at DESC"
            MessageSearchSort.OLDEST_FIRST -> "c.update_at ASC"
        }
        val cursor = db.query(
            """
            SELECT DISTINCT mn.id, message.value, mn.conversation_id, c.title, c.update_at
            FROM message_node mn
            JOIN conversationentity c ON c.id = mn.conversation_id,
                 json_each(mn.messages) message,
                 json_each(json_extract(message.value, '$.parts')) part
            WHERE json_extract(part.value, '$.url') IS NOT NULL
            ORDER BY $orderBy
            """.trimIndent()
        )
        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    val message = JsonInstant.decodeFromString<UIMessage>(it.getString(1))
                    val attachments = message.parts.filter {
                        it is UIMessagePart.Image ||
                            it is UIMessagePart.Video ||
                            it is UIMessagePart.Audio ||
                            it is UIMessagePart.Document
                    }
                    val hasMissingLocalAttachment = attachments.any { part ->
                        val url = when (part) {
                            is UIMessagePart.Image -> part.url
                            is UIMessagePart.Video -> part.url
                            is UIMessagePart.Audio -> part.url
                            is UIMessagePart.Document -> part.url
                            else -> return@any false
                        }
                        url.startsWith("file://") &&
                            runCatching { !url.toUri().toFile().isFile }.getOrDefault(true)
                    }
                    val matches = when (state) {
                        MessageAttachmentState.EXISTS -> !hasMissingLocalAttachment
                        MessageAttachmentState.MISSING -> hasMissingLocalAttachment
                    }
                    if (matches) {
                        add(
                            MessageSearchResult(
                                nodeId = it.getString(0),
                                messageId = message.id.toString(),
                                conversationId = it.getString(2),
                                title = it.getString(3),
                                updateAt = Instant.ofEpochMilli(it.getLong(4)),
                                snippet = message.extractFtsText().ifBlank {
                                    attachments.filterIsInstance<UIMessagePart.Document>()
                                        .joinToString("\n") { document -> document.fileName }
                                },
                            )
                        )
                    }
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

internal fun String.findOrderedTerms(terms: List<String>): List<IntRange>? {
    if (terms.isEmpty()) return emptyList()
    var searchStart = 0
    var bestRanges: List<IntRange>? = null
    var bestGapCount = Int.MAX_VALUE
    while (searchStart < length) {
        val forwardRanges = mutableListOf<IntRange>()
        var nextSearchStart = searchStart
        for (term in terms) {
            val start = indexOf(term, startIndex = nextSearchStart)
            if (start < 0) return bestRanges
            forwardRanges += start until start + term.length
            nextSearchStart = start + term.length
        }

        val compactRanges = forwardRanges.toMutableList()
        for (index in terms.lastIndex - 1 downTo 0) {
            val latestStart = compactRanges[index + 1].first - terms[index].length
            val start = lastIndexOf(terms[index], startIndex = latestStart)
            compactRanges[index] = start until start + terms[index].length
        }
        val gapCount = compactRanges.orderedTermGapCount()
        if (gapCount < bestGapCount) {
            bestRanges = compactRanges
            bestGapCount = gapCount
            if (gapCount == 0) break
        }
        searchStart = compactRanges.first().first + 1
    }
    return bestRanges
}

internal fun List<IntRange>.orderedTermGapCount(): Int =
    zipWithNext().sumOf { (current, next) -> (next.first - current.last - 1).coerceAtLeast(0) }

private fun String.orderedSnippet(ranges: List<IntRange>): String {
    val firstMatch = ranges.first().first
    val lastMatch = ranges.last().last
    val lineStart = lastIndexOf('\n', startIndex = (firstMatch - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    val previousLineStart = if (lineStart == 0) {
        0
    } else {
        lastIndexOf('\n', startIndex = (lineStart - 2).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
    }
    val lineEnd = indexOf('\n', startIndex = lastMatch + 1)
        .let { if (it < 0) length else it }
    val nextLineEnd = if (lineEnd == length) {
        length
    } else {
        indexOf('\n', startIndex = lineEnd + 1).let { if (it < 0) length else it }
    }
    return buildString {
        if (previousLineStart > 0) append("...")
        var sourceIndex = previousLineStart
        ranges.forEach { range ->
            if (range.first > sourceIndex) append(this@orderedSnippet, sourceIndex, range.first)
            append('[')
            append(this@orderedSnippet, range.first, range.last + 1)
            append(']')
            sourceIndex = range.last + 1
        }
        if (sourceIndex < nextLineEnd) append(this@orderedSnippet, sourceIndex, nextLineEnd)
        if (nextLineEnd < length) append("...")
    }
}

private fun UIMessage.extractFtsText(): String =
    parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }

internal fun rebuildMessageSearchCache(db: SupportSQLiteDatabase) {
    db.execSQL("DELETE FROM message_search_cache")
    db.query("SELECT id, title, update_at FROM conversationentity").use { conversations ->
        while (conversations.moveToNext()) {
            val conversationId = conversations.getString(0)
            val title = conversations.getString(1)
            val updateAt = conversations.getLong(2).toString()
            db.query(
                """
                SELECT id, messages
                FROM message_node
                WHERE conversation_id = ?
                ORDER BY node_index
                """.trimIndent(),
                arrayOf(conversationId),
            ).use { nodes ->
                while (nodes.moveToNext()) {
                    val nodeId = nodes.getString(0)
                    val messages = JsonInstant.decodeFromString<List<UIMessage>>(nodes.getString(1))
                    messages.forEach { message ->
                        val text = message.extractFtsText()
                        if (text.isNotBlank()) {
                            db.execSQL(
                                """
                                INSERT INTO message_search_cache(
                                    text, node_id, message_id, conversation_id, title, update_at
                                ) VALUES (?, ?, ?, ?, ?, ?)
                                """.trimIndent(),
                                arrayOf(
                                    text,
                                    nodeId,
                                    message.id.toString(),
                                    conversationId,
                                    title,
                                    updateAt,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
