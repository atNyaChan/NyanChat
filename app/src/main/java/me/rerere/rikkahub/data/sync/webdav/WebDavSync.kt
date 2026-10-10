package me.rerere.rikkahub.data.sync.webdav

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.utils.fileSizeToString
import java.io.File
import java.time.Instant

private const val TAG = "WebDavSync"

class WebDavSync(
    private val backupManager: BackupManager,
    private val context: Context,
    private val httpClient: HttpClient,
) {
    private fun getClient(config: WebDavConfig): WebDavClient {
        return WebDavClient(config, httpClient)
    }

    suspend fun testConnection(config: WebDavConfig) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        // Test by listing the root directory
        client.propfind(depth = 0).getOrThrow()
        Log.i(TAG, "testConnection: Connection successful")
    }

    suspend fun backup(config: WebDavConfig) = withContext(Dispatchers.IO) {
        val file = backupManager.createBackup(
            includeFiles = WebDavConfig.BackupItem.FILES in config.items,
            includeWorkspace = WebDavConfig.BackupItem.WORKSPACE in config.items,
        )
        val client = getClient(config)

        // Ensure the backup directory exists
        client.ensureCollectionExists().getOrThrow()

        // Upload the backup file
        client.put(
            path = file.name,
            file = file,
            contentType = "application/x-tar"
        ).getOrThrow()

        Log.i(TAG, "backup: Uploaded ${file.name} (${file.length().fileSizeToString()})")

        // Clean up temp file
        file.delete()
    }

    suspend fun listBackupFiles(config: WebDavConfig): List<WebDavBackupItem> = withContext(Dispatchers.IO) {
        val client = getClient(config)

        // Ensure the backup directory exists
        client.ensureCollectionExists().getOrThrow()

        val resources = client.list().getOrThrow()

        resources
            .filter {
                !it.isCollection &&
                    (it.displayName.startsWith("backup_") || it.displayName.startsWith("NyanChatBackup-")) &&
                    (it.displayName.endsWith(BackupManager.EXTENSION) || it.displayName.endsWith(".zip"))
            }
            .map { resource ->
                WebDavBackupItem(
                    href = resource.href,
                    displayName = resource.displayName,
                    size = resource.contentLength,
                    lastModified = resource.lastModified ?: Instant.EPOCH
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun restore(config: WebDavConfig, item: WebDavBackupItem) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        // Preserve the original extension (.tar / .zip) so the restore pipeline detects the format correctly.
        val backupFile = File(context.cacheDir, "restore_${System.currentTimeMillis()}_${item.displayName}")

        try {
            // Download backup file directly to file to avoid OOM
            Log.i(TAG, "restore: Downloading ${item.displayName}")
            client.downloadToFile(item.displayName, backupFile).getOrThrow()

            Log.i(TAG, "restore: Downloaded ${backupFile.length().fileSizeToString()}")

            // Restore from backup file
            restoreFromBackupFile(backupFile)
        } finally {
            // Clean up temp file
            if (backupFile.exists()) {
                backupFile.delete()
                Log.i(TAG, "restore: Cleaned up temporary backup file")
            }
        }
    }

    suspend fun deleteBackupFile(config: WebDavConfig, item: WebDavBackupItem) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        client.delete(item.displayName).getOrThrow()
        Log.i(TAG, "deleteBackupFile: Deleted ${item.displayName}")
    }

    suspend fun restoreFromLocalFile(file: File) {
        restoreFromBackupFile(file)
    }

    private suspend fun restoreFromBackupFile(backupFile: File) = backupManager.stageRestore(backupFile)
}

data class WebDavBackupItem(
    val href: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
