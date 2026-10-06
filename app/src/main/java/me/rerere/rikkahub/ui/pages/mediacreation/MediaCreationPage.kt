package me.rerere.rikkahub.ui.pages.mediacreation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.MediaCreationNode
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

/**
 * 一个媒体创作会话：一条时间线，最新的记录在最下面，紧挨着输入区，上一轮的产出可以直接放回输入区继续用。
 * 再来一次、修改后重新生成的结果留在原来那条记录的位置上，作为它的另一个版本。
 */
@Composable
fun MediaCreationPage(id: String) {
    val vm: MediaCreationVM = koinViewModel(parameters = { parametersOf(id) })
    val navController = LocalNavController.current
    val toaster = LocalToaster.current

    val settings by vm.settings.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val uploadConfigured by vm.uploadConfigured.collectAsStateWithLifecycle()
    val recentOutputs by vm.recentOutputs.collectAsStateWithLifecycle()

    var showUploadDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MediaCreationEvent.Error -> toaster.show(event.message, type = ToastType.Error)
                MediaCreationEvent.UploadNotConfigured -> showUploadDialog = true
                MediaCreationEvent.SessionGone -> navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = session?.title?.ifBlank { null } ?: stringResource(R.string.media_creation_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (session != null) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(HugeIcons.PencilEdit01, contentDescription = stringResource(R.string.common_rename))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // 底部的安全区和键盘留给输入区自己处理，它的背景要一直铺到屏幕底边
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                MediaCreationTimeline(
                    vm = vm,
                    editingNodeId = draft.editingNodeId,
                    hasModels = settings.mediaGenerationProviders.any { it.models.isNotEmpty() },
                    onOpenMediaSettings = { navController.navigate(Screen.SettingMedia) },
                )
            }
            MediaCreationComposer(
                vm = vm,
                draft = draft,
                selection = selection,
                providers = settings.mediaGenerationProviders,
                recentOutputs = recentOutputs,
                uploadConfigured = uploadConfigured,
                onOpenMediaSettings = { navController.navigate(Screen.SettingMedia) },
            )
        }
    }

    RikkaConfirmDialog(
        show = showUploadDialog,
        title = stringResource(R.string.media_creation_page_upload_dialog_title),
        confirmText = stringResource(R.string.media_creation_page_go_configure),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            showUploadDialog = false
            navController.navigate(Screen.SettingMedia)
        },
        onDismiss = { showUploadDialog = false },
    ) {
        Text(stringResource(R.string.media_creation_page_upload_dialog_text))
    }

    if (showRenameDialog) {
        RenameSessionDialog(
            initialTitle = session?.title.orEmpty(),
            onConfirm = { title ->
                vm.renameSession(title)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    RikkaConfirmDialog(
        show = showDeleteDialog,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            showDeleteDialog = false
            vm.deleteSession()
        },
        onDismiss = { showDeleteDialog = false },
    ) {
        Text(
            stringResource(
                if ((session?.activeCount ?: 0) > 0) {
                    R.string.media_creation_page_delete_session_active_text
                } else {
                    R.string.media_creation_page_delete_session_text
                },
                session?.title?.ifBlank { null } ?: stringResource(R.string.media_creation_page_new_session),
                session?.nodeCount ?: 0,
            )
        )
    }
}

@Composable
private fun RenameSessionDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.media_creation_page_rename_session)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) {
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
private fun MediaCreationTimeline(
    vm: MediaCreationVM,
    editingNodeId: Uuid?,
    hasModels: Boolean,
    onOpenMediaSettings: () -> Unit,
) {
    val nodes = vm.nodes.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    // 列表是倒序排布的，第 0 项就是最下面那条最新的记录。停在底部时，新记录出现后跟着露出来；
    // 往上翻看旧记录时不打扰。
    val newestId = nodes.newestId()
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }
    // 自己提交的记录无论当前翻到哪里都滚过去；新版本出现在原来那一项上，它还在屏幕里时不动
    LaunchedEffect(vm) {
        vm.submitted.collect { submitted ->
            val key = submitted.nodeId.toString()
            if (listState.layoutInfo.visibleItemsInfo.none { it.key == key }) {
                listState.scrollToItem(submitted.position)
            }
        }
    }

    if (nodes.loadState.refresh is LoadState.NotLoading && nodes.itemCount == 0) {
        TimelineEmpty(hasModels = hasModels, onOpenMediaSettings = onOpenMediaSettings)
        return
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = nodes.itemCount,
            // 以项为单位：切换版本时原地换内容，不会被当成另一项
            key = nodes.itemKey { it.id.toString() },
        ) { index ->
            val node = nodes[index]
            if (node != null) {
                MediaCreationRecordItem(node = node, editing = node.id == editingNodeId, vm = vm)
            } else {
                // 还没加载到的记录先占位，保证列表总长度和滚动位置稳定
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {}
            }
        }
    }
}

private fun LazyPagingItems<MediaCreationNode>.newestId(): Uuid? =
    if (itemCount > 0) peek(0)?.id else null

@Composable
private fun TimelineEmpty(
    hasModels: Boolean,
    onOpenMediaSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hasModels) {
            Text(
                text = stringResource(R.string.media_creation_page_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = stringResource(R.string.media_creation_page_no_providers),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenMediaSettings) {
                Text(stringResource(R.string.media_creation_page_go_configure))
            }
        }
    }
}
