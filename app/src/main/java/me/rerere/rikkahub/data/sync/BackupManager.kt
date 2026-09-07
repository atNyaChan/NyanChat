package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import com.github.luben.zstd.ZstdOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.SQLiteConfiguration
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceShellStatus
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

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
        val archive = File(context.cacheDir, "NyanChatBackup-$timestamp${BackupArchive.EXTENSION}")
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "backup-").toFile()
        try {
            val settings = settingsStore.settingsFlowRaw.first()
            TarArchiveOutputStream(FileOutputStream(archive).buffered()).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                addBytes(tar, "settings.json", json.encodeToString(settings).toByteArray(Charsets.UTF_8))
                if (includeDatabase) {
                    // Consistent, standalone snapshot (VACUUM INTO) compressed as in the legacy NyanChat format.
                    val snapshot = File(staging, SQLiteConfiguration.DATABASE_NAME)
                    DatabaseBackup.createSnapshot(database.openHelper.writableDatabase, snapshot)
                    val compressed = File(staging, "rikka_hub.db.zst")
                    ZstdOutputStream(
                        FileOutputStream(compressed).buffered(IO_BUFFER_SIZE),
                        DATABASE_COMPRESSION_LEVEL,
                    ).setWorkers(ZSTD_WORKERS).use { output ->
                        snapshot.inputStream().buffered(IO_BUFFER_SIZE).use { input ->
                            input.copyTo(output, IO_BUFFER_SIZE)
                        }
                    }
                    addFile(tar, compressed, "rikka_hub.db.zst")
                }
                if (includeFiles) {
                    for (folder in listOf(FileFolders.UPLOAD, FileFolders.SKILLS, FileFolders.FONTS)) {
                        val directory = File(context.filesDir, folder)
                        if (directory.isDirectory) addDirectory(tar, directory, folder)
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
        if (archive.extension.equals("tar", ignoreCase = true)) {
            // Legacy NyanChat tar archives are converted to the zip layout before staging.
            val legacyZip = File.createTempFile("restore_", ".zip", context.cacheDir)
            try {
                BackupArchive.toLegacyZip(archive, legacyZip)
                stageRestoreInternal(legacyZip)
            } finally {
                legacyZip.delete()
            }
            return@withContext
        }

        val restore = pendingRestore(context)
        val staging = restore.createStagingDirectory()
        try {
            val payload = File(staging, "payload")
            val stagedDatabase = File(payload, "database/${SQLiteConfiguration.DATABASE_NAME}")
            val stagedWal = File(stagedDatabase.path + "-wal")
            val seen = mutableSetOf<String>()
            var restoredEntries = 0
            var restoredWorkspaces = false
            ZipFile(archive).use { zip ->
                for (entry in zip.entries()) {
                    currentCoroutineContext().ensureActive()
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (name.startsWith(WORKSPACES_ENTRY) && name.endsWith(".tar.zst")) {
                        val workspaceRoot = name
                            .removePrefix(WORKSPACES_ENTRY)
                            .removeSuffix(".tar.zst")
                        require(workspaceRoot.isValidWorkspaceRoot()) {
                            "Invalid workspace archive entry: $name"
                        }
                        require(seen.add(name)) { "Duplicate backup entry: $name" }
                        rootfsInstaller.restoreWorkspaceArchive(workspaceRoot, zip.getInputStream(entry))
                        restoredWorkspaces = true
                        restoredEntries++
                        continue
                    }
                    val target = when (name) {
                        "settings.json" -> File(staging, "settings.json")
                        DatabaseBackup.ARCHIVE_DATABASE -> stagedDatabase
                        DatabaseBackup.WAL -> stagedWal
                        DatabaseBackup.SHM -> null // Rebuilt by SQLite; never restore shared-memory state.
                        else -> if (isAttachment(name)) {
                            PendingRestore.resolveInside(File(payload, "files"), name)
                        } else null
                    } ?: continue
                    require(seen.add(name)) { "Duplicate backup entry: $name" }
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) {
                        "Cannot create backup staging directory"
                    }
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                    restoredEntries++
                }
            }
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
                val settings = json.decodeFromString<Settings>(SettingsJsonMigrator.migrate(settingsFile.readText()))
                require(!settings.init) { "Backup contains uninitialized settings" }
                // Persist the migrated value once, including generated IDs, for restart/retry consistency.
                PendingRestore.writeDurably(settingsFile, json.encodeToString(settings))
            }
            currentCoroutineContext().ensureActive()
            restore.publish(staging)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun writeMarker(staging: File, name: String) {
        File(staging, name).writeText("")
    }

    private fun isAttachment(name: String): Boolean {
        val folder = name.substringBefore('/')
        if (folder !in listOf(FileFolders.UPLOAD, FileFolders.SKILLS, FileFolders.FONTS) || '/' !in name) return false
        val relative = name.substringAfter('/')
        require(relative.isNotBlank()) { "Invalid backup attachment: $name" }
        require(folder == FileFolders.SKILLS || '/' !in relative) { "Invalid backup attachment: $name" }
        return true
    }

    private fun addDirectory(tar: TarArchiveOutputStream, root: File, prefix: String) {
        root.walkTopDown().filter(File::isFile).forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            addFile(tar, file, "$prefix/$relative")
        }
    }

    private fun addFile(tar: TarArchiveOutputStream, file: File, name: String) {
        val entry = TarArchiveEntry(file, name)
        tar.putArchiveEntry(entry)
        file.inputStream().use { it.copyTo(tar) }
        tar.closeArchiveEntry()
    }

    private fun addBytes(tar: TarArchiveOutputStream, name: String, bytes: ByteArray) {
        val entry = TarArchiveEntry(name).apply { size = bytes.size.toLong() }
        tar.putArchiveEntry(entry)
        tar.write(bytes)
        tar.closeArchiveEntry()
    }

    companion object {
        private val ZSTD_WORKERS = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        private const val DATABASE_COMPRESSION_LEVEL = 9
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
                SettingsStore.restoreBeforeInitialization(context, json.decodeFromString<Settings>(settingsJson))
            }
            if (restored) {
                runCatching { RestoredAttachmentUrlRewriter.rewrite(context, json) }
                    .onFailure { Log.w(TAG, "Failed to rewrite restored attachment URLs", it) }
                if (needsWorkspaceReset) {
                    runCatching { RestoredZipWorkspaceStatusResetter.resetAfterCompatibleZipRestore(context) }
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
