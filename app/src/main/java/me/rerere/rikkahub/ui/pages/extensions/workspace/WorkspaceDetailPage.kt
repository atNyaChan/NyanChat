package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.content.Intent
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.rerere.ui.toast.ToastType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.Bash
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileEdit
import me.rerere.hugeicons.stroke.FileExport
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.FileView
import me.rerere.hugeicons.stroke.FingerPrint
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.FolderAdd
import me.rerere.hugeicons.stroke.FolderExport
import me.rerere.hugeicons.stroke.FolderOpen
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Share08
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.WorkspaceFileWriteToolNames
import me.rerere.rikkahub.data.ai.tools.resolveWorkspaceToolApproval
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.AppLoadingIndicator
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.HorizontalPager
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.components.ui.DeleteConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.components.BackupDialog
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.RootfsInstallStage
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

private enum class WorkspaceDetailTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    BASIC(HugeIcons.Settings03, R.string.workspace_detail_tab_basic),
    FILES(HugeIcons.File02, R.string.workspace_detail_tab_files);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
fun WorkspaceDetailPage(id: String) {
    val navController = LocalNavController.current
    val vm: WorkspaceDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    val installProgress by vm.installProgress.collectAsStateWithLifecycle()
    val installError by vm.installError.collectAsStateWithLifecycle()
    val settingsError by vm.settingsError.collectAsStateWithLifecycle()
    val hasWorkspaceContent = state.workspaceHasContent
    val detailTabs: List<WorkspaceDetailTab> =
        if (hasWorkspaceContent) WorkspaceDetailTab.entries else listOf(WorkspaceDetailTab.BASIC)
    val pagerState = rememberPagerState { detailTabs.size }
    var deleteTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    var showInstallDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteWorkspaceConfirm by remember { mutableStateOf(false) }
    var previewImageUri by remember { mutableStateOf<String?>(null) }
    var wasInstallingRootfs by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else null
        } ?: uri.lastPathSegment ?: "imported_file"
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
        vm.importFile(inputStream, fileName)
    }
    var exportTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val entry = exportTarget.also { exportTarget = null } ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return@rememberLauncherForActivityResult
        vm.exportFile(entry, outputStream)
    }
    val directoryExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        vm.exportFilesToDirectory(uri, context.contentResolver)
    }
    var isExportingRootfs by rememberSaveable { mutableStateOf(false) }
    val rootfsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zstd"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val outputStream = context.contentResolver.openOutputStream(uri)
        if (outputStream == null) {
            toaster.show(
                context.getString(R.string.workspace_detail_export_failed, ""),
                type = ToastType.Error,
            )
            return@rememberLauncherForActivityResult
        }
        isExportingRootfs = true
        vm.exportRootfsArchive(outputStream) { error ->
            isExportingRootfs = false
            if (error == null) {
                toaster.show(
                    context.getString(R.string.workspace_detail_export_success),
                    type = ToastType.Success,
                )
            } else {
                toaster.show(
                    context.getString(R.string.workspace_detail_export_failed, error.message.orEmpty()),
                    type = ToastType.Error,
                )
            }
        }
    }
    val rootfsImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else {
                null
            }
        } ?: uri.lastPathSegment ?: "rootfs.tar.gz"
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return@rememberLauncherForActivityResult
        showInstallDialog = false
        vm.installRootfs(inputStream, fileName)
    }

    val installSuccessMessage = state.workspace?.name?.let { name ->
        stringResource(R.string.workspace_detail_install_success, name)
    }
    androidx.compose.runtime.LaunchedEffect(installProgress, installError) {
        if (installProgress != null) {
            wasInstallingRootfs = true
        } else if (wasInstallingRootfs) {
            wasInstallingRootfs = false
            if (installError == null && installSuccessMessage != null) {
                toaster.show(installSuccessMessage, type = ToastType.Success)
            }
        }
    }

    val onFilesTab = detailTabs.getOrNull(pagerState.currentPage) == WorkspaceDetailTab.FILES

    BackHandler(enabled = onFilesTab && state.path.isNotBlank()) {
        vm.goUp()
    }
    BackHandler(enabled = isExportingRootfs || installProgress != null) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.workspace?.name ?: stringResource(R.string.workspace_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    if (onFilesTab) {
                        if (state.workspace?.supportsTerminal() == true) {
                            IconButton(
                                onClick = {
                                    val cwd = state.layout?.absolutePath(state.area, state.path)
                                        ?: when (state.area) {
                                            WorkspaceStorageArea.FILES -> state.path
                                                .takeIf(String::isNotBlank)
                                                ?.let { "/workspace/$it" }
                                                ?: "/workspace"

                                            WorkspaceStorageArea.LINUX -> state.path
                                                .takeIf(String::isNotBlank)
                                                ?.let { "/$it" }
                                                ?: "/"
                                        }
                                    navController.navigate(Screen.WorkspaceTerminal(id, cwd))
                                }
                            ) {
                                Icon(HugeIcons.ComputerTerminal01, contentDescription = null)
                            }
                        }
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(HugeIcons.Refresh01, contentDescription = null)
                        }
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(
                                HugeIcons.FolderAdd,
                                contentDescription = stringResource(R.string.chat_page_create_folder),
                            )
                        }
                        IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                            Icon(
                                HugeIcons.FileImport,
                                contentDescription = stringResource(R.string.workspace_detail_import_file),
                            )
                        }
                    }
                    if (!onFilesTab) {
                        // 远程工作区的文件不在本机, 没有可导出的 Rootfs 归档
                        if (hasWorkspaceContent && state.workspace?.capabilities()?.rootfs != false) {
                            IconButton(
                                onClick = {
                                    val name = state.workspace?.name
                                        ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
                                        ?.ifBlank { "workspace" }
                                        ?: "workspace"
                                    rootfsExportLauncher.launch("$name.tar.zst")
                                },
                                enabled = !isExportingRootfs,
                            ) {
                                Icon(
                                    HugeIcons.FolderExport,
                                    contentDescription = stringResource(R.string.common_export),
                                )
                            }
                        }
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(HugeIcons.PencilEdit01, contentDescription = stringResource(R.string.common_rename))
                        }
                        IconButton(onClick = { showDeleteWorkspaceConfirm = true }) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            PagerTabs(
                pagerState = pagerState,
                tabs = detailTabs,
                modifier = Modifier.padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (detailTabs[page]) {
                    WorkspaceDetailTab.BASIC -> WorkspaceBasicPage(
                        workspace = state.workspace,
                        installProgress = installProgress,
                        onInstallRootfs = { showInstallDialog = true },
                        onToolApprovalChange = vm::setToolApproval,
                        onShellCompatibilityModeChange = vm::setShellCompatibilityMode,
                        onEditSsh = { navController.navigate(Screen.WorkspaceSshEditor(id)) },
                    )

                    WorkspaceDetailTab.FILES -> WorkspaceFilesPage(
                        state = state,
                        isActive = onFilesTab,
                        onSelectArea = vm::selectArea,
                        onGoUp = vm::goUp,
                        onResolveImage = { entry, area -> vm.resolveImageFile(entry, area) },
                        onOpen = { entry ->
                            when {
                                entry.isDirectory -> vm.open(entry)
    
                                entry.name.substringAfterLast('.').equals("svg", ignoreCase = true) ->
                                    navController.navigate(
                                        Screen.WorkspaceFileEditor(id, state.area.name, entry.path)
                                    )
    
                                else -> when (entry.detectFileType()) {
                                    WorkspaceFileType.TEXT -> navController.navigate(
                                        Screen.WorkspaceFileEditor(id, state.area.name, entry.path)
                                    )
    
                                    WorkspaceFileType.IMAGE -> vm.exportToCacheFile(entry, context.cacheDir) { file ->
                                        // 传绝对路径 (而非 content:// URI): Coil 可直接加载,
                                        // 预览弹窗的保存按钮 saveMessageImage 只认 "/" 开头路径, content URI 会报错
                                        previewImageUri = file.absolutePath
                                    }
    
                                    WorkspaceFileType.OTHER -> vm.exportToCacheFile(entry, context.cacheDir) { file ->
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file,
                                        )
                                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                                            file.extension.lowercase()
                                        ) ?: "*/*"
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mime)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(intent, null))
                                        }
                                    }
                                }
                            }
                        },
                        onDelete = { deleteTarget = it },
                        onBatchExport = { entries ->
                            if (vm.prepareBatchExport(entries)) directoryExportLauncher.launch(null)
                        },
                        onExport = { entry ->
                            exportTarget = entry
                            exportLauncher.launch(entry.name)
                        },
                        onShare = { entry ->
                            vm.exportToCacheFile(entry, context.cacheDir) { file ->
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        },
                    )
                }
            }
        }
    }

    state.workspace?.let { workspace ->
        if (showInstallDialog) {
            InstallRootfsDialog(
                workspace = workspace,
                onDismiss = { showInstallDialog = false },
                onImportLocal = { rootfsImportLauncher.launch(arrayOf("*/*")) },
                onConfirm = { url ->
                    vm.installRootfs(url)
                    showInstallDialog = false
                },
            )
        }
    }

    installError?.let { message ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = vm::dismissInstallError,
            title = { Text(stringResource(R.string.workspace_detail_rootfs_install_failed)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = vm::dismissInstallError) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    settingsError?.let { message ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = vm::dismissSettingsError,
            title = { Text(stringResource(R.string.workspace_detail_settings_save_failed)) },
            text = { Text(message.ifBlank { stringResource(R.string.workspace_detail_settings_save_failed) }) },
            confirmButton = {
                TextButton(onClick = vm::dismissSettingsError) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    installProgress?.let { progress ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = {},
            title = { Text(stringResource(R.string.workspace_detail_installing_rootfs)) },
            text = { RootfsProgress(progress) },
            confirmButton = {},
        )
    }

    previewImageUri?.let { uri ->
        ImagePreviewDialog(
            images = listOf(uri),
            onDismissRequest = { previewImageUri = null },
        )
    }

    state.exportResult?.let { result ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = vm::dismissExportResult,
            title = { Text(stringResource(R.string.workspace_detail_export_result)) },
            text = { Text(result, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = vm::dismissExportResult) { Text(stringResource(R.string.common_confirm_action)) }
            },
        )
    }

    deleteTarget?.let { entry ->
        DeleteConfirmDialog(
            show = true,
            type = if (entry.isDirectory) stringResource(R.string.delete_type_directory) else stringResource(R.string.delete_type_file),
            name = entry.path,
            onConfirm = {
                vm.delete(entry)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (showRenameDialog) {
        var name by remember(state.workspace?.name) {
            mutableStateOf(state.workspace?.name.orEmpty())
        }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.workspace_page_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameWorkspace(name.trim())
                        showRenameDialog = false
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (isExportingRootfs) {
        BackupDialog(exporting = true)
    }

    DeleteConfirmDialog(
        show = showDeleteWorkspaceConfirm,
        type = stringResource(R.string.delete_type_workspace),
        onConfirm = {
            showDeleteWorkspaceConfirm = false
            vm.deleteWorkspace { navController.popBackStack() }
        },
        onDismiss = { showDeleteWorkspaceConfirm = false },
        extra = { Text(stringResource(R.string.workspace_page_delete_confirm)) },
    )

    if (showCreateFolderDialog) {
        CreateWorkspaceFolderDialog(
            entries = state.entries,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                vm.createDirectory(name)
            },
        )
    }
}

@Composable
private fun CreateWorkspaceFolderDialog(
    entries: List<WorkspaceFileEntry>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val trimmedName = name.trim()
    val existingEntry = entries.firstOrNull { it.name == trimmedName }
    val validationError = when {
        '/' in name || '\\' in name -> stringResource(R.string.workspace_detail_folder_name_slash_error)
        attemptedSubmit && trimmedName.isEmpty() ->
            stringResource(R.string.workspace_detail_folder_name_empty_error)
        existingEntry != null -> stringResource(
            R.string.workspace_detail_folder_name_exists_error,
            stringResource(
                if (existingEntry.isDirectory) {
                    R.string.workspace_detail_folder_type
                } else {
                    R.string.workspace_detail_file_type
                }
            ),
        )
        else -> null
    }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_page_create_folder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.workspace_detail_create_folder_prompt))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = validationError?.let { error ->
                        {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attemptedSubmit = true
                    if (trimmedName.isNotEmpty() && validationError == null) {
                        onConfirm(trimmedName)
                    }
                },
            ) {
                Text(stringResource(R.string.common_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun WorkspaceBasicPage(
    workspace: WorkspaceEntity?,
    installProgress: RootfsInstallProgress?,
    onInstallRootfs: () -> Unit,
    onToolApprovalChange: (String, Boolean) -> Unit,
    onShellCompatibilityModeChange: (Boolean) -> Unit,
    onEditSsh: () -> Unit,
) {
    val shellStatus = workspace?.shellStatus
    val installing = installProgress != null
    // 加载完成前按本地工作区展示
    val hasRootfs = workspace?.capabilities()?.rootfs ?: true
    val rootfsReady = shellStatus == WorkspaceShellStatus.READY.name
    val isBroken = shellStatus == WorkspaceShellStatus.BROKEN.name
    val isReinstall = rootfsReady || isBroken
    val installButtonText =
        if (isReinstall) stringResource(R.string.workspace_detail_reinstall_rootfs)
        else stringResource(R.string.workspace_detail_install_rootfs)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (workspace?.isSsh() == true) {
            item {
                SshConnectionCard(workspace = workspace, onEdit = onEditSsh)
            }
        }
        if (hasRootfs) {
            item {
                CardGroup(
                    title = {
                        Text(stringResource(R.string.workspace_detail_enable_shell))
                    },
                ) {
                    item(
                        headlineContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                CardGroupIcon(HugeIcons.Bash)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(stringResource(R.string.workspace_detail_area_rootfs))
                                    Text(
                                        text = stringResource(R.string.workspace_detail_enable_shell_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onInstallRootfs,
                                    enabled = workspace != null && !installing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.Bash,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(installButtonText)
                                }
                                installProgress?.let { RootfsProgress(it) }
                            }
                        },
                    )
                    if (rootfsReady) {
                        val compatibilityMode = workspace.shellCompatibilityMode
                        switchItem(
                            checked = compatibilityMode,
                            onCheckedChange = onShellCompatibilityModeChange,
                            supportingContent = {
                                Text(stringResource(R.string.workspace_detail_compatibility_mode_desc))
                            },
                            // 关闭时图标褪成中性色
                            leadingContent = {
                                CardGroupIcon(
                                    icon = HugeIcons.Wrench01,
                                    containerColor = if (compatibilityMode) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    contentColor = if (compatibilityMode) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.workspace_detail_compatibility_mode)) },
                        )
                    }
                }
            }
        }

        // 本地工作区需要 Rootfs 就绪后才能使用这些工具; 远程工作区不依赖 Rootfs, 始终显示
        if (!hasRootfs || rootfsReady) {
            item {
                WorkspaceToolApprovalCard(
                    workspace = workspace,
                    onToolApprovalChange = onToolApprovalChange,
                )
            }
        }
    }
}

@Composable
private fun SshConnectionCard(
    workspace: WorkspaceEntity,
    onEdit: () -> Unit,
) {
    val settings = workspace.sshSettings()
    CardGroup(title = { Text(stringResource(R.string.workspace_detail_ssh_connection)) }) {
        item(
            leadingContent = { CardGroupIcon(HugeIcons.CloudServer) },
            headlineContent = { Text(stringResource(R.string.workspace_detail_ssh_address)) },
            supportingContent = {
                Text(
                    settings?.let { "${it.username}@${it.host}:${it.port}" }
                        ?: stringResource(R.string.workspace_detail_ssh_config_missing)
                )
            },
        )
        item(
            leadingContent = { CardGroupIcon(HugeIcons.Folder01) },
            headlineContent = { Text(stringResource(R.string.workspace_detail_ssh_remote_dir)) },
            supportingContent = { Text(settings?.remoteDir ?: "-") },
        )
        item(
            leadingContent = { CardGroupIcon(HugeIcons.FingerPrint) },
            headlineContent = { Text(stringResource(R.string.workspace_detail_ssh_host_key)) },
            supportingContent = {
                val hostKey = settings?.hostKey
                Text(
                    text = hostKey?.let { "${it.type}\n${it.fingerprint}" }
                        ?: stringResource(R.string.workspace_detail_ssh_host_key_unconfirmed),
                    style = LocalTextStyle.current.copy(
                        fontFamily = if (hostKey != null) FontFamily.Monospace else null,
                        fontFeatureSettings = if (hostKey != null) {
                            LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings
                        } else {
                            null
                        },
                    ),
                )
            },
        )
        item(
            onClick = onEdit,
            leadingContent = { CardGroupIcon(HugeIcons.PencilEdit01) },
            headlineContent = { Text(stringResource(R.string.workspace_detail_ssh_edit)) },
            supportingContent = { Text(stringResource(R.string.workspace_detail_ssh_edit_desc)) },
        )
    }
}

@Composable
private fun WorkspaceToolApprovalCard(
    workspace: WorkspaceEntity?,
    onToolApprovalChange: (String, Boolean) -> Unit,
) {
    val overrides = workspace?.toolApprovalOverrides().orEmpty()
    // 远程工作区不提供写文件、编辑文件工具, 审批项跟着隐藏
    val fileWriteTools = workspace?.capabilities()?.fileWriteTools ?: true
    val tools = workspaceToolApprovalItems().filter { (toolName) ->
        fileWriteTools || toolName !in WorkspaceFileWriteToolNames
    }

    CardGroup(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.workspace_detail_tool_approval))
                Text(
                    text = stringResource(R.string.workspace_detail_tool_approval_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        tools.forEach { (toolName, label, icon) ->
            val approved = resolveWorkspaceToolApproval(toolName, overrides)
            switchItem(
                checked = approved,
                onCheckedChange = { onToolApprovalChange(toolName, it) },
                enabled = workspace != null,
                supportingContent = {
                    Text(
                        text = toolName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // 关闭时图标褪成中性色
                leadingContent = {
                    CardGroupIcon(
                        icon = icon,
                        containerColor = if (approved) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (approved) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                headlineContent = { Text(label) },
            )
        }
    }
}

@Composable
private fun workspaceToolApprovalItems() = listOf(
    Triple("workspace_read_file", stringResource(R.string.workspace_detail_tool_read_file), HugeIcons.FileView),
    Triple("workspace_write_file", stringResource(R.string.workspace_detail_tool_write_file), HugeIcons.PencilEdit01),
    Triple("workspace_edit_file", stringResource(R.string.workspace_detail_tool_edit_file), HugeIcons.FileEdit),
    Triple("workspace_shell", stringResource(R.string.workspace_detail_tool_shell), HugeIcons.ComputerTerminal01),
)

@Composable
private fun RootfsProgress(progress: RootfsInstallProgress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val fraction = progress.totalBytes?.takeIf { it > 0 }?.let {
            (progress.bytesRead.toFloat() / it).coerceIn(0f, 1f)
        }
        if (fraction != null && progress.stage == RootfsInstallStage.DOWNLOADING) {
            LinearWavyProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = when (progress.stage) {
                RootfsInstallStage.DOWNLOADING -> {
                    val total = progress.totalBytes?.let { " / ${it.fileSizeToString()}" }.orEmpty()
                    stringResource(R.string.workspace_detail_downloading, progress.bytesRead.fileSizeToString(), total)
                }

                RootfsInstallStage.EXTRACTING -> {
                    val entry = progress.currentEntry?.let { " · $it" }.orEmpty()
                    stringResource(R.string.workspace_detail_extracting, progress.entriesExtracted, entry)
                }

                RootfsInstallStage.INSTALLED -> stringResource(R.string.workspace_detail_install_complete)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InstallRootfsDialog(
    workspace: WorkspaceEntity,
    onDismiss: () -> Unit,
    onImportLocal: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by rememberSaveable(workspace.id) { mutableStateOf(DEFAULT_ROOTFS_URL) }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_detail_install_rootfs)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.workspace_detail_install_rootfs_desc, workspace.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(DEFAULT_ROOTFS_URL) },
                    maxLines = 5,
                )
                Button(
                    onClick = onImportLocal,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.workspace_detail_import_rootfs_local))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim()) },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.common_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun WorkspaceFilesPage(
    state: WorkspaceDetailState,
    isActive: Boolean,
    onSelectArea: (WorkspaceStorageArea) -> Unit,
    onGoUp: () -> Unit,
    onResolveImage: suspend (WorkspaceFileEntry, WorkspaceStorageArea) -> File?,
    onOpen: (WorkspaceFileEntry) -> Unit,
    onDelete: (WorkspaceFileEntry) -> Unit,
    onExport: (WorkspaceFileEntry) -> Unit,
    onBatchExport: (List<WorkspaceFileEntry>) -> Unit,
    onShare: (WorkspaceFileEntry) -> Unit,
) {
    var selecting by remember(state.area, state.path) { mutableStateOf(false) }
    var selectedPaths by remember(state.area, state.path) { mutableStateOf(emptySet<String>()) }
    val files = state.entries.filterNot { it.isDirectory }
    val selectedFiles = files.filter { it.path in selectedPaths }
    val allSelected = files.isNotEmpty() && selectedFiles.size == files.size
    fun toggleSelection(entry: WorkspaceFileEntry) {
        selectedPaths = if (entry.path in selectedPaths) selectedPaths - entry.path else selectedPaths + entry.path
    }
    fun exitSelection() {
        selecting = false
        selectedPaths = emptySet()
    }
    BackHandler(enabled = selecting && isActive) {
        exitSelection()
    }
    val showLoading = rememberDelayedVisibility(state.loading)
    val isEmptyDirectory = !state.loading && state.entries.isEmpty() && state.error == null
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                // 多选时底部留出悬浮工具栏的位置
                bottom = if (selecting) 128.dp else 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isEmptyDirectory) {
                // 空目录时让空状态在路径栏下方垂直居中（整项撑满可视区域，头部占剩余高度由空状态填充）
                item {
                    Column(modifier = Modifier.fillParentMaxSize()) {
                        WorkspaceFilesHeader(
                            state = state,
                            onSelectArea = onSelectArea,
                            onGoUp = onGoUp,
                            showLoading = showLoading,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.workspace_detail_empty_directory),
                                icon = HugeIcons.FolderOpen,
                            )
                        }
                    }
                }
            } else {
                item {
                    WorkspaceFilesHeader(
                        state = state,
                        onSelectArea = onSelectArea,
                        onGoUp = onGoUp,
                        showLoading = showLoading,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            if (showLoading && state.entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppLoadingIndicator()
                    }
                }
            }

            items(state.entries, key = { "${state.area.name}:${it.path}" }) { entry ->
                WorkspaceFileItem(
                    entry = entry,
                    area = state.area,
                    // 复制出去的路径要能直接给 AI 工具和终端用，所以按执行环境内的绝对路径给
                    copyPath = state.layout?.absolutePath(state.area, entry.path),
                    onResolveImage = { onResolveImage(entry, state.area) },
                    selecting = selecting,
                    selected = entry.path in selectedPaths,
                    onLongClick = {
                        selecting = true
                        selectedPaths = selectedPaths + entry.path
                    },
                    onOpen = {
                        if (selecting && !entry.isDirectory) toggleSelection(entry) else onOpen(entry)
                    },
                    onDelete = { onDelete(entry) },
                    onExport = { onExport(entry) },
                    onShare = { onShare(entry) },
                )
            }
        }

        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -FloatingToolbarDefaults.ScreenOffset),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = CustomColors.floatingToolbarColors,
                leadingContent = {
                    IconButton(onClick = { exitSelection() }) {
                        Icon(
                            HugeIcons.Cancel01,
                            contentDescription = stringResource(R.string.workspace_detail_selection_cancel),
                        )
                    }
                },
                trailingContent = {
                    IconButton(
                        onClick = {
                            selectedPaths = if (allSelected) emptySet() else files.map { it.path }.toSet()
                        },
                    ) {
                        Icon(
                            HugeIcons.CheckList,
                            contentDescription = if (allSelected) {
                                stringResource(R.string.workspace_detail_selection_deselect_all)
                            } else {
                                stringResource(R.string.workspace_detail_selection_select_all)
                            },
                        )
                    }
                },
            ) {
                Button(
                    onClick = { onBatchExport(selectedFiles) },
                    enabled = selectedFiles.isNotEmpty() && !state.exporting,
                ) {
                    Icon(
                        imageVector = HugeIcons.FileExport,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.workspace_detail_export_count, selectedFiles.size))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceFilesHeader(
    state: WorkspaceDetailState,
    onSelectArea: (WorkspaceStorageArea) -> Unit,
    onGoUp: () -> Unit,
    showLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    // 远程工作区没有 Rootfs, 两个区域分别是工作目录和根目录
    val hasRootfs = state.workspace?.capabilities()?.rootfs ?: true
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkspaceAreaSelector(
            selected = state.area,
            hasRootfs = hasRootfs,
            onSelected = onSelectArea,
        )
        WorkspacePathBar(
            path = state.layout?.absolutePath(state.area, state.path) ?: when (state.area) {
                WorkspaceStorageArea.FILES -> state.path
                    .takeIf(String::isNotBlank)
                    ?.let { "/workspace/$it" }
                    ?: "/workspace"
                WorkspaceStorageArea.LINUX -> state.path
                    .takeIf(String::isNotBlank)
                    ?.let { "/$it" }
                    ?: "/"
            },
            canGoUp = state.path.isNotBlank(),
            onGoUp = onGoUp,
        )
        // 刷新时保留现有列表, 只在顶部提示正在加载
        if (showLoading && state.entries.isNotEmpty()) {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.exporting) {
            ExportProgressCard(
                completed = state.exportCompleted,
                total = state.exportTotal,
            )
        }
        state.error?.let { ErrorCard(it) }
    }
}

@Composable
private fun WorkspaceAreaSelector(
    selected: WorkspaceStorageArea,
    hasRootfs: Boolean,
    onSelected: (WorkspaceStorageArea) -> Unit,
) {
    ConnectedChoiceButtons(
        options = WorkspaceStorageArea.entries,
        selected = selected,
        onSelect = onSelected,
        // 直接放在页面背景上，用更深一档的底色才分得开
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) { area, isSelected ->
        ConnectedChoiceLabel(
            text = when (area) {
                WorkspaceStorageArea.FILES -> stringResource(
                    if (hasRootfs) R.string.workspace_detail_area_files else R.string.workspace_detail_area_working_dir
                )

                WorkspaceStorageArea.LINUX -> stringResource(
                    if (hasRootfs) R.string.workspace_detail_area_rootfs else R.string.workspace_detail_area_root
                )
            },
            selected = isSelected,
            icon = HugeIcons.Tick01,
        )
    }
}

@Composable
private fun WorkspacePathBar(
    path: String,
    canGoUp: Boolean,
    onGoUp: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = CustomColors.listItemColors.containerColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                enabled = canGoUp,
                onClick = onGoUp,
            ) {
                Icon(HugeIcons.ArrowTurnBackward, contentDescription = null)
            }
            Text(
                text = path.ifBlank { "/" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = LocalSettings.current.displaySetting
                        .enableCodeLigatures.codeFontFeatureSettings,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                // 路径太长时省略开头，当前所在的目录始终看得到
                overflow = TextOverflow.StartEllipsis,
            )
        }
    }
}

@Composable
private fun ExportProgressCard(
    completed: Int,
    total: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.workspace_detail_exporting_progress, completed, total),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearWavyProgressIndicator(
                progress = { if (total > 0) completed.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WorkspaceFileItem(
    entry: WorkspaceFileEntry,
    area: WorkspaceStorageArea,
    // 复制出去的路径要能直接给 AI 工具和终端用，所以按执行环境内的绝对路径给
    copyPath: String?,
    onResolveImage: suspend () -> File?,
    selecting: Boolean,
    selected: Boolean,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
) {
    val isImage = !entry.isDirectory && entry.detectFileType() == WorkspaceFileType.IMAGE
    val imageFile by produceState<File?>(
        initialValue = null,
        key1 = if (isImage) area else null,
        key2 = if (isImage) entry.path else null,
        key3 = if (isImage) "${entry.updatedAt}:${entry.sizeBytes}" else null,
    ) {
        if (isImage) {
            value = try {
                onResolveImage()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
    }
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )

    OutlinedItemCard(
        modifier = Modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = if (entry.isDirectory) null else onLongClick,
            onLongClickLabel = if (entry.isDirectory) null else stringResource(R.string.workspace_detail_select_file),
        ),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isImage) {
                val context = LocalContext.current
                val imageRequest = remember(imageFile, entry.updatedAt, entry.sizeBytes) {
                    imageFile?.let {
                        ImageRequest.Builder(context)
                            .data(it)
                            .memoryCacheKey("workspace:${it.absolutePath}:${entry.updatedAt}:${entry.sizeBytes}")
                            .build()
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.File02,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    imageRequest?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            } else if (entry.isDirectory) {
                CardGroupIcon(
                    icon = HugeIcons.Folder01,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                // 选中时整行换成 secondaryContainer，图标底色跟着换一档，不然会融进背景
                CardGroupIcon(
                    icon = HugeIcons.File02,
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.surfaceBright
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!entry.isDirectory) {
                    Text(
                        text = entry.sizeBytes.fileSizeToString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selecting) {
                // 和右侧操作按钮占一样大的位置，进出多选时行高不跳；点整行切换，勾选框自己不接收点击
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!entry.isDirectory) {
                        Checkbox(checked = selected, onCheckedChange = null)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = LocalContext.current
                    // 压紧图标按钮，多个操作才能在一行里放得下
                    val actionButtonModifier = Modifier.size(40.dp)
                    if (copyPath != null) {
                        IconButton(
                            onClick = {
                                context.writeClipboardText(copyPath)
                                Toast.makeText(context, R.string.workspace_detail_path_copied, Toast.LENGTH_SHORT).show()
                            },
                            modifier = actionButtonModifier,
                        ) {
                            Icon(
                                imageVector = HugeIcons.Copy01,
                                contentDescription = stringResource(R.string.workspace_detail_copy_path),
                            )
                        }
                    }
                    if (!entry.isDirectory) {
                        IconButton(onClick = onExport, modifier = actionButtonModifier) {
                            Icon(
                                imageVector = HugeIcons.FileExport,
                                contentDescription = stringResource(R.string.common_export),
                            )
                        }
                        IconButton(onClick = onShare, modifier = actionButtonModifier) {
                            Icon(
                                imageVector = HugeIcons.Share08,
                                contentDescription = stringResource(R.string.common_share),
                            )
                        }
                    }
                    IconButton(onClick = onDelete, modifier = actionButtonModifier) {
                        Icon(
                            imageVector = HugeIcons.Delete01,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** 加载超过 [delayMillis] 才显示指示器, 本地工作区瞬间完成的加载不会闪一下 */
@Composable
private fun rememberDelayedVisibility(loading: Boolean, delayMillis: Long = 300): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(delayMillis)
            visible = true
        } else {
            visible = false
        }
    }
    return visible
}

/** 列表里展示的状态: 远程工作区显示连接地址, 本地工作区显示 shell 状态 */
@Composable
internal fun WorkspaceEntity.statusLabel(): String =
    if (isSsh()) {
        sshSettings()?.let { "SSH · ${it.username}@${it.host}" } ?: "SSH"
    } else {
        shellStatus.toShellStatusLabel()
    }

@Composable
private fun ErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Alert02,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun String.toShellStatusLabel(): String = when (this) {
    WorkspaceShellStatus.READY.name -> stringResource(R.string.workspace_detail_shell_ready)
    WorkspaceShellStatus.BROKEN.name -> stringResource(R.string.workspace_detail_shell_broken)
    else -> stringResource(R.string.workspace_detail_shell_disabled)
}

private const val DEFAULT_ROOTFS_URL =
    "https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/ubuntu-base-26.04-base-arm64.tar.gz"
