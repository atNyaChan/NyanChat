package me.rerere.rikkahub.data.sync

import com.github.luben.zstd.ZstdInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object BackupArchive {
    const val EXTENSION = ".tar"

    /** Converts the legacy tar archive to the zip entry layout used by the restore pipeline. */
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

    private const val IO_BUFFER_SIZE = 128 * 1024
}
