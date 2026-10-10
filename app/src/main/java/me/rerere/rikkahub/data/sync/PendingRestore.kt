package me.rerere.rikkahub.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.files.FileFolders
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

/**
 * Staged restores live outside cache and are installed before any database/settings consumers start.
 * The journal and atomic moves allow an interrupted installation to resume on the next launch.
 */
internal class PendingRestore(
    private val root: File,
    private val databaseFile: File,
    private val filesDir: File,
) {
    private val pending get() = File(root, "pending")

    fun createStagingDirectory(): File {
        check(!pending.exists()) { "A backup restore is already pending. Restart the app first." }
        check(root.isDirectory || root.mkdirs()) { "Cannot create backup staging directory" }
        return Files.createTempDirectory(root.toPath(), "preparing-").toFile()
    }

    fun publish(staging: File) {
        check(!pending.exists()) { "A backup restore is already pending. Restart the app first." }
        move(staging, pending)
    }

    suspend fun apply(restoreSettings: suspend (String) -> Unit): Boolean {
        // Completed restores must never be replayed, even if cleanup was interrupted.
        root.listFiles()?.filter {
            it.name.startsWith("completed-") || it.name.startsWith("preparing-")
        }?.forEach { it.deleteRecursively() }
        if (!pending.isDirectory) return false

        val replaceUpload = isReplaceUpload()
        val journal = File(pending, "journal.json")
        val entries = if (journal.exists()) {
            // An existing journal may describe an interrupted installation; keep it if reading fails.
            Json.decodeFromString<List<RestoreEntry>>(journal.readText())
        } else {
            try {
                buildEntries().also { writeDurably(journal, Json.encodeToString(it)) }
            } catch (e: Exception) {
                // No live files have been changed yet, so this restore can be safely rejected.
                move(pending, File(root, "failed-${UUID.randomUUID()}"))
                throw RestoreFailedException(e)
            }
        }
        try {
            // 存档带非空 upload 时整体替换原 upload, 逐文件日志里不再包含 upload 条目
            if (replaceUpload) {
                // 先落一个持久标记, 保证「原 upload 已移走 / 存档 upload 已移入」后进程中断时,
                // 重试仍按整体替换处理, 不会退化成逐文件合并而漏掉失败回滚。
                writeDurably(File(pending, REPLACE_UPLOAD_MARKER), "")
                replaceUploadDirectory()
            }
            entries.forEach { entry ->
                val target = targetFile(entry.path)
                val original = File(pending, "originals/${entry.path}")
                val source = File(pending, "payload/${entry.path}")
                if (entry.install && !source.exists()) {
                    // A previous process already moved this source into place.
                    check(target.isFile) { "Interrupted restore is missing ${entry.path}" }
                } else {
                    if (entry.hadOriginal && !original.exists()) move(target, original)
                    if (entry.install) move(source, target)
                }
            }
            val settings = File(pending, "settings.json")
            if (settings.isFile) restoreSettings(settings.readText())
        } catch (e: Exception) {
            val rollbackError = runCatching { rollback(entries) }.exceptionOrNull()
            val uploadError = runCatching { restoreUploadDirectory(replaceUpload) }.exceptionOrNull()
            if (rollbackError != null) {
                e.addSuppressed(rollbackError)
                uploadError?.let { e.addSuppressed(it) }
                // Do not start the app with a partially restored database. Keep the journal for retry.
                throw e
            }
            if (uploadError != null) {
                // DB 与设置已回滚, 只有 upload 未还原; 保留 pending 以便下次启动重试, 但不能抛出
                // 未捕获异常导致启动崩溃 (RikkaHubApp 只处理 RestoreFailedException)。
                e.addSuppressed(uploadError)
                throw RestoreFailedException(e)
            }
            move(pending, File(root, "failed-${UUID.randomUUID()}"))
            throw RestoreFailedException(e)
        }

        // Commit by removing the pending name atomically before any live connection can be opened.
        // If this move fails, startup aborts and the next launch retries the same installation.
        val completed = File(root, "completed-${UUID.randomUUID()}")
        move(pending, completed)
        completed.deleteRecursively()
        return true
    }

    private fun buildEntries(): List<RestoreEntry> {
        val replaceUpload = isReplaceUpload()
        val payload = File(pending, "payload")
        val paths = payload.walkTopDown().filter { it.isFile }.map {
            it.relativeTo(payload).invariantSeparatorsPath
        }.toList().sorted()
        val entries = paths
            // upload 由 replaceUploadDirectory 整体处理, 不进逐文件日志
            .filter { path -> !(replaceUpload && path.startsWith("files/${FileFolders.UPLOAD}/")) }
            .map { path ->
                RestoreEntry(path, install = true, hadOriginal = targetFile(path).exists())
            }.toMutableList()
        if (paths.contains("database/${databaseFile.name}")) {
            // The new database is standalone. Keep the old DB's sidecars with the old DB only.
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                val path = "database/${databaseFile.name}$suffix"
                entries.add(0, RestoreEntry(path, install = false, hadOriginal = targetFile(path).exists()))
            }
        }
        return entries
    }

    /**
     * 存档里是否带了非空 upload。首次运行时看 payload；替换开始前会写入 [REPLACE_UPLOAD_MARKER]，
     * 若上次已完成「旧 upload 已移到备份」这一步则用备份目录的存在来表示整体替换模式。
     */
    private fun isReplaceUpload(): Boolean {
        if (File(pending, REPLACE_UPLOAD_MARKER).exists()) return true
        if (File(pending, "originals-upload").exists()) return true
        val upload = File(pending, "payload/files/${FileFolders.UPLOAD}")
        return upload.isDirectory && upload.listFiles()?.any { it.isFile } == true
    }

    /** 把旧 upload 整体移到 originals-upload 备份, 再把存档里的 upload 目录整体移入。 */
    private fun replaceUploadDirectory() {
        val live = File(filesDir, FileFolders.UPLOAD)
        val backup = File(pending, "originals-upload")
        val source = File(pending, "payload/files/${FileFolders.UPLOAD}")
        if (!backup.exists() && live.exists()) move(live, backup)
        if (source.exists()) {
            if (live.exists()) live.deleteRecursively()
            move(source, live)
        }
    }

    /** 恢复失败时把新 upload 删掉, 再尽量把 originals-upload 备份放回。 */
    private fun restoreUploadDirectory(replaceUpload: Boolean) {
        if (!replaceUpload) return
        val live = File(filesDir, FileFolders.UPLOAD)
        val backup = File(pending, "originals-upload")
        val source = File(pending, "payload/files/${FileFolders.UPLOAD}")
        when {
            backup.exists() -> {
                // 原 upload 已备份, 删掉当前 (新) 目录并还原
                if (live.exists()) live.deleteRecursively()
                move(backup, live)
            }

            !source.exists() -> {
                // 本来没有原 upload, 且存档 upload 已移入: 删掉以还原到「无 upload」
                if (live.exists()) live.deleteRecursively()
            }

            // 其余情况原 upload 仍在 live (尚未备份), 保持原样, 绝不能删
            else -> Unit
        }
    }

    private fun rollback(entries: List<RestoreEntry>) {
        entries.asReversed().forEach { entry ->
            val target = targetFile(entry.path)
            val original = File(pending, "originals/${entry.path}")
            val source = File(pending, "payload/${entry.path}")
            if (entry.install && !source.exists() && target.exists()) move(target, source)
            if (original.exists()) move(original, target)
        }
    }

    private fun targetFile(path: String): File {
        return when {
            path.startsWith("database/") -> {
                val name = path.removePrefix("database/")
                require(name in listOf(
                    databaseFile.name, databaseFile.name + "-wal",
                    databaseFile.name + "-shm", databaseFile.name + "-journal"
                )) {
                    "Invalid restore database path"
                }
                File(databaseFile.parentFile, name)
            }
            path.startsWith("files/") -> resolveInside(filesDir, path.removePrefix("files/"))
            else -> error("Invalid restore path: $path")
        }
    }

    @Serializable
    private data class RestoreEntry(val path: String, val install: Boolean, val hadOriginal: Boolean)

    companion object {
        /** 落盘的「本次恢复整体替换 upload」标记, 用于中断重试时保持同一处理模式。 */
        private const val REPLACE_UPLOAD_MARKER = "replace-upload"

        fun resolveInside(root: File, relativePath: String): File {
            require(relativePath.isNotBlank() && !relativePath.startsWith('/') &&
                '\\' !in relativePath && relativePath.split('/').none { it == ".." || it == "." }) {
                "Invalid backup file path: $relativePath"
            }
            val target = File(root, relativePath).canonicalFile
            require(target.path.startsWith(root.canonicalPath + File.separator)) {
                "Backup file is outside its target directory"
            }
            return target
        }

        fun writeDurably(file: File, text: String) {
            val temporary = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            move(temporary, file)
        }

        private fun move(source: File, target: File) {
            val parent = requireNotNull(target.parentFile)
            check(parent.isDirectory || parent.mkdirs()) { "Cannot create restore directory" }
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        }
    }
}

/** Only thrown when original files are intact and the failed import has been isolated. */
internal class RestoreFailedException(cause: Exception) : Exception("Backup restore failed; original files restored", cause)
