package me.rerere.rikkahub.data.db.fts

import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

data class MessageSearchResult(
    val nodeId: String,
    val messageId: String,
    val conversationId: String,
    val title: String,
    /**
     * 搜索结果展示与排序使用的时间。
     * 消息级结果为消息自身的生成时间；仅匹配标题的结果为对应会话的更新时间。
     */
    val timeAt: Instant,
    val snippet: String,
    /** 会话所属助手 ID，用于在结果中标注来源；旧调用方可能拿不到此值。 */
    val assistantId: String? = null,
    /** 会话所属文件夹名称，未归类时为 null。 */
    val folderName: String? = null,
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
    NONE,
    EXISTS,
    MISSING,
}

/** 搜索页提交的统一查询条件，各条件可同时生效。 */
data class MessageSearchRequest(
    val keyword: String = "",
    val mode: MessageSearchMode = MessageSearchMode.EXACT,
    val sort: MessageSearchSort = MessageSearchSort.NEWEST_FIRST,
    val modelId: Uuid? = null,
    val manuallyEdited: Boolean = false,
    val attachmentState: MessageAttachmentState? = null,
    val assistantId: String? = null,
) {
    val hasMessageFilter: Boolean
        get() = modelId != null || manuallyEdited || attachmentState != null
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
                            text, node_id, message_id, conversation_id, title, message_at, update_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf(
                            text,
                            node.id.toString(),
                            message.id.toString(),
                            conversationId,
                            conversation.title,
                            message.createdAtInstant().toEpochMilli().toString(),
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
        assistantId: String? = null,
    ): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        searchCached(keyword, sort, mode, assistantId).drop(offset).take(limit)
    }

    /**
     * 统一搜索入口：关键词、模型筛选、手动编辑筛选与附件筛选可以同时生效。
     * 没有任何消息级筛选时复用 `message_search_cache` 快速路径，
     * 否则扫描消息 JSON 在内存中逐条过滤。
     */
    suspend fun searchCombined(request: MessageSearchRequest): List<MessageSearchResult> =
        withContext(Dispatchers.IO) {
            if (!request.hasMessageFilter) {
                searchCached(request.keyword, request.sort, request.mode, request.assistantId)
            } else {
                searchCombinedScan(request)
            }
        }

    private fun searchCached(
        keyword: String,
        sort: MessageSearchSort,
        mode: MessageSearchMode,
        assistantId: String? = null,
    ): List<MessageSearchResult> {
        if (mode != MessageSearchMode.FUZZY) {
            return searchExact(keyword, sort, mode, assistantId)
        }
        val terms = keyword.split(Regex("\\s+")).filter(String::isNotEmpty)
        if (terms.isEmpty()) return emptyList()
        // 若缓存由旧版写入（无 message_at），退回该会话的更新时间。
        val timeExpression = "COALESCE(msc.message_at, msc.update_at)"
        val orderBy = when (sort) {
            MessageSearchSort.RELEVANCE, MessageSearchSort.NEWEST_FIRST -> "$timeExpression DESC"
            MessageSearchSort.OLDEST_FIRST -> "$timeExpression ASC"
        }
        val cursor = db.query(
            """
            SELECT msc.node_id, msc.message_id, msc.conversation_id, msc.title, $timeExpression, msc.text,
                   c.assistant_id, f.name
            FROM message_search_cache msc
            JOIN conversationentity c ON c.id = msc.conversation_id
            LEFT JOIN conversation_folder f ON f.id = c.folder_id
            WHERE instr(lower(msc.text), lower(?)) > 0
            ${assistantFilter(assistantId)}
            ORDER BY $orderBy
            """.trimIndent(),
            sqlArgs(terms.first(), assistantId),
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
                                timeAt = Instant.ofEpochMilli(it.getLong(4)),
                                snippet = text.orderedSnippet(ranges),
                                assistantId = it.getString(6),
                                folderName = it.getString(7),
                            )
                        )
                    }
                }
            }
        }
        val ordered = if (sort == MessageSearchSort.RELEVANCE) {
            matches.sortedWith(compareBy<Pair<Int, MessageSearchResult>> { it.first }
                .thenByDescending { it.second.timeAt })
        } else {
            matches
        }
        return ordered.map { it.second }
    }

    private fun searchCombinedScan(request: MessageSearchRequest): List<MessageSearchResult> {
        val keyword = request.keyword
        val mode = request.mode
        val modelId = request.modelId
        val manuallyEdited = request.manuallyEdited
        val attachmentState = request.attachmentState
        val assistantId = request.assistantId
        val titleOnly = mode == MessageSearchMode.TITLE_ONLY
        val fuzzyTerms = if (mode == MessageSearchMode.FUZZY) {
            keyword.split(Regex("\\s+")).filter(String::isNotEmpty)
        } else {
            emptyList()
        }
        if (mode == MessageSearchMode.FUZZY && keyword.isNotBlank() && fuzzyTerms.isEmpty()) {
            return emptyList()
        }

        data class Candidate(val result: MessageSearchResult, val relevanceGap: Int)

        val cursor = db.query(
            """
            SELECT mn.id, message.value, mn.conversation_id, c.title,
                   c.update_at, c.assistant_id, f.name
            FROM message_node mn
            JOIN conversationentity c ON c.id = mn.conversation_id
            LEFT JOIN conversation_folder f ON f.id = c.folder_id,
                 json_each(mn.messages) message
            WHERE 1 = 1
            ${conversationAssistantFilter(assistantId)}
            """.trimIndent(),
            if (assistantId != null) arrayOf(assistantId) else emptyArray(),
        )

        val seenTitleConversations = mutableSetOf<String>()
        val candidates = mutableListOf<Candidate>()
        cursor.use {
            while (it.moveToNext()) {
                val message = JsonInstant.decodeFromString<UIMessage>(it.getString(1))
                when {
                    manuallyEdited -> {
                        if (message.role != MessageRole.ASSISTANT || message.modelId != null) continue
                    }
                    modelId != null -> {
                        if (message.role != MessageRole.ASSISTANT || message.modelId != modelId) continue
                    }
                }
                if (attachmentState != null &&
                    !message.matchesAttachmentState(attachmentState)
                ) {
                    continue
                }

                val title = it.getString(3)
                val text = message.extractFtsText()
                val conversationId = it.getString(2)
                val timeAt: Instant
                val snippet: String
                val relevanceGap: Int
                if (titleOnly) {
                    if (keyword.isNotBlank() && !title.contains(keyword)) continue
                    if (!seenTitleConversations.add(conversationId)) continue
                    timeAt = Instant.ofEpochMilli(it.getLong(4))
                    snippet = ""
                    relevanceGap = 0
                } else {
                    when {
                        keyword.isBlank() -> {
                            snippet = text.ifBlank { message.attachmentFileNames() }
                            relevanceGap = 0
                        }
                        mode == MessageSearchMode.FUZZY -> {
                            val ranges = text.findOrderedTerms(fuzzyTerms) ?: continue
                            snippet = text.orderedSnippet(ranges)
                            relevanceGap = ranges.orderedTermGapCount()
                        }
                        else -> {
                            if (text.indexOf(keyword) < 0) continue
                            snippet = text.exactSnippet(keyword)
                            relevanceGap = 0
                        }
                    }
                    timeAt = message.createdAtInstant()
                }
                candidates += Candidate(
                    result = MessageSearchResult(
                        nodeId = it.getString(0),
                        messageId = message.id.toString(),
                        conversationId = conversationId,
                        title = title,
                        timeAt = timeAt,
                        snippet = snippet,
                        assistantId = it.getString(5),
                        folderName = it.getString(6),
                    ),
                    relevanceGap = relevanceGap,
                )
            }
        }

        val ordered = when (request.sort) {
            MessageSearchSort.RELEVANCE -> if (mode == MessageSearchMode.FUZZY &&
                keyword.isNotBlank() &&
                !titleOnly
            ) {
                candidates.sortedWith(
                    compareBy<Candidate> { it.relevanceGap }.thenByDescending { it.result.timeAt }
                )
            } else {
                candidates.sortedByDescending { it.result.timeAt }
            }
            MessageSearchSort.NEWEST_FIRST -> candidates.sortedByDescending { it.result.timeAt }
            MessageSearchSort.OLDEST_FIRST -> candidates.sortedBy { it.result.timeAt }
        }
        return ordered.map { it.result }
    }

    suspend fun countByModel(modelId: Uuid, assistantId: String? = null): Int = withContext(Dispatchers.IO) {
        val cursor = db.query(
            """
            SELECT COUNT(*)
            FROM message_node mn
            JOIN conversationentity c ON c.id = mn.conversation_id,
                 json_each(mn.messages) j
            WHERE json_extract(j.value, '$.role') = 'assistant'
              AND json_extract(j.value, '$.modelId') = ?
              ${conversationAssistantFilter(assistantId)}
            """.trimIndent(),
            sqlArgs(modelId.toString(), assistantId),
        )
        cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    suspend fun getUsedModelIds(assistantId: String? = null): List<Uuid> = withContext(Dispatchers.IO) {
        val cursor = db.query(
            """
            SELECT DISTINCT json_extract(j.value, '$.modelId')
            FROM message_node mn
            JOIN conversationentity c ON c.id = mn.conversation_id,
                 json_each(mn.messages) j
            WHERE json_extract(j.value, '$.role') = 'assistant'
              AND json_extract(j.value, '$.modelId') IS NOT NULL
              ${conversationAssistantFilter(assistantId)}
            """.trimIndent(),
            if (assistantId != null) arrayOf(assistantId) else emptyArray(),
        )
        buildList {
            cursor.use {
                while (it.moveToNext()) {
                    runCatching { Uuid.parse(it.getString(0)) }.getOrNull()?.let(::add)
                }
            }
        }
    }

    private fun conversationAssistantFilter(assistantId: String?): String =
        if (assistantId != null) " AND c.assistant_id = ?" else ""

    private fun assistantFilter(assistantId: String?): String =
        if (assistantId != null) {
            """
            AND EXISTS (
                SELECT 1 FROM conversationentity AS conversation
                WHERE conversation.id = msc.conversation_id
                  AND conversation.assistant_id = ?
            )
            """.trimIndent()
        } else {
            ""
        }

    private fun sqlArgs(vararg args: Any?): Array<Any?> = args.filterNotNull().toTypedArray()

    private fun searchExact(
        keyword: String,
        sort: MessageSearchSort,
        mode: MessageSearchMode,
        assistantId: String? = null,
    ): List<MessageSearchResult> {
        val titleOnly = mode == MessageSearchMode.TITLE_ONLY
        val direction = if (sort == MessageSearchSort.OLDEST_FIRST) "ASC" else "DESC"
        // 仅匹配标题时每个会话只保留一行，时间沿用该会话的更新时间（忽略消息时间）；
        // 消息级结果若缓存由旧版写入（无 message_at），退回该会话的更新时间。
        val timeColumn = if (titleOnly) "msc.update_at" else "COALESCE(msc.message_at, msc.update_at)"
        val groupBy = if (titleOnly) "GROUP BY msc.conversation_id" else ""
        val cursor = db.query(
            """
            SELECT msc.node_id, msc.message_id, msc.conversation_id, msc.title, $timeColumn, msc.text,
                   c.assistant_id, f.name
            FROM message_search_cache msc
            JOIN conversationentity c ON c.id = msc.conversation_id
            LEFT JOIN conversation_folder f ON f.id = c.folder_id
            WHERE instr(${if (titleOnly) "msc.title" else "msc.text"}, ?) > 0
            ${assistantFilter(assistantId)}
            $groupBy
            ORDER BY $timeColumn $direction
            """.trimIndent(),
            sqlArgs(keyword, assistantId),
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
                            timeAt = Instant.ofEpochMilli(it.getLong(4)),
                            snippet = if (titleOnly) "" else it.getString(5).exactSnippet(keyword),
                            assistantId = it.getString(6),
                            folderName = it.getString(7),
                        )
                    )
                }
            }
        }
    }

}

private fun UIMessage.attachmentParts(): List<UIMessagePart> = parts.filter {
    it is UIMessagePart.Image ||
        it is UIMessagePart.Video ||
        it is UIMessagePart.Audio ||
        it is UIMessagePart.Document
}

private fun UIMessage.hasMissingLocalAttachment(): Boolean = attachmentParts().any { part ->
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

private fun UIMessage.matchesAttachmentState(state: MessageAttachmentState): Boolean {
    val attachments = attachmentParts()
    return when (state) {
        MessageAttachmentState.NONE -> attachments.isEmpty()
        MessageAttachmentState.EXISTS -> attachments.isNotEmpty() && !hasMissingLocalAttachment()
        MessageAttachmentState.MISSING -> hasMissingLocalAttachment()
    }
}

private fun UIMessage.attachmentFileNames(): String =
    attachmentParts().filterIsInstance<UIMessagePart.Document>()
        .joinToString("\n") { document -> document.fileName }

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
            val start = indexOfIgnoreCase(term, startIndex = nextSearchStart)
            if (start < 0) return bestRanges
            forwardRanges += start until start + term.length
            nextSearchStart = start + term.length
        }

        val compactRanges = forwardRanges.toMutableList()
        for (index in terms.lastIndex - 1 downTo 0) {
            val latestStart = compactRanges[index + 1].first - terms[index].length
            val start = lastIndexOfIgnoreCase(terms[index], startIndex = latestStart)
            if (start < 0) return bestRanges
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

private fun String.indexOfIgnoreCase(term: String, startIndex: Int): Int {
    if (term.isEmpty()) return startIndex.coerceIn(0, length)
    val lastPossibleStart = length - term.length
    var index = startIndex.coerceAtLeast(0)
    while (index <= lastPossibleStart) {
        if (regionMatches(index, term, 0, term.length, ignoreCase = true)) return index
        index++
    }
    return -1
}

private fun String.lastIndexOfIgnoreCase(term: String, startIndex: Int): Int {
    if (term.isEmpty()) return startIndex.coerceIn(0, length)
    var index = startIndex.coerceAtMost(length - term.length)
    while (index >= 0) {
        if (regionMatches(index, term, 0, term.length, ignoreCase = true)) return index
        index--
    }
    return -1
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

/** 消息自身的生成时间，用于搜索结果展示与排序。 */
private fun UIMessage.createdAtInstant(): Instant =
    createdAt.toInstant(TimeZone.currentSystemDefault()).toJavaInstant()

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
                                    text, node_id, message_id, conversation_id, title, message_at, update_at
                                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                                """.trimIndent(),
                                arrayOf(
                                    text,
                                    nodeId,
                                    message.id.toString(),
                                    conversationId,
                                    title,
                                    message.createdAtInstant().toEpochMilli().toString(),
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
