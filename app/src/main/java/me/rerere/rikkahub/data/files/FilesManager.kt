package me.rerere.rikkahub.data.files

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.db.entity.FileUsage
import me.rerere.rikkahub.data.db.entity.FileUsageType
import me.rerere.rikkahub.data.db.entity.ManagedFileCasEntity
import me.rerere.rikkahub.data.db.entity.decodeUsages
import me.rerere.rikkahub.data.db.entity.isMessageUsage
import me.rerere.rikkahub.data.db.entity.isPendingUsage
import me.rerere.rikkahub.data.db.entity.latestDisplayName
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.entity.encodeUsages
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.FilesRepository
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
 */
class FilesManager(
    private val context: Context,
    private val repository: FilesRepository,
    private val appScope: AppScope,
) {
    companion object {
        private const val TAG = "FilesManager"

        /** 设置类使用记录：这些类型在 [updateSettingsAttachmentUsages] 里整批重建。 */
        private val SETTINGS_USAGE_TYPES = setOf(
            FileUsageType.ASSISTANT_AVATAR,
            FileUsageType.ASSISTANT_BACKGROUND,
            FileUsageType.USER_AVATAR,
        )
    }

    /** 登记表读写的互斥锁: 落盘文件后落登记、同步补登记都经过它。 */
    private val rowsMutex = Mutex()

    /** 使用记录读写的互斥锁: 保存会话、重建索引与删除会话都经过它。 */
    private val usagesMutex = Mutex()

    private val filesRoot get() = context.filesDir

    /** 供落盘形式的相对附件路径换算使用（见 [LocalFileUrls]）。 */
    val filesDir: File get() = context.filesDir

    private val uploadDir get() = File(filesRoot, FileFolders.UPLOAD)

    // ------------------------------------------------------------------ 保存

    suspend fun saveUploadFromBytes(
        bytes: ByteArray,
        displayName: String? = null,
        mimeType: String = "application/octet-stream",
    ): ManagedFileCasEntity = withContext(Dispatchers.IO) {
        val (sha256, file) = writeUploadFile { it.write(bytes) }
        ensureRow(sha256, file, mimeType, displayName, pending = true)
    }

    // ------------------------------------------------------------------ 查询

    suspend fun get(id: Long): ManagedFileCasEntity? = repository.getById(id)

    /** 按落盘相对路径（如 `upload/<sha256>`）查询；非 upload 目录返回 null。 */
    suspend fun getByRelativePath(relativePath: String): ManagedFileCasEntity? {
        if (!relativePath.startsWith("${FileFolders.UPLOAD}/")) return null
        return repository.getBySha(relativePath.substringAfterLast('/'))
    }

    fun getFile(entity: ManagedFileCasEntity): File = File(uploadDir, entity.sha256)

    /** 每个内容时间最晚那条记录的展示名，供输入框显示附件名。 */
    fun latestDisplayNames(): Flow<Map<String, String>> =
        repository.listAll().map { rows ->
            rows.mapNotNull { row ->
                row.decodeUsages().latestDisplayName()
                    .takeIf { it.isNotBlank() }
                    ?.let { row.sha256 to it }
            }.toMap()
        }

    // ------------------------------------------------------------------ 聊天附件

    fun createChatFilesByContents(uris: List<Uri>): List<Uri> =
        uris.mapNotNull { uri -> storeUploadFromUri(uri, null, getFileNameFromUri(uri)) }

    private fun storeUploadFromUri(uri: Uri, mimeType: String?, displayName: String? = null): Uri? {
        return runCatching {
            val sourceMime = mimeType ?: getFileMimeType(uri)
            val (sha256, file) = writeUploadFile { output ->
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: error("Failed to open input stream for $uri")
                inputStream.use { input -> input.copyTo(output) }
            }
            val guessedMime = sourceMime ?: guessMimeType(file, sha256)
            trackSha(sha256, file, guessedMime, displayName)
            file.toUri()
        }.onFailure {
            Log.e(TAG, "storeUploadFromUri: Failed to save file from $uri", it)
            Logging.log(
                TAG,
                "storeUploadFromUri: Failed to save file from $uri ${it.message} | ${it.stackTraceToString()}"
            )
        }.getOrNull()
    }

    fun createChatFilesByByteArrays(
        byteArrays: List<ByteArray>,
        mimeType: String = "image/png",
    ): List<Uri> {
        val newUris = mutableListOf<Uri>()
        byteArrays.forEach { byteArray ->
            val (sha256, file) = writeUploadFile { output -> output.write(byteArray) }
            trackSha(sha256, file, mimeType)
            newUris.add(file.toUri())
        }
        return newUris
    }

    fun createChatTextFile(text: String): UIMessagePart.Document {
        val (sha256, file) = writeUploadFile { it.write(text.toByteArray()) }
        trackSha(sha256, file, "text/plain")
        return UIMessagePart.Document(
            url = file.toUri().toString(),
            fileName = "pasted_text.txt",
            mime = "text/plain",
        )
    }

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
                                val urls = createChatFilesByByteArrays(listOf(byteArray))
                                Log.i(
                                    TAG,
                                    "convertBase64ImagePartToLocalFile: convert base64 img to ${urls.joinToString(", ")}"
                                )
                                part.copy(url = urls.first().toString())
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
     * 删除聊天附件。内容寻址的文件可能被多处共享，只有不再被任何消息使用时才真正删除文件与登记。
     */
    suspend fun deleteChatFiles(uris: List<Uri>) = withContext(Dispatchers.IO) {
        val uploadPath = runCatching { uploadDir.canonicalPath }.getOrNull()
        uris.forEach { uri ->
            val file = runCatching { uri.toFile() }.getOrNull() ?: return@forEach
            val parent = runCatching { file.parentFile?.canonicalPath }.getOrNull()
            if (parent == uploadPath) {
                val row = repository.getBySha(file.name) ?: return@forEach
                val usages = row.decodeUsages()
                val remaining = usages.filterNot { it.isPendingUsage() }
                if (remaining.any { it.isMessageUsage() }) {
                    // 仍被真实消息引用：只移除这次从输入框移除的临时占位记录
                    if (remaining.size != usages.size) {
                        repository.update(row.encodeUsages(withNoneUsage(remaining) { fileTime(file.name) }))
                    }
                    return@forEach
                }
                file.delete()
                repository.deleteBySha(file.name)
            } else {
                getRelativePathInFilesDir(file)?.let { file.delete() }
            }
        }
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
            refsBySha.keys.forEach { ensureRowForSha(it) }
            val rows = repository.listAllNow()
            rows.forEach { row ->
                val usages = row.decodeUsages()
                // 按消息身份（sha + 会话 + 节点 + 消息）保留旧文件名/展示名，避免删除或重排消息后串位
                val preservedByKey = usages
                    .filter { it.isMessageUsage() && it.details["conversationId"] == conversationId }
                    .mapNotNull { usage -> usage.toMessageRef()?.let { filenameKey(row.sha256, it) to usage } }
                    .toMap()
                val refs = refsBySha[row.sha256].orEmpty()
                // 该内容时间最晚那条记录的展示名；新增附件的临时占位记录就是最新一条，
                // 图片/视频/音频没有 part.fileName，发送成消息后从这里把展示名带走
                val baseDisplayName = usages.latestDisplayName()
                // 本次会话已引用的内容不再是待发附件，丢弃它的临时占位记录
                val others = usages.filterNot {
                    (it.isMessageUsage() && it.details["conversationId"] == conversationId) ||
                        (refs.isNotEmpty() && it.isPendingUsage())
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
            val seenReferences = mutableSetOf<String>()
            repository.listConversationIdsByLatest().forEach { conversationId ->
                repository.listAttachmentReferences(conversationId).forEach { reference ->
                    val sha = attachmentShaFromUrl(reference.attachmentUrl) ?: return@forEach
                    val ref = MessageRef(
                        conversationId = conversationId,
                        nodeId = reference.nodeId,
                        messageId = reference.messageId,
                        createdAt = parseMessageAt(reference.messageAt),
                        displayName = reference.fileName,
                    )
                    // json_tree 会把同一条消息里的附件展开多次, 按 sha256 + 消息标识去重
                    if (!seenReferences.add(filenameKey(sha, ref))) return@forEach
                    refsBySha.getOrPut(sha) { mutableListOf() } += ref
                }
            }
            refsBySha.keys.forEach { ensureRowForSha(it) }

            val rows = repository.listAllNow()
            rows.forEach { row ->
                val usages = row.decodeUsages()
                // 临时占位记录不属于消息库，保留在 others 里
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
     * 与消息使用记录互不影响，设置里不再引用的内容会退回 `none` 记录。
     */
    suspend fun updateSettingsAttachmentUsages(settings: Settings) = withContext(Dispatchers.IO) {
        usagesMutex.withLock {
            val existingTimes = mutableMapOf<String, Long>()
            val rows = repository.listAllNow()
            rows.forEach { row ->
                row.decodeUsages().filter { it.type in SETTINGS_USAGE_TYPES }.forEach { usage ->
                    existingTimes[usageIdentity(row.sha256, usage.type, usage.details)] = usage.createdAt
                }
            }

            val usagesBySha = linkedMapOf<String, MutableList<FileUsage>>()
            fun add(url: String?, type: String, details: Map<String, String>) {
                val sha256 = url?.let(::attachmentShaFromUrl)?.takeIf { it.isNotBlank() } ?: return
                val createdAt = existingTimes[usageIdentity(sha256, type, details)] ?: System.currentTimeMillis()
                usagesBySha.getOrPut(sha256) { mutableListOf() } += FileUsage(
                    type = type,
                    createdAt = createdAt,
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

    /** 清空全部上传附件。 */
    suspend fun deleteAllCasAttachments(): Boolean = withContext(Dispatchers.IO) {
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

    suspend fun delete(id: Long, deleteFromDisk: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val row = repository.getById(id) ?: return@withContext false
        if (deleteFromDisk) {
            runCatching { File(uploadDir, row.sha256).delete() }
        }
        repository.deleteById(id) > 0
    }

    /**
     * 启动时对齐磁盘与登记表：为 `upload` 下没有登记的 sha256 补一条记录，并清理文件已不存在的记录。
     */
    suspend fun syncManagedFiles() = withContext(Dispatchers.IO) {
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
            if (!File(dir, row.sha256).isFile) {
                repository.deleteBySha(row.sha256)
                return@forEach
            }
            // 输入框里的临时占位记录不跨进程存活，启动时统一清掉
            val usages = row.decodeUsages()
            if (usages.any { it.isPendingUsage() }) {
                val cleaned = usages.filterNot { it.isPendingUsage() }
                repository.update(row.encodeUsages(withNoneUsage(cleaned) { fileTime(row.sha256) }))
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

    private fun trackSha(
        sha256: String,
        file: File,
        mimeType: String,
        displayName: String? = null,
    ) {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                ensureRow(sha256, file, mimeType, displayName, pending = true)
            }.onFailure {
                Log.e(TAG, "trackSha: Failed to track file ${file.absolutePath}", it)
                Logging.log(
                    TAG,
                    "trackSha: Failed to track file ${file.absolutePath} ${it.message} | ${it.stackTraceToString()}"
                )
            }
        }
    }

    private suspend fun ensureRow(
        sha256: String,
        file: File,
        mimeType: String,
        displayName: String? = null,
        pending: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
    ): ManagedFileCasEntity = rowsMutex.withLock {
        val existing = repository.getBySha(sha256)
        if (existing == null) {
            val usages = if (pending) {
                emptyList<FileUsage>().addPendingUsage(displayName, createdAt)
            } else {
                listOf(noneUsage(createdAt))
            }
            repository.insert(
                ManagedFileCasEntity(
                    sha256 = sha256,
                    mimeType = mimeType,
                    sizeBytes = file.length(),
                ).encodeUsages(usages)
            )
        } else {
            val resolvedMime = if (existing.mimeType.isBlank()) mimeType else existing.mimeType
            val sizeBytes = file.length()
            val usages = existing.decodeUsages()
            val merged = if (pending) usages.addPendingUsage(displayName, createdAt) else usages
            val normalized = withNoneUsage(merged) { createdAt }
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

    /**
     * 新增聊天附件：按当前时间追加一条临时 `message` 占位记录，再把展示名写到时间最晚的那条记录上。
     * 占位记录不带会话/节点/消息定位，发送成消息后由 [updateAttachmentIndex] 换成真实消息记录。
     */
    private fun List<FileUsage>.addPendingUsage(displayName: String?, now: Long): List<FileUsage> {
        val appended = this + FileUsage(type = FileUsageType.MESSAGE, createdAt = now)
        if (displayName.isNullOrBlank()) return appended
        val latest = appended.indices.maxByOrNull { appended[it].createdAt } ?: return appended
        return appended.toMutableList().also { it[latest] = it[latest].copy(displayName = displayName) }
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
        return listOf(existing?.copy(createdAt = createdAt) ?: FileUsage(type = FileUsageType.NONE, createdAt = createdAt))
    }

    private fun noneUsage(createdAt: Long): FileUsage = FileUsage(
        type = FileUsageType.NONE,
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

    /** 设置类使用记录的稳定标识：sha256 + type + 排序后的 details，用于保留原有时间。 */
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

    private fun parseMessageAt(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching {
            LocalDateTime.parse(value).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }.getOrDefault(0L)
    }

    private fun LocalDateTime.toEpochMillis(): Long =
        toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun getRelativePathInFilesDir(file: File): String? =
        FileUtils.getRelativePathInFilesDir(context.filesDir, file)

    fun getFileNameFromUri(uri: Uri): String? = FileUtils.getFileNameFromUri(context, uri)

    fun getFileMimeType(uri: Uri): String? = FileUtils.getFileMimeType(context, uri)

    private fun guessMimeType(file: File, fileName: String): String =
        FileUtils.guessMimeType(file, fileName)

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
    /** 真实消息使用记录；文件管理页只展示这部分，设置类与待发占位记录都不提供跳转。 */
    val references: List<FileUsage>
        get() = usages.filter { it.isMessageUsage() }

    /** 没有任何使用（只剩 `none` 记录）。设置类使用也算被使用，只是不在文件管理页展示。 */
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
 * （`upload/<sha>`）都取 `upload/` 之后的那一段。
 */
internal fun attachmentShaFromUrl(url: String): String? {
    val marker = "${FileFolders.UPLOAD}/"
    val index = url.lastIndexOf(marker)
    if (index < 0) return null
    val rest = url.substring(index + marker.length)
    val name = rest.substringBefore('/')
    return name.takeIf { it.isNotBlank() }
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
    const val SKILLS = "skills"
    const val BUILTIN_SKILLS = "builtin_skills"
    const val FONTS = "fonts"
    const val TOOL_OUTPUTS = "tool_outputs"
    const val MEDIA_CREATION = "media_creation"
}
