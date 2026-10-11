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
import me.rerere.hugeicons.stroke.Sorting01
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.fts.MessagePreviewKey
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.files.CasAttachment
import me.rerere.rikkahub.data.files.ChatAttachmentEntry
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppLoadingIndicator
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.components.ui.MessageSearchResultCard
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.compose.koinInject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid
import me.rerere.rikkahub.ui.components.ui.bottomSheetMaxHeight

private const val UNUSED_ATTACHMENTS = "unused_attachments"

/** 附件排序方式在整个应用启动期间有效, 进程重启后回到默认值。 */
private var sessionAttachmentSortOrder by mutableStateOf(AttachmentSortOrder.LATEST_REFERENCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingFilesPage(
    filesManager: FilesManager = koinInject(),
    conversationRepo: ConversationRepository = koinInject(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val navController = LocalNavController.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val assistantNames = remember(settings.assistants) {
        settings.assistants.associate { it.id.toString() to it.name }
    }

    // 预先获取字符串资源
    val deletedToast = stringResource(R.string.setting_files_page_deleted_toast)
    val deleteFailedToast = stringResource(R.string.setting_files_page_delete_failed_toast)
    val cleanedToast = stringResource(R.string.setting_files_page_cleaned_toast)
    val cleanFailedToast = stringResource(R.string.setting_files_page_clean_failed_toast)

    var selectedFolder by remember { mutableStateOf(FileFolders.UPLOAD) }
    val sortOrder = sessionAttachmentSortOrder
    var pendingDelete by remember { mutableStateOf<CasAttachment?>(null) }
    var referenceAttachment by remember { mutableStateOf<CasAttachment?>(null) }
    var showCleanSheet by remember { mutableStateOf(false) }
    var selectedCleanRange by remember { mutableStateOf(CleanRange.DAYS_30) }
    var showRebuildDialog by remember { mutableStateOf(false) }
    var loadedFiles by remember { mutableStateOf<List<CasAttachment>?>(null) }
    var isRebuilding by remember { mutableStateOf(false) }
    LaunchedEffect(filesManager) {
        loadedFiles = filesManager.listCasAttachments()
    }
    val hasUnusedAttachments = loadedFiles?.any { it.references.isEmpty() } == true
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
    // 切换排序后回到顶部, 否则列表会停留在切换前的索引位置
    LaunchedEffect(sortOrder) {
        gridState.scrollToItem(0)
    }
    val files = loadedFiles.orEmpty()
        .filter {
            if (selectedFolder == UNUSED_ATTACHMENTS) it.references.isEmpty() else it.references.isNotEmpty()
        }
        .sortedWith(
            when (sortOrder) {
                // 两种排序都把较新的卡片放在上方, 只是取每个附件的“时间”不同:
                // 最新引用取它在 managed_files 里最晚的登记时间, 最旧引用取最早的登记时间
                AttachmentSortOrder.LATEST_REFERENCE ->
                    compareByDescending<CasAttachment> { it.latestManagedAt }
                AttachmentSortOrder.OLDEST_REFERENCE ->
                    compareByDescending<CasAttachment> { it.oldestManagedAt }
            }
        )
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
                                loadedFiles = filesManager.listCasAttachments(forceRebuild = true)
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
                            val ok = filesManager.deleteCasAttachments(listOf(target.sha256))
                            if (ok) {
                                toaster.show(deletedToast)
                                loadedFiles = loadedFiles?.filterNot { it.sha256 == target.sha256 }
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

    if (referenceAttachment != null) {
        val target = referenceAttachment!!
        UsedByMessagesDialog(
            attachment = target,
            assistantNames = assistantNames,
            loadPreviews = conversationRepo::getMessagePreviews,
            loadDisplayNames = filesManager::uploadDisplayNames,
            onDismiss = { referenceAttachment = null },
            onOpenMessage = { reference ->
                referenceAttachment = null
                navigateToChatPage(
                    navController,
                    chatId = Uuid.parse(reference.conversationId),
                    nodeId = Uuid.parse(reference.nodeId),
                )
            },
        )
    }

    if (showCleanSheet) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        ModalBottomSheet(containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showCleanSheet = false },
            sheetState = sheetState,
        ) {
            CleanFilesSheet(
                selectedRange = selectedCleanRange,
                onRangeSelected = { selectedCleanRange = it },
                onClean = {
                    showCleanSheet = false
                    scope.launch {
                        val cutoff = selectedCleanRange.days?.let { days ->
                            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
                        }
                        val ok = if (cutoff == null && selectedFolder != UNUSED_ATTACHMENTS) {
                            filesManager.deleteAllCasAttachments()
                        } else {
                            filesManager.deleteCasAttachments(
                                files
                                    .filter { attachment -> cutoff == null || attachment.latestManagedAt < cutoff }
                                    .map { it.sha256 }
                            )
                        }
                        loadedFiles = filesManager.listCasAttachments()
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
                    AttachmentSortMenu(
                        sortOrder = sortOrder,
                        onSortOrderChange = { sessionAttachmentSortOrder = it },
                    )
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
                        items(files, key = { it.sha256 }) { item ->
                            FileItem(
                                attachment = item,
                                fileOnDisk = item.file,
                                showReference = selectedFolder != UNUSED_ATTACHMENTS,
                                onReference = {
                                    // 只有一条引用时直接跳到消息, 多条引用才弹窗选择
                                    val single = item.references.singleOrNull()
                                    if (single != null) {
                                        navigateToChatPage(
                                            navController,
                                            chatId = Uuid.parse(single.conversationId),
                                            nodeId = Uuid.parse(single.nodeId),
                                        )
                                    } else {
                                        referenceAttachment = item
                                    }
                                },
                                onDelete = { pendingDelete = item },
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

/** “存储管理”页的附件排序方式, 在整个应用启动期间有效。 */
private enum class AttachmentSortOrder {
    /** 取每个附件最晚的 `managed_files` 登记时间作为它的时间, 较新的卡片在上方。 */
    LATEST_REFERENCE,

    /** 取每个附件最早的 `managed_files` 登记时间作为它的时间, 较新的卡片在上方。 */
    OLDEST_REFERENCE,
}

@Composable
private fun AttachmentSortMenu(
    sortOrder: AttachmentSortOrder,
    onSortOrderChange: (AttachmentSortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = HugeIcons.Sorting01,
                contentDescription = stringResource(R.string.setting_files_page_sort),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = rememberScreenEdgeCornerShape(),
        ) {
            AttachmentSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (order) {
                                    AttachmentSortOrder.LATEST_REFERENCE ->
                                        R.string.setting_files_page_sort_latest_reference
                                    AttachmentSortOrder.OLDEST_REFERENCE ->
                                        R.string.setting_files_page_sort_oldest_reference
                                }
                            )
                        )
                    },
                    leadingIcon = {
                        RadioButton(
                            selected = order == sortOrder,
                            onClick = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortOrderChange(order)
                    },
                )
            }
        }
    }
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
            .bottomSheetMaxHeight()
            .padding(horizontal = 24.dp)
            .padding(bottom = 8.dp),
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

/**
 * 展示某个附件被哪些消息使用: 每条引用先显示该软链接对应的展示名 (缺失时退回软链接文件名),
 * 再显示与搜索结果一致的消息预览卡片, 点击卡片跳转到对应消息。
 */
@Composable
private fun UsedByMessagesDialog(
    attachment: CasAttachment,
    assistantNames: Map<String, String>,
    loadPreviews: suspend (List<MessagePreviewKey>) -> Map<MessagePreviewKey, MessageSearchResult>,
    loadDisplayNames: suspend () -> Map<String, String>,
    onDismiss: () -> Unit,
    onOpenMessage: (ChatAttachmentEntry) -> Unit,
) {
    val references = attachment.references
    var previews by remember(attachment) {
        mutableStateOf<Map<MessagePreviewKey, MessageSearchResult>?>(null)
    }
    var displayNames by remember(attachment) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    LaunchedEffect(attachment) {
        displayNames = loadDisplayNames()
        previews = loadPreviews(
            references.map { MessagePreviewKey(it.conversationId, it.nodeId, it.messageId) }
        )
    }
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_files_page_used_by_messages)) },
        text = {
            val loaded = previews
            if (loaded == null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AppLoadingIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    references.forEachIndexed { index, reference ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        val key = MessagePreviewKey(
                            conversationId = reference.conversationId,
                            nodeId = reference.nodeId,
                            messageId = reference.messageId,
                        )
                        Text(
                            text = displayNames[reference.fileName] ?: reference.fileName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        loaded[key]?.let { preview ->
                            MessageSearchResultCard(
                                result = preview,
                                assistantName = preview.assistantId?.let { assistantNames[it] },
                                onClick = { onOpenMessage(reference) },
                                snippetMaxLines = 3,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@Composable
private fun FileItem(
    attachment: CasAttachment,
    fileOnDisk: File,
    showReference: Boolean,
    onReference: () -> Unit,
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
            if (attachment.mimeType.startsWith("image/")) {
                if (fileOnDisk.isFile) showImagePreview = true
            } else {
                openManagedFile(context, fileOnDisk, attachment.mimeType)
            }
        },
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (attachment.mimeType.startsWith("image/")) {
                    AsyncImage(
                        model = fileOnDisk,
                        contentDescription = attachment.displayName,
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
                    text = attachment.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // 没有可用的引用展示名时标题已经退回 sha256 缩写, 不再重复显示四位缩写
                if (attachment.referenceDisplayNames.isNotEmpty()) {
                    Text(
                        text = attachment.sha256Short,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${attachment.mimeType}  ${attachment.sizeBytes.fileSizeToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showReference && attachment.references.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable(onClick = onReference),
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
