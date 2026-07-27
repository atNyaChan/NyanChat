package me.rerere.rikkahub.ui.pages.backup.tabs

import android.provider.OpenableColumns

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.FileImport
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.StickyHeader
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.backup.components.BackupDialog
import me.rerere.rikkahub.ui.pages.backup.components.RestoreWarningDialog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ImportExportTab(
    vm: BackupVM,
) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var exportLegacy by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importSucceeded by remember { mutableStateOf(false) }

    // 导入类型：local 为本地备份，chatbox 为 Chatbox 导入，cherry 为 Cherry Studio 导入
    var importType by remember { mutableStateOf("local") }
    var confirmLocalRestore by remember { mutableStateOf(false) }

    // 创建文件保存的launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { targetUri ->
            scope.launch {
                isExporting = true
                vm.trackBackupOrRestore {
                    val previousBackupTime = vm.updateBackupTimeBeforeExport()
                    runCatching {
                        // 导出文件
                        val exportFile = if (exportLegacy) vm.exportLegacyToFile() else vm.exportToFile()

                        // 复制到用户选择的位置
                        val outputStream = context.contentResolver.openOutputStream(targetUri)
                            ?: error("Unable to open backup destination")
                        outputStream.use {
                            FileInputStream(exportFile).use { inputStream ->
                                inputStream.copyTo(it)
                            }
                        }

                        // 清理临时文件
                        exportFile.delete()

                        toaster.show(
                            context.getString(R.string.backup_page_backup_success),
                            type = ToastType.Success
                        )
                    }.onFailure { e ->
                        vm.restoreBackupTime(previousBackupTime)
                        e.printStackTrace()
                        toaster.show(
                            context.getString(R.string.backup_page_export_failed_detail, e.message.orEmpty()),
                            type = ToastType.Error
                        )
                    }
                }
                isExporting = false
            }
        }
    }

    // 创建文件选择的launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { sourceUri ->
            showImportDialog = true
            importSucceeded = false
            scope.launch {
                isRestoring = true
                runCatching {
                    when (importType) {
                            "local" -> {
                                val displayName = context.contentResolver.query(
                                    sourceUri,
                                    arrayOf(OpenableColumns.DISPLAY_NAME),
                                    null,
                                    null,
                                    null,
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) cursor.getString(0) else null
                                } ?: sourceUri.lastPathSegment.orEmpty()
                                val suffix = if (displayName.endsWith(".tar", true)) ".tar" else ".zip"
                                val tempFile = File(
                                    context.cacheDir,
                                    "temp_restore_${System.currentTimeMillis()}$suffix",
                                )

                                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                                    FileOutputStream(tempFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }

                                // 从临时文件恢复
                                vm.restoreFromLocalFile(tempFile)

                                // 清理临时文件
                                tempFile.delete()
                            }

                            "chatbox" -> {
                                // Chatbox导入：处理json文件
                                val tempFile =
                                    File(context.cacheDir, "temp_chatbox_${System.currentTimeMillis()}.json")

                                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                                    FileOutputStream(tempFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }

                                // 从Chatbox文件恢复
                                vm.restoreFromChatBox(tempFile)

                                // 清理临时文件
                                tempFile.delete()
                            }

                            "cherry" -> {
                                // Cherry Studio导入：处理zip文件
                                val tempFile =
                                    File(context.cacheDir, "temp_cherry_${System.currentTimeMillis()}.zip")

                                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                                    FileOutputStream(tempFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }

                                // 从Cherry Studio备份恢复
                                vm.restoreFromCherryStudio(tempFile)

                                // 清理临时文件
                                tempFile.delete()
                            }
                    }

                    toaster.show(
                        context.getString(R.string.backup_page_restore_success),
                        type = ToastType.Success
                    )
                    importSucceeded = true
                }.onFailure { e ->
                    e.printStackTrace()
                    showImportDialog = false
                    toaster.show(
                        context.getString(R.string.backup_page_import_failed_detail, e.message.orEmpty()),
                        type = ToastType.Error
                    )
                }
                isRestoring = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        stickyHeader {
            StickyHeader {
                Text(stringResource(R.string.backup_page_rikkahub_format))
            }
        }

        item {
            CardGroup {
                item(
                    onClick = if (!isExporting) {
                        {
                            exportLegacy = false
                            val timestamp = LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                            createDocumentLauncher.launch("rikkahub_backup_$timestamp.tar")
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_local_backup_export)) },
                    supportingContent = {
                        Text(
                            if (isExporting && !exportLegacy) {
                                stringResource(R.string.backup_page_exporting)
                            } else {
                                stringResource(R.string.backup_page_tar_desc)
                            }
                        )
                    },
                    leadingContent = {
                        Icon(HugeIcons.File01, null)
                    },
                )

                item(
                    onClick = if (!isExporting) {
                        {
                            exportLegacy = true
                            val timestamp = LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                            createDocumentLauncher.launch("rikkahub_backup_legacy_$timestamp.zip")
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_legacy_export)) },
                    supportingContent = {
                        Text(
                            if (isExporting && exportLegacy) {
                                stringResource(R.string.backup_page_exporting)
                            } else {
                                stringResource(R.string.backup_page_legacy_export_desc)
                            }
                        )
                    },
                    leadingContent = {
                        Icon(HugeIcons.File01, null)
                    },
                )

                item(
                    onClick = if (!isRestoring) {
                        {
                            confirmLocalRestore = true
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_import_local)) },
                    supportingContent = {
                        Text(
                            if (isRestoring && importType == "local") {
                                stringResource(R.string.backup_page_importing)
                            } else {
                                stringResource(R.string.backup_page_import_desc)
                            }
                        )
                    },
                    leadingContent = {
                        Icon(HugeIcons.FileImport, null)
                    },
                )
            }
        }

        stickyHeader {
            StickyHeader {
                Text(stringResource(R.string.backup_page_import_from_other_app))
            }
        }

        item {
            CardGroup {
                item(
                    onClick = if (!isRestoring) {
                        {
                            importType = "chatbox"
                            openDocumentLauncher.launch(arrayOf("application/json"))
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_import_from_chatbox)) },
                    supportingContent = { Text(stringResource(R.string.backup_page_import_chatbox_desc)) },
                    leadingContent = {
                        Icon(HugeIcons.FileImport, null)
                    },
                )

                item(
                    onClick = if (!isRestoring) {
                        {
                            importType = "cherry"
                            openDocumentLauncher.launch(arrayOf("application/zip"))
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_import_from_cherry_studio)) },
                    supportingContent = { Text(stringResource(R.string.backup_page_import_cherry_studio_desc)) },
                    leadingContent = {
                        Icon(HugeIcons.FileImport, null)
                    },
                )
            }
        }
    }

    RestoreWarningDialog(
        show = confirmLocalRestore,
        onConfirm = {
            confirmLocalRestore = false
            importType = "local"
            openDocumentLauncher.launch(
                arrayOf("application/x-tar", "application/zip", "application/octet-stream")
            )
        },
        onDismiss = { confirmLocalRestore = false },
    )

    if (showImportDialog) {
        BackupDialog(importing = !importSucceeded)
    }
    if (isExporting) {
        BackupDialog(exporting = true)
    }
}
