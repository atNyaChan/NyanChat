package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.sanitizeBuiltInTools
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.SQLiteConfiguration
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.MediaCreationFiles
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceShellStatus
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val TAG = "BackupManager"

/** Shared archive format and restore lifecycle for local, WebDAV and S3 backups. */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val workspaceRepository: WorkspaceRepository,
    private val rootfsInstaller: RootfsInstaller,
) {
    private val restoreMutex = Mutex()

    suspend fun createBackup(
        includeDatabase: Boolean,
        includeFiles: Boolean,
        includeWorkspace: Boolean,
    ): File = withContext(Dispatchers.IO) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val archive = File(context.cacheDir, "NyanChatBackup-$timestamp$EXTENSION")
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "backup-").toFile()
        try {
            val settings = settingsStore.awaitLoaded()
            TarArchiveOutputStream(FileOutputStream(archive).buffered()).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                // 附件按内容去重：逐字节相同的文件只在 tar 里存一份，后续同名内容写成 TAR 硬链接
                val deduplicator = AttachmentDeduplicator()
                val settingsJson = json.encodeSettings(settings, settingsStore.launchCountFlow.first())
                addBytes(tar, "settings.json", settingsJson.toByteArray(Charsets.UTF_8))
                if (includeDatabase) {
                    // Consistent, standalone snapshot (VACUUM INTO) compressed as in the legacy NyanChat format.
                    val snapshot = File(staging, SQLiteConfiguration.DATABASE_NAME)
                    DatabaseBackup.createSnapshot(database.openHelper.writableDatabase, snapshot)
                    val compressed = File(staging, DATABASE_ENTRY)
                    ZstdOutputStream(
                        FileOutputStream(compressed).buffered(IO_BUFFER_SIZE),
                        DATABASE_COMPRESSION_LEVEL,
                    ).setLong(ZSTD_LONG_WINDOW_LOG).setWorkers(ZSTD_WORKERS).use { output ->
                        snapshot.inputStream().buffered(IO_BUFFER_SIZE).use { input ->
                            input.copyTo(output, IO_BUFFER_SIZE)
                        }
                    }
                    addFile(tar, compressed, DATABASE_ENTRY)
                }
                if (includeFiles) {
                    for (folder in ATTACHMENT_FOLDERS) {
                        val directory = File(context.filesDir, folder)
                        if (directory.isDirectory) addDirectory(tar, directory, folder, deduplicator)
                    }
                }
                if (includeWorkspace) {
                    addWorkspaceArchives(tar)
                }
                tar.finish()
            }
            archive
        } catch (e: Throwable) {
            archive.delete()
            throw e
        } finally {
            staging.deleteRecursively()
        }
    }

    /** 导出完整备份（聊天记录始终包含）；[includeFiles] / [includeWorkspace] 对应备份项开关。 */
    suspend fun createBackup(includeFiles: Boolean, includeWorkspace: Boolean): File = createBackup(
        includeDatabase = true,
        includeFiles = includeFiles,
        includeWorkspace = includeWorkspace,
    )

    /**
     * 导出上游 RikkaHub 兼容的旧 ZIP 备份：数据库不压缩，设置剥离本分支独有字段。
     * 仅本地“导出旧版格式”使用，S3 与 WebDAV 只导出新 TAR 格式。
     */
    suspend fun createLegacyBackup(includeFiles: Boolean): File = withContext(Dispatchers.IO) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupFile = File(context.cacheDir, "backup_$timestamp.zip")
        if (backupFile.exists()) backupFile.delete()

        val settings = settingsStore.awaitLoaded()
        ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
            addVirtualFileToZip(zipOut, "settings.json", legacyCompatibleSettingsJson(settings))

            // Backup database files (chat records are always exported)
            val dbFile = context.getDatabasePath(SQLiteConfiguration.DATABASE_NAME)
            if (dbFile.exists()) {
                addFileToZip(zipOut, dbFile, DatabaseBackup.ARCHIVE_DATABASE)
            }
            val walFile = File(dbFile.parentFile, DatabaseBackup.WAL)
            if (walFile.exists()) {
                addFileToZip(zipOut, walFile, DatabaseBackup.WAL)
            }
            val shmFile = File(dbFile.parentFile, DatabaseBackup.SHM)
            if (shmFile.exists()) {
                addFileToZip(zipOut, shmFile, DatabaseBackup.SHM)
            }

            if (includeFiles) {
                val uploadFolder = File(context.filesDir, FileFolders.UPLOAD)
                if (uploadFolder.exists() && uploadFolder.isDirectory) {
                    Log.i(TAG, "createLegacyBackup: Backing up files from ${uploadFolder.absolutePath}")
                    uploadFolder.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            addFileToZip(zipOut, file, "${FileFolders.UPLOAD}/${file.name}")
                        }
                    }
                } else {
                    Log.w(TAG, "createLegacyBackup: Upload folder does not exist or is not a directory")
                }

                val skillsFolder = File(context.filesDir, FileFolders.SKILLS)
                if (skillsFolder.exists() && skillsFolder.isDirectory) {
                    Log.i(TAG, "createLegacyBackup: Backing up skills from ${skillsFolder.absolutePath}")
                    addDirectoryToZip(
                        zipOut = zipOut,
                        rootDir = skillsFolder,
                        currentDir = skillsFolder,
                        entryPrefix = "${FileFolders.SKILLS}/",
                    )
                } else {
                    Log.w(TAG, "createLegacyBackup: Skills folder does not exist or is not a directory")
                }

                val fontsFolder = File(context.filesDir, FileFolders.FONTS)
                if (fontsFolder.exists() && fontsFolder.isDirectory) {
                    Log.i(TAG, "createLegacyBackup: Backing up fonts from ${fontsFolder.absolutePath}")
                    fontsFolder.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            addFileToZip(zipOut, file, "${FileFolders.FONTS}/${file.name}")
                        }
                    }
                } else {
                    Log.w(TAG, "createLegacyBackup: Fonts folder does not exist or is not a directory")
                }
            }
        }

        Log.i(
            TAG,
            "createLegacyBackup: Created backup file ${backupFile.name} (${backupFile.length().fileSizeToString()})"
        )
        backupFile
    }

    private fun legacyCompatibleSettingsJson(settings: Settings): String = makeRikkaHubCompatible(
        json.encodeToJsonElement(Settings.serializer(), settings)
    ).toString()

    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            zipOut.putNextEntry(ZipEntry(entryName))
            fis.copyTo(zipOut)
            zipOut.closeEntry()
            Log.d(TAG, "addFileToZip: Added $entryName (${file.length()} bytes) to zip")
        }
    }

    private fun addDirectoryToZip(
        zipOut: ZipOutputStream,
        rootDir: File,
        currentDir: File,
        entryPrefix: String,
    ) {
        currentDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                addDirectoryToZip(zipOut, rootDir, file, entryPrefix)
            } else if (file.isFile) {
                val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
                addFileToZip(zipOut, file, "$entryPrefix$relativePath")
            }
        }
    }

    private fun addVirtualFileToZip(zipOut: ZipOutputStream, name: String, content: String) {
        zipOut.putNextEntry(ZipEntry(name))
        zipOut.write(content.toByteArray())
        zipOut.closeEntry()
        Log.i(TAG, "addVirtualFileToZip: $name (${content.length} bytes)")
    }

    private suspend fun addWorkspaceArchives(tar: TarArchiveOutputStream): Unit {
        tar.putArchiveEntry(TarArchiveEntry(WORKSPACES_ENTRY))
        tar.closeArchiveEntry()
        workspaceRepository.list()
            .filter { it.shellStatus != WorkspaceShellStatus.DISABLED.name }
            .forEach { workspace ->
                val archive = File.createTempFile("workspace_", ".tar.zst", context.cacheDir)
                try {
                    archive.outputStream().use { output ->
                        workspaceRepository.exportRootfsArchive(workspace.id, output)
                    }
                    require(workspace.root.isValidWorkspaceRoot()) {
                        "Invalid workspace root: ${workspace.root}"
                    }
                    // 子归档是刚生成的临时文件, 不参与附件内容去重
                    addFile(tar, archive, "$WORKSPACES_ENTRY${workspace.root}.tar.zst")
                } finally {
                    archive.delete()
                }
            }
    }

    suspend fun stageRestore(archive: File) = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            stageRestoreInternal(archive)
        }
    }

    private suspend fun stageRestoreInternal(archive: File): Unit = withContext(Dispatchers.IO) {
        val restore = pendingRestore(context)
        val staging = restore.createStagingDirectory()
        try {
            val payload = File(staging, "payload")
            val stagedDatabase = File(payload, "database/${SQLiteConfiguration.DATABASE_NAME}")
            val stagedWal = File(stagedDatabase.path + "-wal")
            val seen = mutableSetOf<String>()
            // 已落盘的条目名 -> 暂存文件, 用于把 TAR 硬链接条目展开成独立副本
            val extracted = mutableMapOf<String, File>()
            val hardLinks = mutableListOf<PendingHardLink>()
            var restoredEntries = 0
            var restoredWorkspaces = false

            fun targetOf(name: String): File? = when (name) {
                "settings.json" -> File(staging, "settings.json")
                DATABASE_ENTRY, DatabaseBackup.ARCHIVE_DATABASE -> stagedDatabase
                DatabaseBackup.WAL -> stagedWal
                DatabaseBackup.SHM -> null // Rebuilt by SQLite; never restore shared-memory state.
                else -> if (isAttachment(name)) {
                    PendingRestore.resolveInside(File(payload, "files"), name)
                } else null
            }

            fun createParent(target: File) {
                check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) {
                    "Cannot create backup staging directory"
                }
            }

            fun stageFile(name: String, target: File, input: InputStream) {
                require(seen.add(name)) { "Duplicate backup entry: $name" }
                createParent(target)
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
                extracted[name] = target
                restoredEntries++
            }

            fun stageWorkspace(name: String, input: InputStream) {
                val workspaceRoot = name.removePrefix(WORKSPACES_ENTRY).removeSuffix(".tar.zst")
                require(workspaceRoot.isValidWorkspaceRoot()) { "Invalid workspace archive entry: $name" }
                require(seen.add(name)) { "Duplicate backup entry: $name" }
                rootfsInstaller.restoreWorkspaceArchive(workspaceRoot, input)
                restoredWorkspaces = true
                restoredEntries++
            }

            // zstd 流可能越过条目边界预读, 先落到临时文件再解压, 避免破坏 TAR 解析
            fun stageDatabase(input: InputStream) {
                require(seen.add(DATABASE_ENTRY)) { "Duplicate backup entry: $DATABASE_ENTRY" }
                createParent(stagedDatabase)
                val compressed = File.createTempFile("restore_db_", ".zst", staging)
                try {
                    compressed.outputStream().buffered(IO_BUFFER_SIZE).use { input.copyTo(it, IO_BUFFER_SIZE) }
                    ZstdInputStream(compressed.inputStream().buffered(IO_BUFFER_SIZE)).use { zstd ->
                        FileOutputStream(stagedDatabase).use { output ->
                            zstd.copyTo(output, IO_BUFFER_SIZE)
                            output.fd.sync()
                        }
                    }
                } finally {
                    compressed.delete()
                }
                extracted[DATABASE_ENTRY] = stagedDatabase
                restoredEntries++
            }

            if (archive.extension.equals("tar", ignoreCase = true)) {
                TarArchiveInputStream(FileInputStream(archive).buffered()).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        currentCoroutineContext().ensureActive()
                        val name = entry.name
                        when {
                            entry.isDirectory -> Unit
                            // TAR 硬链接条目没有数据区, 记下目标, 等目标条目落盘后复制成独立文件
                            entry.isLink -> {
                                val linkName = entry.linkName
                                targetOf(name)?.let { target ->
                                    require(seen.add(name)) { "Duplicate backup entry: $name" }
                                    hardLinks += PendingHardLink(name, linkName, target)
                                }
                            }
                            isWorkspaceArchiveEntry(name) -> stageWorkspace(name, tar)
                            name == DATABASE_ENTRY -> stageDatabase(tar)
                            else -> targetOf(name)?.let { stageFile(name, it, tar) }
                        }
                        entry = tar.nextEntry
                    }
                }
            } else {
                // 旧 RikkaHub/NyanChat ZIP 备份仍按原样读取
                ZipFile(archive).use { zip ->
                    for (entry in zip.entries()) {
                        currentCoroutineContext().ensureActive()
                        if (entry.isDirectory) continue
                        val name = entry.name
                        if (isWorkspaceArchiveEntry(name)) {
                            stageWorkspace(name, zip.getInputStream(entry))
                            continue
                        }
                        val target = targetOf(name) ?: continue
                        zip.getInputStream(entry).use { stageFile(name, target, it) }
                    }
                }
            }
            expandHardLinks(hardLinks, extracted)
            require(restoredEntries > 0) { "No data found in the backup" }
            require(!stagedWal.exists() || stagedDatabase.exists()) { "Backup WAL has no matching database" }
            if (stagedDatabase.exists()) {
                DatabaseBackup.normalize(context, stagedDatabase)
                // Reject unsupported schemas before publishing; run supported old migrations on the copy.
                val room = AppDatabaseFactory.create(context, stagedDatabase.absolutePath)
                try {
                    DatabaseBackup.checkpoint(room.openHelper.writableDatabase)
                } finally {
                    room.close()
                }
                DatabaseBackup.removeSidecars(stagedDatabase)
                if (!restoredWorkspaces) {
                    // Old/legacy archives carry no workspace rootfs; disable them after install.
                    writeMarker(staging, "needs-workspace-reset")
                }
            }

            val settingsFile = File(staging, "settings.json")
            if (settingsFile.exists()) {
                val migrated = SettingsJsonMigrator.migrate(settingsFile.readText())
                val settings = json.decodeFromString<Settings>(migrated)
                require(!settings.init) { "Backup contains uninitialized settings" }
                // 关闭备份里当前提供商类型不支持的内置工具
                val sanitized = settings.sanitizeBuiltInTools()
                // Persist the migrated value once, including generated IDs, for restart/retry consistency.
                PendingRestore.writeDurably(settingsFile, json.encodeSettings(sanitized, json.launchCountOf(migrated)))
            }
            currentCoroutineContext().ensureActive()
            restore.publish(staging)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun isWorkspaceArchiveEntry(name: String): Boolean =
        name.startsWith(WORKSPACES_ENTRY) && name.endsWith(".tar.zst")

    /**
     * 打包时写入的 TAR 硬链接条目没有数据区，恢复时在目标条目落盘后把它展开成独立副本
     * （Android 环境不保证支持硬链接，所以不用硬链接还原）；目标本身也是硬链接条目时循环解析。
     */
    private fun expandHardLinks(links: List<PendingHardLink>, extracted: MutableMap<String, File>) {
        var unresolved = links
        while (unresolved.isNotEmpty()) {
            val pending = mutableListOf<PendingHardLink>()
            for (link in unresolved) {
                val source = extracted[link.linkName]
                if (source == null) {
                    pending += link
                    continue
                }
                check(link.target.parentFile!!.isDirectory || link.target.parentFile!!.mkdirs()) {
                    "Cannot create backup staging directory"
                }
                FileOutputStream(link.target).use { output ->
                    source.inputStream().use { it.copyTo(output, IO_BUFFER_SIZE) }
                    output.fd.sync()
                }
                extracted[link.name] = link.target
            }
            check(pending.size < unresolved.size) {
                "Backup hard link target not found: ${pending.first().linkName}"
            }
            unresolved = pending
        }
    }

    private data class PendingHardLink(val name: String, val linkName: String, val target: File)

    private fun writeMarker(staging: File, name: String) {
        File(staging, name).writeText("")
    }

    private fun isAttachment(name: String): Boolean {
        val folder = name.substringBefore('/')
        if (folder !in ATTACHMENT_FOLDERS || '/' !in name) return false
        val relative = name.substringAfter('/')
        require(relative.isNotBlank()) { "Invalid backup attachment: $name" }
        require(folder in NESTED_ATTACHMENT_FOLDERS || '/' !in relative) { "Invalid backup attachment: $name" }
        return true
    }

    private fun addDirectory(
        tar: TarArchiveOutputStream,
        root: File,
        prefix: String,
        deduplicator: AttachmentDeduplicator,
    ) {
        // upload / fonts 只含顶层文件；skills / media_creation 需要连同子目录一起备份。
        val files = if (prefix in NESTED_ATTACHMENT_FOLDERS) {
            root.walkTopDown().filter(File::isFile)
        } else {
            root.listFiles()?.asSequence()?.filter(File::isFile).orEmpty()
        }
        files.forEach { file ->
            // A media result still being downloaded is incomplete and gets renamed when it finishes.
            if (prefix == FileFolders.MEDIA_CREATION && file.name.endsWith(MediaCreationFiles.PARTIAL_SUFFIX)) {
                return@forEach
            }
            val relative = file.relativeTo(root).invariantSeparatorsPath
            addFile(tar, file, "$prefix/$relative", deduplicator)
        }
    }

    private fun addFile(
        tar: TarArchiveOutputStream,
        file: File,
        name: String,
        deduplicator: AttachmentDeduplicator? = null,
    ) {
        if (deduplicator != null) {
            val existing = deduplicator.resolve(file)
            if (existing != null) {
                addHardLink(tar, name, existing)
                return
            }
        }
        // 备份包不保留源文件的 POSIX 权限与属主信息, 只按条目名创建普通文件 (0644)
        val entry = TarArchiveEntry(name).apply { size = file.length() }
        tar.putArchiveEntry(entry)
        file.inputStream().use { it.copyTo(tar) }
        tar.closeArchiveEntry()
        deduplicator?.register(file, name)
    }

    // 内容完全相同的文件已经写入过, 这里只记录指向首份数据的硬链接, 不再写第二份字节
    private fun addHardLink(tar: TarArchiveOutputStream, name: String, linkName: String) {
        val entry = TarArchiveEntry(name, TarConstants.LF_LINK).apply {
            this.linkName = linkName
        }
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun addBytes(tar: TarArchiveOutputStream, name: String, bytes: ByteArray) {
        val entry = TarArchiveEntry(name).apply { size = bytes.size.toLong() }
        tar.putArchiveEntry(entry)
        tar.write(bytes)
        tar.closeArchiveEntry()
    }

    companion object {
        const val EXTENSION = ".tar"

        /** 数据库在 TAR 里以 zstd 压缩存储; 旧 ZIP 备份里则是未压缩的 [DatabaseBackup.ARCHIVE_DATABASE]。 */
        private const val DATABASE_ENTRY = "rikka_hub.db.zst"

        private val ATTACHMENT_FOLDERS =
            listOf(FileFolders.UPLOAD, FileFolders.SKILLS, FileFolders.FONTS, FileFolders.MEDIA_CREATION)

        /** Backed up with their subdirectories; the other folders only contain top-level files. */
        private val NESTED_ATTACHMENT_FOLDERS = setOf(FileFolders.SKILLS, FileFolders.MEDIA_CREATION)

        // 启动次数不在 Settings 里，备份文件里仍放在 settings.json 的这个字段，和旧备份保持一致
        private const val LAUNCH_COUNT_KEY = "launchCount"

        private fun Json.encodeSettings(settings: Settings, launchCount: Int): String {
            val fields = encodeToJsonElement(settings).jsonObject + (LAUNCH_COUNT_KEY to JsonPrimitive(launchCount))
            return encodeToString(JsonObject(fields))
        }

        // 更早的备份没有这个字段，按 0 恢复，和它还在 Settings 里时的默认值一致
        private fun Json.launchCountOf(settingsJson: String): Int =
            parseToJsonElement(settingsJson).jsonObject[LAUNCH_COUNT_KEY]?.jsonPrimitive?.intOrNull ?: 0

        private val ZSTD_WORKERS = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        private const val DATABASE_COMPRESSION_LEVEL = 9
        private const val ZSTD_LONG_WINDOW_LOG = 27
        private const val IO_BUFFER_SIZE = 128 * 1024

        private fun pendingRestore(context: Context) = PendingRestore(
            root = File(context.noBackupFilesDir, "backup-restore"),
            databaseFile = context.getDatabasePath(SQLiteConfiguration.DATABASE_NAME),
            filesDir = context.filesDir,
        )

        /** Must finish before Koin, Room, SettingsStore or any background consumers are initialized. */
        suspend fun applyPendingRestore(context: Context, json: Json): Boolean = withContext(Dispatchers.IO) {
            val root = File(context.noBackupFilesDir, "backup-restore")
            val needsWorkspaceReset = File(root, "pending/needs-workspace-reset").isFile
            val restored = pendingRestore(context).apply { settingsJson ->
                SettingsStore.restoreBeforeInitialization(
                    context = context,
                    settings = json.decodeFromString<Settings>(settingsJson),
                    launchCount = json.launchCountOf(settingsJson),
                )
            }
            if (restored) {
                runCatching { RestoredAttachmentUrlRewriter.rewrite(context, json) }
                    .onFailure { Log.w(TAG, "Failed to rewrite restored attachment URLs", it) }
                if (needsWorkspaceReset) {
                    runCatching { RestoredWorkspaceStatusResetter.resetAfterRestore(context) }
                        .onFailure { Log.w(TAG, "Failed to reset workspace status after restore", it) }
                }
            }
            restored
        }

        private const val WORKSPACES_ENTRY = "workspaces/"
        private val WORKSPACE_ROOT_PATTERN = Regex("[A-Za-z0-9._-]+")

        private fun String.isValidWorkspaceRoot(): Boolean =
            this != "." && this != ".." && matches(WORKSPACE_ROOT_PATTERN)
    }
}

/**
 * 备份打包时对附件做内容去重。逐字节相同的文件只在 tar 中写入一份数据，其它条目写成指向首份数据的
 * TAR 硬链接，从而缩小备份体积（尤其是上传到 WebDAV/S3 的归档）。
 *
 * 先用文件大小筛选：只有大小相同的文件才计算 SHA-256（每个候选的哈希只算一次），大小唯一的文件完全
 * 不做哈希；inode 相同的文件（已经硬链接过）直接跳过计算。
 */
private class AttachmentDeduplicator {
    private class Candidate(val file: File, val entryName: String, var hash: String? = null)

    private val inodeToEntry = HashMap<Any, String>()
    private val sizeToCandidates = HashMap<Long, MutableList<Candidate>>()

    /** 返回内容与 [file] 相同的已写入条目名；[file] 是新的唯一内容时返回 null（调用方随后用 [register] 登记）。 */
    fun resolve(file: File): String? {
        val inode = inodeKey(file)
        if (inode != null) {
            inodeToEntry[inode]?.let { return it }
        }
        val candidates = sizeToCandidates[file.length()] ?: return null
        val hash = file.sha256() ?: return null
        for (candidate in candidates) {
            val candidateHash = candidate.hash
                ?: candidate.file.sha256()?.also { candidate.hash = it }
                ?: continue
            if (candidateHash == hash) {
                inode?.let { inodeToEntry[it] = candidate.entryName }
                return candidate.entryName
            }
        }
        return null
    }

    /** 登记 [file] 已作为普通文件写入 [entryName]，供后续相同内容的文件引用。 */
    fun register(file: File, entryName: String) {
        inodeKey(file)?.let { inodeToEntry.putIfAbsent(it, entryName) }
        val candidates = sizeToCandidates.getOrPut(file.length()) { mutableListOf() }
        if (candidates.none { it.entryName == entryName }) {
            candidates += Candidate(file, entryName)
        }
    }

    private fun inodeKey(file: File): Any? = runCatching {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).fileKey()
    }.getOrNull()

    private fun File.sha256(): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered(HASH_BUFFER_SIZE).use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().toHex()
    }.getOrNull()

    private fun ByteArray.toHex(): String {
        val hex = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            hex[index * 2] = HEX[value ushr 4]
            hex[index * 2 + 1] = HEX[value and 0x0F]
        }
        return String(hex)
    }

    private companion object {
        const val HASH_BUFFER_SIZE = 128 * 1024
        val HEX = "0123456789abcdef".toCharArray()
    }
}
