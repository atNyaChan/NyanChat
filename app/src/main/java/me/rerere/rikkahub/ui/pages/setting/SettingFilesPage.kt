package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Clean
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Share04
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.dao.ManagedFileWithReference
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.compose.koinInject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

private const val UNUSED_ATTACHMENTS = "unused_attachments"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingFilesPage(
    filesManager: FilesManager = koinInject(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val navController = LocalNavController.current

    // 预先获取字符串资源
    val deletedToast = stringResource(R.string.setting_files_page_deleted_toast)
    val deleteFailedToast = stringResource(R.string.setting_files_page_delete_failed_toast)
    val cleanedToast = stringResource(R.string.setting_files_page_cleaned_toast)
    val cleanFailedToast = stringResource(R.string.setting_files_page_clean_failed_toast)

    var selectedFolder by remember { mutableStateOf(FileFolders.UPLOAD) }
    var pendingDelete by remember { mutableStateOf<ManagedFileEntity?>(null) }
    var showCleanSheet by remember { mutableStateOf(false) }
    var selectedCleanRange by remember { mutableStateOf(CleanRange.DAYS_30) }
    var showRebuildDialog by remember { mutableStateOf(false) }
    var loadedFiles by remember { mutableStateOf<List<ManagedFileWithReference>?>(null) }
    var isRebuilding by remember { mutableStateOf(false) }
    LaunchedEffect(filesManager) {
        loadedFiles = filesManager.listWithReferences(FileFolders.UPLOAD)
    }
    val hasUnusedAttachments = loadedFiles?.any {
        it.conversationId == null || it.nodeId == null
    } == true
    val folders = remember(hasUnusedAttachments) {
        if (hasUnusedAttachments) {
            listOf(FileFolders.UPLOAD, UNUSED_ATTACHMENTS)
        } else {
            listOf(FileFolders.UPLOAD)
        }
    }
    LaunchedEffect(hasUnusedAttachments) {
        if (!hasUnusedAttachments && selectedFolder == UNUSED_ATTACHMENTS) {
            selectedFolder = FileFolders.UPLOAD
        }
    }
    val files = loadedFiles.orEmpty().filter {
        val hasReference = it.conversationId != null && it.nodeId != null
        if (selectedFolder == UNUSED_ATTACHMENTS) !hasReference else hasReference
    }
    val loading = loadedFiles == null

    if (showRebuildDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showRebuildDialog = false },
            title = { Text(stringResource(R.string.setting_files_page_rebuild_index)) },
            text = { Text(stringResource(R.string.search_page_rebuild_index_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebuildDialog = false
                        loadedFiles = null
                        isRebuilding = true
                        scope.launch {
                            try {
                                loadedFiles = filesManager.listWithReferences(
                                    folder = FileFolders.UPLOAD,
                                    forceRebuild = true,
                                )
                            } finally {
                                isRebuilding = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (pendingDelete != null) {
        val target = pendingDelete!!
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    stringResource(
                        R.string.setting_files_page_delete_attachment_title,
                        stringResource(
                            if (target.mimeType.startsWith("image/")) {
                                R.string.setting_files_page_attachment_image
                            } else {
                                R.string.setting_files_page_attachment_file
                            }
                        )
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.setting_files_page_delete_attachment_confirmation,
                        stringResource(
                            if (target.mimeType.startsWith("image/")) {
                                R.string.setting_files_page_attachment_image
                            } else {
                                R.string.setting_files_page_attachment_file
                            }
                        )
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val ok = filesManager.delete(target.id, deleteFromDisk = true)
                            if (ok) {
                                toaster.show(deletedToast)
                                loadedFiles = loadedFiles?.filterNot { it.file.id == target.id }
                            } else {
                                toaster.show(deleteFailedToast, type = ToastType.Error)
                            }
                            pendingDelete = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showCleanSheet) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        ModalBottomSheet(
            onDismissRequest = { showCleanSheet = false },
            sheetState = sheetState,
        ) {
            CleanFilesSheet(
                selectedRange = selectedCleanRange,
                onRangeSelected = { selectedCleanRange = it },
                onClean = {
                    showCleanSheet = false
                    scope.launch {
                        val ok = if (selectedFolder == UNUSED_ATTACHMENTS) {
                            val cutoff = selectedCleanRange.days?.let { days ->
                                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
                            }
                            files
                                .filter { file -> cutoff == null || file.file.createdAt < cutoff }
                                .all { filesManager.delete(it.file.id, deleteFromDisk = true) }
                        } else {
                            selectedCleanRange.days?.let { days ->
                                filesManager.deleteOlderThan(
                                    folder = selectedFolder,
                                    cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()),
                                )
                            } ?: filesManager.deleteAll(selectedFolder)
                        }
                        loadedFiles = filesManager.listWithReferences(folder = FileFolders.UPLOAD)
                        toaster.show(
                            if (ok) cleanedToast else cleanFailedToast,
                            type = if (ok) ToastType.Success else ToastType.Error,
                        )
                    }
                },
            )
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_chat_storage)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = { showRebuildDialog = true },
                        enabled = !loading,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Refresh01,
                            contentDescription = stringResource(R.string.search_page_rebuild_button),
                        )
                    }
                    IconButton(
                        onClick = { showCleanSheet = true },
                        enabled = files.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Clean,
                            contentDescription = stringResource(R.string.setting_files_page_clean_content_description),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
        ) {
            if (isRebuilding || loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                FolderRow(
                    folders = folders,
                    selectedFolder = selectedFolder,
                    onFolderSelected = { selectedFolder = it }
                )
                if (files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.setting_files_page_no_files))
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = innerPadding.calculateBottomPadding() + 16.dp,
                        ),
                        verticalItemSpacing = 8.dp,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        state = gridState,
                        columns = StaggeredGridCells.Fixed(2)
                    ) {
                        items(files, key = { it.file.id }) { item ->
                            FileItem(
                                file = item.file,
                                fileOnDisk = filesManager.getFile(item.file),
                                showReference = selectedFolder != UNUSED_ATTACHMENTS,
                                reference = item,
                                onReference = { conversationId, nodeId ->
                                    navigateToChatPage(
                                        navigator = navController,
                                        chatId = Uuid.parse(conversationId),
                                        nodeId = Uuid.parse(nodeId),
                                    )
                                },
                                onDelete = { pendingDelete = item.file },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class CleanRange(val days: Int?) {
    DAYS_7(7),
    DAYS_14(14),
    DAYS_30(30),
    ALL(null),
}

@Composable
private fun CleanFilesSheet(
    selectedRange: CleanRange,
    onRangeSelected: (CleanRange) -> Unit,
    onClean: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.setting_files_page_clean_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.setting_files_page_clean_range_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )

        CleanRange.entries.forEach { range ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRangeSelected(range) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedRange == range,
                    onClick = { onRangeSelected(range) },
                )
                Text(
                    text = range.days?.let {
                        stringResource(R.string.setting_files_page_clean_older_than_days, it)
                    } ?: stringResource(R.string.setting_files_page_clean_all),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        TextButton(
            onClick = onClean,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.setting_files_page_clean_action))
        }
    }
}

@Composable
private fun FolderRow(
    folders: List<String>,
    selectedFolder: String,
    onFolderSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        folders.forEach { folder ->
            FilterChip(
                selected = selectedFolder == folder,
                onClick = { onFolderSelected(folder) },
                label = { Text(folderDisplayName(folder)) }
            )
        }
    }
}

@Composable
private fun folderDisplayName(folder: String): String = when (folder) {
    FileFolders.UPLOAD -> stringResource(R.string.setting_files_page_folder_upload)
    UNUSED_ATTACHMENTS -> stringResource(R.string.setting_files_page_folder_unused)
    else -> folder
}

@Composable
private fun FileItem(
    file: ManagedFileEntity,
    fileOnDisk: File,
    showReference: Boolean,
    reference: ManagedFileWithReference,
    onReference: (conversationId: String, nodeId: String) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var showImagePreview by remember { mutableStateOf(false) }
    if (showImagePreview) {
        ImagePreviewDialog(images = listOf(fileOnDisk.toUri().toString())) {
            showImagePreview = false
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CustomColors.listItemColors.containerColor),
        onClick = {
            if (file.mimeType.startsWith("image/")) {
                if (fileOnDisk.isFile) showImagePreview = true
            } else {
                openManagedFile(context, fileOnDisk, file.mimeType)
            }
        },
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (file.mimeType.startsWith("image/")) {
                    AsyncImage(
                        model = fileOnDisk,
                        contentDescription = file.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = HugeIcons.Image02,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.mimeType}  ${file.sizeBytes.fileSizeToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showReference && reference.conversationId != null && reference.nodeId != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable {
                                        onReference(reference.conversationId, reference.nodeId)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    HugeIcons.Share04,
                                    contentDescription = stringResource(
                                        R.string.setting_files_page_open_reference
                                    ),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            VerticalDivider(
                                modifier = Modifier.height(22.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable(onClick = onDelete),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.common_delete),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openManagedFile(context: Context, file: File, mimeType: String) {
    runCatching {
        if (!file.isFile) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
