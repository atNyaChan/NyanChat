package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clean
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.FileAudio
import me.rerere.hugeicons.stroke.FileVideo
import me.rerere.hugeicons.stroke.FolderOpen
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.Sorting01
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import me.rerere.ui.toast.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.FileUsage
import me.rerere.rikkahub.data.db.entity.FileUsageType
import me.rerere.rikkahub.data.db.fts.MessagePreviewKey
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.files.CasAttachment
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.attachmentShaFromUrl
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.components.ai.CountBadge
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppLoadingIndicator
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.components.ui.MessageSearchResultCard
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings
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
    val hasUnusedAttachments = loadedFiles?.any { it.isUnused } == true
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
            // 消息与设置类（头像/背景、用户头像）使用都算引用；“未使用”只放完全没有引用的
            if (selectedFolder == UNUSED_ATTACHMENTS) it.isUnused else it.hasReferences
        }
        .sortedWith(
            when (sortOrder) {
                // 两种排序都把较新的卡片放在上方, 只是取每个附件的“时间”不同:
                // 最新引用取它在 usages 里最晚的使用时间, 最旧引用取最早的使用时间
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
                            // 该内容可能被助手头像/背景、用户头像引用：先清掉这些引用，避免删文件后设置悬空
                            clearSettingsAttachmentReferences(settingsStore, setOf(target.sha256))
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
            onDismiss = { referenceAttachment = null },
            onOpenMessage = { reference ->
                referenceAttachment = null
                val key = reference.messagePreviewKey() ?: return@UsedByMessagesDialog
                navigateToChatPage(
                    navController,
                    chatId = Uuid.parse(key.conversationId),
                    nodeId = Uuid.parse(key.nodeId),
                )
            },
            onOpenAssistantSettings = { assistantId ->
                referenceAttachment = null
                navController.navigate(Screen.AssistantBasic(assistantId))
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
                        // 全量清理时被删的是当前列表里的全部内容，否则是时间范围内那份
                        val targets = if (cutoff == null && selectedFolder != UNUSED_ATTACHMENTS) {
                            null
                        } else {
                            files.filter { attachment -> cutoff == null || attachment.latestManagedAt < cutoff }
                        }
                        // 清理掉被助手头像/背景、用户头像引用的内容前先清掉设置里的引用
                        clearSettingsAttachmentReferences(settingsStore, (targets ?: files).map { it.sha256 }.toSet())
                        val ok = if (targets == null) {
                            filesManager.deleteAllCasAttachments()
                        } else {
                            filesManager.deleteCasAttachments(targets.map { it.sha256 })
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
                    ExtensionEmptyState(
                        message = stringResource(R.string.setting_files_page_no_files),
                        icon = HugeIcons.FolderOpen,
                    )
                } else {
                    LazyVerticalStaggeredGrid(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
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
                                showReference = item.hasReferences,
                                onReference = {
                                    // 被头像/背景引用的附件永远先弹出使用列表;
                                    // 只有一条消息引用且没有设置类使用时才直接跳到消息
                                    val single = item.references.singleOrNull()?.messagePreviewKey()
                                    if (single != null && item.settingsUsages.isEmpty()) {
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
    /** 取每个附件最新一次使用的消息时间作为它的时间, 较新的卡片在上方。 */
    LATEST_REFERENCE,

    /** 取每个附件最早一次使用的消息时间作为它的时间, 较新的卡片在上方。 */
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
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_files_page_clean_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.setting_files_page_clean_range_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CardGroup(modifier = Modifier.selectableGroup()) {
            CleanRange.entries.forEach { range ->
                val selected = selectedRange == range
                item(
                    onClick = { onRangeSelected(range) },
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    },
                    headlineContent = {
                        Text(
                            range.days?.let {
                                stringResource(R.string.setting_files_page_clean_older_than_days, it)
                            } ?: stringResource(R.string.setting_files_page_clean_all)
                        )
                    },
                    // 点整行选中，单选圈自己不接收点击
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                )
            }
        }

        Button(
            onClick = onClean,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(
                imageVector = HugeIcons.Clean,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        folders.forEach { folder ->
            val selected = selectedFolder == folder
            ToggleButton(
                checked = selected,
                onCheckedChange = { if (!selected) onFolderSelected(folder) },
                modifier = Modifier.semantics { role = Role.Tab },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Text(
                    text = folderDisplayName(folder),
                    maxLines = 1,
                    softWrap = false,
                )
            }
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
 * 展示某个附件被哪些使用引用: 每条引用先显示该次使用的展示名 (缺失时退回 `uuid.ext` 文件名),
 * 再显示与搜索结果一致的消息预览卡片, 点击卡片跳转到对应消息;
 * 被助手头像/背景或用户头像引用的内容在文件名下方用描边卡片列出使用位置 (没有消息引用时也能弹窗)。
 */
@Composable
private fun UsedByMessagesDialog(
    attachment: CasAttachment,
    assistantNames: Map<String, String>,
    loadPreviews: suspend (List<MessagePreviewKey>) -> Map<MessagePreviewKey, MessageSearchResult>,
    onDismiss: () -> Unit,
    onOpenMessage: (FileUsage) -> Unit,
    onOpenAssistantSettings: (String) -> Unit,
) {
    val references = attachment.references
    val settingsUsages = attachment.settingsUsages
    var previews by remember(attachment) {
        mutableStateOf<Map<MessagePreviewKey, MessageSearchResult>?>(null)
    }
    LaunchedEffect(attachment) {
        previews = loadPreviews(references.mapNotNull { it.messagePreviewKey() })
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
                // 新文件显示在上面：按引用消息的时间从新到旧排列
                val ordered = references.sortedByDescending { reference ->
                    reference.messagePreviewKey()?.let { loaded[it] }?.timeAt?.toEpochMilli()
                        ?: Long.MIN_VALUE
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 设置类使用项：在文件名下面用描边卡片给出被哪个助手/用户用作头像或背景
                    settingsUsages.forEachIndexed { index, usage ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        Text(
                            text = attachment.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // 助手头像/背景可以直接跳到对应助手的设置页；用户头像没有独立设置页，不提供跳转
                        val usageAssistantId = usage.details["assistantId"]
                        val openAssistantSettings: (() -> Unit)? = if (
                            usageAssistantId != null &&
                            assistantNames.containsKey(usageAssistantId) &&
                            (usage.type == FileUsageType.ASSISTANT_AVATAR ||
                                usage.type == FileUsageType.ASSISTANT_BACKGROUND)
                        ) {
                            { onOpenAssistantSettings(usageAssistantId) }
                        } else {
                            null
                        }
                        OutlinedItemCard(onClick = openAssistantSettings) {
                            Text(
                                text = settingsUsageDescription(usage, assistantNames),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    ordered.forEachIndexed { index, reference ->
                        if (settingsUsages.isNotEmpty() || index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        val key = reference.messagePreviewKey() ?: return@forEachIndexed
                        Text(
                            text = reference.displayName.ifBlank { reference.filename },
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

/** 设置类使用记录的说明文本：助手头像/背景带上助手名，取不到时退回“未知助手”。 */
@Composable
private fun settingsUsageDescription(usage: FileUsage, assistantNames: Map<String, String>): String =
    when (usage.type) {
        FileUsageType.ASSISTANT_AVATAR -> stringResource(
            R.string.setting_files_page_used_by_assistant_avatar,
            usage.details["assistantId"]?.let(assistantNames::get)
                ?: stringResource(R.string.setting_files_page_unknown_assistant),
        )

        FileUsageType.ASSISTANT_BACKGROUND -> stringResource(
            R.string.setting_files_page_used_by_assistant_background,
            usage.details["assistantId"]?.let(assistantNames::get)
                ?: stringResource(R.string.setting_files_page_unknown_assistant),
        )

        FileUsageType.USER_AVATAR -> stringResource(R.string.setting_files_page_used_by_user_avatar)
        else -> usage.type
    }

/**
 * 从设置里清掉指向这些 sha256 的助手头像/背景与用户头像。
 * 存储管理删文件前先调用，避免设置仍指向已被删除的附件。
 */
private suspend fun clearSettingsAttachmentReferences(settingsStore: SettingsStore, shas: Set<String>) {
    if (shas.isEmpty()) return
    fun referencesSha(url: String): Boolean = attachmentShaFromUrl(url)?.let(shas::contains) == true
    settingsStore.update { settings ->
        val assistants = settings.assistants.map { assistant ->
            val clearAvatar = assistant.avatar.let { it is Avatar.Image && referencesSha(it.url) }
            val clearBackground = assistant.background?.let(::referencesSha) == true
            if (clearAvatar || clearBackground) {
                assistant.copy(
                    avatar = if (clearAvatar) Avatar.Dummy else assistant.avatar,
                    background = if (clearBackground) null else assistant.background,
                )
            } else {
                assistant
            }
        }
        val userAvatar = settings.displaySetting.userAvatar
        val clearUserAvatar = userAvatar is Avatar.Image && referencesSha(userAvatar.url)
        val displaySetting = if (clearUserAvatar) {
            settings.displaySetting.copy(userAvatar = Avatar.Dummy)
        } else {
            settings.displaySetting
        }
        // 没有指向被删内容时保留原实例，避免一次无意义的设置写盘
        if (assistants == settings.assistants && displaySetting == settings.displaySetting) {
            settings
        } else {
            settings.copy(assistants = assistants, displaySetting = displaySetting)
        }
    }
}

private val FileItemCorner = 20.dp
private val FileItemPreviewInset = 4.dp

@Composable
private fun FileItem(
    attachment: CasAttachment,
    fileOnDisk: File,
    showReference: Boolean,
    onReference: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val codeFontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings
    val displayNameIsSha256 = attachment.displayName == attachment.sha256Short
    var showImagePreview by remember { mutableStateOf(false) }
    if (showImagePreview) {
        ImagePreviewDialog(images = listOf(fileOnDisk.toUri().toString())) {
            showImagePreview = false
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FileItemCorner),
        color = CustomColors.listItemColors.containerColor,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .padding(FileItemPreviewInset)
                    // 预览图内缩一圈，圆角跟着减小，和卡片外沿保持同心
                    .clip(RoundedCornerShape(FileItemCorner - FileItemPreviewInset))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (attachment.mimeType.startsWith("image/")) {
                    AsyncImage(
                        model = fileOnDisk,
                        contentDescription = attachment.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialShapes.Cookie9Sided.toShape(),
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                attachment.mimeType.startsWith("video/") -> HugeIcons.FileVideo
                                attachment.mimeType.startsWith("audio/") -> HugeIcons.FileAudio
                                else -> HugeIcons.File01
                            },
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 6.dp)
            ) {
                Text(
                    text = attachment.displayName,
                    style = if (displayNameIsSha256) {
                        MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetbrainsMono,
                            fontFeatureSettings = codeFontFeatureSettings,
                        )
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // 没有可用的展示名时标题已经退回 sha256 缩写, 这里仅在展示名不是 sha256 时补一行缩写
                if (!displayNameIsSha256) {
                    Text(
                        text = attachment.sha256Short,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetbrainsMono,
                            fontFeatureSettings = codeFontFeatureSettings,
                        ),
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
                        if (showReference) {
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
                                // 一个附件被不止一处引用时, 在跳转键右上角标注引用次数:
                                // 点击后不会直接跳转, 而是先弹出引用列表供选择
                                val usageCount = attachment.references.size + attachment.settingsUsages.size
                                if (usageCount > 1) {
                                    CountBadge(
                                        count = usageCount,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 2.dp, end = 4.dp),
                                    )
                                }
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

/** 从消息使用记录里取出消息定位；非消息类型或字段缺失时返回 null。 */
private fun FileUsage.messagePreviewKey(): MessagePreviewKey? {
    val conversationId = details["conversationId"] ?: return null
    val nodeId = details["nodeId"] ?: return null
    val messageId = details["messageId"] ?: return null
    return MessagePreviewKey(conversationId, nodeId, messageId)
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
