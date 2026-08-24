package me.rerere.rikkahub.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellStatus
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object BackupArchive {
    const val EXTENSION = ".tar"

    suspend fun create(
        context: Context,
        output: File,
        settingsJson: String,
        includeFiles: Boolean,
        includeWorkspace: Boolean,
        workspaceRepository: WorkspaceRepository,
    ) {
        TarArchiveOutputStream(FileOutputStream(output).buffered()).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            addBytes(tar, "settings.json", settingsJson.toByteArray())

            val database = context.getDatabasePath("rikka_hub")
            if (database.isFile) {
                checkpoint(database)
                val compressed = File(context.cacheDir, "rikka_hub.db.zst")
                try {
                    ZstdOutputStream(
                        FileOutputStream(compressed).buffered(IO_BUFFER_SIZE),
                        DATABASE_COMPRESSION_LEVEL,
                    ).setWorkers(ZSTD_WORKERS).use { outputStream ->
                        database.inputStream().buffered(IO_BUFFER_SIZE).use {
                            it.copyTo(outputStream, IO_BUFFER_SIZE)
                        }
                    }
                    addFile(tar, compressed, "rikka_hub.db.zst")
                } finally {
                    compressed.delete()
                }
            }

            if (includeFiles) {
                listOf(FileFolders.UPLOAD, FileFolders.SKILLS, FileFolders.FONTS).forEach { folderName ->
                    val folder = File(context.filesDir, folderName)
                    if (folder.isDirectory) addDirectory(tar, folder, folderName)
                }
            }

            if (includeWorkspace) {
                addWorkspaceArchives(
                    tar = tar,
                    context = context,
                    workspaceRepository = workspaceRepository,
                )
            }
            tar.finish()
        }
    }

    private suspend fun addWorkspaceArchives(
        tar: TarArchiveOutputStream,
        context: Context,
        workspaceRepository: WorkspaceRepository,
    ) {
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

    /** Converts the new tar format to the historical zip entry layout used by the restore pipeline. */
    fun toLegacyZip(archive: File, targetZip: File) {
        ZipOutputStream(FileOutputStream(targetZip).buffered()).use { zip ->
            TarArchiveInputStream(FileInputStream(archive).buffered()).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val zipName = if (entry.name == "rikka_hub.db.zst") "rikka_hub.db" else entry.name
                        zip.putNextEntry(ZipEntry(zipName))
                        if (entry.name == "rikka_hub.db.zst") {
                            val compressed = File.createTempFile("restore_db_", ".zst", archive.parentFile)
                            try {
                                compressed.outputStream().buffered(IO_BUFFER_SIZE).use {
                                    tar.copyTo(it, IO_BUFFER_SIZE)
                                }
                                ZstdInputStream(
                                    compressed.inputStream().buffered(IO_BUFFER_SIZE)
                                ).use {
                                    it.copyTo(zip, IO_BUFFER_SIZE)
                                }
                            } finally {
                                compressed.delete()
                            }
                        } else {
                            tar.copyTo(zip)
                        }
                        zip.closeEntry()
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }

    private fun checkpoint(databaseFile: File) {
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Database WAL checkpoint failed" }
            }
        }
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

    private val ZSTD_WORKERS = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private const val DATABASE_COMPRESSION_LEVEL = 9
    private const val IO_BUFFER_SIZE = 128 * 1024
    private const val WORKSPACES_ENTRY = "workspaces/"
    private val WORKSPACE_ROOT_PATTERN = Regex("[A-Za-z0-9._-]+")
    private fun String.isValidWorkspaceRoot(): Boolean =
        this != "." && this != ".." && matches(WORKSPACE_ROOT_PATTERN)
}
