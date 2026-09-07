package me.rerere.rikkahub.data.sync.webdav

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.BackupArchive
import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.utils.fileSizeToString
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "WebDavSync"

class WebDavSync(
    private val backupManager: BackupManager,
    private val settingsStore: SettingsStore,
    private val json: Json,
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
        val file = prepareBackupFile(config)
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
                    (it.displayName.endsWith(BackupArchive.EXTENSION) || it.displayName.endsWith(".zip"))
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

    suspend fun prepareBackupFile(config: WebDavConfig): File = backupManager.createBackup(
        includeDatabase = true,
        includeFiles = WebDavConfig.BackupItem.FILES in config.items,
        includeWorkspace = WebDavConfig.BackupItem.WORKSPACE in config.items,
    )

    suspend fun prepareLegacyBackupFile(config: WebDavConfig): File = withContext(Dispatchers.IO) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupFile = File(context.cacheDir, "backup_$timestamp.zip")

        if (backupFile.exists()) {
            backupFile.delete()
        }

        // Create zip file and backup data
        ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
            addVirtualFileToZip(
                zipOut = zipOut,
                name = "settings.json",
                content = legacyCompatibleSettingsJson()
            )

            // Backup database files (chat records are always exported)
            val dbFile = context.getDatabasePath("rikka_hub")
            if (dbFile.exists()) {
                addFileToZip(zipOut, dbFile, "rikka_hub.db")
            }

            val walFile = File(dbFile.parentFile, "rikka_hub-wal")
            if (walFile.exists()) {
                addFileToZip(zipOut, walFile, "rikka_hub-wal")
            }

            val shmFile = File(dbFile.parentFile, "rikka_hub-shm")
            if (shmFile.exists()) {
                addFileToZip(zipOut, shmFile, "rikka_hub-shm")
            }

            // Backup app files
            if (config.items.contains(WebDavConfig.BackupItem.FILES)) {
                val uploadFolder = File(context.filesDir, FileFolders.UPLOAD)
                if (uploadFolder.exists() && uploadFolder.isDirectory) {
                    Log.i(TAG, "prepareBackupFile: Backing up files from ${uploadFolder.absolutePath}")
                    uploadFolder.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            addFileToZip(zipOut, file, "${FileFolders.UPLOAD}/${file.name}")
                        }
                    }
                } else {
                    Log.w(TAG, "prepareBackupFile: Upload folder does not exist or is not a directory")
                }

                val skillsFolder = File(context.filesDir, FileFolders.SKILLS)
                if (skillsFolder.exists() && skillsFolder.isDirectory) {
                    Log.i(TAG, "prepareBackupFile: Backing up skills from ${skillsFolder.absolutePath}")
                    addDirectoryToZip(
                        zipOut = zipOut,
                        rootDir = skillsFolder,
                        currentDir = skillsFolder,
                        entryPrefix = "${FileFolders.SKILLS}/"
                    )
                } else {
                    Log.w(TAG, "prepareBackupFile: Skills folder does not exist or is not a directory")
                }

                val fontsFolder = File(context.filesDir, FileFolders.FONTS)
                if (fontsFolder.exists() && fontsFolder.isDirectory) {
                    Log.i(TAG, "prepareBackupFile: Backing up fonts from ${fontsFolder.absolutePath}")
                    fontsFolder.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            addFileToZip(zipOut, file, "${FileFolders.FONTS}/${file.name}")
                        }
                    }
                } else {
                    Log.w(TAG, "prepareBackupFile: Fonts folder does not exist or is not a directory")
                }
            }
        }

        Log.i(
            TAG,
            "prepareBackupFile: Created backup file ${backupFile.name} (${backupFile.length().fileSizeToString()})"
        )
        backupFile
    }

    private fun legacyCompatibleSettingsJson(): String {
        return makeRikkaHubCompatible(
            json.encodeToJsonElement(Settings.serializer(), settingsStore.settingsFlow.value)
        ).toString()
    }

    private suspend fun restoreFromBackupFile(backupFile: File) = backupManager.stageRestore(backupFile)

    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(entryName)
            zipOut.putNextEntry(zipEntry)
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
                addDirectoryToZip(
                    zipOut = zipOut,
                    rootDir = rootDir,
                    currentDir = file,
                    entryPrefix = entryPrefix,
                )
            } else if (file.isFile) {
                val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
                addFileToZip(zipOut, file, "$entryPrefix$relativePath")
            }
        }
    }

    private fun addVirtualFileToZip(zipOut: ZipOutputStream, name: String, content: String) {
        val zipEntry = ZipEntry(name)
        zipOut.putNextEntry(zipEntry)
        zipOut.write(content.toByteArray())
        zipOut.closeEntry()
        Log.i(TAG, "addVirtualFileToZip: $name (${content.length} bytes)")
    }
}

internal fun makeRikkaHubCompatible(settings: JsonElement): JsonElement {
    fun stripForkFields(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { it != "price" }.mapValues { (_, value) -> stripForkFields(value) }
        )
        is JsonArray -> JsonArray(element.map(::stripForkFields))
        else -> element
    }

    val root = stripForkFields(settings) as? JsonObject ?: return settings
    val compatibleRoot = root - setOf("skillOrder", "workspaceOrder")

    val displaySetting = (compatibleRoot["displaySetting"] as? JsonObject)?.let {
        it - setOf(
            "enableCodeLigatures",
            "useChatFontGlobally",
            "screenCornerAdaptation",
            "showThinkingContentPreview",
        )
    }
    val assistants = (compatibleRoot["assistants"] as? JsonArray)?.let { array ->
        JsonArray(array.map { assistantElement ->
            val assistant = assistantElement as? JsonObject ?: return@map assistantElement
            val compatibleAssistant = assistant - setOf("contextCache", "manualAuthorizationTools", "includeHistoryReasoning")
            val localTools = (compatibleAssistant["localTools"] as? JsonArray)?.let { tools ->
                JsonArray(tools.filterNot { tool ->
                    val type = (tool as? JsonObject)?.get("type")?.toString()?.trim('"')
                    type == "battery" || type == "location"
                })
            }
            JsonObject(
                if (localTools == null) {
                    compatibleAssistant
                } else {
                    compatibleAssistant + ("localTools" to localTools)
                }
            )
        })
    }

    val result = compatibleRoot.toMutableMap()
    displaySetting?.let { result["displaySetting"] = JsonObject(it) }
    assistants?.let { result["assistants"] = it }
    return JsonObject(result)
}

data class WebDavBackupItem(
    val href: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
