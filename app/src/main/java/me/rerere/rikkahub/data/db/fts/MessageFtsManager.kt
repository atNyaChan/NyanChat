package me.rerere.rikkahub.data.db.fts

import android.database.Cursor
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.files.withRuntimeLocalUrls
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

/** 按 (会话, 消息节点, 消息) 定位一条消息所需的最小信息, 用于附件引用弹窗的消息预览。 */
data class MessagePreviewKey(
    val conversationId: String,
    val nodeId: String,
    val messageId: String,
)

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
    /** 会话所属助手 ID，用于在结果中标注来源。 */
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

/** 搜索页「匹配内容」：只在这些内容里查找关键词，可多选。 */
enum class MessageSearchContent {
    /** 用户发出的消息正文 */
    USER,

    /** 模型回复的正文（以及工具消息等非用户角色的正文） */
    BODY,

    /** 思考过程 */
    REASONING,

    /** 工具调用的输入与输出 */
    TOOL,
    ;

    companion object {
        val DEFAULT: Set<MessageSearchContent> = setOf(USER, BODY)
    }
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
    val contents: Set<MessageSearchContent> = MessageSearchContent.DEFAULT,
) {
    val hasMessageFilter: Boolean
        get() = modelId != null || manuallyEdited || attachmentState != null
}

private class IndexedMessage(
    val rowid: Long,
    val nodeId: String,
    val texts: MessageCacheTexts,
)

class MessageFtsManager(private val database: AppDatabase, private val filesDir: File) {

    private val db get() = database.openHelper.writableDatabase

    /**
     * 增量更新会话的索引：只重写新增或内容变了的消息，删掉已不存在的消息。
     * 每次保存会话都会调用，长会话里大部分消息没变，不必整份删掉重新写。
     * 标题与会话更新时间不入索引，搜索时联表从 conversationentity 读取。
     */
    suspend fun indexConversation(conversation: Conversation) = database.withTransaction {
        val conversationId = conversation.id.toString()
        val indexed = HashMap<String, IndexedMessage>()
        val stale = mutableListOf<Long>()
        db.query(
            """
            SELECT rowid, node_id, message_id, user_text, body_text, reasoning_text, tool_text
            FROM message_search_cache
            WHERE conversation_id = ?
            """.trimIndent(),
            arrayOf(conversationId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val row = IndexedMessage(
                    rowid = cursor.getLong(0),
                    nodeId = cursor.getString(1),
                    texts = MessageCacheTexts(
                        user = cursor.getString(3).orEmpty(),
                        body = cursor.getString(4).orEmpty(),
                        reasoning = cursor.getString(5).orEmpty(),
                        tool = cursor.getString(6).orEmpty(),
                    ),
                )
                if (indexed.putIfAbsent(cursor.getString(2), row) != null) stale.add(row.rowid)
            }
        }
        conversation.messageNodes.forEach { node ->
            val nodeId = node.id.toString()
            node.messages.forEach { message ->
                val texts = message.extractCacheTexts()
                val existing = indexed.remove(message.id.toString())
                if (existing != null && existing.nodeId == nodeId && existing.texts == texts) {
                    return@forEach
                }
                existing?.let { stale.add(it.rowid) }
                if (!texts.isBlank) {
                    db.execSQL(
                        """
                        INSERT INTO message_search_cache(
                            node_id, message_id, conversation_id, message_at,
                            user_text, body_text, reasoning_text, tool_text
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf(
                            nodeId,
                            message.id.toString(),
                            conversationId,
                            message.createdAtInstant().toEpochMilli().toString(),
                            texts.user,
                            texts.body,
                            texts.reasoning,
                            texts.tool,
                        )
                    )
                }
            }
        }
        indexed.values.forEach { stale.add(it.rowid) }
        stale.forEach { rowid ->
            db.execSQL("DELETE FROM message_search_cache WHERE rowid = ?", arrayOf(rowid))
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
        searchCached(keyword, sort, mode, listOfNotNull(assistantId)).drop(offset).take(limit)
    }

    /** 只在给定的这些助手的对话里搜索 */
    suspend fun searchOfAssistants(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
        assistantIds: List<String>,
    ): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        searchCached(keyword, sort, MessageSearchMode.FUZZY, assistantIds)
    }

    /**
     * 统一搜索入口：关键词、模型筛选、手动编辑筛选、附件筛选与匹配内容可以同时生效。
     * 匹配内容只读 `message_search_cache` 的分类列，任何组合都不会为了内容去遍历消息 JSON；
     * 只有模型/手动编辑/附件这些消息级筛选才扫描消息 JSON。
     */
    suspend fun searchCombined(request: MessageSearchRequest): List<MessageSearchResult> =
        withContext(Dispatchers.IO) {
            if (!request.hasMessageFilter) {
                searchCached(
                    keyword = request.keyword,
                    sort = request.sort,
                    mode = request.mode,
                    assistantIds = listOfNotNull(request.assistantId),
                    contents = request.contents,
                )
            } else {
                searchCombinedScan(request)
            }
        }

    private fun searchCached(
        keyword: String,
        sort: MessageSearchSort,
        mode: MessageSearchMode,
        assistantIds: List<String> = emptyList(),
        contents: Set<MessageSearchContent> = MessageSearchContent.DEFAULT,
    ): List<MessageSearchResult> {
        if (mode != MessageSearchMode.FUZZY) {
            return searchExact(keyword, sort, mode, assistantIds, contents)
        }
        val terms = keyword.split(Regex("\\s+")).filter(String::isNotEmpty)
        if (terms.isEmpty()) return emptyList()
        // 缓存缺少 message_at 时退回该会话的更新时间。
        val timeExpression = "COALESCE(msc.message_at, c.update_at)"
        val orderBy = when (sort) {
            MessageSearchSort.RELEVANCE, MessageSearchSort.NEWEST_FIRST -> "$timeExpression DESC"
            MessageSearchSort.OLDEST_FIRST -> "$timeExpression ASC"
        }
        val contentColumns = contents.cacheColumns()
        if (contentColumns.isEmpty()) return emptyList()
        val matchCondition = contentColumns.joinToString(" OR ") {
            "instr(lower(msc.$it), lower(?)) > 0"
        }
        val queryArgs = Array(contentColumns.size) { terms.first() } + assistantIds.toTypedArray()
        val cursor = db.query(
            """
            SELECT msc.node_id, msc.message_id, msc.conversation_id, c.title, $timeExpression,
                   msc.user_text, msc.body_text, msc.reasoning_text, msc.tool_text,
                   c.assistant_id, f.name
            FROM message_search_cache msc
            JOIN conversationentity c ON c.id = msc.conversation_id
            LEFT JOIN conversation_folder f ON f.id = c.folder_id
            WHERE $matchCondition
            ${assistantFilter(assistantIds)}
            ORDER BY $orderBy
            """.trimIndent(),
            queryArgs,
        )
        val matches = buildList {
            cursor.use {
                while (it.moveToNext()) {
                    val text = combineContentText(contents, it.cacheRowTexts())
                    text.findOrderedTerms(terms)?.let { ranges ->
                        add(
                            ranges.orderedTermGapCount() to MessageSearchResult(
                                nodeId = it.getString(0),
                                messageId = it.getString(1),
                                conversationId = it.getString(2),
                                title = it.getString(3),
                                timeAt = Instant.ofEpochMilli(it.getLong(4)),
                                snippet = text.orderedSnippet(ranges),
                                assistantId = it.getString(9),
                                folderName = it.getString(10),
                            )
                        )
                    }
                }
            }
        }
        val ordered = if (sort == MessageSearchSort.RELEVANCE) {
            matches.sortedWith(
                compareBy<Pair<Int, MessageSearchResult>> { it.first }
                    .thenByDescending { it.second.timeAt }
            )
        } else {
            matches
        }
        return ordered.map { it.second }
    }

    private fun searchCombinedScan(request: MessageSearchRequest): List<MessageSearchResult> {
        val keyword = request.keyword
        val mode = request.mode
        val contents = request.contents
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
                // 库里是相对路径，附件存在性判断需要展开成当前安装的绝对 url
                if (attachmentState != null &&
                    !message.withRuntimeLocalUrls(filesDir).matchesAttachmentState(attachmentState)
                ) {
                    continue
                }

                val title = it.getString(3)
                val text = message.extractCacheTexts().select(contents)
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

    /**
     * 按 (会话, 消息节点, 消息) 定位消息, 返回与搜索页一致的结果结构, 供附件引用弹窗预览。
     * 消息正文为空时退回附件文件名, 保证预览仍有内容; 已不存在的消息不会出现在结果里。
     */
    suspend fun getMessagePreviews(
        keys: List<MessagePreviewKey>,
    ): Map<MessagePreviewKey, MessageSearchResult> = withContext(Dispatchers.IO) {
        val wanted = keys.toSet()
        if (wanted.isEmpty()) return@withContext emptyMap()
        val previews = mutableMapOf<MessagePreviewKey, MessageSearchResult>()
        wanted.map(MessagePreviewKey::nodeId).distinct().forEach { nodeId ->
            val cursor = db.query(
                """
                SELECT mn.id, message.value, mn.conversation_id, c.title, c.assistant_id, f.name
                FROM message_node mn
                JOIN conversationentity c ON c.id = mn.conversation_id
                LEFT JOIN conversation_folder f ON f.id = c.folder_id,
                     json_each(mn.messages) message
                WHERE mn.id = ?
                """.trimIndent(),
                arrayOf(nodeId),
            )
            cursor.use {
                while (it.moveToNext()) {
                    val message = JsonInstant.decodeFromString<UIMessage>(it.getString(1))
                    val key = MessagePreviewKey(
                        conversationId = it.getString(2),
                        nodeId = it.getString(0),
                        messageId = message.id.toString(),
                    )
                    if (key !in wanted) continue
                    val text = message.extractCacheTexts().combined
                    previews[key] = MessageSearchResult(
                        nodeId = key.nodeId,
                        messageId = key.messageId,
                        conversationId = key.conversationId,
                        title = it.getString(3),
                        timeAt = message.createdAtInstant(),
                        snippet = text.ifBlank { message.attachmentFileNames() },
                        assistantId = it.getString(4),
                        folderName = it.getString(5),
                    )
                }
            }
        }
        previews
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

    private fun assistantFilter(assistantIds: List<String>): String =
        if (assistantIds.isEmpty()) {
            ""
        } else {
            """
            AND EXISTS (
                SELECT 1 FROM conversationentity AS conversation
                WHERE conversation.id = msc.conversation_id
                  AND conversation.assistant_id IN (${assistantIds.joinToString(",") { "?" }})
            )
            """.trimIndent()
        }

    private fun sqlArgs(vararg args: Any?): Array<Any?> = args.filterNotNull().toTypedArray()

    private fun searchExact(
        keyword: String,
        sort: MessageSearchSort,
        mode: MessageSearchMode,
        assistantIds: List<String> = emptyList(),
        contents: Set<MessageSearchContent> = MessageSearchContent.DEFAULT,
    ): List<MessageSearchResult> {
        val titleOnly = mode == MessageSearchMode.TITLE_ONLY
        val direction = if (sort == MessageSearchSort.OLDEST_FIRST) "ASC" else "DESC"
        // 仅匹配标题时每个会话只保留一行，时间沿用该会话的更新时间（忽略消息时间）；
        // 消息级结果缺少 message_at 时退回该会话的更新时间。
        val timeColumn = if (titleOnly) "c.update_at" else "COALESCE(msc.message_at, c.update_at)"
        val groupBy = if (titleOnly) "GROUP BY msc.conversation_id" else ""
        val contentColumns = if (titleOnly) emptyList() else contents.cacheColumns()
        // 仅在选中的分类列里查找关键词，切换匹配内容不需要遍历消息 JSON
        val matchCondition = if (titleOnly) {
            "instr(c.title, ?) > 0"
        } else {
            if (contentColumns.isEmpty()) return emptyList()
            contentColumns.joinToString(" OR ") { "instr(msc.$it, ?) > 0" }
        }
        val matchArgs = if (titleOnly) {
            arrayOf(keyword)
        } else {
            Array(contentColumns.size) { keyword }
        }
        val queryArgs = matchArgs + assistantIds.toTypedArray()
        val cursor = db.query(
            """
            SELECT msc.node_id, msc.message_id, msc.conversation_id, c.title, $timeColumn,
                   msc.user_text, msc.body_text, msc.reasoning_text, msc.tool_text,
                   c.assistant_id, f.name
            FROM message_search_cache msc
            JOIN conversationentity c ON c.id = msc.conversation_id
            LEFT JOIN conversation_folder f ON f.id = c.folder_id
            WHERE $matchCondition
            ${assistantFilter(assistantIds)}
            $groupBy
            ORDER BY $timeColumn $direction
            """.trimIndent(),
            queryArgs,
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
                            snippet = if (titleOnly) {
                                ""
                            } else {
                                combineContentText(contents, it.cacheRowTexts())
                                    .exactSnippet(keyword)
                            },
                            assistantId = it.getString(9),
                            folderName = it.getString(10),
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

/**
 * 一条消息按匹配内容拆开的缓存文本。
 * `user` / `body` 按消息角色二选一，`reasoning` 与 `tool` 各自独立；
 * `combined` 为用户正文加其余角色正文，供预览使用。
 */
private data class MessageCacheTexts(
    val user: String,
    val body: String,
    val reasoning: String,
    val tool: String,
) {
    val combined: String = listOf(user, body).filter(String::isNotBlank).joinToString("\n")

    val isBlank: Boolean
        get() = combined.isBlank() && reasoning.isBlank() && tool.isBlank()

    /** 拼接搜索页选中的那些内容；与 [combineContentText] 同一套规则。 */
    fun select(contents: Set<MessageSearchContent>): String =
        combineContentText(contents, listOf(user, body, reasoning, tool))
}

private fun UIMessage.extractCacheTexts(): MessageCacheTexts {
    val textParts = parts.filterIsInstance<UIMessagePart.Text>()
        .map(UIMessagePart.Text::text)
        .filter(String::isNotBlank)
        .joinToString("\n")
    val isUser = role == MessageRole.USER
    return MessageCacheTexts(
        user = if (isUser) textParts else "",
        body = if (isUser) "" else textParts,
        reasoning = parts.filterIsInstance<UIMessagePart.Reasoning>()
            .map(UIMessagePart.Reasoning::reasoning)
            .filter(String::isNotBlank)
            .joinToString("\n"),
        tool = toolCacheText(),
    )
}

/** 工具调用的输入与输出（含结果 JSON），不含工具名。 */
@Suppress("DEPRECATION")
private fun UIMessage.toolCacheText(): String {
    val pieces = buildList {
        parts.forEach { part ->
            when (part) {
                is UIMessagePart.Tool -> {
                    add(part.input)
                    part.output.filterIsInstance<UIMessagePart.Text>()
                        .forEach { add(it.text) }
                }

                is UIMessagePart.ServerTool -> {
                    part.input?.let { add(it.toString()) }
                    part.output?.let { add(it.toString()) }
                }

                is UIMessagePart.ToolCall -> add(part.arguments)

                is UIMessagePart.ToolResult -> {
                    add(part.arguments.toString())
                    add(part.content.toString())
                }

                else -> {}
            }
        }
    }
    return pieces.filter(String::isNotBlank).joinToString("\n")
}

/** 按枚举顺序映射到缓存表的分类列。 */
private fun Set<MessageSearchContent>.cacheColumns(): List<String> =
    MessageSearchContent.entries.filter(this::contains).map {
        when (it) {
            MessageSearchContent.USER -> "user_text"
            MessageSearchContent.BODY -> "body_text"
            MessageSearchContent.REASONING -> "reasoning_text"
            MessageSearchContent.TOOL -> "tool_text"
        }
    }

/** 拼接选中内容的缓存文本，顺序固定为用户→正文→思考→工具。 */
private fun combineContentText(contents: Set<MessageSearchContent>, texts: List<String>): String =
    MessageSearchContent.entries
        .filter(contents::contains)
        .map { texts[it.ordinal] }
        .filter(String::isNotBlank)
        .joinToString("\n")

/** 缓存查询里按枚举顺序取出的四个分类正文列（SELECT 中紧随 title/time 之后）。 */
private fun Cursor.cacheRowTexts(): List<String> = listOf(
    getString(5),
    getString(6),
    getString(7),
    getString(8),
).map(String::orEmpty)

/** 消息自身的生成时间，用于搜索结果展示与排序。 */
private fun UIMessage.createdAtInstant(): Instant =
    createdAt.toInstant(TimeZone.currentSystemDefault()).toJavaInstant()

internal fun rebuildMessageSearchCache(db: SupportSQLiteDatabase) {
    db.execSQL("DELETE FROM message_search_cache")
    db.query("SELECT id FROM conversationentity").use { conversations ->
        while (conversations.moveToNext()) {
            val conversationId = conversations.getString(0)
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
                        val texts = message.extractCacheTexts()
                        if (!texts.isBlank) {
                            db.execSQL(
                                """
                                INSERT INTO message_search_cache(
                                    node_id, message_id, conversation_id, message_at,
                                    user_text, body_text, reasoning_text, tool_text
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                """.trimIndent(),
                                arrayOf(
                                    nodeId,
                                    message.id.toString(),
                                    conversationId,
                                    message.createdAtInstant().toEpochMilli().toString(),
                                    texts.user,
                                    texts.body,
                                    texts.reasoning,
                                    texts.tool,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
