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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.db.dao.ManagedFileWithReference
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.JsonInstant
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
        private const val ATTACHMENT_INDEX_FILE = "chat_attachment_index.json"
    }

    private val attachmentIndexMutex = Mutex()
    private val attachmentIndexFile get() = File(context.cacheDir, ATTACHMENT_INDEX_FILE)

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

    suspend fun get(id: Long): ManagedFileEntity? = repository.getById(id)

    suspend fun getByRelativePath(relativePath: String): ManagedFileEntity? = repository.getByPath(relativePath)

    suspend fun listWithReferences(
        folder: String = FileFolders.UPLOAD,
        forceRebuild: Boolean = false,
    ): List<ManagedFileWithReference> = withContext(Dispatchers.IO) {
        attachmentIndexMutex.withLock {
            val files = repository.listByFolder(folder).first()
            val cached = if (!forceRebuild) readAttachmentIndex() else null
            val entries = cached ?: rebuildAttachmentIndex(folder)
            val references = entries.associateBy { it.relativePath }
            files.map { file ->
                val reference = references[file.relativePath]
                ManagedFileWithReference(
                    file = file,
                    conversationId = reference?.conversationId,
                    nodeId = reference?.nodeId,
                )
            }
        }
    }

    suspend fun updateAttachmentIndex(conversation: Conversation) = withContext(Dispatchers.IO) {
        attachmentIndexMutex.withLock {
            val existing = readAttachmentIndex() ?: return@withLock
            val referencedNodes = buildMap {
                conversation.messageNodes.forEach { node ->
                    node.messages.forEach { message ->
                        message.parts.collectAttachmentFileNames().forEach { fileName ->
                            put(fileName, node.id.toString())
                        }
                    }
                }
            }
            val conversationId = conversation.id.toString()
            val updated = existing
                .filterNot { it.conversationId == conversationId }
                .associateByTo(linkedMapOf()) { it.relativePath }
            referencedNodes.forEach { (fileName, nodeId) ->
                val relativePath = "${FileFolders.UPLOAD}/$fileName"
                updated[relativePath] = AttachmentIndexEntry(
                    relativePath = relativePath,
                    conversationId = conversationId,
                    nodeId = nodeId,
                )
            }
            val newEntries = updated.values.toList()
            if (newEntries != existing) {
                writeAttachmentIndex(newEntries)
            }
        }
    }

    suspend fun removeConversationFromAttachmentIndex(conversationId: Uuid) = withContext(Dispatchers.IO) {
        attachmentIndexMutex.withLock {
            val existing = readAttachmentIndex() ?: return@withLock
            val updated = existing.filterNot { it.conversationId == conversationId.toString() }
            if (updated.size != existing.size) {
                writeAttachmentIndex(updated)
            }
        }
    }

    suspend fun invalidateAttachmentIndex() = withContext(Dispatchers.IO) {
        attachmentIndexMutex.withLock {
            attachmentIndexFile.delete()
        }
        AttachmentCas.clearIndex(context.cacheDir)
    }

    private suspend fun rebuildAttachmentIndex(folder: String): List<AttachmentIndexEntry> {
        val files = repository.listByFolder(folder).first()
        val filesByName = files.associateBy { it.relativePath.substringAfter('/') }
        val conversationIds = repository.listConversationIdsByLatest()
        val entriesByPath = linkedMapOf<String, AttachmentIndexEntry>()
        conversationIds.forEach { conversationId ->
            repository.listAttachmentReferences(conversationId).forEach references@{ reference ->
                val file = filesByName.entries.firstOrNull { (fileName, _) ->
                    reference.attachmentUrl.contains(fileName)
                }?.value ?: return@references
                entriesByPath.putIfAbsent(
                    file.relativePath,
                    AttachmentIndexEntry(
                        relativePath = file.relativePath,
                        conversationId = conversationId,
                        nodeId = reference.nodeId,
                    ),
                )
            }
        }
        val entries = entriesByPath.values.toList()
        writeAttachmentIndex(entries)
        return entries
    }

    private fun readAttachmentIndex(): List<AttachmentIndexEntry>? = runCatching {
        if (!attachmentIndexFile.isFile) return null
        JsonInstant.decodeFromString<AttachmentIndexCache>(attachmentIndexFile.readText())
            .takeIf { it.version == 1 }
            ?.entries
    }.getOrNull()

    private fun writeAttachmentIndex(entries: List<AttachmentIndexEntry>) {
        val temporary = File(attachmentIndexFile.parentFile, "${attachmentIndexFile.name}.tmp")
        temporary.writeText(JsonInstant.encodeToString(AttachmentIndexCache(entries = entries)))
        if (!temporary.renameTo(attachmentIndexFile)) {
            attachmentIndexFile.writeText(temporary.readText())
            temporary.delete()
        }
    }

    fun getFile(entity: ManagedFileEntity): File =
        File(context.filesDir, entity.relativePath)

    fun createChatFilesByContents(uris: List<Uri>): List<Uri> {
        val newUris = mutableListOf<Uri>()
        uris.forEach { uri ->
            runCatching {
                val sourceName = getFileNameFromUri(uri) ?: uri.lastPathSegment ?: "file"
                val sourceMime = getFileMimeType(uri)
                val file = storeUploadFile(sourceName, sourceMime) { output ->
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: error("Failed to open input stream for $uri")
                    inputStream.use { input ->
                        input.copyTo(output)
                    }
                }
                val guessedMime = sourceMime ?: guessMimeType(file, sourceName)
                trackManagedFile(
                    folder = FileFolders.UPLOAD,
                    file = file,
                    displayName = sourceName,
                    mimeType = guessedMime
                )
                newUris.add(file.toUri())
            }.onFailure {
                it.printStackTrace()
                Log.e(TAG, "createChatFilesByContents: Failed to save file from $uri", it)
                Logging.log(
                    TAG,
                    "createChatFilesByContents: Failed to save file from $uri ${it.message} | ${it.stackTraceToString()}"
                )
            }
        }
        return newUris
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

    suspend fun countChatFiles(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val dir = uploadDir
        if (!dir.exists()) {
            return@withContext Pair(0, 0)
        }
        val files = dir.listFiles() ?: return@withContext Pair(0, 0)
        val count = files.size
        // 软链接会跟随到实体文件, 直接相加会把共享的实体重复计算; 实体只按实际存储统计一次。
        val legacySize = files.filterNot(AttachmentCas::isSymlink).sumOf { it.length() }
        val entitySize = uploadCasDir.listFiles()
            ?.filterNot(AttachmentCas::isTemporary)
            ?.sumOf { it.length() }
            ?: 0L
        Pair(count, legacySize + entitySize)
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
     * 批量删除指定附件: 磁盘文件与共享索引只处理一次, 再删除数据库记录。
     * 返回所有数据库记录是否都删除成功。
     */
    suspend fun deleteAll(ids: List<Long>, deleteFromDisk: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val entities = ids.distinct().mapNotNull { repository.getById(it) }
        if (entities.isEmpty()) return@withContext true
        if (deleteFromDisk) {
            deleteEntitiesFromDisk(entities)
        }
        var allDeleted = true
        entities.forEach { entity ->
            if (repository.deleteById(entity.id) == 0) {
                allDeleted = false
            }
        }
        allDeleted
    }

    suspend fun deleteAll(folder: String = FileFolders.UPLOAD): Boolean = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, folder)
        val entries = dir.listFiles()
        if (dir.exists() && entries == null) {
            return@withContext false
        }

        var allDeletedFromDisk = true
        entries.orEmpty().forEach { entry ->
            if (!runCatching { entry.deleteRecursively() }.getOrDefault(false)) {
                allDeletedFromDisk = false
            }
        }

        if (folder == FileFolders.UPLOAD) {
            // 软链接已全部移除: 实体文件与索引也一并清掉; 否则至少丢弃索引以便扫描重建
            if (allDeletedFromDisk) {
                AttachmentCas.clearAll(context.cacheDir, filesRoot)
            } else {
                AttachmentCas.clearIndex(context.cacheDir)
            }
        }

        if (allDeletedFromDisk) {
            repository.deleteByFolder(folder)
            return@withContext true
        }

        repository.listByFolder(folder).first().forEach { entity ->
            if (!getFile(entity).exists()) {
                repository.deleteById(entity.id)
            }
        }
        false
    }

    suspend fun deleteOlderThan(
        folder: String = FileFolders.UPLOAD,
        cutoffMillis: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val candidates = repository.listByFolder(folder).first()
            .filter { it.createdAt < cutoffMillis }
        // 一次处理全部软链接, 避免逐个删除时反复读写共享索引
        val deletedFromDisk = deleteEntitiesFromDisk(candidates)
        var allDeleted = true
        candidates.forEachIndexed { index, entity ->
            if (deletedFromDisk[index]) {
                if (repository.deleteById(entity.id) == 0) {
                    allDeleted = false
                }
            } else {
                allDeleted = false
            }
        }
        allDeleted
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
            // 实体落盘、建立软链接与索引更新由 AttachmentCas 在同一把锁内完成, 避免与并发删除互相穿插
            return AttachmentCas.linkEntity(context.cacheDir, filesRoot, sha256, linkName, temporary)
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
     * 删除一批 `upload` 下的软链接, 并一次性更新共享索引; 当实体文件不再被任何软链接引用时将其删除。
     * 返回与 [files] 一一对应的「删除后该位置已不存在软链接」结果。
     */
    private fun deleteUploadFiles(files: List<File>): List<Boolean> {
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
            AttachmentCas.removeLinks(context.cacheDir, filesRoot, refs)
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

@Serializable
private data class AttachmentIndexCache(
    val version: Int = 1,
    val entries: List<AttachmentIndexEntry>,
)

@Serializable
private data class AttachmentIndexEntry(
    val relativePath: String,
    val conversationId: String,
    val nodeId: String,
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
