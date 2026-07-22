package me.rerere.rikkahub.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import me.rerere.rikkahub.data.files.FileFolders
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

    fun create(
        context: Context,
        output: File,
        settingsJson: String,
        includeDatabase: Boolean,
        includeFiles: Boolean,
    ) {
        TarArchiveOutputStream(FileOutputStream(output).buffered()).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            addBytes(tar, "settings.json", settingsJson.toByteArray())

            if (includeDatabase) {
                val database = context.getDatabasePath("rikka_hub")
                if (database.isFile) {
                    checkpoint(database)
                    val compressed = File(context.cacheDir, "rikka_hub.db.zst")
                    try {
                        ZstdOutputStream(FileOutputStream(compressed), 9).use { outputStream ->
                            database.inputStream().use { it.copyTo(outputStream) }
                        }
                        addFile(tar, compressed, "rikka_hub.db.zst")
                    } finally {
                        compressed.delete()
                    }
                }
            }

            if (includeFiles) {
                listOf(FileFolders.UPLOAD, FileFolders.SKILLS, FileFolders.FONTS).forEach { folderName ->
                    val folder = File(context.filesDir, folderName)
                    if (folder.isDirectory) addDirectory(tar, folder, folderName)
                }
            }
            tar.finish()
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
                                compressed.outputStream().use { tar.copyTo(it) }
                                ZstdInputStream(compressed.inputStream()).use { it.copyTo(zip) }
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
}
