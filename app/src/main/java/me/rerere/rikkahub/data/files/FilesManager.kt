package me.rerere.rikkahub.data.files

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.Logging
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.entity.FileUsage
import me.rerere.rikkahub.data.db.entity.FileUsageType
import me.rerere.rikkahub.data.db.entity.ManagedFileCasEntity
import me.rerere.rikkahub.data.db.entity.decodeUsages
import me.rerere.rikkahub.data.db.entity.encodeUsages
import me.rerere.rikkahub.data.db.entity.isMessageUsage
import me.rerere.rikkahub.data.db.entity.isSettingsUsage
import me.rerere.rikkahub.data.db.entity.latestDisplayName
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.exportImageFile
import me.rerere.rikkahub.utils.getActivity
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

/**
 * 聊天附件管理。
 *
 * 附件实体以 sha256 命名保存在 `files/upload/<sha256>`，登记信息（MIME、大小、使用位置与
 * 展示名/时间）保存在 `managed_files_cas` 表的 `usages` JSON 里。消息里的附件 url 是
 * `upload/<sha256>`，兼容旧版 ZIP 导出时再按登记的使用记录还原成 `upload/<uuid.ext>`。
 *
 * 输入框/队列里的附件先放在 `cacheDir/upload_tmp` 临时目录（不登记、不参与内容寻址），只有会话
 * 真正落库（`commitConversationFiles`）时才按内容 sha256 提交成 CAS 实体；来源展示名/MIME 先
 * 记在进程内，紧接着的 [updateAttachmentIndex] 会一条 insert 直接写成 `message` 使用记录。
 */
class FilesManager(
    private val context: Context,
    private val repository: FilesRepository,
) {
    companion object {
        private const val TAG = "FilesManager"

        /** 设置类使用记录：这些类型在 [updateSettingsAttachmentUsages] 里整批重建。 */
        private val SETTINGS_USAGE_TYPES = FileUsageType.SETTINGS_TYPES
    }

    /** 登记表读写的互斥锁: 落盘文件后落登记都经过它。 */
    private val rowsMutex = Mutex()

    /** 使用记录读写的互斥锁: 保存会话、重建索引与删除附件都经过它。 */
    private val usagesMutex = Mutex()

    private val filesRoot get() = context.filesDir

    /** 供落盘形式的相对附件路径换算使用（见 [LocalFileUrls]）。 */
    val filesDir: File get() = context.filesDir

    private val uploadDir get() = File(filesRoot, FileFolders.UPLOAD)

    /**
     * 输入框/队列里的临时附件目录（`cacheDir` 下）；不参与 CAS，随消息提交或进程启动清理。
     * 系统清理缓存导致文件提前消失时，提交阶段会把对应附件丢弃，不会把临时路径落库。
     */
    private val tempUploadDir get() = File(context.cacheDir, FileFolders.UPLOAD_TEMP)

    /** 临时附件的展示名与 MIME（只在进程内有效，临时文件不跨进程存活）。 */
    private val tempAttachments = MutableStateFlow<Map<String, TempAttachment>>(emptyMap())

    /**
     * 刚提交成 CAS、还没写使用记录的附件信息（sha256 -> 展示名/MIME）。
     * 提交后紧接的 [updateAttachmentIndex] 会用它直接 insert 最终的 `message` 使用记录，
     * 不用先写一条带名字的 `none` 再改写；进程中断丢失时由启动时的 [syncManagedFiles] 兜底登记。
     */
    private val committedAttachments = ConcurrentHashMap<String, CommitInfo>()

    // ------------------------------------------------------------------ CAS 保存

    /**
     * 直接落一份 CAS 内容（设置头像/背景、工具输出、导入等非输入框场景）。
     * 先写 `none` 使用记录（带展示名），被消息/设置引用后由索引更新成真实使用。
     */
    suspend fun saveUploadFromBytes(
        bytes: ByteArray,
        displayName: String? = null,
        mimeType: String = "application/octet-stream",
    ): ManagedFileCasEntity = withContext(Dispatchers.IO) {
        val (sha256, file) = writeUploadFile { it.write(bytes) }
        usagesMutex.withLock { ensureRow(sha256, file, mimeType, displayName) }
    }

    /** 直接落一份 CAS 内容（URI 形式，设置里的头像/背景等使用）。 */
    suspend fun saveUploadFromUri(
        uri: Uri,
        displayName: String? = null,
        mimeType: String? = null,
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val sourceMime = mimeType ?: getFileMimeType(uri)
            val (sha256, file) = writeUploadFile { output ->
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: error("Failed to open input stream for $uri")
                inputStream.use { input -> input.copyTo(output) }
            }
            val guessedMime = sourceMime ?: guessMimeType(file, sha256)
            usagesMutex.withLock { ensureRow(sha256, file, guessedMime, displayName) }
            file.toUri()
        }.onFailure {
            Log.e(TAG, "saveUploadFromUri: Failed to save file from $uri", it)
            Logging.log(
                TAG,
                "saveUploadFromUri: Failed to save file from $uri ${it.message} | ${it.stackTraceToString()}"
            )
        }.getOrNull()
    }

    // ------------------------------------------------------------------ 查询

    suspend fun get(id: Long): ManagedFileCasEntity? = repository.getById(id)

    /** 按落盘相对路径（如 `upload/<sha256>`）查询；非 upload 目录返回 null。 */
    suspend fun getByRelativePath(relativePath: String): ManagedFileCasEntity? {
        if (!relativePath.startsWith("${FileFolders.UPLOAD}/")) return null
        return repository.getBySha(relativePath.substringAfterLast('/'))
    }

    fun getFile(entity: ManagedFileCasEntity): File = File(uploadDir, entity.sha256)

    /**
     * 每个附件当前应显示的展示名：CAS 内容取其时间最晚那条使用记录的展示名，输入框临时附件取
     * 添加时的来源文件名。键是附件绝对 url。
     */
    fun latestDisplayNames(): Flow<Map<String, String>> =
        combine(repository.listAll(), tempAttachments) { rows, temp ->
            buildMap {
                rows.forEach { row ->
                    row.decodeUsages().latestDisplayName()
                        .takeIf { it.isNotBlank() }
                        ?.let { put(getFile(row).toUri().toString(), it) }
                }
                temp.forEach { (url, attachment) ->
                    attachment.displayName.takeIf { it.isNotBlank() }?.let { put(url, it) }
                }
            }
        }

    // ------------------------------------------------------------------ 输入框临时附件

    /**
     * 把 URI 内容复制到输入框临时目录。临时附件不写数据库，[commitConversationFiles] 时才提交成 CAS。
     */
    fun createChatFilesByContents(uris: List<Uri>): List<Uri> =
        uris.mapNotNull { uri -> storeTempFromUri(uri, null, getFileNameFromUri(uri)) }

    /** 把字节数组写入输入框临时目录（画板等场景）。 */
    fun createChatFilesByByteArrays(
        byteArrays: List<ByteArray>,
        mimeType: String = "image/png",
    ): List<Uri> = byteArrays.map { byteArray ->
        val file = writeTempFile(
            writeContent = { it.write(byteArray) },
            displayName = null,
            mimeType = mimeType,
        )
        recordTempAttachment(file, displayName = null, mimeType = mimeType)
        file.toUri()
    }

    /** 把一段文本写成输入框里的临时文档附件。 */
    fun createChatTextFile(text: String): UIMessagePart.Document {
        val file = writeTempFile(
            writeContent = { it.write(text.toByteArray()) },
            displayName = "pasted_text.txt",
            mimeType = "text/plain",
        )
        recordTempAttachment(file, displayName = "pasted_text.txt", mimeType = "text/plain")
        return UIMessagePart.Document(
            url = file.toUri().toString(),
            fileName = "pasted_text.txt",
            mime = "text/plain",
        )
    }

    /**
     * 把会话里仍在临时目录的附件提交成 CAS 实体，返回 url 替换后的会话。
     * [move] 为 true 时提交后删除临时文件，调用方必须用返回值替换内存中的会话。
     * 临时文件已被清理（如系统清缓存）导致提交失败的附件会从消息中丢弃，绝不把临时路径落库。
     */
    suspend fun commitConversationFiles(
        conversation: Conversation,
        move: Boolean = true,
    ): Conversation = withContext(Dispatchers.IO) {
        val tempUrls = collectTempAttachmentUrls(conversation)
        if (tempUrls.isEmpty()) return@withContext conversation
        val mapping = tempUrls
            .mapNotNull { url -> commitTempAttachment(url, move)?.let { url to it } }
            .toMap()
        conversation.copy(
            messageNodes = conversation.messageNodes.map { node ->
                node.copy(
                    messages = node.messages.map { message ->
                        message.copy(parts = message.parts.applyCommittedTempUrls(mapping))
                    }
                )
            }
        )
    }

    /**
     * 把 parts 里的临时附件 url 换成 CAS url；提交失败的临时附件整段丢掉。
     * 非临时 url（含工具结果里的嵌套附件）原样保留。
     */
    private fun List<UIMessagePart>.applyCommittedTempUrls(mapping: Map<String, String>): List<UIMessagePart> =
        mapNotNull { part ->
            when (part) {
                is UIMessagePart.Image -> commitUrl(part.url, mapping)?.let { part.copy(url = it) }
                is UIMessagePart.Document -> commitUrl(part.url, mapping)?.let { part.copy(url = it) }
                is UIMessagePart.Video -> commitUrl(part.url, mapping)?.let { part.copy(url = it) }
                is UIMessagePart.Audio -> commitUrl(part.url, mapping)?.let { part.copy(url = it) }
                is UIMessagePart.Tool -> part.copy(output = part.output.applyCommittedTempUrls(mapping))
                else -> part
            }
        }

    /** 非临时 url 返回原值；临时 url 返回提交后的 CAS url，提交失败返回 null（丢弃该附件）。 */
    private fun commitUrl(url: String, mapping: Map<String, String>): String? =
        if (tempAttachmentNameFromUrl(url) == null) url else mapping[url]

    /** 收集会话里所有临时附件 url（含工具结果里的嵌套附件）。 */
    private fun collectTempAttachmentUrls(conversation: Conversation): List<String> = buildList {
        val seen = mutableSetOf<String>()
        conversation.messageNodes.forEach { node ->
            node.messages.forEach { message ->
                message.parts.localFileUrls().forEach { url ->
                    if (tempAttachmentNameFromUrl(url) != null && seen.add(url)) add(url)
                }
            }
        }
    }

    /**
     * 提交一份临时附件：按内容 sha256 落到 `upload/<sha256>`，并记下来源展示名/MIME 供紧接着的
     * [updateAttachmentIndex] 一次性写成 `message` 使用记录。
     * [move] 为 true 时提交后临时文件被移走（相同内容已存在时直接删除临时文件）。
     */
    private fun commitTempAttachment(url: String, move: Boolean): String? {
        val name = tempAttachmentNameFromUrl(url) ?: return null
        val tempFile = File(tempUploadDir, name)
        if (!tempFile.isFile) return null
        return runCatching {
            val info = tempAttachments.value[url]
            val sha256 = AttachmentCas.sha256(tempFile)
            val entity = commitTempFile(tempFile, sha256, move)
            committedAttachments[sha256] = CommitInfo(
                displayName = info?.displayName.orEmpty(),
                mimeType = info?.mimeType?.takeIf { it.isNotBlank() } ?: guessMimeType(entity, sha256),
            )
            // 复制模式下临时文件还在，保留展示名供后续真正的提交使用
            if (move) tempAttachments.update { it - url }
            entity.toUri().toString()
        }.onFailure {
            Log.e(TAG, "commitTempAttachment: failed to commit $url", it)
        }.getOrNull()
    }

    private fun commitTempFile(tempFile: File, sha256: String, move: Boolean): File {
        val dir = uploadDir.apply { mkdirs() }
        if (move) return AttachmentCas.commitTemporary(dir, sha256, tempFile)
        val entity = File(dir, sha256)
        if (!entity.exists()) tempFile.copyTo(entity, overwrite = true)
        return entity
    }

    private fun storeTempFromUri(uri: Uri, mimeType: String?, displayName: String?): Uri? =
        runCatching {
            val resolvedMime = mimeType ?: getFileMimeType(uri)
            val file = writeTempFile(
                writeContent = { output ->
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: error("Failed to open input stream for $uri")
                    inputStream.use { input -> input.copyTo(output) }
                },
                displayName = displayName,
                mimeType = resolvedMime,
            )
            recordTempAttachment(file, displayName, resolvedMime)
            file.toUri()
        }.onFailure {
            Log.e(TAG, "storeTempFromUri: Failed to save file from $uri", it)
            Logging.log(
                TAG,
                "storeTempFromUri: Failed to save file from $uri ${it.message} | ${it.stackTraceToString()}"
            )
        }.getOrNull()

    private fun recordTempAttachment(file: File, displayName: String?, mimeType: String?) {
        val url = file.toUri().toString()
        tempAttachments.update {
            it + (url to TempAttachment(displayName = displayName.orEmpty(), mimeType = mimeType.orEmpty()))
        }
    }

    private fun writeTempFile(
        writeContent: (OutputStream) -> Unit,
        displayName: String?,
        mimeType: String?,
    ): File {
        val dir = tempUploadDir.apply { mkdirs() }
        val file = File(dir, FileUtils.buildUuidFileName(displayName, mimeType))
        FileOutputStream(file).use(writeContent)
        return file
    }

    // ------------------------------------------------------------------ 聊天附件

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun convertBase64ImagePartToLocalFile(message: UIMessage): UIMessage =
        withContext(Dispatchers.IO) {
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Image -> {
                            if (part.url.startsWith("data:image")) {
                                val sourceByteArray = Base64.decode(part.url.substringAfter("base64,").toByteArray())
                                val bitmap = BitmapFactory.decodeByteArray(sourceByteArray, 0, sourceByteArray.size)
                                val byteArray = FileUtils.compressBitmapToPng(bitmap)
                                val entity = saveUploadFromBytes(byteArray, mimeType = "image/png")
                                Log.i(
                                    TAG,
                                    "convertBase64ImagePartToLocalFile: convert base64 img to ${entity.sha256}"
                                )
                                part.copy(url = getFile(entity).toUri().toString())
                            } else {
                                part
                            }
                        }

                        else -> part
                    }
                }
            )
        }

    /**
     * 删除聊天附件。CAS 内容仍被任何使用（消息、设置）引用时保留；输入框临时附件直接删除。
     */
    suspend fun deleteChatFiles(uris: List<Uri>) = withContext(Dispatchers.IO) {
        usagesMutex.withLock {
            val uploadPath = runCatching { uploadDir.canonicalPath }.getOrNull()
            uris.forEach { uri ->
                val file = runCatching { uri.toFile() }.getOrNull() ?: return@forEach
                val parent = runCatching { file.parentFile?.canonicalPath }.getOrNull()
                when {
                    parent == uploadPath -> {
                        val row = repository.getBySha(file.name) ?: return@forEach
                        deleteUploadEntity(row)
                    }

                    // 输入框临时附件在 cacheDir 下，不在 filesDir 里，直接删
                    tempAttachmentNameFromUrl(uri.toString()) != null -> {
                        file.delete()
                        tempAttachments.update { it - uri.toString() }
                    }

                    else -> getRelativePathInFilesDir(file)?.let { file.delete() }
                }
            }
        }
    }

    /** 删除一份 `upload` 内容；调用方需持有 [usagesMutex]。仍被任何使用引用时保留。 */
    private suspend fun deleteUploadEntity(row: ManagedFileCasEntity) {
        val usages = row.decodeUsages()
        if (usages.any { it.type != FileUsageType.NONE }) return
        File(uploadDir, row.sha256).delete()
        repository.deleteBySha(row.sha256)
    }

    // ------------------------------------------------------------------ 使用记录

    /** 保存会话后刷新该会话在 `managed_files_cas` 里的消息使用记录。 */
    suspend fun updateAttachmentIndex(conversation: Conversation) = withContext(Dispatchers.IO) {
        usagesMutex.withLock {
            val conversationId = conversation.id.toString()
            val refsBySha = linkedMapOf<String, MutableList<MessageRef>>()
            conversation.messageNodes.forEach { node ->
                val nodeId = node.id.toString()
                node.messages.forEach { message ->
                    val seenInMessage = mutableSetOf<String>()
                    val messageAt = message.createdAt.toEpochMillis()
                    message.parts.collectAttachmentRefs().forEach refLoop@{ (sha, fileName) ->
                        if (!seenInMessage.add(sha)) return@refLoop
                        refsBySha.getOrPut(sha) { mutableListOf() } +=
                            MessageRef(conversationId, nodeId, message.id.toString(), messageAt, fileName)
                    }
                }
            }
            refsBySha.forEach { (sha256, refs) ->
                val existing = repository.getBySha(sha256)
                val info = committedAttachments.remove(sha256)
                if (existing != null) {
                    // 内容已登记：只把本次的来源展示名写到只有 none 的登记上（与重新添加同名内容的旧行为一致）
                    if (info != null && info.displayName.isNotBlank()) {
                        val usages = existing.decodeUsages()
                        if (usages.all { it.type == FileUsageType.NONE }) {
                            val createdAt = usages.firstOrNull()?.createdAt?.takeIf { it > 0 } ?: fileTime(sha256)
                            repository.update(existing.encodeUsages(listOf(noneUsage(createdAt, info.displayName))))
                        }
                    }
                    return@forEach
                }
                val file = File(uploadDir, sha256)
                if (!file.isFile) return@forEach
                // 没有登记的内容：一条 insert 直接写成最终使用记录，不再先写 none
                val mimeType = info?.mimeType?.takeIf { it.isNotBlank() } ?: guessMimeType(file, sha256)
                val usages = refs.map { ref ->
                    messageUsage(
                        filename = FileUtils.buildUuidFileName(null, mimeType),
                        displayName = ref.displayName?.takeIf { it.isNotBlank() } ?: info?.displayName.orEmpty(),
                        ref = ref,
                    )
                }
                repository.insert(
                    ManagedFileCasEntity(
                        sha256 = sha256,
                        mimeType = mimeType,
                        sizeBytes = file.length(),
                    ).encodeUsages(usages)
                )
            }
            val rows = repository.listAllNow()
            rows.forEach { row ->
                val usages = row.decodeUsages()
                // 按消息身份（sha + 会话 + 节点 + 消息）保留旧文件名/展示名，避免删除或重排消息后串位
                val preservedByKey = usages
                    .filter { it.isMessageUsage() && it.details["conversationId"] == conversationId }
                    .mapNotNull { usage -> usage.toMessageRef()?.let { filenameKey(row.sha256, it) to usage } }
                    .toMap()
                val refs = refsBySha[row.sha256].orEmpty()
                // 该内容时间最晚那条记录的展示名；已登记的旧内容（如 none 记录）可能带着来源文件名，
                // 图片/视频/音频没有 part.fileName，新增消息引用时从这里把展示名带走
                val baseDisplayName = usages.latestDisplayName()
                val others = usages.filterNot {
                    it.isMessageUsage() && it.details["conversationId"] == conversationId
                }
                val newUsages = refs.map { ref ->
                    val previous = preservedByKey[filenameKey(row.sha256, ref)]
                    val filename = previous?.filename?.takeIf { it.isNotBlank() }
                        ?: FileUtils.buildUuidFileName(null, row.mimeType)
                    val displayName = ref.displayName?.takeIf { it.isNotBlank() }
                        ?: previous?.displayName?.takeIf { it.isNotBlank() }
                        ?: baseDisplayName
                    messageUsage(filename, displayName, ref)
                }
                val combined = withNoneUsage(others + newUsages) { fileTime(row.sha256) }
                if (combined != usages) repository.update(row.encodeUsages(combined))
            }
        }
    }

    /** 删除会话时移除它在全部登记里的消息使用记录。 */
    suspend fun removeConversationFromAttachmentIndex(conversationId: Uuid) = withContext(Dispatchers.IO) {
        usagesMutex.withLock {
            val id = conversationId.toString()
            repository.listAllNow().forEach { row ->
                val usages = row.decodeUsages()
                val filtered = usages.filterNot {
                    it.type == FileUsageType.MESSAGE && it.details["conversationId"] == id
                }
                val combined = withNoneUsage(filtered) { fileTime(row.sha256) }
                if (combined != usages) repository.update(row.encodeUsages(combined))
            }
        }
    }

    /** 按消息库完整重建全部消息使用记录（保留已有记录里的 [FileUsage.filename] 与 [FileUsage.displayName]）。 */
    suspend fun invalidateAttachmentIndex() = withContext(Dispatchers.IO) {
        usagesMutex.withLock {
            val preservedFilenames = mutableMapOf<String, ArrayDeque<String>>()
            val preservedDisplayNames = mutableMapOf<String, ArrayDeque<String>>()
            repository.listAllNow().forEach { row ->
                row.decodeUsages().filter { it.isMessageUsage() }.forEach { usage ->
                    val ref = usage.toMessageRef() ?: return@forEach
                    val key = filenameKey(row.sha256, ref)
                    preservedFilenames.getOrPut(key) { ArrayDeque() } += usage.filename
                    preservedDisplayNames.getOrPut(key) { ArrayDeque() } += usage.displayName
                }
            }

            val refsBySha = linkedMapOf<String, MutableList<MessageRef>>()
            // 复用与 updateAttachmentIndex 相同的收集逻辑（不直接扫 JSON 里所有 url），
            // 保证重建结果与常规保存一致；单条消息 JSON 损坏时跳过该节点。
            repository.listConversationIdsByLatest().forEach { conversationId ->
                repository.listMessageNodes(conversationId).forEach nodeLoop@{ node ->
                    val messages = runCatching {
                        JsonInstant.decodeFromString<List<UIMessage>>(node.messages)
                    }.getOrNull() ?: return@nodeLoop
                    messages.forEach { message ->
                        val seenInMessage = mutableSetOf<String>()
                        val messageAt = message.createdAt.toEpochMillis()
                        message.parts.collectAttachmentRefs().forEach refLoop@{ (sha, fileName) ->
                            if (!seenInMessage.add(sha)) return@refLoop
                            refsBySha.getOrPut(sha) { mutableListOf() } += MessageRef(
                                conversationId = conversationId,
                                nodeId = node.id,
                                messageId = message.id.toString(),
                                createdAt = messageAt,
                                displayName = fileName,
                            )
                        }
                    }
                }
            }
            refsBySha.keys.forEach { ensureRowForSha(it) }

            val rows = repository.listAllNow()
            rows.forEach { row ->
                val usages = row.decodeUsages()
                // 设置类与 none 记录不属于消息库，保留在 others 里
                val others = usages.filterNot { it.isMessageUsage() }
                val baseDisplayName = usages.latestDisplayName()
                val refs = refsBySha[row.sha256].orEmpty()
                val newUsages = refs.map { ref ->
                    val key = filenameKey(row.sha256, ref)
                    val filename = preservedFilenames[key]?.removeFirstOrNull()
                        ?: FileUtils.buildUuidFileName(null, row.mimeType)
                    val displayName = ref.displayName?.takeIf { it.isNotBlank() }
                        ?: preservedDisplayNames[key]?.removeFirstOrNull()?.takeIf { it.isNotBlank() }
                        ?: baseDisplayName
                    messageUsage(filename, displayName, ref)
                }
                val combined = withNoneUsage(others + newUsages) { fileTime(row.sha256) }
                if (combined != usages) repository.update(row.encodeUsages(combined))
            }
        }
    }

    /**
     * 设置（助手头像/聊天背景、用户头像）变化后，重建这些附件在 `managed_files_cas` 里的使用记录。
     * 与消息使用记录互不影响，设置里不再引用的内容会退回 `none` 记录；展示名沿用原有设置记录或
     * `none` 记录里的值（旧库导入与刚提交的附件都先写在 `none` 上）。
     */
    suspend fun updateSettingsAttachmentUsages(settings: Settings) = withContext(Dispatchers.IO) {
        usagesMutex.withLock {
            val existingNames = mutableMapOf<String, String>()
            val existingTimes = mutableMapOf<String, Long>()
            val noneNames = mutableMapOf<String, String>()
            val rows = repository.listAllNow()
            rows.forEach { row ->
                row.decodeUsages().forEach { usage ->
                    when {
                        usage.type in SETTINGS_USAGE_TYPES -> {
                            val identity = usageIdentity(row.sha256, usage.type, usage.details)
                            existingTimes[identity] = usage.createdAt
                            usage.displayName.takeIf { it.isNotBlank() }?.let { existingNames[identity] = it }
                        }

                        usage.type == FileUsageType.NONE ->
                            usage.displayName.takeIf { it.isNotBlank() }?.let { noneNames[row.sha256] = it }
                    }
                }
            }

            val usagesBySha = linkedMapOf<String, MutableList<FileUsage>>()
            fun add(url: String?, type: String, details: Map<String, String>) {
                val sha256 = url?.let(::attachmentShaFromUrl)?.takeIf { it.isNotBlank() } ?: return
                val identity = usageIdentity(sha256, type, details)
                usagesBySha.getOrPut(sha256) { mutableListOf() } += FileUsage(
                    type = type,
                    displayName = existingNames[identity] ?: noneNames[sha256].orEmpty(),
                    createdAt = existingTimes[identity] ?: System.currentTimeMillis(),
                    details = details,
                )
            }
            settings.assistants.forEach { assistant ->
                val details = mapOf("assistantId" to assistant.id.toString())
                add((assistant.avatar as? Avatar.Image)?.url, FileUsageType.ASSISTANT_AVATAR, details)
                add(assistant.background, FileUsageType.ASSISTANT_BACKGROUND, details)
            }
            add((settings.displaySetting.userAvatar as? Avatar.Image)?.url, FileUsageType.USER_AVATAR, emptyMap())

            usagesBySha.keys.forEach { ensureRowForSha(it) }
            repository.listAllNow().forEach { row ->
                val usages = row.decodeUsages()
                val others = usages.filterNot { it.type in SETTINGS_USAGE_TYPES }
                val combined = withNoneUsage(others + usagesBySha[row.sha256].orEmpty()) { fileTime(row.sha256) }
                if (combined != usages) repository.update(row.encodeUsages(combined))
            }
        }
    }

    // ------------------------------------------------------------------ 存储管理

    /**
     * “存储管理”页的附件列表。附件属性直接取自登记表，使用记录同步来自 [FileUsage]。
     */
    suspend fun listCasAttachments(forceRebuild: Boolean = false): List<CasAttachment> =
        withContext(Dispatchers.IO) {
            if (forceRebuild) invalidateAttachmentIndex()
            repository.listAllNow()
                .mapNotNull { row ->
                    val file = File(uploadDir, row.sha256)
                    if (!file.isFile || AttachmentCas.isTemporary(file)) return@mapNotNull null
                    CasAttachment(
                        sha256 = row.sha256,
                        file = file,
                        mimeType = row.mimeType.ifBlank { guessMimeType(file, row.sha256) },
                        sizeBytes = file.length(),
                        usages = row.decodeUsages(),
                    )
                }
                .sortedByDescending { it.latestManagedAt }
        }

    /** 删除指定 sha256 内容：文件与登记一并删除。 */
    suspend fun deleteCasAttachments(shas: Collection<String>): Boolean = withContext(Dispatchers.IO) {
        usagesMutex.withLock {
            var allDeleted = true
            shas.toSet().forEach { sha ->
                val file = File(uploadDir, sha)
                val deleted = !file.exists() || runCatching { file.delete() }.getOrDefault(false)
                if (deleted) {
                    repository.deleteBySha(sha)
                } else {
                    allDeleted = false
                }
            }
            allDeleted
        }
    }

    /** 清空全部上传附件。 */
    suspend fun deleteAllCasAttachments(): Boolean = withContext(Dispatchers.IO) {
        usagesMutex.withLock {
            var allDeleted = true
            uploadDir.listFiles().orEmpty().forEach { file ->
                if (!runCatching { file.deleteRecursively() }.getOrDefault(false)) allDeleted = false
            }
            if (allDeleted) {
                repository.deleteAll()
            } else {
                repository.listAllNow().forEach { row ->
                    if (!File(uploadDir, row.sha256).isFile) repository.deleteBySha(row.sha256)
                }
            }
            allDeleted
        }
    }

    suspend fun countChatFiles(): CasStorageStats = withContext(Dispatchers.IO) {
        val files = uploadDir.listFiles()
            ?.filter { it.isFile && !AttachmentCas.isTemporary(it) }
            .orEmpty()
        val references = repository.listAllNow()
            .sumOf { row -> row.decodeUsages().count { it.isMessageUsage() } }
        CasStorageStats(
            fileCount = files.size,
            referenceCount = references,
            sizeBytes = files.sumOf { it.length() },
        )
    }

    /**
     * 删除一份上传内容（Web 端按 id 删除）。
     * 仍被消息/设置引用时保留文件，行存在即返回 true；行不存在时返回 false。
     */
    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        usagesMutex.withLock {
            val row = repository.getById(id) ?: return@withLock false
            deleteUploadEntity(row)
            true
        }
    }

    /**
     * 启动时对齐磁盘与登记表：为 `upload` 下没有登记的 sha256 补一条记录，并清理文件已不存在的记录。
     * 输入框临时目录不在这里处理，由启动时的目录清理直接删除。
     */
    suspend fun syncManagedFiles() = withContext(Dispatchers.IO) {
        usagesMutex.withLock {
            val dir = uploadDir.apply { mkdirs() }
            AttachmentCas.deleteStaleTemporaryFiles(dir)
            dir.listFiles().orEmpty()
                .filter { it.isFile && !AttachmentCas.isTemporary(it) }
                .forEach { file ->
                    val sha256 = file.name
                    if (repository.getBySha(sha256) == null) {
                        ensureRow(
                            sha256 = sha256,
                            file = file,
                            mimeType = guessMimeType(file, sha256),
                            createdAt = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                        )
                    }
                }
            repository.listAllNow().forEach { row ->
                if (!File(dir, row.sha256).isFile) repository.deleteBySha(row.sha256)
            }
        }
    }

    // ------------------------------------------------------------------ 图片导出

    fun getImagesDir(): File {
        val dir = context.filesDir.resolve("images")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun createImageFileFromBase64(base64Data: String, filePath: String): File {
        val data = if (base64Data.startsWith("data:image")) {
            base64Data.substringAfter("base64,")
        } else {
            base64Data
        }

        val byteArray = Base64.decode(data.toByteArray())
        val file = File(filePath)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArray)
        return file
    }

    fun listImageFiles(): List<File> {
        val imagesDir = getImagesDir()
        return imagesDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp") }
            ?.toList()
            ?: emptyList()
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun saveMessageImage(activityContext: Context, image: String) = withContext(Dispatchers.IO) {
        val activity = requireNotNull(activityContext.getActivity()) { "Activity not found" }
        when {
            image.startsWith("data:image") -> {
                val byteArray = Base64.decode(image.substringAfter("base64,").toByteArray())
                val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                activityContext.exportImage(activity, bitmap)
            }

            image.startsWith("file://") -> {
                val file = image.toUri().toFile()
                activityContext.exportImageFile(activity, file)
            }

            image.startsWith("/") -> {
                activityContext.exportImageFile(activity, File(image))
            }

            image.startsWith("http") -> {
                runCatching {
                    val url = URL(image)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connect()

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                        activityContext.exportImage(activity, bitmap)
                    } else {
                        Log.e(
                            TAG,
                            "saveMessageImage: Failed to download image from $image, response code: ${connection.responseCode}"
                        )
                    }
                }.getOrNull()
            }

            else -> error("Invalid image format")
        }
    }

    // ------------------------------------------------------------------ 内部

    private fun writeUploadFile(writeContent: (OutputStream) -> Unit): Pair<String, File> {
        val dir = uploadDir.apply { mkdirs() }
        val (temporary, sha256) = AttachmentCas.writeTemporary(dir, writeContent)
        return sha256 to AttachmentCas.commitTemporary(dir, sha256, temporary)
    }

    private suspend fun ensureRow(
        sha256: String,
        file: File,
        mimeType: String,
        displayName: String? = null,
        createdAt: Long = System.currentTimeMillis(),
    ): ManagedFileCasEntity = rowsMutex.withLock {
        val existing = repository.getBySha(sha256)
        if (existing == null) {
            repository.insert(
                ManagedFileCasEntity(
                    sha256 = sha256,
                    mimeType = mimeType,
                    sizeBytes = file.length(),
                ).encodeUsages(listOf(noneUsage(createdAt, displayName.orEmpty())))
            )
        } else {
            val resolvedMime = if (existing.mimeType.isBlank()) mimeType else existing.mimeType
            val sizeBytes = file.length()
            val usages = existing.decodeUsages()
            val normalized = if (displayName.isNullOrBlank() || usages.any { it.type != FileUsageType.NONE }) {
                withNoneUsage(usages) { createdAt }
            } else {
                // 只有 none 记录时更新展示名，保留原来的落盘时间
                val none = usages.firstOrNull { it.type == FileUsageType.NONE }
                listOf(noneUsage(none?.createdAt?.takeIf { it > 0 } ?: createdAt, displayName))
            }
            if (resolvedMime != existing.mimeType || sizeBytes != existing.sizeBytes || normalized != usages) {
                val updated = existing.copy(
                    mimeType = resolvedMime,
                    sizeBytes = sizeBytes,
                ).encodeUsages(normalized)
                repository.update(updated)
                updated
            } else {
                existing
            }
        }
    }

    private suspend fun ensureRowForSha(sha256: String): ManagedFileCasEntity? {
        if (repository.getBySha(sha256) != null) return null
        val file = File(uploadDir, sha256)
        if (!file.isFile) return null
        return ensureRow(sha256, file, guessMimeType(file, sha256))
    }

    /** 附件不再被任何使用引用时保留一条 [FileUsageType.NONE] 记录，有引用时把它去掉。 */
    private fun withNoneUsage(usages: List<FileUsage>, fallbackCreatedAt: () -> Long): List<FileUsage> {
        val referenced = usages.filterNot { it.type == FileUsageType.NONE }
        if (referenced.isNotEmpty()) return referenced
        val existing = usages.firstOrNull { it.type == FileUsageType.NONE }
        val createdAt = existing?.createdAt?.takeIf { it > 0 } ?: fallbackCreatedAt()
        return listOf(noneUsage(createdAt, existing?.displayName.orEmpty()))
    }

    private fun noneUsage(createdAt: Long, displayName: String = ""): FileUsage = FileUsage(
        type = FileUsageType.NONE,
        displayName = displayName,
        createdAt = createdAt,
    )

    private fun messageUsage(filename: String, displayName: String, ref: MessageRef): FileUsage = FileUsage(
        type = FileUsageType.MESSAGE,
        filename = filename,
        displayName = displayName,
        createdAt = ref.createdAt,
        details = mapOf(
            "conversationId" to ref.conversationId,
            "nodeId" to ref.nodeId,
            "messageId" to ref.messageId,
        ),
    )

    private fun filenameKey(sha256: String, ref: MessageRef): String =
        "$sha256\u0000${ref.conversationId}\u0000${ref.nodeId}\u0000${ref.messageId}"

    /** 设置类使用记录的稳定标识：sha256 + type + 排序后的 details，用于保留原有时间与展示名。 */
    private fun usageIdentity(sha256: String, type: String, details: Map<String, String>): String =
        "$sha256\u0000$type\u0000" +
            details.toSortedMap().entries.joinToString("\u0000") { "${it.key}=${it.value}" }

    private fun FileUsage.toMessageRef(): MessageRef? {
        if (type != FileUsageType.MESSAGE) return null
        return MessageRef(
            conversationId = details["conversationId"] ?: return null,
            nodeId = details["nodeId"] ?: return null,
            messageId = details["messageId"] ?: return null,
            createdAt = createdAt,
            displayName = displayName,
        )
    }

    private fun fileTime(sha256: String): Long =
        File(uploadDir, sha256).lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()

    private fun LocalDateTime.toEpochMillis(): Long =
        toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun getRelativePathInFilesDir(file: File): String? =
        FileUtils.getRelativePathInFilesDir(context.filesDir, file)

    fun getFileNameFromUri(uri: Uri): String? = FileUtils.getFileNameFromUri(context, uri)

    fun getFileMimeType(uri: Uri): String? = FileUtils.getFileMimeType(context, uri)

    /** 按本地文件名/内容猜测 MIME（输入框临时附件没有来源 MIME 时使用）。 */
    fun guessLocalFileMimeType(file: File): String = FileUtils.guessMimeType(file, file.name)

    private fun guessMimeType(file: File, fileName: String): String =
        FileUtils.guessMimeType(file, fileName)

    /** 输入框临时附件的展示名与 MIME。 */
    private data class TempAttachment(
        val displayName: String,
        val mimeType: String,
    )

    /** 刚提交成 CAS 的内容信息，用于一次性写入消息使用记录。 */
    private data class CommitInfo(
        val displayName: String,
        val mimeType: String,
    )

    private data class MessageRef(
        val conversationId: String,
        val nodeId: String,
        val messageId: String,
        val createdAt: Long = 0L,
        val displayName: String? = null,
    )
}

/**
 * “存储管理”页展示的一份内容寻址附件：相同内容只显示一张卡片。
 * [usages] 是登记表里的全部使用记录（含未被引用时的 `none` 记录）。
 */
data class CasAttachment(
    val sha256: String,
    val file: File,
    val mimeType: String,
    val sizeBytes: Long,
    val usages: List<FileUsage>,
) {
    /** 真实消息使用记录；文件管理页只把“消息”与设置类使用算作引用。 */
    val references: List<FileUsage>
        get() = usages.filter { it.isMessageUsage() }

    /** 设置类使用记录：助手头像/聊天背景、用户头像。 */
    val settingsUsages: List<FileUsage>
        get() = usages.filter { it.isSettingsUsage() }

    /** 是否被消息或设置（头像/背景、用户头像）引用。 */
    val hasReferences: Boolean
        get() = references.isNotEmpty() || settingsUsages.isNotEmpty()

    /** 没有任何真实使用（只剩 `none` 记录）。 */
    val isUnused: Boolean
        get() = usages.none { it.type != FileUsageType.NONE }

    /** 全部使用记录里出现过的展示名（缺失时退回该次使用的 `uuid.ext` 文件名）。 */
    private val displayNames: List<String>
        get() = usages.map { it.displayName.ifBlank { it.filename } }.filter { it.isNotBlank() }.distinct()

    /**
     * 存储管理页的卡片标题：有展示名时取展示名，多个展示名不完全一致时在后追加 `, ...`；
     * 没有展示名时退回 sha256 缩写。
     */
    val displayName: String
        get() = when {
            displayNames.isEmpty() -> sha256Short
            displayNames.size == 1 -> displayNames.first()
            else -> "${displayNames.first()}, ..."
        }

    /** “按最新的引用排序”取全部使用记录里最晚的时间。 */
    val latestManagedAt: Long get() = usages.maxOfOrNull { it.createdAt } ?: 0L

    /** “按最旧的引用排序”取全部使用记录里最早的时间。 */
    val oldestManagedAt: Long get() = usages.minOfOrNull { it.createdAt } ?: 0L

    /** sha256 的前 6 位与后 6 位, 中间用 `...` 连接。 */
    val sha256Short: String get() = "${sha256.take(6)}...${sha256.takeLast(6)}"
}

/** “存储管理”统计: [fileCount] 为 `upload` 下的文件数, [referenceCount] 为消息使用记录数。 */
data class CasStorageStats(
    val fileCount: Int,
    val referenceCount: Int,
    val sizeBytes: Long,
)

/**
 * 从附件 url 里取出 sha256。运行形式（`file://.../files/upload/<sha>`）与落盘形式
 * （`upload/<sha>`）都取 `upload/` 之后的那一段。只接受本地形式，避免把 http/content
 * URL 里碰巧出现的 `upload/` 路径段当成附件。
 */
internal fun attachmentShaFromUrl(url: String): String? {
    if (url.isBlank()) return null
    if (url.contains("://") && !url.startsWith("file://")) return null
    val marker = "${FileFolders.UPLOAD}/"
    val index = url.lastIndexOf(marker)
    if (index < 0) return null
    val rest = url.substring(index + marker.length)
    val name = rest.substringBefore('/')
    return name.takeIf { it.isNotBlank() }
}

/**
 * 从输入框临时附件 url 里取出临时文件名。只接受本地形式（`file://.../upload_tmp/<name>` 或
 * 落盘形式 `upload_tmp/<name>`），不是临时附件时返回 null。
 */
internal fun tempAttachmentNameFromUrl(url: String): String? {
    if (url.isBlank()) return null
    if (url.contains("://") && !url.startsWith("file://")) return null
    val marker = "${FileFolders.UPLOAD_TEMP}/"
    val index = url.lastIndexOf(marker)
    if (index < 0) return null
    val name = url.substring(index + marker.length).substringBefore('/')
    return name.takeIf { it.isNotBlank() && !it.contains("..") }
}

private fun List<UIMessagePart>.collectAttachmentRefs(): List<Pair<String, String?>> = buildList {
    this@collectAttachmentRefs.forEach { part ->
        when (part) {
            is UIMessagePart.Image -> attachmentShaFromUrl(part.url)?.let { add(it to null) }
            is UIMessagePart.Document -> attachmentShaFromUrl(part.url)?.let { add(it to part.fileName) }
            is UIMessagePart.Video -> attachmentShaFromUrl(part.url)?.let { add(it to null) }
            is UIMessagePart.Audio -> attachmentShaFromUrl(part.url)?.let { add(it to null) }
            is UIMessagePart.Tool -> addAll(part.output.collectAttachmentRefs())
            else -> Unit
        }
    }
}

object FileFolders {
    const val UPLOAD = "upload"

    /** 输入框/队列里的临时附件目录（`cacheDir` 下，不参与 CAS）。 */
    const val UPLOAD_TEMP = "upload_tmp"
    const val SKILLS = "skills"
    const val BUILTIN_SKILLS = "builtin_skills"
    const val FONTS = "fonts"
    const val TOOL_OUTPUTS = "tool_outputs"
    const val MEDIA_CREATION = "media_creation"
}
