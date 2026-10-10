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
 * `chat_attachment_symlinks.json` 记录 sha256 -> 引用该实体的软链接文件名列表, 用于删除
 * 软链接时判断实体文件是否还有引用 (索引丢失时可以直接扫描 `upload` 目录重建)。
 */
object AttachmentCas {
    const val SYMLINK_INDEX_FILE = "chat_attachment_symlinks.json"

    /** 软链接指向 `upload` 同级的 `upload-cas`, 使用相对路径以便整个目录可以整体搬迁/打包。 */
    private const val LINK_TARGET_PREFIX = "../${FileFolders.UPLOAD_CAS}/"
    private const val TEMP_PREFIX = "upload-"
    private const val TEMP_SUFFIX = ".tmp"
    private const val HASH_BUFFER_SIZE = 128 * 1024
    private const val INDEX_VERSION = 1

    /** 启动时清理残留临时文件的年龄阈值, 避免删掉正在写入的临时文件。 */
    private const val STALE_TEMP_MAX_AGE_MILLIS = 60 * 60 * 1000L
    private val HEX = "0123456789abcdef".toCharArray()

    private val indexLock = Any()

    fun uploadDir(filesRoot: File): File = File(filesRoot, FileFolders.UPLOAD)

    fun casDir(filesRoot: File): File = File(filesRoot, FileFolders.UPLOAD_CAS)

    fun indexFile(cacheDir: File): File = File(cacheDir, SYMLINK_INDEX_FILE)

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
     * 把 [sha256] 实体落盘并在 `upload` 下建立指向它的软链接, 索引一并更新。
     *
     * 实体落盘与建立软链接必须在同一把锁内完成: 否则「[removeLinks] 判断实体已无引用并删除它」
     * 可能穿插在「本方法判断实体已存在」与「建立软链接」之间, 留下指向已删实体的软链接。
     */
    fun linkEntity(
        cacheDir: File,
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
        val index = readIndexLocked(cacheDir, filesRoot)
        val links = index.getOrPut(sha256) { mutableListOf() }
        if (linkName !in links) {
            links += linkName
        }
        writeIndexLocked(cacheDir, index)
        link
    }

    /**
     * 一次性移除一批软链接的引用 (调用方已先删除对应的软链接文件), 并删除不再被任何软链接引用的实体。
     *
     * 索引可能缺失或过期, 因此是否还有引用以磁盘上的软链接为准; 无论删除多少个引用, 索引只读写一次、
     * `upload` 目录只回扫一次, 批量清理不会退化成逐个删除的平方级开销。
     */
    fun removeLinks(cacheDir: File, filesRoot: File, links: List<LinkRef>) = synchronized(indexLock) {
        val index = readIndexLocked(cacheDir, filesRoot)
        val candidates = linkedSetOf<String>()
        links.forEach { ref ->
            val names = index[ref.sha256]
            if (names != null) {
                names.remove(ref.linkName)
                if (names.isEmpty()) index.remove(ref.sha256)
            }
            // 只在索引已不含该实体 (刚删空或索引过期) 时才考虑删除, 避免误删仍被引用的实体
            if (ref.sha256 !in index) {
                candidates += ref.sha256
            }
        }
        if (candidates.isNotEmpty()) {
            val stillReferenced = linkedShas(filesRoot)
            candidates.forEach { sha256 ->
                if (sha256 !in stillReferenced) {
                    File(casDir(filesRoot), sha256).delete()
                }
            }
        }
        writeIndexLocked(cacheDir, index)
    }

    /** 扫描 `upload` 目录, 返回仍被软链接引用的 sha256 集合。 */
    private fun linkedShas(filesRoot: File): Set<String> =
        uploadDir(filesRoot).listFiles().orEmpty().mapNotNull { readSha(it) }.toSet()

    /** 清空索引 (例如整目录替换或恢复后), 后续读取会从磁盘扫描重建。 */
    fun clearIndex(cacheDir: File) = synchronized(indexLock) {
        indexFile(cacheDir).delete()
    }

    /** 丢弃实体文件与索引, 供清空全部附件时使用。 */
    fun clearAll(cacheDir: File, filesRoot: File) = synchronized(indexLock) {
        casDir(filesRoot).listFiles().orEmpty().forEach { it.delete() }
        indexFile(cacheDir).delete()
    }

    private fun readIndexLocked(cacheDir: File, filesRoot: File): MutableMap<String, MutableList<String>> {
        val indexFile = indexFile(cacheDir)
        if (indexFile.isFile) {
            val parsed = runCatching {
                JsonInstant.decodeFromString<SymlinkIndex>(indexFile.readText())
            }.getOrNull()
            if (parsed?.version == INDEX_VERSION) {
                return parsed.entries
                    .mapValuesTo(linkedMapOf<String, MutableList<String>>()) { (_, links) -> links.toMutableList() }
            }
        }
        return rebuildIndexLocked(cacheDir, filesRoot)
    }

    private fun rebuildIndexLocked(cacheDir: File, filesRoot: File): MutableMap<String, MutableList<String>> {
        val index = linkedMapOf<String, MutableList<String>>()
        uploadDir(filesRoot).listFiles().orEmpty().forEach { file ->
            val sha256 = readSha(file) ?: return@forEach
            index.getOrPut(sha256) { mutableListOf() }.add(file.name)
        }
        writeIndexLocked(cacheDir, index)
        return index
    }

    private fun writeIndexLocked(cacheDir: File, index: Map<String, List<String>>) {
        val indexFile = indexFile(cacheDir)
        indexFile.parentFile?.mkdirs()
        val sorted = index.entries
            .sortedBy { it.key }
            .associateTo(linkedMapOf<String, List<String>>()) { (sha256, links) -> sha256 to links.sorted() }
        val temporary = File(indexFile.parentFile, "${indexFile.name}.tmp")
        temporary.writeText(JsonInstant.encodeToString(SymlinkIndex(entries = sorted)))
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

@Serializable
private data class SymlinkIndex(
    val version: Int = 1,
    val entries: Map<String, List<String>> = emptyMap(),
)
