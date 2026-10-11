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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.exportImageFile
import me.rerere.rikkahub.utils.getActivity
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

class FilesManager(
    private val context: Context,
    private val repository: FilesRepository,
    private val appScope: AppScope,
) {
    companion object {
        private const val TAG = "FilesManager"
    }

    private val attachmentIndexMutex = Mutex()

    private val filesRoot get() = context.filesDir
    private val uploadDir get() = AttachmentCas.uploadDir(filesRoot)
    private val uploadCasDir get() = AttachmentCas.casDir(filesRoot)

    suspend fun saveManagedFromUri(
        folder: String,
        uri: Uri,
        displayName: String? = null,
        mimeType: String? = null,
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val resolvedName = displayName ?: getFileNameFromUri(uri) ?: "file"
        val resolvedMime = mimeType ?: getFileMimeType(uri) ?: "application/octet-stream"
        val target = if (folder == FileFolders.UPLOAD) {
            storeUploadFile(resolvedName, resolvedMime) { output ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.copyTo(output)
                }
            }
        } else {
            createTargetFile(folder, resolvedName, resolvedMime).also { file ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        createManagedFileEntity(
            folder = folder,
            file = target,
            displayName = resolvedName,
            mimeType = resolvedMime,
        )
    }

    suspend fun saveManagedFromBytes(
        folder: String,
        bytes: ByteArray,
        displayName: String,
        mimeType: String = "application/octet-stream",
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val target = if (folder == FileFolders.UPLOAD) {
            storeUploadFile(displayName, mimeType) { it.write(bytes) }
        } else {
            createTargetFile(folder, displayName, mimeType).also { it.writeBytes(bytes) }
        }
        createManagedFileEntity(
            folder = folder,
            file = target,
            displayName = displayName,
            mimeType = mimeType,
        )
    }

    suspend fun saveManagedText(
        folder: String,
        text: String,
        displayName: String = "pasted_text.txt",
        mimeType: String = "text/plain",
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val target = if (folder == FileFolders.UPLOAD) {
            storeUploadFile(displayName, mimeType) { it.write(text.toByteArray()) }
        } else {
            createTargetFile(folder, displayName, mimeType).also { it.writeText(text) }
        }
        createManagedFileEntity(
            folder = folder,
            file = target,
            displayName = displayName,
            mimeType = mimeType,
        )
    }

    fun observe(folder: String = FileFolders.UPLOAD): Flow<List<ManagedFileEntity>> =
        repository.listByFolder(folder)

    suspend fun list(folder: String = FileFolders.UPLOAD): List<ManagedFileEntity> =
        repository.listByFolder(folder).first()

    /** `upload` 软链接文件名 -> 展示名, 供“使用了该文件的消息”列表显示人类可读名称。 */
    suspend fun uploadDisplayNames(): Map<String, String> =
        list(FileFolders.UPLOAD).associate { it.relativePath.substringAfterLast('/') to it.displayName }

    suspend fun get(id: Long): ManagedFileEntity? = repository.getById(id)

    suspend fun getByRelativePath(relativePath: String): ManagedFileEntity? = repository.getByPath(relativePath)

    /**
     * 启动时确保附件索引存在, 不存在或损坏时扫描消息库与 `upload`/`upload-cas` 完整重建。
     * 索引就绪后, 新增/删除附件等路径会原地更新它。
     */
    suspend fun ensureAttachmentIndex() = withContext(Dispatchers.IO) {
        attachmentIndexMutex.withLock {
            if (AttachmentCas.needsRebuild(context.cacheDir)) {
                rebuildAttachmentIndex()
            }
        }
    }

    /**
     * 列出全部 `upload-cas` 实体, 每个实体的引用来自索引。排序交给调用方 (存储管理页支持切换排序),
     * 这里不排序。
     */
    suspend fun listCasAttachments(forceRebuild: Boolean = false): List<CasAttachment> =
        withContext(Dispatchers.IO) {
            val index = attachmentIndexMutex.withLock {
                if (forceRebuild) {
                    rebuildAttachmentIndex()
                } else {
                    AttachmentCas.readIndex(context.cacheDir) ?: rebuildAttachmentIndex()
                }
            }
            // 附件的时间与展示名不写入索引, 统一从 managed_files 表读取该实体全部软链接的信息
            val uploadsBySha = managedUploads()
            uploadCasDir.listFiles().orEmpty()
                .filter { it.isFile && !AttachmentCas.isTemporary(it) }
                .map { entity ->
                    val sha256 = entity.name
                    val entries = index[sha256].orEmpty()
                    val uploads = uploadsBySha[sha256].orEmpty()
                    val times = uploads.map { it.createdAt }
                    val displayNameByFileName = uploads.associate { it.fileName to it.displayName }
                    val createdAtByFileName = uploads.associate { it.fileName to it.createdAt }
                    // 引用按登记时间从新到旧排序后取展示名并去重, 首项即“最新的引用”的展示名
                    val referenceDisplayNames = entries
                        .sortedByDescending { createdAtByFileName[it.fileName] ?: Long.MIN_VALUE }
                        .mapNotNull { displayNameByFileName[it.fileName] }
                        .distinct()
                    val fallback = entity.lastModified()
                    CasAttachment(
                        sha256 = sha256,
                        file = entity,
                        // 实体文件名只有 sha256, 用软链接文件名 (带扩展名) 判断类型, 没有引用时按内容嗅探
                        mimeType = FileUtils.guessMimeType(entity, entries.firstOrNull()?.fileName.orEmpty()),
                        sizeBytes = entity.length(),
                        // 清理时以最新登记时间为准: 只要还有近期登记就不算过期
                        latestManagedAt = times.maxOrNull() ?: fallback,
                        oldestManagedAt = times.minOrNull() ?: fallback,
                        references = entries,
                        referenceDisplayNames = referenceDisplayNames,
                    )
                }
        }

    /** [managedUploads] 汇总的一个软链接: 文件名、展示名与在 `managed_files` 中的登记时间。 */
    private data class ManagedUpload(
        val fileName: String,
        val displayName: String,
        val createdAt: Long,
    )

    /**
     * 读取 `upload` 下每个软链接在 `managed_files` 中的展示名与登记时间, 按 sha256 汇总该实体的
     * 全部软链接。附件排序、清理与“最新引用展示名”都使用这份信息, 不再写入 `chat_attachments.json`。
     */
    private suspend fun managedUploads(): Map<String, List<ManagedUpload>> {
        val result = linkedMapOf<String, MutableList<ManagedUpload>>()
        repository.listByFolder(FileFolders.UPLOAD).first().forEach { entity ->
            val fileName = entity.relativePath.substringAfterLast('/')
            val sha256 = AttachmentCas.readSha(File(uploadDir, fileName)) ?: return@forEach
            result.getOrPut(sha256) { mutableListOf() } += ManagedUpload(
                fileName = fileName,
                displayName = entity.displayName,
                createdAt = entity.createdAt,
            )
        }
        return result
    }

    suspend fun updateAttachmentIndex(conversation: Conversation) = withContext(Dispatchers.IO) {
        attachmentIndexMutex.withLock {
            val conversationId = conversation.id.toString()
            val references = mutableListOf<AttachmentMessageReference>()
            conversation.messageNodes.forEach { node ->
                node.messages.forEach { message ->
                    message.parts.collectAttachmentFileNames().forEach { fileName ->
                        references += AttachmentMessageReference(
                            fileName = fileName,
                            nodeId = node.id.toString(),
                            messageId = message.id.toString(),
                        )
                    }
                }
            }
            AttachmentCas.updateIndexIfPresent(context.cacheDir) { entries ->
                // 先移除该会话的旧引用, 再按当前消息重新写入全部引用
                val iterator = entries.entries.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    item.value.removeAll { it.conversationId == conversationId }
                    if (item.value.isEmpty()) iterator.remove()
                }
                references.forEach { reference ->
                    // 索引不再保存软链接集合, 直接读软链接得到 sha256; 软链接已不存在则跳过
                    val sha256 = AttachmentCas.readSha(File(uploadDir, reference.fileName))
                        ?: return@forEach
                    entries.getOrPut(sha256) { mutableListOf() } += ChatAttachmentEntry(
                        fileName = reference.fileName,
                        conversationId = conversationId,
                        nodeId = reference.nodeId,
                        messageId = reference.messageId,
                    )
                }
            }
        }
    }

    suspend fun removeConversationFromAttachmentIndex(conversationId: Uuid) = withContext(Dispatchers.IO) {
        attachmentIndexMutex.withLock {
            val id = conversationId.toString()
            AttachmentCas.updateIndexIfPresent(context.cacheDir) { entries ->
                val iterator = entries.entries.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    item.value.removeAll { it.conversationId == id }
                    if (item.value.isEmpty()) iterator.remove()
                }
            }
        }
    }

    suspend fun invalidateAttachmentIndex() = withContext(Dispatchers.IO) {
        attachmentIndexMutex.withLock {
            // 清空后立即重建, 避免下次启动前 updateIndexIfPresent 因索引缺失而丢弃更新
            AttachmentCas.clearIndex(context.cacheDir)
            rebuildAttachmentIndex()
        }
    }

    /**
     * 扫描 `upload` 软链接、`upload-cas` 实体与全部会话中的附件引用, 重建 sha256 -> 全部消息引用索引。
     * 只有真实存在实体的引用才会写入缓存, 避免留下指向已删实体的陈年条目。
     */
    private suspend fun rebuildAttachmentIndex(): Map<String, List<ChatAttachmentEntry>> {
        val shaByFileName = linkedMapOf<String, String>()
        uploadDir.listFiles().orEmpty().forEach { link ->
            val sha256 = AttachmentCas.readSha(link) ?: return@forEach
            shaByFileName[link.name] = sha256
        }
        val existingEntities = uploadCasDir.listFiles().orEmpty()
            .filter { it.isFile && !AttachmentCas.isTemporary(it) }
            .mapTo(HashSet()) { it.name }
        val index = linkedMapOf<String, MutableList<ChatAttachmentEntry>>()
        repository.listConversationIdsByLatest().forEach { conversationId ->
            repository.listAttachmentReferences(conversationId).forEach references@{ reference ->
                val fileName = reference.attachmentUrl.toUri().lastPathSegment ?: return@references
                val sha256 = shaByFileName[fileName] ?: return@references
                if (sha256 !in existingEntities) return@references
                index.getOrPut(sha256) { mutableListOf() } += ChatAttachmentEntry(
                    fileName = fileName,
                    conversationId = conversationId,
                    nodeId = reference.nodeId,
                    messageId = reference.messageId,
                )
            }
        }
        // 同一条消息内同一附件可能被 json_tree 多次展开, 去重后与增量更新的结果保持一致
        val result = index.mapValuesTo(linkedMapOf()) { (_, entries) -> entries.distinct() }
        AttachmentCas.replaceIndex(context.cacheDir, entries = result)
        return result
    }

    fun getFile(entity: ManagedFileEntity): File =
        File(context.filesDir, entity.relativePath)

    fun createChatFilesByContents(uris: List<Uri>): List<Uri> =
        uris.mapNotNull { uri ->
            storeUploadFromUri(
                uri = uri,
                displayName = getFileNameFromUri(uri) ?: uri.lastPathSegment ?: "file",
                mimeType = null,
            )
        }

    private fun storeUploadFromUri(uri: Uri, displayName: String, mimeType: String?): Uri? {
        return runCatching {
            val sourceMime = mimeType ?: getFileMimeType(uri)
            val file = storeUploadFile(displayName, sourceMime) { output ->
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: error("Failed to open input stream for $uri")
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            val guessedMime = sourceMime ?: guessMimeType(file, displayName)
            trackManagedFile(
                folder = FileFolders.UPLOAD,
                file = file,
                displayName = displayName,
                mimeType = guessedMime
            )
            file.toUri()
        }.onFailure {
            it.printStackTrace()
            Log.e(TAG, "storeUploadFromUri: Failed to save file from $uri", it)
            Logging.log(
                TAG,
                "storeUploadFromUri: Failed to save file from $uri ${it.message} | ${it.stackTraceToString()}"
            )
        }.getOrNull()
    }

    fun createChatFilesByByteArrays(
        byteArrays: List<ByteArray>,
        displayName: String = "image.png",
        mimeType: String = "image/png",
    ): List<Uri> {
        val newUris = mutableListOf<Uri>()
        byteArrays.forEach { byteArray ->
            val file = storeUploadFile(displayName, mimeType) { output ->
                output.write(byteArray)
            }
            trackManagedFile(
                folder = FileFolders.UPLOAD,
                file = file,
                displayName = displayName,
                mimeType = mimeType
            )
            newUris.add(file.toUri())
        }
        return newUris
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
                                part.copy(
                                    url = urls.first().toString(),
                                )
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
     * 删除聊天附件。删除软链接会同步读写共享索引, 因此这里在 IO 线程内完成整批删除,
     * 调用方 (通常是 UI 事件) 应在自己的协程中调用并等待结果。
     */
    suspend fun deleteChatFiles(uris: List<Uri>) = withContext(Dispatchers.IO) {
        val candidates = uris.filter { it.toString().startsWith("file://") }
        if (candidates.isEmpty()) return@withContext
        val relativePaths = mutableSetOf<String>()
        val uploadFiles = mutableListOf<File>()
        candidates.forEach { uri ->
            val file = uri.toFile()
            val uploadName = uploadFileName(file)
            when {
                uploadName != null -> {
                    uploadFiles += file
                    relativePaths.add("${FileFolders.UPLOAD}/$uploadName")
                }
                // 实体文件由软链接的共享引用计数管理, 绝不应通过附件 URL 直接删除
                isUploadCasEntity(file) -> Unit
                else -> {
                    getRelativePathInFilesDir(file)?.let { relativePaths.add(it) }
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        }
        // 多个附件一次更新共享索引, 避免逐个删除时反复读写索引
        if (uploadFiles.isNotEmpty()) {
            deleteUploadFiles(uploadFiles)
        }
        relativePaths.forEach { path ->
            repository.deleteByPath(path)
        }
    }

    suspend fun countChatFiles(): CasStorageStats = withContext(Dispatchers.IO) {
        // 存储管理按 upload-cas 实体统计, 相同内容只算一份
        val files = uploadCasDir.listFiles()
            ?.filter { it.isFile && !AttachmentCas.isTemporary(it) }
            ?: return@withContext CasStorageStats(fileCount = 0, referenceCount = 0, sizeBytes = 0)
        CasStorageStats(
            fileCount = files.size,
            // 引用数按 `upload` 下的软链接数量统计: 同一份实体可以被多个软链接共享
            referenceCount = uploadDir.listFiles().orEmpty().count { AttachmentCas.isSymlink(it) },
            sizeBytes = files.sumOf { it.length() },
        )
    }

    fun createChatTextFile(text: String): UIMessagePart.Document {
        val file = storeUploadFile("pasted_text.txt", "text/plain") { it.write(text.toByteArray()) }
        trackManagedFile(
            folder = FileFolders.UPLOAD,
            file = file,
            displayName = "pasted_text.txt",
            mimeType = "text/plain"
        )
        return UIMessagePart.Document(
            url = file.toUri().toString(),
            fileName = "pasted_text.txt",
            mime = "text/plain"
        )
    }

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

    suspend fun syncFolder(folder: String = FileFolders.UPLOAD): SyncResult = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, folder)
        val diskFiles = if (dir.exists()) {
            dir.listFiles()?.filter { it.isFile }
                ?: return@withContext SyncResult(inserted = 0, removed = 0)
        } else {
            emptyList()
        }

        // 磁盘 -> 数据库：补录尚未登记的文件
        var inserted = 0
        val diskRelativePaths = HashSet<String>()
        diskFiles.forEach { file ->
            val relativePath = "${folder}/${file.name}"
            diskRelativePaths.add(relativePath)
            val existing = repository.getByPath(relativePath)
            if (existing == null) {
                val now = System.currentTimeMillis()
                val displayName = file.name
                val mimeType = guessMimeType(file, displayName)
                repository.insert(
                    ManagedFileEntity(
                        folder = folder,
                        relativePath = relativePath,
                        displayName = displayName,
                        mimeType = mimeType,
                        sizeBytes = file.length(),
                        createdAt = file.lastModified().takeIf { it > 0 } ?: now,
                        updatedAt = now,
                    )
                )
                inserted += 1
            }
        }

        // 数据库 -> 磁盘：清理文件已不存在的孤儿记录
        var removed = 0
        repository.listByFolder(folder).first().forEach { entity ->
            if (entity.relativePath !in diskRelativePaths && !getFile(entity).isFile) {
                removed += repository.deleteByPath(entity.relativePath)
            }
        }

        SyncResult(inserted = inserted, removed = removed)
    }

    suspend fun delete(id: Long, deleteFromDisk: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val entity = repository.getById(id) ?: return@withContext false
        if (deleteFromDisk) {
            // upload 下是软链接, 删除时必须同步维护实体文件与索引
            deleteEntitiesFromDisk(listOf(entity))
        }
        repository.deleteById(id) > 0
    }

    /**
     * 删除指定 sha256 实体: 一并删除指向它的全部软链接、附件索引条目与数据库记录。
     * 返回所有软链接是否都删除成功。
     */
    suspend fun deleteCasAttachments(shas: Collection<String>): Boolean = withContext(Dispatchers.IO) {
        val targets = shas.toSet()
        if (targets.isEmpty()) return@withContext true
        // 索引不再保存软链接集合, 直接扫描 `upload` 定位指向目标实体的软链接
        val links = uploadDir.listFiles().orEmpty().filter { AttachmentCas.readSha(it) in targets }
        // 跳过 deleteUploadFiles 的索引更新, 与下面的孤儿实体清理合并成一次索引读写
        val deleted = deleteUploadFiles(links, updateIndex = false)
        // 删除失败的软链接仍在磁盘上, 只为确实删掉的链接移除数据库记录, 避免留下无法再定位的残留链接
        val removedLinks = links.filterIndexed { index, _ -> deleted[index] }
        removedLinks.forEach { link ->
            repository.deleteByPath("${FileFolders.UPLOAD}/${link.name}")
        }
        // 没有软链接的孤立实体不会被 deleteUploadFiles 处理, 这里一并删除;
        // 返回值是磁盘上仍被引用的 sha256 集合, 用于决定哪些索引桶不能清除
        val stillReferenced = AttachmentCas.deleteUnreferencedEntities(filesRoot, targets)
        val removedFileNames = removedLinks.mapTo(HashSet()) { it.name }
        // 一次索引更新同时完成两件事: 移除已删软链接的消息引用, 以及清除已无软链接的 sha256 的整个索引桶
        // (软链接已缺失的陈旧条目也靠这一步兜底); 仍有软链接的 sha256 必须保留引用信息,
        // 否则会被误判成 "未使用的附件"
        AttachmentCas.updateIndexIfPresent(context.cacheDir) { entries ->
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.key in targets && item.key !in stillReferenced) {
                    iterator.remove()
                    continue
                }
                item.value.removeAll { it.fileName in removedFileNames }
                if (item.value.isEmpty()) iterator.remove()
            }
        }
        deleted.all { it }
    }

    /** 清空全部 `upload-cas` 实体及其软链接与数据库记录。 */
    suspend fun deleteAllCasAttachments(): Boolean = withContext(Dispatchers.IO) {
        val files = uploadDir.listFiles().orEmpty().toList()
        val results = deleteUploadFiles(files)
        if (!results.all { it }) {
            // 只为确实删掉的软链接移除数据库记录, 保留删除失败链接的记录与实体, 避免留下悬空链接
            files.filterIndexed { index, _ -> results[index] }.forEach { file ->
                repository.deleteByPath("${FileFolders.UPLOAD}/${file.name}")
            }
            return@withContext false
        }
        // 全部删除成功: 顺带清掉没有对应文件的孤立数据库记录
        repository.deleteByFolder(FileFolders.UPLOAD)
        // deleteUploadFiles 已按引用删除实体; 清空场景直接把残留实体与索引一并丢弃
        AttachmentCas.clearAll(context.cacheDir, filesRoot)
        true
    }

    /**
     * 把上传内容写入内容寻址存储, 并在 `upload` 下创建指向 `../upload-cas/<sha256>` 的软链接。
     * [writeContent] 负责把原始内容写入传入的输出流; 内容边写边计算 sha256, 不再二次读盘。
     */
    private fun storeUploadFile(
        displayName: String,
        mimeType: String?,
        writeContent: (OutputStream) -> Unit,
    ): File {
        val casDir = uploadCasDir.apply { mkdirs() }
        val (temporary, sha256) = AttachmentCas.writeTemporary(casDir, writeContent)
        try {
            val linkName = buildUuidFileName(displayName, mimeType)
            // 实体落盘与建立软链接由 AttachmentCas 在同一把锁内完成, 避免与并发删除互相穿插
            return AttachmentCas.linkEntity(filesRoot, sha256, linkName, temporary)
        } catch (e: Throwable) {
            temporary.delete()
            throw e
        }
    }

    /**
     * 批量删除附件的磁盘文件: `upload` 下的软链接一次性交给 [AttachmentCas.removeLinks] 处理
     * (共享索引只读写一次), 其它目录逐个删除。返回与 [entities] 一一对应的磁盘删除结果。
     */
    private fun deleteEntitiesFromDisk(entities: List<ManagedFileEntity>): List<Boolean> {
        val uploadEntities = entities.filter { it.folder == FileFolders.UPLOAD }
        val uploadResults = deleteUploadFiles(uploadEntities.map { getFile(it) })
        val results = MutableList(entities.size) { true }
        var uploadIndex = 0
        entities.forEachIndexed { index, entity ->
            if (entity.folder == FileFolders.UPLOAD) {
                results[index] = uploadResults[uploadIndex++]
            } else {
                val file = getFile(entity)
                results[index] = !file.exists() || runCatching { file.deleteRecursively() }.getOrDefault(false)
            }
        }
        return results
    }

    /**
     * 删除一批 `upload` 下的软链接; 当实体文件不再被任何软链接引用时将其删除。
     * [updateIndex] 为 true 时同时更新共享索引, 否则由调用方合并到后续的索引读写里。
     * 返回与 [files] 一一对应的「删除后该位置已不存在软链接」结果。
     */
    private fun deleteUploadFiles(files: List<File>, updateIndex: Boolean = true): List<Boolean> {
        val refs = mutableListOf<AttachmentCas.LinkRef>()
        val results = files.map { file ->
            if (!AttachmentCas.isSymlink(file)) {
                file.delete() || !file.exists()
            } else {
                val sha256 = AttachmentCas.readSha(file)
                val deleted = file.delete()
                if (deleted && sha256 != null) {
                    refs += AttachmentCas.LinkRef(sha256, file.name)
                }
                deleted || !AttachmentCas.isSymlink(file)
            }
        }
        if (refs.isNotEmpty()) {
            AttachmentCas.removeLinks(context.cacheDir, filesRoot, refs, updateIndex)
        }
        return results
    }

    private fun createTargetFile(folder: String, displayName: String, mimeType: String?): File {
        val dir = File(context.filesDir, folder)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, FileUtils.buildUuidFileName(displayName = displayName, mimeType = mimeType))
    }

    private fun buildUuidFileName(displayName: String?, mimeType: String?): String =
        FileUtils.buildUuidFileName(displayName, mimeType)

    private suspend fun createManagedFileEntity(
        folder: String,
        file: File,
        displayName: String,
        mimeType: String,
    ): ManagedFileEntity {
        val now = System.currentTimeMillis()
        return repository.insert(
            ManagedFileEntity(
                folder = folder,
                relativePath = buildRelativePath(folder, file),
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = file.length(),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private fun trackManagedFile(folder: String, file: File, displayName: String, mimeType: String) {
        val relativePath = buildRelativePath(folder, file)
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val existing = repository.getByPath(relativePath)
                if (existing != null) {
                    return@runCatching
                }
                val now = System.currentTimeMillis()
                repository.insert(
                    ManagedFileEntity(
                        folder = folder,
                        relativePath = relativePath,
                        displayName = displayName,
                        mimeType = mimeType,
                        sizeBytes = file.length(),
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }.onFailure {
                Log.e(TAG, "trackManagedFile: Failed to track file ${file.absolutePath}", it)
                Logging.log(
                    TAG,
                    "trackManagedFile: Failed to track file ${file.absolutePath} ${it.message} | ${it.stackTraceToString()}"
                )
            }
        }
    }

    private fun buildRelativePath(folder: String, file: File): String =
        FileUtils.buildRelativePath(folder, file)

    private fun getRelativePathInFilesDir(file: File): String? =
        FileUtils.getRelativePathInFilesDir(context.filesDir, file)

    /**
     * 文件位于 `filesDir/upload` 下时返回其文件名, 否则返回 null。
     * 只比较父目录, 不使用 canonicalFile, 避免把软链接解析到 `upload-cas`。
     */
    private fun uploadFileName(file: File): String? {
        val parent = runCatching { file.parentFile?.canonicalFile }.getOrNull() ?: return null
        val upload = runCatching { uploadDir.canonicalFile }.getOrNull() ?: return null
        return if (parent == upload && file.name.isNotBlank()) file.name else null
    }

    /** 文件是否直接位于 `filesDir/upload-cas` 下 (实体文件, 由引用计数管理)。 */
    private fun isUploadCasEntity(file: File): Boolean {
        val parent = runCatching { file.parentFile?.canonicalFile }.getOrNull() ?: return false
        val cas = runCatching { uploadCasDir.canonicalFile }.getOrNull() ?: return false
        return parent == cas
    }

    fun getFileNameFromUri(uri: Uri): String? =
        FileUtils.getFileNameFromUri(context, uri)

    fun getFileMimeType(uri: Uri): String? =
        FileUtils.getFileMimeType(context, uri)

    private fun guessMimeType(file: File, fileName: String): String =
        FileUtils.guessMimeType(file, fileName)
}

/**
 * “存储管理”页展示的一个 `upload-cas` 实体: 相同内容的多个软链接只显示一张卡片。
 * [latestManagedAt] 与 [oldestManagedAt] 取自 `managed_files` 表中该实体全部软链接的登记时间;
 * 没有任何登记记录时退回实体文件的修改时间。
 */
data class CasAttachment(
    val sha256: String,
    val file: File,
    val mimeType: String,
    val sizeBytes: Long,
    val latestManagedAt: Long,
    val oldestManagedAt: Long,
    val references: List<ChatAttachmentEntry>,
    /** 引用该实体的全部软链接展示名, 按登记时间从新到旧去重; 首项即“最新的引用”的展示名。 */
    val referenceDisplayNames: List<String> = emptyList(),
) {
    /**
     * 存储管理页的卡片标题: 有引用时取最新引用的展示名, 多个引用的展示名不完全一致时
     * 在后加 `, ...`; 没有任何可用的引用展示名时退回 sha256 缩写。
     */
    val displayName: String
        get() = when {
            referenceDisplayNames.isEmpty() -> sha256Abbreviation
            referenceDisplayNames.size == 1 -> referenceDisplayNames.first()
            else -> "${referenceDisplayNames.first()}, ..."
        }

    /** sha256 的前 6 位与后 6 位, 中间用 `...` 连接。 */
    val sha256Short: String
        get() = "${sha256.take(6)}...${sha256.takeLast(6)}"

    private val sha256Abbreviation: String
        get() = if (sha256.length > 12) "${sha256.take(6)}…${sha256.takeLast(6)}" else sha256
}

/** “存储管理”统计: [fileCount] 为 `upload-cas` 实体数, [referenceCount] 为 `upload` 下的软链接数量。 */
data class CasStorageStats(
    val fileCount: Int,
    val referenceCount: Int,
    val sizeBytes: Long,
)

/** [FilesManager.updateAttachmentIndex] 扫描一条消息时得到的一条附件引用。 */
private data class AttachmentMessageReference(
    val fileName: String,
    val nodeId: String,
    val messageId: String,
)

private fun List<UIMessagePart>.collectAttachmentFileNames(): Set<String> = buildSet {
    this@collectAttachmentFileNames.forEach { part ->
        val url = when (part) {
            is UIMessagePart.Image -> part.url
            is UIMessagePart.Document -> part.url
            is UIMessagePart.Video -> part.url
            is UIMessagePart.Audio -> part.url
            is UIMessagePart.Tool -> {
                addAll(part.output.collectAttachmentFileNames())
                null
            }
            else -> null
        }
        if (url?.startsWith("file://") == true) {
            url.toUri().lastPathSegment?.let(::add)
        }
    }
}

data class SyncResult(
    val inserted: Int,
    val removed: Int,
)

object FileFolders {
    const val UPLOAD = "upload"
    const val UPLOAD_CAS = "upload-cas"
    const val SKILLS = "skills"
    const val BUILTIN_SKILLS = "builtin_skills"
    const val FONTS = "fonts"
    const val TOOL_OUTPUTS = "tool_outputs"
    const val MEDIA_CREATION = "media_creation"
}

suspend fun FilesManager.saveUploadFromUri(
    uri: Uri,
    displayName: String? = null,
    mimeType: String? = null,
): ManagedFileEntity = saveManagedFromUri(
    folder = FileFolders.UPLOAD,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
)

suspend fun FilesManager.saveUploadFromBytes(
    bytes: ByteArray,
    displayName: String,
    mimeType: String = "application/octet-stream",
): ManagedFileEntity = saveManagedFromBytes(
    folder = FileFolders.UPLOAD,
    bytes = bytes,
    displayName = displayName,
    mimeType = mimeType,
)

suspend fun FilesManager.saveUploadText(
    text: String,
    displayName: String = "pasted_text.txt",
    mimeType: String = "text/plain",
): ManagedFileEntity = saveManagedText(
    folder = FileFolders.UPLOAD,
    text = text,
    displayName = displayName,
    mimeType = mimeType,
)
