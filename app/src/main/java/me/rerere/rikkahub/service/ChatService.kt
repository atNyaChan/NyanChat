package me.rerere.rikkahub.service

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.agent.AgentEvent
import me.rerere.ai.agent.AgentFinishReason
import me.rerere.ai.agent.RetryAttempt
import me.rerere.ai.agent.runAgent
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.chatRequestBuiltInTools
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.finishPendingTools
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ChatRequestFactory
import me.rerere.rikkahub.data.ai.ChatTurnContext
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.ai.TranslationHandler
import me.rerere.rikkahub.data.ai.backgroundTextGenerationParams
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.prompts.buildCompressPrompt
import me.rerere.rikkahub.data.ai.tools.InvalidMcpServerNamesException
import me.rerere.rikkahub.data.ai.tools.shouldUseExternalWebSearch
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.FileUsage
import me.rerere.rikkahub.data.db.entity.FileUsageType
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.files.CommitInfo
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.attachmentShaFromUrl
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.bindConfig
import me.rerere.rikkahub.data.model.fillModelSnapshots
import me.rerere.rikkahub.data.model.fork
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.getStoredAssistantOf
import me.rerere.rikkahub.data.model.insertContextCheckpoint
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.setFollowAssistant
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.model.withAssistantUpdate
import me.rerere.rikkahub.data.model.withoutConversationFields
import me.rerere.rikkahub.data.model.withoutUnresumableToolCalls
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.wordCount
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

/** 输入框的上下文统计：这一步真正会发给模型的正文词数、工具数和附件数 */
data class RequestContextStats(
    val wordCount: Int,
    val toolCount: Int,
    val fileCount: Int,
)

enum class ChatErrorSolution {
    CheckFastModelSettings,
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val requestFactory: ChatRequestFactory,
    private val memoryConsolidationScheduler: MemoryConsolidationScheduler,
    private val translationHandler: TranslationHandler,
    private val providerManager: ProviderManager,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val folderRepository: FolderRepository,
) {
    /** 一轮生成的结果 */
    private sealed interface TurnResult {
        data class Finished(val reason: AgentFinishReason) : TurnResult

        /** 没能完成：错误已经报告给用户，消息队列也已暂停 */
        data object Failed : TurnResult
    }

    private val conversationStore = object : ConversationStore {
        override suspend fun prepareWrite(): (Conversation) -> Conversation {
            // 会话落库即视为开始：清掉“还没开始”的标记，并把助手的配置固定到会话上
            val settings = settingsStore.awaitLoaded()
            return { it.copy(newConversation = false).bindConfig(settings).fillModelSnapshots(settings) }
        }

        // fork：输入框的临时附件必须在落库前提交成 CAS，提交结果要同时写回内存，
        // 否则内存里还是 cacheDir 的临时 url，下一次落库又得重新提交。
        // flush/replace 都持有会话的写锁，这里的提交与删除会话天然互斥。
        override suspend fun beforeWrite(current: Conversation): Conversation {
            val commit = filesManager.commitConversationFiles(current)
            if (commit.infos.isNotEmpty()) pendingCommitInfos[current.id] = commit.infos
            return commit.conversation
        }

        override suspend fun write(previous: Conversation?, current: Conversation) {
            val commitInfos = pendingCommitInfos.remove(current.id).orEmpty()
            if (previous == null && !conversationRepo.existsConversationById(current.id)) {
                conversationRepo.insertConversation(current, commitInfos)
            } else {
                conversationRepo.updateConversation(current, commitInfos)
            }
            // 只删上次落库时引用、这次落库不再引用的附件；只在内存里出现过的附件宁可漏删也不误删
            if (previous != null && previous.messageNodes !== current.messageNodes) {
                withContext(Dispatchers.IO) { deleteUnreferencedFiles(previous, current) }
            }
        }
    }

    // 本次落库要透传给附件索引的来源信息（sha256 -> 展示名/MIME），由 beforeWrite 写入、write 消费
    private val pendingCommitInfos = ConcurrentHashMap<Uuid, Map<String, CommitInfo>>()

    private val sessionManager = ConversationSessionManager(
        scope = appScope,
        store = conversationStore,
        createInitialConversation = { id ->
            Conversation.ofId(id, assistantId = settingsStore.settingsFlow.value.getCurrentAssistant().id)
        },
        onGenerationFinished = ::onSessionGenerationFinished,
        onSessionRemoved = memoryConsolidationScheduler::onConversationLeft,
    )

    // 翻译任务（fork）：按 (会话, 消息) 记录，支持单个取消并对外暴露“翻译中”状态
    private val translationJobs = ConcurrentHashMap<Pair<Uuid, Uuid>, Job>()

    private val _translatingMessages = MutableStateFlow<Set<Pair<Uuid, Uuid>>>(emptySet())
    val translatingMessages: StateFlow<Set<Pair<Uuid, Uuid>>> = _translatingMessages.asStateFlow()

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    init {
        appScope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    // 进入后台时把只改了内存的内容补存一次，进程被杀也不会丢
                    if (event == Lifecycle.Event.ON_STOP) persistIdleConversations()
                }
            )
        }
    }

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    fun cleanup() = runCatching { sessionManager.cleanup() }

    private fun onSessionGenerationFinished(session: ConversationSession, cause: Throwable?) {
        if (cause != null) session.messageQueue.pause()
        if (session.state.value.isAwaitingToolApproval) {
            session.messageQueue.failReplyWaiters(context.getString(R.string.chat_page_voice_tool_approval))
        }
        appScope.launch { dispatchNextQueuedMessage(session.id) }
    }

    // 保留 UI/Web 的入口，生命周期和状态查询统一交给 SessionManager。
    fun addConversationReference(conversationId: Uuid) {
        sessionManager.acquire(conversationId)
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessionManager.release(conversationId)
    }

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        sessionManager.getConversationFlow(conversationId)

    /** 会话的最新内容：已加载的以内存为准，否则读数据库 */
    suspend fun getConversation(conversationId: Uuid): Conversation? =
        sessionManager.get(conversationId)?.takeIf { it.isInitialized }?.state?.value
            ?: conversationRepo.getConversationById(conversationId)

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        sessionManager.getGenerationJobStateFlow(conversationId)

    fun getGeneratingMessageIdFlow(conversationId: Uuid): Flow<Uuid?> =
        sessionManager.getGeneratingMessageIdFlow(conversationId)

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessionManager.getProcessingStatusFlow(conversationId)

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> = sessionManager.getConversationJobs()

    private fun launchGenerationJob(
        conversationId: Uuid,
        keepAliveInBackground: Boolean = true,
        block: suspend () -> Unit,
    ): Job {
        if (!keepAliveInBackground) return appScope.launch(start = CoroutineStart.LAZY) { block() }

        return appScope.launch(start = CoroutineStart.LAZY) {
            val generationId = Uuid.random()
            val foregroundStarted = ChatGenerationForegroundService.acquire(
                context = context,
                generationId = generationId,
                conversationId = conversationId,
            )
            try {
                block()
            } finally {
                if (foregroundStarted) {
                    ChatGenerationForegroundService.release(context, generationId)
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid, folderId: Uuid? = null) {
        sessionManager.withSession(conversationId) { session ->
            session.initialize(
                load = { loadConversation(conversationId) },
                create = { newConversation(conversationId, folderId) },
            )
            // 新建的空对话只留在内存里：发出第一条消息（或改名等写入）时才落库，
            // 否则每开一个「新聊天」都会在会话列表里留下一条空记录。
            settingsStore.selectAssistant(session.state.value.assistantId)
        }
    }

    private suspend fun ensureInitialized(session: ConversationSession) {
        session.initialize(
            load = { loadConversation(session.id) },
            create = { newConversation(session.id) },
        )
    }

    /** 持有会话引用并确保已加载后执行 [block]；[createIfMissing] 为 false 时数据库里没有就抛 NotFoundException */
    private suspend fun <T> withLoadedSession(
        conversationId: Uuid,
        createIfMissing: Boolean = true,
        block: suspend (ConversationSession) -> T,
    ): T = sessionManager.withSession(conversationId) { session ->
        session.initialize(
            load = { loadConversation(conversationId) },
            create = {
                if (createIfMissing) newConversation(conversationId) else throw NotFoundException("Conversation not found")
            },
        )
        block(session)
    }

    // 新建对话, 并添加预设消息
    private suspend fun newConversation(conversationId: Uuid, folderId: Uuid? = null): Conversation {
        val assistant = settingsStore.awaitLoaded().getCurrentAssistant()
        return Conversation.ofId(
            id = conversationId,
            assistantId = assistant.id,
            newConversation = true,
        ).copy(
            // 新聊天在发送消息前就把默认标题写入会话数据，
            // 而不是依赖标题栏的空标题兜底渲染
            title = defaultConversationTitle(),
            folderId = folderId,
        ).updateCurrentMessages(assistant.presetMessages)
    }

    // 引入会话配置之前创建的会话没有固定配置，加载时按助手当前的值补上，之后不再随助手变化。
    private suspend fun loadConversation(conversationId: Uuid): Conversation? {
        val conversation = conversationRepo.getConversationById(conversationId) ?: return null
        // settingsFlow 在启动初期还是占位值，固定配置必须基于真实设置
        val bound = conversation.bindConfig(settingsStore.awaitLoaded())
        if (bound !== conversation) conversationRepo.updateConversationConfig(bound)
        return bound
    }

    /**
     * 加载会话后在最新状态上修改并落库。
     * 删除消息或切换分支也可能解除工具审批阻塞，落库后重新检查队列。
     */
    private suspend fun mutate(conversationId: Uuid, transform: (Conversation) -> Conversation): Conversation =
        withLoadedSession(conversationId) { session ->
            session.update(transform)
            session.flush().also { dispatchNextQueuedMessage(conversationId) }
        }

    /**
     * 删掉被移除的附件：输入框临时附件直接删；CAS 附件先按会话归属抹掉消息使用记录再判断，
     * 不必等附件索引刷新，也不会误删仍被其它消息/会话/设置引用的内容。
     */
    private suspend fun deleteUnreferencedFiles(previous: Conversation, current: Conversation) {
        val session = sessionManager.get(current.id)
        val queuedFiles = (session?.messageQueue?.state?.value?.messages.orEmpty() +
                listOfNotNull(session?.submittingMessage))
            .flatMap { it.parts }.localFileUrls().map { it.toUri() }
        val retainedFiles = (current.files + queuedFiles).toSet()
        val deletedFiles = previous.files.filter { it !in retainedFiles }
        if (deletedFiles.isEmpty()) return
        deleteAttachments(current.id, deletedFiles)
    }

    private suspend fun deleteAttachments(conversationId: Uuid, files: List<Uri>) {
        val (tempFiles, casFiles) = files.partition { attachmentShaFromUrl(it.toString()) == null }
        // 该内容已不在会话文件里，抹掉本会话全部消息使用；没有其它引用时才真正删除
        val removedUsage = FileUsage(
            type = FileUsageType.MESSAGE,
            details = mapOf("conversationId" to conversationId.toString()),
        )
        if (tempFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(tempFiles)
        }
        if (casFiles.isNotEmpty()) {
            Log.w(TAG, "deleteAttachments: $casFiles")
            filesManager.deleteChatFiles(casFiles, removedUsage)
        }
    }

    private fun persistIdleConversations() {
        sessionManager.snapshot()
            .filter { it.isInitialized }
            .forEach { session -> appScope.launch(Dispatchers.IO) { session.flush() } }
    }

    // ---- 发送消息 ----

    fun getMessageQueueFlow(conversationId: Uuid): StateFlow<MessageQueueState> =
        sessionManager.getMessageQueueFlow(conversationId)

    fun removeQueuedMessage(conversationId: Uuid, messageId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.remove(messageId)?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    fun beginEditQueuedMessage(conversationId: Uuid, messageId: Uuid): QueuedMessage? =
        sessionManager.get(conversationId)?.messageQueue?.beginEdit(messageId)

    fun finishEditQueuedMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>? = null
    ) {
        sessionManager.get(conversationId)?.messageQueue?.finishEdit(messageId, parts)
            ?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    private fun cleanupQueuedAttachments(previous: QueuedMessage) {
        val candidates = previous.parts.localFileUrls()
        if (candidates.isEmpty()) return
        appScope.launch {
            try {
                // 未打开的会话及未选中的分支也可能引用同一附件。
                val persistedReferences =
                    candidates.filter { conversationRepo.hasFileReference(it) }.toSet()
                // 数据库查询挂起期间队列可能已推进，删除前重新读取内存引用。
                val currentSessions = sessionManager.snapshot()
                val unusedFiles = unreferencedQueuedAttachmentUrls(
                    previous = previous,
                    conversations = currentSessions.map { it.state.value },
                    pendingMessages = currentSessions.flatMap {
                        it.messageQueue.state.value.messages + listOfNotNull(it.submittingMessage)
                    },
                ) - persistedReferences
                if (unusedFiles.isNotEmpty()) {
                    filesManager.deleteChatFiles(unusedFiles.map { it.toUri() })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 无法确认引用时保留文件，避免误删。
                Log.w(TAG, "Failed to clean queued attachments", e)
            }
        }
    }

    fun resumeMessageQueue(conversationId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.resume()
        dispatchNextQueuedMessage(conversationId)
    }

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(content, answer)
            dispatchNextQueuedMessage(conversationId)
        }
    }

    /** Enqueue immediately; the result belongs to this item even after edits or later turns. */
    fun enqueueVoiceMessage(conversationId: Uuid, text: String): Deferred<String?> {
        val session = sessionManager.getOrCreate(conversationId)
        val reply = CompletableDeferred<String?>()
        synchronized(session) {
            check(text.isNotBlank()) { context.getString(R.string.chat_page_voice_empty) }
            check(!session.messageQueue.state.value.paused || session.messageQueue.state.value.messages.isEmpty()) {
                context.getString(R.string.chat_page_voice_resume_queue)
            }
            check(!session.state.value.isAwaitingToolApproval) {
                context.getString(R.string.chat_page_voice_tools_before_resume)
            }
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(listOf(UIMessagePart.Text(text)), reply = reply)
            dispatchNextQueuedMessage(conversationId)
        }
        return reply
    }

    private fun dispatchNextQueuedMessage(conversationId: Uuid): Job? {
        val session = sessionManager.get(conversationId) ?: return null
        synchronized(session) {
            // A pending tool approval is still part of the current turn.
            if (session.getJob() != null || session.state.value.isAwaitingToolApproval) return null
            val next = session.messageQueue.takeNext() ?: return null
            session.submittingMessage = next
            return sendQueuedMessage(session, next)
        }
    }

    private fun sendQueuedMessage(session: ConversationSession, queued: QueuedMessage): Job {
        val conversationId = session.id
        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = queued.answer,
        ) {
            try {
                ensureInitialized(session)
                finishInterruptedPendingTools(session)

                val assistant = settingsStore.awaitLoaded().getStoredAssistantOf(session.state.value)
                val processedContent = preprocessUserInputParts(queued.parts, assistant)

                // 添加消息到列表；标题仍为空时兜底写入默认标题，避免标题模型返回前显示空标题
                val previousIds = session.state.value.currentMessages.mapTo(HashSet()) { it.id }
                session.update {
                    it.copy(
                        // 发出第一条消息即视为会话开始，之后才允许落库
                        newConversation = false,
                        title = it.title.ifBlank { defaultConversationTitle() },
                        messageNodes = it.messageNodes + UIMessage(
                            role = MessageRole.USER,
                            parts = processedContent,
                        ).toMessageNode(),
                    )
                }
                session.flush()
                session.submittingMessage = null

                // 开始补全
                val result = if (queued.answer) runTurn(session) else null

                queued.reply?.completeWith(runCatching {
                    check(result != TurnResult.Failed) { context.getString(R.string.chat_page_voice_generation_failed) }
                    check((result as? TurnResult.Finished)?.reason !is AgentFinishReason.AwaitingApproval) {
                        context.getString(R.string.chat_page_voice_tool_approval)
                    }
                    session.state.value.currentMessages
                        .filter { it.id !in previousIds && it.role == MessageRole.ASSISTANT }
                        .joinToString("\n") { it.toText() }
                })
                // Voice owns playback, including when its observer has already left the page.
                // The ordinary autoplay collector must not read a late voice reply again.
                if (queued.reply == null) _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                queued.reply?.completeExceptionally(e)
                e.printStackTrace()
                reportFailure(session, e, R.string.error_title_send_message)
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) queued.reply?.completeExceptionally(cause)
            synchronized(session) {
                if (session.submittingMessage?.id == queued.id) session.submittingMessage = null
            }
        }
        session.setJob(job)
        return job
    }

    // 发送、重新生成和审批失败时报告错误并暂停队列，排队的消息等用户处理完再继续
    private fun reportFailure(session: ConversationSession, error: Exception, @StringRes title: Int) {
        if (error is CancellationException) throw error
        session.messageQueue.pause()
        addError(error, session.id, title = context.getString(title))
    }

    private fun preprocessUserInputParts(parts: List<UIMessagePart>, assistant: Assistant): List<UIMessagePart> {
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false
                        )
                    )
                }

                else -> part
            }
        }
    }

    /**
     * 输入框的上下文统计：用真正会发给模型的请求来数，系统和历史消息、转换结果都算在内。
     * 编辑历史消息时只统计被编辑消息之前的内容（被编辑消息的新内容由草稿计入）。
     */
    suspend fun estimateRequestContextStats(
        conversationId: Uuid,
        editingMessageId: Uuid? = null,
    ): RequestContextStats {
        val settings = settingsStore.awaitLoaded()
        val conversation = getConversation(conversationId)
            ?: return RequestContextStats(wordCount = 0, toolCount = 0, fileCount = 0)
        val currentMessages = conversation.currentMessages
        val messages = if (editingMessageId != null) {
            val editedIndex = currentMessages.indexOfFirst { it.id == editingMessageId }
            if (editedIndex > 0) currentMessages.subList(0, editedIndex) else emptyList()
        } else {
            currentMessages
        }
        // 没有可用模型、MCP 服务名不合法时不去打断输入框，统计按 0 显示
        val turn = runCatching { requestFactory.createContext(settings, conversation) }.getOrNull()
            ?: return RequestContextStats(wordCount = 0, toolCount = 0, fileCount = 0)
        val wordCount = requestFactory.prepareMessages(turn, messages).sumOf { message ->
            message.parts.sumOf { part ->
                when (part) {
                    is UIMessagePart.Text -> part.text.wordCount()
                    is UIMessagePart.Reasoning ->
                        if (turn.assistant.includeHistoryReasoning) part.reasoning.wordCount() else 0
                    else -> 0
                }
            }
        }
        // 与输入框附件统计一致：图片/视频/音频按文件计入；
        // 文档能解析为正文文本的按词数计入（不计文件数），解析失败的按文件计入
        var fileCount = 0
        for (message in messages) {
            for (part in message.parts) {
                when (part) {
                    is UIMessagePart.Image,
                    is UIMessagePart.Video,
                    is UIMessagePart.Audio -> fileCount++

                    is UIMessagePart.Document ->
                        if (DocumentAsPromptTransformer.extractDocumentText(part) == null) fileCount++

                    else -> {}
                }
            }
        }
        // 内置工具只计入实际会传给模型的部分（受“模型内置搜索”开关与提供商支持情况限制）
        val builtInToolCount = turn.model.findProvider(settings.providers)
            ?.let { chatRequestBuiltInTools(turn.model, it, turn.assistant.useBuiltInSearch).size }
            ?: 0
        return RequestContextStats(
            wordCount = wordCount,
            toolCount = turn.tools.size + builtInToolCount,
            fileCount = fileCount,
        )
    }

    // ---- 重新生成消息 ----

    /** 重新生成用户消息时去掉它之后的节点再回复；重新生成助手消息时在它的节点上新开一个分支 */
    fun regenerateAtMessage(conversationId: Uuid, messageId: Uuid) {
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            val previousJob = session.getJob()
            val job = launchGenerationJob(conversationId = conversationId) {
                try {
                    previousJob?.join()
                    ensureInitialized(session)
                    val node = session.state.value.getMessageNodeByMessageId(messageId)
                        ?: return@launchGenerationJob

                    if (node.role == MessageRole.USER) {
                        session.tryUpdate { conversation ->
                            val index = conversation.messageNodes.indexOfFirst { it.id == node.id }
                            // 查找之后这条消息被删掉了
                            if (index == -1) return@tryUpdate null
                            conversation.copy(messageNodes = conversation.messageNodes.take(index + 1))
                        } ?: return@launchGenerationJob
                        session.flush()
                        runTurn(session)
                    } else {
                        runTurn(session, regenerateNodeId = node.id)
                    }

                    _generationDoneFlow.emit(conversationId)
                } catch (e: Exception) {
                    reportFailure(session, e, R.string.error_title_regenerate_message)
                }
            }

            session.setJob(job)
        }
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) {
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            val previousJob = session.getJob()

            val hasOtherPendingTools = session.state.value.currentMessages.any { message ->
                message.getTools().any { it.isPending && it.toolCallId != toolCallId }
            }

            val job = launchGenerationJob(
                conversationId = conversationId,
                keepAliveInBackground = !hasOtherPendingTools,
            ) {
                try {
                    afterPreviousGeneration(previousJob) {
                        ensureInitialized(session)
                        val newApprovalState = when {
                            answer != null -> ToolApprovalState.Answered(answer)
                            approved -> ToolApprovalState.Approved
                            else -> ToolApprovalState.Denied(reason)
                        }

                        val updated = session.tryUpdate { conversation ->
                            // Ignore double taps and stale approvals for completed or inactive tools.
                            if (conversation.currentMessages.none { message ->
                                    message.getTools().any { it.toolCallId == toolCallId && it.isPending }
                                }) return@tryUpdate null
                            conversation.copy(
                                messageNodes = conversation.messageNodes.map { node ->
                                    node.copy(messages = node.messages.map { message ->
                                        message.copy(parts = message.parts.map { part ->
                                            if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) {
                                                part.copy(approvalState = newApprovalState)
                                            } else {
                                                part
                                            }
                                        })
                                    })
                                }
                            )
                        } ?: return@afterPreviousGeneration
                        session.flush()

                        // Only continue generation when all pending tools are handled
                        if (!updated.isAwaitingToolApproval) {
                            runTurn(session)
                        }

                        _generationDoneFlow.emit(conversationId)
                    }
                } catch (e: Exception) {
                    reportFailure(session, e, R.string.error_title_tool_approval)
                }
            }

            session.setJob(job, cancelPrevious = false)
        }
    }

    // ---- 生成回复 ----

    /**
     * 生成一轮回复：请求模型、执行工具，直到模型不再调用工具、有工具需要审批或达到步数上限。
     *
     * 默认接着当前分支的末尾回复；[regenerateNodeId] 不为空时只发送这个节点之前的消息，回复作为它的新分支。
     * 发送的最后一条是助手消息时从它继续（比如审批之后），回复写回那条消息。
     * 失败时报告错误、暂停消息队列并返回 [TurnResult.Failed]；取消时直接抛出。
     */
    private suspend fun runTurn(session: ConversationSession, regenerateNodeId: Uuid? = null): TurnResult {
        val conversationId = session.id
        // 生成开始前失败时通知只用来取消 Live Update，用不到发送者名称
        var senderName = ""
        // 本次生成的助手消息：新回复先放一条空占位消息，续跑时就是已有的那条
        var turn: ChatTurnContext? = null
        var placeholderId: Uuid? = null
        try {
            val settings = settingsStore.awaitLoaded()
            // 清掉上一轮的建议；没法继续执行的工具调用发给模型会被拒绝，先去掉。
            // 重新生成时只清理要发给模型的节点，被重新生成的节点保持原样，旧回复留作分支
            val conversation = session.update { latest ->
                val untilNode = regenerateNodeId
                    ?.let { nodeId -> latest.messageNodes.indexOfFirst { it.id == nodeId } }
                    ?.takeIf { it != -1 }
                    ?: latest.messageNodes.size
                latest.copy(chatSuggestions = emptyList()).withoutUnresumableToolCalls(untilNode)
            }

            val nodes = conversation.messageNodes
            val history = if (regenerateNodeId != null) {
                val index = nodes.indexOfFirst { it.id == regenerateNodeId }
                check(index != -1) { "Message node $regenerateNodeId not found" }
                conversation.currentMessages.take(index)
            } else {
                conversation.currentMessages
            }
            // 接着已有的助手消息继续（工具审批/续跑）时直接写回那条消息，否则新开一条回复
            val continuing = history.lastOrNull()?.role == MessageRole.ASSISTANT
            val replyNodeId = when {
                continuing -> nodes[history.lastIndex].id
                regenerateNodeId != null -> regenerateNodeId
                else -> Uuid.random()
            }
            // 开始请求前先把助手消息放进会话：界面立刻把它显示为“生成中”，
            // 也不会在构建工具（可能较慢，含 MCP）的窗口里被当成手动编辑的消息。
            // 这条占位消息就是本轮要写的消息，agent 循环会接着它写，所以模型归属先按会话固定配置填好。
            val messages = if (continuing) {
                history
            } else {
                val placeholder = UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = emptyList(),
                    modelId = settings.getChatModelOf(conversation)?.id,
                )
                placeholderId = placeholder.id
                session.update { it.upsertMessage(replyNodeId, placeholder) }
                history + placeholder
            }
            session.setGeneratingMessageId(placeholderId ?: history.last().id)

            var finishReason: AgentFinishReason? = null
            // 从构建请求开始（MCP 可能较慢、也可能失败）直到生成结束都要收尾：
            // 失败的占位消息在 finally 里移除，不会留在会话里
            try {
                val turnContext = requestFactory.createContext(settings, conversation)
                turn = turnContext
                senderName = if (turnContext.assistant.useAssistantAvatar) {
                    turnContext.assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
                } else {
                    turnContext.model.displayName
                }
                warnIfToolsUnavailable(turnContext, conversationId)

                val (request, hooks) = requestFactory.createAgent(turnContext, messages, session.processingStatus)
                runAgent(request, hooks).flowOn(Dispatchers.IO).collect { event ->
                    when (event) {
                        is AgentEvent.MessageUpdated -> {
                            // 生成过程中只有网络重试的提示会留着，收到新内容说明重试已经成功
                            session.processingStatus.value = null
                            session.setGeneratingMessageId(event.message.id)
                            session.update { it.upsertMessage(replyNodeId, event.message) }
                            // 通知等边缘副作用由 ChatNotificationManager 消费；
                            // tryEmit 不挂起，事件丢失只影响单次通知更新，不能反压生成链
                            appEventBus.tryEmit(
                                AppEvent.ChatGenerationUpdate(conversationId, event.message, senderName)
                            )
                        }

                        is AgentEvent.Retrying -> {
                            logRetry(event.attempt)
                            session.processingStatus.value = retryStatus(event.attempt)
                        }

                        is AgentEvent.Finished -> {
                            Log.i(TAG, "runTurn: finished (${event.reason})")
                            finishReason = event.reason
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    session.processingStatus.value = null
                    session.setGeneratingMessageId(null)
                    // 可能被取消了，或者意外结束，兜底保存；还没生成出内容的占位消息在这里移除
                    val finished = session.finishGeneration { dropEmptyPlaceholder(it, placeholderId) }
                    // 生成结束：取消 Live Update 通知，后台时发送完成通知
                    appEventBus.emit(
                        AppEvent.ChatGenerationEnded(
                            conversationId = conversationId,
                            senderName = senderName,
                            contentPreview = finished.currentMessages.lastOrNull()
                                ?.toText()?.take(50)?.trim() ?: "",
                        )
                    )
                }
            }

            val reason = checkNotNull(finishReason)
            // 等待审批时这一轮还没结束，标题、建议和记忆整理等审批之后再做
            if (reason !is AgentFinishReason.AwaitingApproval) {
                sessionManager.launchWithSession(conversationId) { generateTitle(conversationId) }
                sessionManager.launchWithSession(conversationId) { generateSuggestion(conversationId) }
                if (checkNotNull(turn).assistant.enableMemory) {
                    memoryConsolidationScheduler.onTurnFinished(session.state.value)
                }
            }
            return TurnResult.Finished(reason)
        } catch (e: CancellationException) {
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
            throw e
        } catch (e: InvalidMcpServerNamesException) {
            session.messageQueue.pause()
            addError(
                error = IllegalStateException(
                    context.getString(R.string.error_mcp_invalid_server_name, e.names.joinToString(", "))
                ),
                conversationId = conversationId,
            )
            return TurnResult.Failed
        } catch (e: Exception) {
            // 兜底取消 Live Update 通知（生成开始前失败时上面的 finally 不会执行）
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
            session.messageQueue.pause()
            e.printStackTrace()
            addError(e, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "runTurn: $e")
            Logging.log(TAG, e.stackTraceToString())
            return TurnResult.Failed
        }
    }

    /**
     * 生成失败、被取消或没有输出时，移除开始生成前插入的助手占位消息（已经有内容时原样返回）。
     * 删除后把聊天排序时间同步为现存消息中最新的一条，避免没结果的重新生成把聊天顶到列表最前。
     */
    private fun dropEmptyPlaceholder(conversation: Conversation, placeholderId: Uuid?): Conversation {
        if (placeholderId == null) return conversation
        var removed = false
        val updatedNodes = conversation.messageNodes.mapNotNull { node ->
            val removeIndex = node.messages.indexOfFirst { message ->
                message.id == placeholderId &&
                    message.parts.isEmptyUIMessage() &&
                    message.getTools().isEmpty()
            }
            if (removeIndex < 0) return@mapNotNull node

            removed = true
            val remainingMessages = node.messages.filterIndexed { index, _ -> index != removeIndex }
            if (remainingMessages.isEmpty()) return@mapNotNull null

            node.copy(
                messages = remainingMessages,
                selectIndex = when {
                    removeIndex < node.selectIndex -> node.selectIndex - 1
                    removeIndex == node.selectIndex -> (node.selectIndex - 1).coerceAtLeast(0)
                    else -> node.selectIndex
                }.coerceIn(remainingMessages.indices),
            )
        }
        if (!removed) return conversation
        val cleaned = conversation.copy(messageNodes = updatedNodes)
        return cleaned.copy(updateAt = cleaned.newestMessageTime ?: cleaned.updateAt)
    }

    // 模型不支持工具调用时，搜索和 MCP 工具都不会生效，提醒一下
    private fun warnIfToolsUnavailable(turn: ChatTurnContext, conversationId: Uuid) {
        if (ModelAbility.TOOL in turn.model.abilities) return
        if (shouldUseExternalWebSearch(turn.assistant, turn.model) ||
            mcpManager.getAllAvailableTools(turn.assistant).isNotEmpty()
        ) {
            addError(
                IllegalStateException(context.getString(R.string.tools_warning)),
                conversationId,
                title = context.getString(R.string.error_title_tool_unavailable)
            )
        }
    }

    private fun logRetry(attempt: RetryAttempt) {
        Log.w(
            TAG,
            "Provider connection failed, retrying in ${attempt.delay} (${attempt.retry}/${attempt.maxRetries})",
            attempt.cause,
        )
    }

    private fun retryStatus(attempt: RetryAttempt): String {
        val reason = when (attempt.cause) {
            is UnknownHostException -> R.string.chat_generation_network_unknown_host
            is SocketTimeoutException -> R.string.chat_generation_network_timeout
            is ConnectException, is NoRouteToHostException -> R.string.chat_generation_network_unreachable
            else -> R.string.chat_generation_network_disconnected
        }
        return context.getString(
            R.string.chat_generation_network_retrying,
            context.getString(reason),
            attempt.retry,
            attempt.maxRetries,
        )
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
                )
            )
        )
    }

    // 生成被停止时还没执行的工具补上取消的结果，否则这条消息之后没法继续发给模型
    private suspend fun finishInterruptedPendingTools(session: ConversationSession) {
        session.tryUpdate { conversation ->
            val lastNode = conversation.messageNodes.lastOrNull() ?: return@tryUpdate null
            val updatedMessage = lastNode.currentMessage.finishPendingTools(::cancelToolByUser)
            if (updatedMessage === lastNode.currentMessage) return@tryUpdate null
            conversation.upsertMessage(lastNode.id, updatedMessage)
        }
        session.flush()
    }

    // ---- 生成标题 ----

    /** 新建对话的默认标题（本地化“新聊天”），也用于判断标题是否仍待标题模型生成。 */
    private fun defaultConversationTitle(): String =
        context.getString(R.string.chat_page_new_chat)

    private fun Conversation.hasPendingGeneratedTitle(): Boolean =
        title.isBlank() || title == defaultConversationTitle()

    suspend fun generateTitle(
        conversationId: Uuid,
        force: Boolean = false
    ): Unit = withContext(Dispatchers.IO) {
        withLoadedSession<Unit>(conversationId) { session ->
            val incarnation = session.incarnation
            val conversation = session.state.value
            if (!force && !conversation.hasPendingGeneratedTitle()) return@withLoadedSession

            val settings = settingsStore.awaitLoaded()
            // 关掉自动生成后只跳过对话结束时的自动调用，手动重新生成照常执行
            if (!force && !settings.enableAutoTitle) return@withLoadedSession

            runCatching {
                val title = generateTitleCandidate(conversation)

                // 只写标题这一列：这时可能已经在生成下一轮，不能把还没生成完的内容一起落库。
                // 生成期间会话被删除的话不写入
                session.updateMetadata(
                    transform = { it.copy(title = title) },
                    persist = { conversationRepo.updateTitle(conversationId, title) },
                    expectedIncarnation = incarnation,
                )
            }.onFailure {
                it.printStackTrace()
                addError(
                    error = it,
                    conversationId = conversationId,
                    title = context.getString(R.string.error_title_generate_title),
                    solution = ChatErrorSolution.CheckFastModelSettings,
                )
            }
        }
    }

    /** 只生成标题文本，不落库（标题编辑框的“重新生成”用它填候选） */
    suspend fun generateTitleCandidate(conversation: Conversation): String {
        val settings = settingsStore.awaitLoaded()
        val model = settings.findModelById(settings.fastModelId)
            ?: throw IllegalStateException(context.getString(R.string.error_fast_model_not_found))
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException(context.getString(R.string.error_fast_model_provider_not_found))

        val providerHandler = providerManager.getProviderByType(provider)
        val result = providerHandler.generateText(
            providerSetting = provider,
            messages = listOf(
                UIMessage.user(
                    prompt = settings.titlePrompt.applyPlaceholders(
                        "locale" to Locale.getDefault().displayName,
                        "content" to conversation.currentMessages
                            .takeLast(4).joinToString("\n\n") { it.summaryAsText(maxLength = 500) })
                ),
            ),
            params = backgroundTextGenerationParams(model, conversation.id, settings.fastModelReasoningLevel),
        )

        return result.message.toText().trim()
    }

    // ---- 生成建议 ----

    private suspend fun generateSuggestion(conversationId: Uuid): Unit = withContext(Dispatchers.IO) {
        sessionManager.withSession<Unit>(conversationId) { session ->
            runCatching {
                val settings = settingsStore.awaitLoaded()
                if (!settings.enableSuggestion) return@runCatching
                val model = settings.findModelById(settings.fastModelId)
                    ?: return@runCatching
                val provider = model.findProvider(settings.providers) ?: return@runCatching

                // 上一轮的建议在这一轮开始时已经清掉了
                ensureInitialized(session)
                val incarnation = session.incarnation
                val conversation = session.state.value

                val providerHandler = providerManager.getProviderByType(provider)
                val result = providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(
                        UIMessage.user(
                            settings.suggestionPrompt.applyPlaceholders(
                                "locale" to Locale.getDefault().displayName,
                                "content" to conversation.currentMessages
                                    .takeLast(8).joinToString("\n\n") { it.summaryAsText(maxLength = 500) }),
                        )
                    ),
                    params = backgroundTextGenerationParams(model, conversationId, settings.fastModelReasoningLevel),
                )
                val suggestions = result.message.toText().split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(10)

                // 只写建议这一列，理由同标题
                session.updateMetadata(
                    transform = { it.copy(chatSuggestions = suggestions) },
                    persist = { conversationRepo.updateChatSuggestions(conversationId, suggestions) },
                    expectedIncarnation = incarnation,
                )
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    // ---- 压缩对话历史 ----

    suspend fun compressConversation(
        conversationId: Uuid,
        additionalPrompt: String,
    ): Result<Unit> = runCatching {
        withLoadedSession<Unit>(conversationId) { session ->
            // 生成中的回复还没写完，压缩不到它，检查点也不能插到它前面
            check(!session.isGenerating) { context.getString(R.string.chat_page_compress_blocked_generating) }

            val conversation = session.state.value
            val nodes = conversation.messageNodes
            // 上一个检查点之后没有新消息，就没什么可压缩的
            val checkpointIndex = nodes.indexOfLast { it.currentMessage.isContextCheckpoint }
            if (checkpointIndex == nodes.lastIndex) {
                throw IllegalStateException(context.getString(R.string.chat_page_compress_nothing_to_compress))
            }
            // 未完成的工具调用没有结果，请求会被服务端拒绝；压到检查点之前也再不会执行。
            check(nodes.subList(checkpointIndex + 1, nodes.size).none { node ->
                node.currentMessage.getTools().any { it.isPending || it.canResumeExecution }
            }) { context.getString(R.string.chat_page_compress_handle_pending_tools) }

            // 必须和对话用同一个模型、同一套请求组装，才能命中提示词缓存
            val turn = requestFactory.createContext(settingsStore.awaitLoaded(), conversation)
            val summary = requestFactory.generateCompressionSummary(
                turn = turn,
                messages = conversation.currentMessages,
                compressPrompt = buildCompressPrompt(
                    additionalPrompt = additionalPrompt,
                    locale = Locale.getDefault().displayName,
                ),
            )

            // 原消息原样保留，摘要插在压缩时的最后一条消息之后。摘要生成期间对话可能已变化，
            // 因此按节点定位插入到最新状态。
            session.update { latest ->
                check(!session.isGenerating) { context.getString(R.string.chat_page_compress_blocked_generating) }
                val updated = latest.insertContextCheckpoint(
                    afterNodeId = nodes.last().id,
                    summary = summary,
                ) ?: throw IllegalStateException(context.getString(R.string.chat_page_compress_conversation_changed))
                updated.copy(chatSuggestions = emptyList())
            }
            session.flush()
        }
    }

    // ---- 聊天页配置 ----

    /**
     * 聊天页对助手的修改：会话持有的字段只改当前会话，其余写回助手设置。
     *
     * @param update 接收会话视角下的助手，返回修改后的助手
     */
    suspend fun updateChatAssistant(conversationId: Uuid, update: (Assistant) -> Assistant) {
        withLoadedSession(conversationId) { session ->
            val settings = settingsStore.awaitLoaded()
            val conversation = session.state.value
            val stored = settings.getStoredAssistantOf(conversation)
            val updated = update(settings.getAssistantOf(conversation))
            val assistant = updated.withoutConversationFields(conversation, stored)
            session.updateMetadata(
                transform = { it.withAssistantUpdate(updated, settings) },
                persist = conversationRepo::updateConversationConfig,
            )
            if (assistant != stored) {
                settingsStore.update { latest ->
                    latest.copy(assistants = latest.assistants.map { if (it.id == assistant.id) assistant else it })
                }
            }
        }
    }

    /**
     * 切换搜索方式，传 null 的一项保持不变。
     * 模型内置搜索是助手的开关，会话开始前/跟随助手时改助手，否则固定在会话上。
     */
    suspend fun updateChatSearch(
        conversationId: Uuid,
        enableWebSearch: Boolean? = null,
        builtInSearch: Boolean? = null,
    ) {
        withLoadedSession(conversationId) { session ->
            val conversation = session.state.value
            val config = conversation.config
            if (config != null && !config.followAssistant) {
                session.updateMetadata(
                    transform = {
                        it.copy(
                            config = it.config?.let { cfg ->
                                cfg.copy(
                                    enableWebSearch = enableWebSearch ?: cfg.enableWebSearch,
                                    builtInSearch = builtInSearch ?: cfg.builtInSearch,
                                )
                            }
                        )
                    },
                    persist = conversationRepo::updateConversationConfig,
                )
                return@withLoadedSession
            }
            settingsStore.update { settings ->
                val assistant = settings.getAssistantOf(conversation)
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistant.id) {
                            it.copy(
                                enableWebSearch = enableWebSearch ?: it.enableWebSearch,
                                useBuiltInSearch = builtInSearch ?: it.useBuiltInSearch,
                            )
                        } else {
                            it
                        }
                    },
                )
            }
        }
    }

    /**
     * fork：切换当前会话是否跟随助手设置。
     * 开启后该会话实时使用助手设置；关闭时用助手当前设置重新拍快照。
     */
    suspend fun updateChatFollowAssistant(conversationId: Uuid, follow: Boolean) {
        withLoadedSession(conversationId) { session ->
            val settings = settingsStore.awaitLoaded()
            session.updateMetadata(
                transform = { it.setFollowAssistant(settings, follow) },
                persist = conversationRepo::updateConversationConfig,
            )
        }
    }

    // 工作目录和配置一起按列落库；还没落过库的新会话先留在内存里，发出第一条消息时一起写入
    suspend fun updateWorkspaceCwd(conversationId: Uuid, workspaceCwd: String?) {
        withLoadedSession(conversationId) { session ->
            session.updateMetadata(
                transform = { it.copy(workspaceCwd = workspaceCwd) },
                persist = conversationRepo::updateConversationConfig,
            )
        }
    }

    suspend fun updateConversationSystemPrompt(conversationId: Uuid, systemPrompt: String?) {
        updateColumn(
            conversationId = conversationId,
            transform = { it.copy(customSystemPrompt = systemPrompt) },
            persist = { conversationRepo.updateCustomSystemPrompt(conversationId, systemPrompt) },
        )
    }

    suspend fun updateTitle(conversationId: Uuid, title: String) {
        updateColumn(
            conversationId = conversationId,
            transform = { it.copy(title = title) },
            persist = { conversationRepo.updateTitle(conversationId, title) },
        )
    }

    /**
     * 用户在聊天页改的单列字段：已落库的会话只写这一列，生成中也不会顺带写入还没生成完的回复；
     * 还没落过库的新会话整份落库，留下记录（没有内容的新会话仍然不写）。
     */
    private suspend fun updateColumn(
        conversationId: Uuid,
        transform: (Conversation) -> Conversation,
        persist: suspend () -> Unit,
    ) {
        withLoadedSession(conversationId) { session ->
            session.updateMetadata(transform, persist = { persist() })
            if (!session.existsInDatabase) {
                // 用户已经手动改了这一列，算作会话开始；flush 会把整份内容写入，列值不会丢
                session.update { it.copy(newConversation = false) }
                session.flush()
            }
        }
    }

    /**
     * fork：整条消息节点变换（分支排序/切换选中），在最新状态上执行后落库。
     * 上游的 selectMessageNode 只能切选中；这里的分支还支持前后移动。
     */
    suspend fun updateMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        update: (MessageNode) -> MessageNode,
    ) {
        mutate(conversationId) { conversation ->
            conversation.copy(
                messageNodes = conversation.messageNodes.map { node ->
                    if (node.id == nodeId) update(node) else node
                }
            )
        }
    }

    // ---- 会话元数据 ----

    private suspend fun updateConversationMetadata(
        conversationId: Uuid,
        transform: (Conversation) -> Conversation,
        persist: suspend (Conversation) -> Unit,
    ) {
        withLoadedSession(conversationId, createIfMissing = false) { session ->
            session.updateMetadata(transform, persist)
        }
    }

    suspend fun toggleConversationPinned(conversationId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            transform = { it.copy(isPinned = !it.isPinned) },
            persist = { conversationRepo.updatePinStatus(conversationId, it.isPinned) },
        )
    }

    suspend fun moveConversationToAssistant(
        conversationId: Uuid,
        assistantId: Uuid,
        folderId: Uuid? = null,
    ) {
        updateConversationMetadata(
            conversationId = conversationId,
            // 文件夹属于助手，移动后清除原助手的文件夹归属（或移动到目标文件夹）。
            transform = { it.copy(assistantId = assistantId, folderId = folderId) },
            persist = {
                conversationRepo.updateConversationAssistant(conversationId, it.assistantId)
                if (folderId != null) {
                    conversationRepo.updateConversationFolderId(conversationId, folderId)
                }
            },
        )
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     * 有 session 的会话先改内存，免得之后整份落库时写回旧的文件夹；没有的只改这一列，不必加载整个会话。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessionManager.get(conversationId) == null) {
            conversationRepo.updateConversationFolderId(conversationId, folderId)
            return
        }
        updateConversationMetadata(
            conversationId = conversationId,
            transform = { it.copy(folderId = folderId) },
            persist = { conversationRepo.updateConversationFolderId(conversationId, folderId) },
        )
    }

    /**
     * 文件夹内是否存在正在生成回复的会话。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威（移动会先同步内存态）。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessionManager.snapshot().any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹（folder_id 归属会被清空，会话本身保留）。
     * 已加载的会话先改内存再删库，之后落库不会写回已删除的文件夹。
     */
    suspend fun deleteFolder(folderId: Uuid) {
        sessionManager.snapshot()
            .filter { it.isInitialized && it.state.value.folderId == folderId }
            .forEach { session ->
                // 这一列由下面删文件夹时统一清空，这里只改内存，不让会话因此变成有未落库的修改
                session.updateMetadata(
                    transform = { if (it.folderId == folderId) it.copy(folderId = null) else it },
                    persist = {},
                )
            }
        folderRepository.deleteFolder(folderId)
    }

    // ---- 删除会话 ----

    /**
     * 删除会话：停掉生成后在会话的写锁内删库，内存换成一个还没落过库的新会话。
     * 还停在这个会话上的页面看到的是空会话，之后再发消息会新建记录，不会写回已删除的那条。
     */
    suspend fun deleteConversation(conversationId: Uuid) {
        val session = sessionManager.getOrCreate(conversationId)
        discardQueuedMessages(session)
        session.replace(newConversation(conversationId), existsInDatabase = false) { previous ->
            // 数据库那份会摘掉本会话的附件使用记录并删掉无人引用的内容
            conversationRepo.getConversationById(conversationId)?.let { conversationRepo.deleteConversation(it) }
            // 数据库那份不知道只在内存里出现过的附件（比如还没落过库的临时附件）
            previous?.files?.takeIf { it.isNotEmpty() }?.let { deleteAttachments(conversationId, it) }
        }
        sessionManager.removeIfIdle(session)
    }

    suspend fun deleteConversationsOfAssistant(assistantId: Uuid) {
        val persisted = conversationRepo.getConversationsOfAssistant(assistantId).first().map { it.id }
        val loaded = sessionManager.snapshot()
            .filter { it.isInitialized && it.state.value.assistantId == assistantId }
            .map { it.id }
        (persisted + loaded).distinct().forEach { deleteConversation(it) }
    }

    private fun discardQueuedMessages(session: ConversationSession) {
        session.messageQueue.pause()
        session.messageQueue.state.value.messages.forEach { queued ->
            session.messageQueue.remove(queued.id)?.let(::cleanupQueuedAttachments)
        }
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        val key = conversationId to message.id
        translationJobs.remove(key)?.cancel()
        _translatingMessages.update { it + key }
        val job = appScope.launch(Dispatchers.IO) {
            sessionManager.withSession<Unit>(conversationId) { session ->
                try {
                    ensureInitialized(session)
                    val settings = settingsStore.awaitLoaded()

                    val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                        .joinToString("\n\n") { it.text }
                        .trim()

                    if (messageText.isBlank()) return@withSession

                    // Set loading state for translation
                    val loadingText = context.getString(R.string.translating)
                    session.update { it.withTranslation(message.id, loadingText) }

                    translationHandler.translateText(
                        settings = settings,
                        sourceText = messageText,
                        targetLanguage = targetLanguage
                    ) { translatedText ->
                        // Update translation field in real-time
                        session.update { it.withTranslation(message.id, translatedText) }
                    }.collect { /* Final translation already handled in onStreamUpdate */ }

                    session.flush()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Clear translation field on error
                    if (session.isInitialized) {
                        session.update { it.withTranslation(message.id, null) }
                        session.flush()
                    }
                    addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
                }
            }
        }
        translationJobs[key] = job
        job.invokeOnCompletion {
            translationJobs.remove(key, job)
            _translatingMessages.update { it - key }
        }
    }

    suspend fun cancelTranslation(conversationId: Uuid, messageId: Uuid) {
        val key = conversationId to messageId
        translationJobs.remove(key)?.cancel()
        _translatingMessages.update { it - key }
        val translation = getConversation(conversationId)?.messageNodes
            ?.asSequence()
            ?.flatMap { it.messages }
            ?.firstOrNull { it.id == messageId }
            ?.translation
        if (translation == context.getString(R.string.translating)) {
            clearTranslationField(conversationId, messageId)
        }
    }

    suspend fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        mutate(conversationId) { it.withTranslation(messageId, null) }
    }

    private fun Conversation.withTranslation(messageId: Uuid, translation: String?): Conversation {
        val node = getMessageNodeByMessageId(messageId) ?: return this
        return upsertMessage(node.id, node.messages.first { it.id == messageId }.copy(translation = translation))
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return
        val settings = settingsStore.awaitLoaded()

        mutate(conversationId) { conversation ->
            val node = conversation.getMessageNodeByMessageId(messageId) ?: return@mutate conversation
            val original = node.messages.first { it.id == messageId }
            // 编辑助手消息时同样要先把 <think> 拆成推理段，否则标签会被原样发给模型
            val processedParts = if (node.role == MessageRole.ASSISTANT) {
                ThinkTagTransformer.parseEditedParts(
                    parts = parts,
                    parseMidThink = settings.displaySetting.parseMidThink,
                )
            } else {
                preprocessUserInputParts(parts, settings.getStoredAssistantOf(conversation))
            }
            val edited = if (original.isContextCheckpoint) {
                // 摘要原地改写：新建分支会丢掉检查点标记，删除摘要时还会露出旧版本。
                original.copy(parts = processedParts)
            } else {
                UIMessage(
                    role = node.role,
                    parts = processedParts,
                    // 编辑的是原消息的内容，来源仍是生成它的模型
                    modelId = original.modelId,
                    modelSnapshot = original.modelSnapshot,
                )
            }
            conversation.upsertMessage(node.id, edited)
        }
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val source = withLoadedSession(conversationId) { it.state.value }
        val existingTitles = conversationRepo
            .getConversationsOfAssistant(source.assistantId)
            .first()
            .mapTo(mutableSetOf()) { it.title }
        // 附件是内容寻址的，分支直接复用同一实体，使用记录会随新会话一起写入；恒等映射不做物理复制
        val fork = source.fork(messageId, existingTitles, copyFile = { it })
            ?: throw NotFoundException("Message not found")
        // 新会话直接落库，打开时再从数据库加载
        val forkConversation = conversationStore.prepareWrite()(fork)
        conversationRepo.insertConversation(forkConversation)
        memoryConsolidationScheduler.onConversationForked(forkConversation)
        return forkConversation
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        mutate(conversationId) { conversation ->
            val targetNode = conversation.messageNodes.firstOrNull { it.id == nodeId }
                ?: throw NotFoundException("Message node not found")
            if (selectIndex !in targetNode.messages.indices) {
                throw BadRequestException("Invalid selectIndex")
            }
            if (targetNode.selectIndex == selectIndex) return@mutate conversation

            conversation.copy(
                messageNodes = conversation.messageNodes.map { node ->
                    if (node.id == nodeId) node.copy(selectIndex = selectIndex) else node
                }
            )
        }
    }

    // 收藏记录在单独的表里，节点上的标记只用于显示，不需要落库
    suspend fun setNodeFavorite(conversationId: Uuid, nodeId: Uuid, favorite: Boolean) {
        val session = sessionManager.get(conversationId)?.takeIf { it.isInitialized } ?: return
        session.updateMetadata(
            transform = { conversation ->
                conversation.copy(
                    messageNodes = conversation.messageNodes.map { node ->
                        if (node.id == nodeId) node.copy(isFavorite = favorite) else node
                    }
                )
            },
            persist = {},
        )
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        mutate(conversationId) { conversation ->
            buildConversationAfterMessageDelete(conversation, messageId)
                ?.let { deleted ->
                    // 删除后把聊天排序时间同步为现存消息中最新的一条
                    deleted.copy(updateAt = deleted.newestMessageTime ?: deleted.updateAt)
                }
                ?: if (failIfMissing) throw NotFoundException("Message not found") else conversation
        }
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    private fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
            if (index != targetNodeIndex) {
                return@mapIndexedNotNull node
            }

            val nextMessages = node.messages.filterNot { it.id == messageId }
            if (nextMessages.isEmpty()) {
                return@mapIndexedNotNull null
            }

            val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
            node.copy(
                messages = nextMessages,
                selectIndex = nextSelectIndex,
            )
        }

        return conversation.copy(messageNodes = updatedNodes)
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        val session = sessionManager.get(conversationId) ?: return
        val jobs = synchronized(session) {
            session.messageQueue.pause()
            session.cancelJobs()
        }
        if (jobs.isEmpty()) return
        jobs.forEach { it.join() }
        if (session.isInitialized) finishInterruptedPendingTools(session)
    }
}
