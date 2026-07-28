package me.rerere.rikkahub.ui.pages.assistant

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Add01
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.EditState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.modifier.onClick
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantImporter
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid
import androidx.compose.foundation.lazy.items as lazyItems

@Composable
fun AssistantPage(vm: AssistantVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val createState = useEditState<Assistant> {
        vm.addAssistant(it)
    }
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 标签过滤状态
    var selectedTagIds by remember { mutableStateOf(emptySet<Uuid>()) }

    // 根据选中的标签过滤助手
    val filteredAssistants = remember(settings.assistants, selectedTagIds) {
        settings.assistants.filter { assistant ->
            val matchesTags = selectedTagIds.isEmpty() ||
                assistant.tags.any { tagId -> tagId in selectedTagIds }
            matchesTags
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(
                        onClick = {
                            createState.open(Assistant())
                        }) {
                        Icon(HugeIcons.Add01, stringResource(R.string.common_add))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(top = 16.dp)
                .consumeWindowInsets(it),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val lazyListState = rememberLazyListState()
            val isFiltering = selectedTagIds.isNotEmpty()
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                if (!isFiltering) {
                    val newAssistants = settings.assistants.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                    vm.updateSettings(settings.copy(assistants = newAssistants))
                }
            }
            val haptic = LocalHapticFeedback.current

            // 标签过滤器
            AssistantTagsFilterRow(
                settings = settings,
                vm = vm,
                selectedTagIds = selectedTagIds,
                onUpdateSelectedTagIds = { ids ->
                    selectedTagIds = ids
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = lazyListState,
            ) {
                lazyItems(filteredAssistants, key = { assistant -> assistant.id }) { assistant ->
                    ReorderableItem(
                        state = reorderableState,
                        key = assistant.id,
                    ) { isDragging ->
                        AssistantItem(
                            assistant = assistant,
                            settings = settings,
                            onEdit = {
                                navController.navigate(Screen.AssistantDetail(id = assistant.id.toString()))
                            },
                            modifier = Modifier
                                .scale(if (isDragging) 0.95f else 1f)
                                .fillMaxWidth()
                                .animateItem()
                                .then(
                                    if (!isFiltering) {
                                        Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                            },
                                            onDragStopped = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            }
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }
    }

    AssistantCreationSheet(createState, settings.assistants)
}

@Composable
private fun AssistantTagsFilterRow(
    settings: Settings,
    vm: AssistantVM,
    selectedTagIds: Set<Uuid>,
    onUpdateSelectedTagIds: (Set<Uuid>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    if (settings.assistantTags.isNotEmpty()) {
        val tagsListState = rememberLazyListState()
        val tagsReorderableState = rememberReorderableLazyListState(tagsListState) { from, to ->
            val newTags = settings.assistantTags.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            vm.updateSettings(settings.copy(assistantTags = newTags))
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
            state = tagsListState
        ) {
            lazyItems(items = settings.assistantTags, key = { tag -> tag.id }) { tag ->
                ReorderableItem(
                    state = tagsReorderableState, key = tag.id
                ) { isDragging ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            onClick = {
                                onUpdateSelectedTagIds(
                                    if (tag.id in selectedTagIds) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                )
                            },
                            label = {
                                Text(tag.name)
                            },
                            selected = tag.id in selectedTagIds,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .scale(if (isDragging) 0.95f else 1f)
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    },
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantCreationSheet(
    state: EditState<Assistant>,
    existingAssistants: List<Assistant>,
) {
    var showCopyPicker by remember { mutableStateOf(false) }
    state.EditStateContent { assistant, update ->
        ModalBottomSheet(
            onDismissRequest = {
                state.dismiss()
            },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            dragHandle = {},
            sheetGesturesEnabled = false
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FormItem(
                        label = {
                            Text(stringResource(R.string.assistant_page_name))
                        },
                    ) {
                        OutlinedTextField(
                            value = assistant.name, onValueChange = {
                                update(
                                    assistant.copy(
                                        name = it
                                    )
                                )
                            }, modifier = Modifier.fillMaxWidth()
                        )
                    }

                    AssistantImporter(
                        onUpdate = {
                            update(it)
                            state.confirm()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { showCopyPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(HugeIcons.Copy01, contentDescription = null)
                        Text(
                            stringResource(R.string.assistant_page_copy_existing),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            state.dismiss()
                        }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(
                        onClick = {
                            state.confirm()
                        }) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
    }
    if (showCopyPicker) {
        AlertDialog(
            onDismissRequest = { showCopyPicker = false },
            title = { Text(stringResource(R.string.assistant_page_copy_existing)) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    lazyItems(existingAssistants, key = { it.id }) { source ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            onClick = {
                                val currentName = state.currentState?.name.orEmpty()
                                state.currentState = source.copy(
                                    id = Uuid.random(),
                                    name = currentName,
                                    avatar = if (source.avatar is me.rerere.rikkahub.data.model.Avatar.Image) {
                                        me.rerere.rikkahub.data.model.Avatar.Dummy
                                    } else {
                                        source.avatar
                                    },
                                )
                                showCopyPicker = false
                                state.confirm()
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                UIAvatar(
                                    name = source.name.ifBlank {
                                        stringResource(R.string.assistant_page_default_assistant)
                                    },
                                    value = source.avatar,
                                    invertDefaultAvatarInDarkMode = true,
                                    modifier = Modifier.size(32.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        source.name.ifBlank {
                                            stringResource(R.string.assistant_page_default_assistant)
                                        }
                                    )
                                    if (source.tags.isNotEmpty()) {
                                        Text(
                                            stringResource(
                                                R.string.assistant_page_copy_picker_tag_count,
                                                source.tags.size,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCopyPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun AssistantItem(
    assistant: Assistant,
    settings: Settings,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onEdit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIAvatar(
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                value = assistant.avatar,
                invertDefaultAvatarInDarkMode = true,
                modifier = Modifier
                    .size(40.dp)
                    .heroAnimation("assistant_${assistant.id}")
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {

                Text(
                    text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (assistant.tags.isNotEmpty()) {
                        assistant.tags.take(2).fastForEach { tagId ->
                            val tag = settings.assistantTags.find { it.id == tagId }
                                ?: return@fastForEach
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    text = tag.name,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (assistant.tags.size > 2) {
                            Text(
                                text = "+${assistant.tags.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
