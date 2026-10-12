package me.rerere.rikkahub.data.files

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * 聊天附件的内容寻址存储 (CAS) 辅助工具。
 *
 * 附件实体直接以 sha256 命名保存在 `files/upload/<sha256>`，内容相同的附件只落一份。
 * 登记信息（展示名、MIME、大小、使用位置）保存在 `managed_files_cas` 表中，这里只负责
 * 计算 sha256、边写边算哈希地落盘，以及清理写入中断残留的临时文件。
 */
object AttachmentCas {
    /** 写入实体过程中产生的临时文件的命名前缀/后缀（见 [isTemporary]）。 */
    private const val TEMP_PREFIX = "upload-"
    private const val TEMP_SUFFIX = ".tmp"
    private const val HASH_BUFFER_SIZE = 128 * 1024

    /** 启动时清理残留临时文件的年龄阈值, 避免删掉正在写入的临时文件。 */
    private const val STALE_TEMP_MAX_AGE_MILLIS = 60 * 60 * 1000L
    private val HEX = "0123456789abcdef".toCharArray()

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

    /** 写入实体过程中产生的临时文件 (进程中断可能残留), 备份、同步与打包时应跳过。 */
    fun isTemporary(file: File): Boolean =
        file.name.startsWith(TEMP_PREFIX) && file.name.endsWith(TEMP_SUFFIX)

    /**
     * 把 [writeContent] 流式写入 [dir] 下的临时文件, 边写边计算 sha256, 避免写完后再整份读一遍。
     * 返回临时文件与 sha256; 写入失败时删除临时文件并抛出。
     */
    fun writeTemporary(dir: File, writeContent: (OutputStream) -> Unit): Pair<File, String> {
        dir.mkdirs()
        val temporary = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, dir)
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
     * 把 [temporary] 落盘成 `dir/<sha256>`; 目标已存在时丢弃临时文件。返回目标文件。
     * 内容按 sha256 命名, 并发覆盖安全。
     */
    fun commitTemporary(dir: File, sha256: String, temporary: File): File {
        val entity = File(dir, sha256)
        if (entity.exists()) {
            temporary.delete()
            return entity
        }
        if (temporary.renameTo(entity)) return entity
        temporary.copyTo(entity, overwrite = true)
        temporary.delete()
        return entity
    }

    /**
     * 清理写入实体过程中残留的临时文件 (进程中断会留下)。只删除 [maxAgeMillis] 之前创建的,
     * 避免删掉其它线程正在写入的临时文件。
     */
    fun deleteStaleTemporaryFiles(dir: File, maxAgeMillis: Long = STALE_TEMP_MAX_AGE_MILLIS) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        dir.listFiles().orEmpty()
            .filter { isTemporary(it) && it.lastModified() < cutoff }
            .forEach { it.delete() }
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
