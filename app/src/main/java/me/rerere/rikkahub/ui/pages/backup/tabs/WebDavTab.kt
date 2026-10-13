package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.backup.components.BackupFileInfo
import me.rerere.rikkahub.ui.pages.backup.components.BackupFilesSheet
import me.rerere.rikkahub.ui.pages.backup.components.BackupItemsDescriptionText
import me.rerere.rikkahub.ui.pages.backup.components.RemoteBackupLayout
import me.rerere.rikkahub.ui.pages.backup.components.RemoteBackupStatus
import me.rerere.rikkahub.ui.pages.backup.components.RestoreWarningDialog
import me.rerere.rikkahub.ui.pages.backup.components.backupContentItem
import me.rerere.ui.components.RikkaConfirmDialog

@Composable
fun WebDavTab(
    vm: BackupVM,
    onShowRestartDialog: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val webDavConfig = settings.webDavConfig
    val backupItemsState by vm.webDavBackupItems.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBackupFiles by remember { mutableStateOf(false) }
    var restoringItemId by remember { mutableStateOf<String?>(null) }
    var pendingRestoreItem by remember { mutableStateOf<WebDavBackupItem?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<WebDavBackupItem?>(null) }
    val isBackingUp by vm.isBackupOrRestoreRunning.collectAsStateWithLifecycle()

    fun updateWebDavConfig(newConfig: WebDavConfig) {
        vm.updateSettings { it.copy(webDavConfig = newConfig) }
    }

    RemoteBackupLayout(
        isBackingUp = isBackingUp,
        onTestConnection = {
            scope.launch {
                try {
                    vm.testWebDav()
                    toaster.show(
                        context.getString(R.string.backup_page_connection_success),
                        type = ToastType.Success
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    toaster.show(
                        context.getString(
                            R.string.backup_page_connection_failed,
                            e.message ?: ""
                        ),
                        type = ToastType.Error
                    )
                }
            }
        },
        onBackup = {
            scope.launch {
                runCatching {
                    vm.backup()
                    vm.loadBackupFileItems()
                    toaster.show(
                        context.getString(R.string.backup_page_backup_success),
                        type = ToastType.Success
                    )
                }.onFailure {
                    it.printStackTrace()
                    toaster.show(
                        it.message ?: context.getString(R.string.backup_page_unknown_error),
                        type = ToastType.Error
                    )
                }
            }
        },
    ) {
        RemoteBackupStatus(
            lastBackupTime = settings.backupReminderConfig.lastBackupTime,
            filesState = backupItemsState,
            onShowFiles = {
                vm.loadBackupFileItems()
                showBackupFiles = true
            },
        ) {
            backupContentItem(
                options = listOf(
                    WebDavConfig.BackupItem.FILES,
                    WebDavConfig.BackupItem.WORKSPACE,
                ),
                selected = webDavConfig.items,
                onChange = { updateWebDavConfig(webDavConfig.copy(items = it)) },
                description = { BackupItemsDescriptionText() },
            ) { item ->
                when (item) {
                    WebDavConfig.BackupItem.FILES -> stringResource(R.string.backup_page_files)
                    WebDavConfig.BackupItem.WORKSPACE -> stringResource(R.string.backup_page_workspace)
                    WebDavConfig.BackupItem.DATABASE -> stringResource(R.string.backup_page_chat_records)
                }
            }
        }

        CardGroup {
            item(
                supportingContent = {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = webDavConfig.url,
                        onValueChange = { updateWebDavConfig(webDavConfig.copy(url = it.trim())) },
                        label = { Text(stringResource(R.string.backup_page_webdav_server_address)) },
                        placeholder = { Text("https://example.com/dav") },
                        singleLine = true
                    )
                },
                headlineContent = {},
            )
            item(
                supportingContent = {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = webDavConfig.username,
                        onValueChange = {
                            updateWebDavConfig(
                                webDavConfig.copy(
                                    username = it.trim()
                                )
                            )
                        },
                        label = { Text(stringResource(R.string.backup_page_username)) },
                        singleLine = true
                    )
                },
                headlineContent = {},
            )
            item(
                supportingContent = {
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = webDavConfig.password,
                        onValueChange = { updateWebDavConfig(webDavConfig.copy(password = it.trim())) },
                        label = { Text(stringResource(R.string.backup_page_password)) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) {
                                HugeIcons.ViewOff
                            } else {
                                HugeIcons.View
                            }
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = null)
                            }
                        },
                        singleLine = true
                    )
                },
                headlineContent = {},
            )
            item(
                supportingContent = {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = webDavConfig.path,
                        onValueChange = { updateWebDavConfig(webDavConfig.copy(path = it.trim())) },
                        label = { Text(stringResource(R.string.backup_page_path)) },
                        singleLine = true
                    )
                },
                headlineContent = {},
            )
        }
    }

    if (showBackupFiles) {
        BackupFilesSheet(
            title = stringResource(R.string.backup_page_webdav_backup_files),
            state = backupItemsState,
            restoringName = restoringItemId,
            info = { BackupFileInfo(name = it.displayName, size = it.size, lastModified = it.lastModified) },
            onDelete = { pendingDeleteItem = it },
            onRestore = { pendingRestoreItem = it },
            onDismiss = { showBackupFiles = false },
        )
    }

    RestoreWarningDialog(
        show = pendingRestoreItem != null,
        onConfirm = {
            val restoreItem = pendingRestoreItem ?: return@RestoreWarningDialog
            pendingRestoreItem = null
            scope.launch {
                restoringItemId = restoreItem.displayName
                runCatching {
                    vm.restore(item = restoreItem)
                    toaster.show(
                        context.getString(R.string.backup_page_restore_success),
                        type = ToastType.Success
                    )
                    showBackupFiles = false
                    onShowRestartDialog()
                }.onFailure { err ->
                    err.printStackTrace()
                    toaster.show(
                        context.getString(R.string.backup_page_restore_failed, err.message ?: ""),
                        type = ToastType.Error
                    )
                }
                restoringItemId = null
            }
        },
        onDismiss = { pendingRestoreItem = null },
    )
    RikkaConfirmDialog(
        show = pendingDeleteItem != null,
        title = stringResource(R.string.backup_page_delete_remote_title, "WebDAV"),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            val item = pendingDeleteItem ?: return@RikkaConfirmDialog
            pendingDeleteItem = null
            scope.launch {
                runCatching {
                    vm.deleteWebDavBackupFile(item)
                    toaster.show(context.getString(R.string.backup_page_delete_success), type = ToastType.Success)
                    vm.loadBackupFileItems()
                }.onFailure { err ->
                    toaster.show(
                        context.getString(R.string.backup_page_delete_failed, err.message ?: ""),
                        type = ToastType.Error,
                    )
                }
            }
        },
        onDismiss = { pendingDeleteItem = null },
    ) { Text(stringResource(R.string.backup_page_delete_remote_desc)) }
}
