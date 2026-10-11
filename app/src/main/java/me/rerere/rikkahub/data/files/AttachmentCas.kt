package me.rerere.rikkahub.data.files

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * 聊天附件的内容寻址存储 (CAS)。
 *
 * 附件的真实内容保存在 `files/upload-cas/<sha256>` 下, `files/upload` 里只保存指向
 * `../upload-cas/<sha256>` 的相对软链接。相同内容只落一份实体文件, 多个软链接可以共享它。
 *
 * `cacheDir/chat_attachments.json` 是完整、可随时重建的附件引用缓存, 只记录 `version` 与 `entries`:
 * `entries` 以 sha256 为键, 记录**全部使用了该附件的消息** (软链接文件名、会话、节点、消息、助手、
 * 文件夹)。不记录未被任何消息引用的软链接, 这类“未使用的附件”由“存储管理”页直接对比
 * `upload-cas` 实体集合得到; 引用的时间不写入缓存, 需要时从 `managed_files` 表读取。
 *
 * 缓存不存在或损坏时由 `FilesManager.rebuildAttachmentIndex()` 扫描消息库与 `upload`/`upload-cas`
 * 完整重建; 新增或删除附件时 [FilesManager] 会在同一把锁内更新它。所有索引读写都经过 [indexLock],
 * 避免并发修改互相覆盖。
 */
object AttachmentCas {
    const val INDEX_FILE = "chat_attachments.json"

    /** 软链接指向 `upload` 同级的 `upload-cas`, 使用相对路径以便整个目录可以整体搬迁/打包。 */
    private const val LINK_TARGET_PREFIX = "../${FileFolders.UPLOAD_CAS}/"
    private const val TEMP_PREFIX = "upload-"
    private const val TEMP_SUFFIX = ".tmp"
    private const val HASH_BUFFER_SIZE = 128 * 1024
    private const val INDEX_VERSION = 1

    /** 启动时清理残留临时文件的年龄阈值, 避免删掉正在写入的临时文件。 */
    private const val STALE_TEMP_MAX_AGE_MILLIS = 60 * 60 * 1000L
    private val HEX = "0123456789abcdef".toCharArray()

    /** 索引读写的唯一锁: 新增、删除与重建都经过它, 防止互相覆盖。 */
    private val indexLock = Any()

    fun uploadDir(filesRoot: File): File = File(filesRoot, FileFolders.UPLOAD)

    fun casDir(filesRoot: File): File = File(filesRoot, FileFolders.UPLOAD_CAS)

    fun indexFile(cacheDir: File): File = File(cacheDir, INDEX_FILE)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(HASH_BUFFER_SIZE).use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    fun isSymlink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    /** 写入实体过程中产生的临时文件 (进程中断可能残留), 备份与打包时应跳过。 */
    fun isTemporary(file: File): Boolean =
        file.name.startsWith(TEMP_PREFIX) && file.name.endsWith(TEMP_SUFFIX)

    /** 读取软链接指向的实体 sha256; 非 CAS 软链接返回 null。 */
    fun readSha(link: File): String? {
        val target = runCatching { Files.readSymbolicLink(link.toPath()) }.getOrNull() ?: return null
        return target.fileName?.toString()?.takeIf { it.isNotBlank() }
    }

    /** 在 [link] 位置创建指向 `../upload-cas/<sha256>` 的相对软链接 (覆盖已有同名链接)。 */
    fun createSymlink(link: File, sha256: String) {
        link.parentFile?.mkdirs()
        if (link.exists() || isSymlink(link)) {
            link.delete()
        }
        Files.createSymbolicLink(link.toPath(), Paths.get(LINK_TARGET_PREFIX + sha256))
    }

    /**
     * 导入时把 [filesRoot] 下的 `upload` 目录转换成 `upload-cas` + 软链接 `upload`。
     *
     * 场景:
     * - 导入 ZIP 备份时, `upload` 里是展开后的真实文件;
     * - 导入不带 `upload-cas` 的 TAR 备份时, `upload` 里是真实文件。
     *
     * 已经是软链接的条目会跳过, 因此对已经转换过的目录重复调用是安全的。
     * 转换发生在恢复流程中, 调用方随后会清空索引, 由下次启动或“存储管理”页完整重建。
     */
    fun convertUploadFolderToCas(filesRoot: File) {
        val upload = uploadDir(filesRoot)
        if (!upload.isDirectory) return
        val cas = casDir(filesRoot)
        if (!cas.isDirectory && !cas.mkdirs()) {
            error("Cannot create directory: $cas")
        }
        upload.listFiles().orEmpty().forEach { file ->
            if (!file.isFile) return@forEach
            if (isSymlink(file)) return@forEach
            val sha256 = sha256(file)
            val entity = File(cas, sha256)
            if (!entity.exists()) {
                if (!file.renameTo(entity)) {
                    // rename 失败 (或并发下目标已被创建) 时退回复制; 内容按 sha256 命名, 覆盖安全
                    file.copyTo(entity, overwrite = true)
                    file.delete()
                }
            }
            createSymlink(file, sha256)
        }
    }

    /** 软链接对实体的一次引用: [sha256] 实体 + 引用它的软链接文件名。 */
    data class LinkRef(val sha256: String, val linkName: String)

    /** 创建写入实体用的临时文件, 命名与 [isTemporary] 的判定保持一致。 */
    fun createTemporaryFile(casDir: File): File =
        File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, casDir)

    /**
     * 把 [writeContent] 流式写入 `casDir` 下的临时文件, 边写边计算 sha256, 避免写完后再整份读一遍。
     * 返回临时文件与 sha256; 写入失败时删除临时文件并抛出。
     */
    fun writeTemporary(casDir: File, writeContent: (OutputStream) -> Unit): Pair<File, String> {
        val temporary = createTemporaryFile(casDir)
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(temporary).use { fileOutput ->
                DigestOutputStream(fileOutput, digest).use { output ->
                    writeContent(output)
                }
            }
            temporary to digest.digest().toHex()
        } catch (e: Throwable) {
            temporary.delete()
            throw e
        }
    }

    /**
     * 清理写入实体过程中残留的临时文件 (进程中断会留下)。只删除 [maxAgeMillis] 之前创建的,
     * 避免删掉其它线程正在写入的临时文件。
     */
    fun deleteStaleTemporaryFiles(filesRoot: File, maxAgeMillis: Long = STALE_TEMP_MAX_AGE_MILLIS) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        casDir(filesRoot).listFiles().orEmpty()
            .filter { isTemporary(it) && it.lastModified() < cutoff }
            .forEach { it.delete() }
    }

    /**
     * 把 [sha256] 实体落盘并在 `upload` 下建立指向它的软链接。实体落盘与建立软链接必须在同一把锁内
     * 完成, 否则「[removeLinks] 判断实体已无引用并删除它」可能穿插在「本方法判断实体已存在」与
     * 「建立软链接」之间, 留下指向已删实体的软链接。
     *
     * 索引里只保存消息引用, 新建软链接本身不产生引用, 因此这里不写索引; 消息真正引用附件时由
     * `FilesManager.updateAttachmentIndex()` 登记。
     */
    fun linkEntity(
        filesRoot: File,
        sha256: String,
        linkName: String,
        temporary: File,
    ): File = synchronized(indexLock) {
        val entity = File(casDir(filesRoot), sha256)
        if (entity.exists()) {
            temporary.delete()
        } else if (!temporary.renameTo(entity)) {
            // rename 失败 (或并发下目标已被创建) 时退回复制; 内容按 sha256 命名, 覆盖安全
            temporary.copyTo(entity, overwrite = true)
            temporary.delete()
        }
        val link = File(uploadDir(filesRoot).apply { mkdirs() }, linkName)
        createSymlink(link, sha256)
        link
    }

    /**
     * 一次性移除一批软链接的引用 (调用方已先删除对应的软链接文件), 删除不再被任何软链接引用的实体,
     * 并把索引里所有引用这些软链接文件名的消息条目一并删除。
     *
     * 实体是否仍被引用以磁盘上的软链接为准 (索引不再保存软链接集合), 因此这里回扫一次 `upload`。
     * [updateIndex] 为 false 时只删除实体、不改索引, 供调用方把索引更新合并到后续一次读写里。
     */
    fun removeLinks(
        cacheDir: File,
        filesRoot: File,
        links: List<LinkRef>,
        updateIndex: Boolean = true,
    ) {
        if (links.isEmpty()) return
        synchronized(indexLock) {
            val stillReferenced = linkedShas(filesRoot)
            links.map { it.sha256 }.toSet().forEach { sha256 ->
                if (sha256 !in stillReferenced) {
                    File(casDir(filesRoot), sha256).delete()
                }
            }
            if (!updateIndex) return@synchronized
            val removedFileNames = links.mapTo(HashSet()) { it.linkName }
            updateIndexIfPresentLocked(cacheDir) { entries ->
                val iterator = entries.entries.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    item.value.removeAll { it.fileName in removedFileNames }
                    if (item.value.isEmpty()) iterator.remove()
                }
            }
        }
    }

    /**
     * 删除不再被任何软链接引用的指定实体 (调用方需先删除对应软链接, 例如孤立实体)。
     * 以磁盘上的软链接为准, 回扫 `upload` 目录, 并返回扫描到的仍被引用的 sha256 集合,
     * 供调用方判断哪些 sha256 的索引条目仍应保留。
     */
    fun deleteUnreferencedEntities(filesRoot: File, shas: Set<String>): Set<String> {
        if (shas.isEmpty()) return emptySet()
        return synchronized(indexLock) {
            val stillReferenced = linkedShas(filesRoot)
            shas.forEach { sha256 ->
                if (sha256 !in stillReferenced) {
                    File(casDir(filesRoot), sha256).delete()
                }
            }
            stillReferenced
        }
    }

    /** 读取附件索引 (sha256 -> 全部消息引用条目); 文件不存在、损坏或版本不匹配时返回 null。 */
    fun readIndex(cacheDir: File): Map<String, List<ChatAttachmentEntry>>? = synchronized(indexLock) {
        readIndexFileLocked(cacheDir)?.mapValuesTo(linkedMapOf()) { (_, entries) -> entries.toList() }
    }

    /** 索引是否缺失或损坏, 需要完整重建。 */
    fun needsRebuild(cacheDir: File): Boolean = synchronized(indexLock) {
        readIndexFileLocked(cacheDir) == null
    }

    /** 用完整重建的结果整体替换索引 (sha256 -> 消息引用)。 */
    fun replaceIndex(
        cacheDir: File,
        entries: Map<String, List<ChatAttachmentEntry>>,
    ) = synchronized(indexLock) {
        writeNormalizedIndexLocked(cacheDir, normalizedIndex(entries))
    }

    /**
     * 索引文件存在时在锁内读取并原地修改它; 文件不存在或损坏时返回 false 且不写入, 交由完整重建补齐,
     * 避免只根据当前一次更新写出不完整的索引。
     */
    fun updateIndexIfPresent(
        cacheDir: File,
        transform: (MutableMap<String, MutableList<ChatAttachmentEntry>>) -> Unit,
    ): Boolean = synchronized(indexLock) {
        updateIndexIfPresentLocked(cacheDir, transform)
    }

    /** 清空索引 (例如恢复后), 后续读取会从消息库与磁盘扫描重建。 */
    fun clearIndex(cacheDir: File) = synchronized(indexLock) {
        indexFile(cacheDir).delete()
    }

    /** 丢弃实体文件与索引, 供清空全部附件时使用。 */
    fun clearAll(cacheDir: File, filesRoot: File) = synchronized(indexLock) {
        casDir(filesRoot).listFiles().orEmpty().forEach { it.delete() }
        indexFile(cacheDir).delete()
    }

    /** 扫描 `upload` 目录, 返回仍被软链接引用的 sha256 集合。 */
    private fun linkedShas(filesRoot: File): Set<String> =
        uploadDir(filesRoot).listFiles().orEmpty().mapNotNull { readSha(it) }.toSet()

    private fun updateIndexIfPresentLocked(
        cacheDir: File,
        transform: (MutableMap<String, MutableList<ChatAttachmentEntry>>) -> Unit,
    ): Boolean {
        val loaded = readIndexFileLocked(cacheDir) ?: return false
        // 文件始终以规范化形式写出, 读回的 loaded 就是上一次的规范形式; 这里只做一次浅拷贝用于比较,
        // 不必为了判断「是否变化」再对整个索引排序一次, 避免每次保存会话都重复排序。
        val before: Map<String, List<ChatAttachmentEntry>> =
            loaded.mapValuesTo(linkedMapOf<String, List<ChatAttachmentEntry>>()) { (_, entries) -> entries.toList() }
        transform(loaded)
        val after = normalizedIndex(loaded)
        // 内容没有变化时不重写文件, 避免每次保存会话都整份读写索引
        if (after != before) {
            writeNormalizedIndexLocked(cacheDir, after)
        }
        return true
    }

    private fun readIndexFileLocked(cacheDir: File): MutableMap<String, MutableList<ChatAttachmentEntry>>? {
        val indexFile = indexFile(cacheDir)
        if (!indexFile.isFile) return null
        val parsed = runCatching {
            JsonInstant.decodeFromString<ChatAttachmentsIndex>(indexFile.readText())
        }.getOrNull()
        if (parsed?.version != INDEX_VERSION) return null
        return parsed.entries.mapValuesTo(linkedMapOf()) { (_, entries) -> entries.toMutableList() }
    }

    /** 索引的规范形式: 丢弃空列表、按 sha256 排序、同一 sha256 的条目按文件名与消息标识排序。 */
    private fun normalizedIndex(
        entries: Map<String, List<ChatAttachmentEntry>>,
    ): Map<String, List<ChatAttachmentEntry>> = entries.entries
        .filter { it.value.isNotEmpty() }
        .sortedBy { it.key }
        .associateTo(linkedMapOf<String, List<ChatAttachmentEntry>>()) { (sha256, list) ->
            sha256 to list.sortedWith(
                compareBy(
                    { it.fileName },
                    { it.conversationId },
                    { it.nodeId },
                    { it.messageId },
                )
            )
        }

    /** 写入已经过 [normalizedIndex] 规范化的索引, 调用方不得传入未排序的条目。 */
    private fun writeNormalizedIndexLocked(
        cacheDir: File,
        entries: Map<String, List<ChatAttachmentEntry>>,
    ) {
        val indexFile = indexFile(cacheDir)
        indexFile.parentFile?.mkdirs()
        val temporary = File(indexFile.parentFile, "${indexFile.name}.tmp")
        temporary.writeText(
            JsonInstant.encodeToString(
                ChatAttachmentsIndex(
                    version = INDEX_VERSION,
                    entries = entries,
                )
            )
        )
        if (!temporary.renameTo(indexFile)) {
            indexFile.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private fun ByteArray.toHex(): String {
        val hex = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            hex[index * 2] = HEX[value ushr 4]
            hex[index * 2 + 1] = HEX[value and 0x0F]
        }
        return String(hex)
    }
}

/**
 * 某个 sha256 实体下的一条附件引用: 一条使用了该附件的消息。
 *
 * [fileName] 是 `upload` 下的软链接文件名 (不带 `upload/` 前缀)。同一个 sha256 被多条消息
 * 使用时会在列表中出现多条记录。引用时间不写入缓存, 需要时按 [fileName] 从 `managed_files` 读取。
 */
@Serializable
data class ChatAttachmentEntry(
    val fileName: String,
    val conversationId: String,
    val nodeId: String,
    val messageId: String,
    val folderId: String? = null,
    val assistantId: String? = null,
)

@Serializable
private data class ChatAttachmentsIndex(
    val version: Int = 1,
    /** sha256 -> 全部消息引用条目。 */
    val entries: Map<String, List<ChatAttachmentEntry>> = emptyMap(),
)
