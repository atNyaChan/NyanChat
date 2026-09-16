package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.MinusSign
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.ScreenCornerAdaptation
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.getQuickMessagesOfAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.service.MessageQueueState
import me.rerere.rikkahub.service.QueuedMessage
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionContext
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionItem
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionList
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.components.hazeBackgroundEffect
import me.rerere.rikkahub.ui.components.toRoundedCornerShape
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.wordCount
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun ChatInput(
    state: ChatInputState,
    requestWordCount: Int?,
    requestToolCount: Int,
    requestFileCount: Int,
    onRequestWordCountRefresh: () -> Unit,
    loading: Boolean,
    settings: Settings,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    onUpdateChatModel: (Model) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    filesExpanded: Boolean,
    onFilesExpandedChange: (Boolean) -> Unit,
    filesPanel: @Composable () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    messageQueue: MessageQueueState = MessageQueueState(),
    onRemoveQueuedMessage: (Uuid) -> Unit = {},
    onBeginEditQueuedMessage: (Uuid) -> QueuedMessage? = { null },
    onFinishEditQueuedMessage: (Uuid, List<UIMessagePart>?) -> Unit = { _, _ -> },
    onResumeMessageQueue: () -> Unit = {},
    onStartVoiceMode: (() -> Unit)? = null,
    voiceState: VoiceSessionState = VoiceSessionState(),
    onStopVoiceMode: () -> Unit = {},
) {
    val toaster = LocalToaster.current
    val assistant = settings.getCurrentAssistant()
    val inputWordCount = state.getContents().sumOf { part ->
        (part as? UIMessagePart.Text)?.text?.wordCount() ?: 0
    }
    val documents = state.messageContent.filterIsInstance<UIMessagePart.Document>()
    val documentWordCounts by produceState<Map<String, Int?>>(
        initialValue = emptyMap(),
        documents,
        settings.displaySetting.showTokenUsage,
    ) {
        if (!settings.displaySetting.showTokenUsage) return@produceState
        val counts = mutableMapOf<String, Int?>()
        documents.forEach { document ->
            counts[document.url] = DocumentAsPromptTransformer
                .extractDocumentText(document)
                ?.wordCount()
            value = counts.toMap()
        }
    }
    val displayedAttachmentCount = state.messageContent.count { part ->
        part !is UIMessagePart.Document ||
            (part.url in documentWordCounts && documentWordCounts[part.url] == null)
    }
    val hazeTintColor = MaterialTheme.colorScheme.surface

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val density = LocalDensity.current
    // Unlike isImeVisible, the target changes as soon as the IME animation starts.
    val imeTargetVisible = WindowInsets.imeAnimationTarget.getBottom(density) > 0
    val containerShape = rememberScreenEdgeCornerShape(
        horizontalInset = 8.dp,
        bottomInset = 8.dp,
        enabled = settings.displaySetting.screenCornerAdaptation == ScreenCornerAdaptation.INPUT_ONLY ||
            settings.displaySetting.screenCornerAdaptation == ScreenCornerAdaptation.ALL,
    )

    var isExpanded by remember { mutableStateOf(false) }
    var collapsedHeightPx by remember { mutableStateOf(0f) }
    // The files panel is measured separately so that collapsing the expanded input only shrinks the
    // text area and keeps the files panel visible, rather than collapsing the whole container and
    // then re-expanding it.
    var filesPanelHeightPx by remember { mutableStateOf(0f) }
    val collapsedWithPanelPx = collapsedHeightPx + if (filesExpanded) filesPanelHeightPx else 0f
    val collapsedHeight = with(density) { collapsedWithPanelPx.toDp() }

    // The expanded input is pinned at a fixed distance from the top of the screen; only its
    // bottom edge moves (following the IME/build-nav bars), so the top edge never shifts with
    // the keyboard. This mirrors Agora's fillMaxHeight + fixed-top-offset anchoring.
    val screenHeightPx = LocalContext.current.resources.displayMetrics.heightPixels
    val topOffsetPx = screenHeightPx * 0.05f
    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    val imeInsetPx = WindowInsets.ime.getBottom(density)
    val bottomPadPx = with(density) { 8.dp.toPx() }
    val fullHeightPx = (
        screenHeightPx
            - topOffsetPx
            - navBarPx
            - imeInsetPx
            - bottomPadPx
        ).coerceAtLeast(collapsedHeightPx)
    val fullHeight = with(density) { fullHeightPx.toDp() }
    val bottomInset = with(density) { (navBarPx + imeInsetPx + bottomPadPx).toDp() }

    // Only expand/collapse animates the height; changes caused by the IME are reflected live in
    // `fullHeight` so the bottom tracks the keyboard instantly while the top stays pinned.
    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(300),
        label = "chatInputExpand",
    )
    val animatedHeight = collapsedHeight + (fullHeight - collapsedHeight) * expandProgress
    val lockHeight = isExpanded || expandProgress > 0f

    fun sendMessage() {
        isExpanded = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onLongSendClick()
    }

    val asr = LocalASRState.current
    val asrState by asr.state.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val soundEffectPlayer: SoundEffectPlayer = koinInject()
    LaunchedEffect(Unit) {
        soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
    }
    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)
    var asrBaseText by remember { mutableStateOf("") }
    LaunchedEffect(asrState.status) {
        when (asrState.status) {
            ASRStatus.Listening -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                soundEffectPlayer.play(R.raw.asr_start)
            }

            ASRStatus.Stopping -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                soundEffectPlayer.play(R.raw.asr_stop)
            }

            else -> {}
        }
    }
    LaunchedEffect(asrState.errorMessage) {
        asrState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            toaster.show(message = message, type = ToastType.Error)
        }
    }

    Surface(
        color = Color.Transparent,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = bottomInset),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (lockHeight) Modifier.height(animatedHeight) else Modifier)
                    .onSizeChanged {
                        if (!isExpanded && !lockHeight && !filesExpanded) {
                            collapsedHeightPx = it.height.toFloat()
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            MessageQueuePanel(
                state = messageQueue,
                settings = settings,
                onRemove = onRemoveQueuedMessage,
                onBeginEdit = onBeginEditQueuedMessage,
                onFinishEdit = onFinishEditQueuedMessage,
                onResume = onResumeMessageQueue,
            )
            Surface(
                modifier = Modifier
                    .then(if (lockHeight) Modifier.weight(1f) else Modifier)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 6.dp,
                        shape = containerShape,
                        clip = false,
                        spotColor = Color.Transparent,
                    )
                    .clip(containerShape)
                    .then(
                        Modifier.hazeBackgroundEffect(
                            effectType = settings.displaySetting.backgroundEffectType,
                            hazeState = hazeState,
                            tintColor = hazeTintColor,
                            shape = containerShape.toRoundedCornerShape(),
                        )
                    ),
                shape = containerShape,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                color = if (settings.displaySetting.backgroundEffectType != BackgroundEffectType.OFF) Color.Transparent else hazeTintColor,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (state.isEditing()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = stringResource(R.string.editing))
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    imageVector = HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.cancel_edit),
                                    modifier = Modifier.clickable { state.clearInput() }
                                )
                            }
                        }
                    }
                    if (voiceState.phase != VoicePhase.Off) {
                        VoiceModeRow(
                            state = voiceState,
                            onStop = onStopVoiceMode,
                            onRetry = { onStartVoiceMode?.invoke() },
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    if (state.messageContent.isNotEmpty()) {
                        MediaFileInputRow(
                            state = state,
                            documentWordCounts = documentWordCounts,
                        )
                    }

                    TextInputRow(
                        state = state,
                        completionProviders = completionProviders,
                        onFocusChanged = { focused ->
                            if (focused) {
                                if (filesExpanded) onFilesExpandedChange(false)
                                onRequestWordCountRefresh()
                            }
                        },
                        onSendMessage = { sendMessage() },
                        expandedFill = lockHeight,
                        onToggleExpand = { isExpanded = !isExpanded },
                        modifier = if (lockHeight) Modifier.weight(1f) else Modifier,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            // Model Picker
                            ModelSelector(
                                modelId = assistant.chatModelId ?: settings.chatModelId,
                                providers = settings.providers,
                                onSelect = {
                                    onUpdateChatModel(it)
                                },
                                type = ModelType.CHAT,
                                onlyIcon = true,
                                modifier = Modifier,
                            )

                            // Reasoning
                            val model = settings.getCurrentChatModel()
                            if (model?.abilities?.contains(ModelAbility.REASONING) == true) {
                                ReasoningButton(
                                    reasoningLevel = assistant.reasoningLevel,
                                    onUpdateReasoningLevel = {
                                        onUpdateAssistant(assistant.copy(reasoningLevel = it))
                                    },
                                    onlyIcon = true,
                                )
                            }

                        }

                        requestWordCount?.takeIf {
                            filesExpanded || imeTargetVisible || inputWordCount > 0 || displayedAttachmentCount > 0
                        }?.let { count ->
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.Upload02,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${inputWordCount.formatNumber()} word (${count.formatNumber()} total)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // 文件数 = 输入框待发送的附件 + 请求上下文中已有消息的附件
                                val totalFileCount = displayedAttachmentCount + requestFileCount
                                if (totalFileCount > 0 || requestToolCount > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (totalFileCount > 0) {
                                            StatCount(
                                                icon = HugeIcons.File02,
                                                count = totalFileCount,
                                                label = "file",
                                            )
                                        }
                                        if (requestToolCount > 0) {
                                            StatCount(
                                                icon = HugeIcons.Tools,
                                                count = requestToolCount,
                                                label = "tool",
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ActionIconButton(
                            onClick = {
                                if (filesExpanded) {
                                    onFilesExpandedChange(false)
                                } else {
                                    focusManager.clearFocus(force = true)
                                    onFilesExpandedChange(true)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (filesExpanded) HugeIcons.MinusSign else HugeIcons.Add01,
                                contentDescription = stringResource(R.string.more_options)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        if (!voiceState.isActive && (asrState.isAvailable || asrState.isRecording)) {
                            AsrButton(
                                state = asrState,
                                onClick = {
                                    when (asrState.status) {
                                        ASRStatus.Listening -> asr.stop()
                                        ASRStatus.Idle, ASRStatus.Error -> {
                                            if (!asrPermission.allRequiredPermissionsGranted) {
                                                asrPermission.requestPermissions()
                                            } else {
                                                asrBaseText = state.textContent.text.toString()
                                                asr.start { transcript ->
                                                    val spacer =
                                                        if (asrBaseText.isBlank() || transcript.isBlank()) "" else " "
                                                    state.setMessageText(asrBaseText + spacer + transcript)
                                                }
                                            }
                                        }

                                        ASRStatus.Connecting, ASRStatus.Stopping -> {}
                                    }
                                }
                            )
                        }

                        if (loading) {
                            KeepScreenOn()
                        }

                        AnimatedVisibility(
                            visible = !asrState.isRecording,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            SendButton(
                                loading = loading,
                                empty = state.isEmpty(),
                                onClick = { sendMessage() },
                                onLongClick = { sendMessageWithoutAnswer() },
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = filesExpanded,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier.onSizeChanged {
                                filesPanelHeightPx = it.height.toFloat()
                            },
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            filesPanel()
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StatCount(
    icon: ImageVector,
    count: Int,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${count.formatNumber()} $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        tonalElevation = 0.dp,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun SendButton(
    loading: Boolean,
    empty: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showStop = loading && empty
    val containerColor = when {
        showStop -> MaterialTheme.colorScheme.errorContainer
        empty -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        showStop -> MaterialTheme.colorScheme.onErrorContainer
        empty -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(30.dp)
            .testTag("chat_send_button")
            .clip(CircleShape)
            .combinedClickable(
                enabled = showStop || !empty,
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = containerColor,
            content = {},
        )
        Icon(
            imageVector = if (showStop) HugeIcons.Cancel01 else HugeIcons.ArrowUp02,
            contentDescription = stringResource(if (showStop) R.string.stop else R.string.send),
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    completionProviders: List<ChatCompletionProvider>,
    onFocusChanged: (Boolean) -> Unit,
    onSendMessage: () -> Unit,
    trailingContent: @Composable () -> Unit = {},
    expandedFill: Boolean = false,
    onToggleExpand: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val filesManager: FilesManager = koinInject()
    val assistant = settings.getCurrentAssistant()
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    Column(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        var isFocused by remember { mutableStateOf(false) }
        var completionList by remember { mutableStateOf<ChatCompletionList?>(null) }
        val receiveContentListener = remember(
            settings.displaySetting.pasteLongTextAsFile, settings.displaySetting.pasteLongTextThreshold
        ) {
            ReceiveContentListener { transferableContent ->
                when {
                    transferableContent.hasMediaType(MediaType.Image) -> {
                        transferableContent.consume { item ->
                            val uri = item.uri
                            if (uri != null) {
                                state.addImages(
                                    filesManager.createChatFilesByContents(
                                        listOf(uri)
                                    )
                                )
                            }
                            uri != null
                        }
                    }

                    settings.displaySetting.pasteLongTextAsFile && transferableContent.hasMediaType(MediaType.Text) -> {
                        transferableContent.consume { item ->
                            val text = item.text?.toString()
                            if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                                val document = filesManager.createChatTextFile(text)
                                state.addFiles(listOf(document))
                                true
                            } else {
                                false
                            }
                        }
                    }

                    else -> transferableContent
                }
            }
        }

        LaunchedEffect(completionProviders, isFocused) {
            if (!isFocused || completionProviders.isEmpty()) {
                completionList = null
                return@LaunchedEffect
            }

            snapshotFlow {
                ChatCompletionContext(
                    text = state.textContent.text.toString(),
                    selection = state.textContent.selection,
                )
            }.collectLatest { context ->
                val lists = completionProviders.mapNotNull { provider ->
                    try {
                        provider.complete(context)
                            ?.takeIf { it.items.isNotEmpty() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                val primary = lists.firstOrNull()
                completionList = primary?.let { list ->
                    val mergedItems = lists
                        .filter { it.replacementRange == list.replacementRange }
                        .flatMap { it.items }
                        .distinctBy { it.label to it.insertText }
                        .sortedWith(
                            compareByDescending<ChatCompletionItem> { it.sortScore }
                                .thenBy { it.label.length }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(8)
                    list.copy(items = mergedItems)
                }
            }
        }

        completionList?.takeIf { it.items.isNotEmpty() }?.let { list ->
            CompletionPopup(
                completionList = list,
                onItemClick = { item ->
                    state.applyCompletion(list.replacementRange, item)
                    completionList = null
                },
            )
        }

        val textScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .then(if (expandedFill) Modifier.weight(1f) else Modifier)
                .fillMaxWidth(),
        ) {
            TextField(
                state = state.textContent,
                scrollState = textScrollState,
                modifier = Modifier
                    .then(if (expandedFill) Modifier.fillMaxHeight() else Modifier)
                    .fillMaxWidth()
                    .testTag("chat_input")
                    .contentReceiver(receiveContentListener)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusChanged(it.isFocused)
                    },
                shape = MaterialTheme.shapes.largeIncreased,
            placeholder = {
                Text(stringResource(R.string.chat_input_placeholder))
            },
            lineLimits = if (expandedFill) {
                TextFieldLineLimits.MultiLine(
                    minHeightInLines = 1,
                    maxHeightInLines = Int.MAX_VALUE,
                )
            } else {
                TextFieldLineLimits.MultiLine(maxHeightInLines = 5)
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default
            ),
            onKeyboardAction = {
                if (settings.displaySetting.sendOnEnter && !state.isEmpty()) {
                    onSendMessage()
                }
            },
            colors = TextFieldDefaults.colors().copy(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isFocused || expandedFill) {
                        IconButton(
                            onClick = {
                                onToggleExpand()
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (expandedFill) R.drawable.collapse_all_24px
                                    else R.drawable.expand_all_24px
                                ),
                                contentDescription = stringResource(
                                    if (expandedFill) R.string.code_block_collapse
                                    else R.string.code_block_expand
                                ),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    trailingContent()
                }
            },
            leadingIcon = if (quickMessages.isNotEmpty()) {
                {
                    QuickMessageButton(quickMessages = quickMessages, state = state)
                }
            } else null,
        )
            TextScrollbar(
                scrollState = textScrollState,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun TextScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(8.dp)
                .fillMaxHeight()
                .pointerInput(scrollState) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val viewport = size.height.toFloat()
                        val max = scrollState.maxValue
                        if (max > 0 && viewport > 0f) {
                            val thumbHeight = (viewport * viewport / (viewport + max))
                                .coerceAtLeast(24f)
                            val trackHeight = viewport - thumbHeight
                            val delta = if (trackHeight > 0f) {
                                dragAmount * max / trackHeight
                            } else 0f
                            scope.launch { scrollState.scrollBy(delta) }
                        }
                    }
                }
        ) {
            val viewport = size.height
            val max = scrollState.maxValue
            if (max > 0 && viewport > 0f) {
                val thumbHeight = (viewport * viewport / (viewport + max)).coerceAtLeast(24f)
                val trackHeight = viewport - thumbHeight
                val fraction = if (trackHeight > 0f) scrollState.value.toFloat() / max else 0f
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(size.width / 2f - 1.5.dp.toPx(), fraction * trackHeight),
                    size = Size(3.dp.toPx(), thumbHeight),
                    cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = rememberScreenEdgeCornerShape(),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(
                items = completionList.items,
                key = { item -> "${item.label}:${item.insertText}" },
            ) { item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ChatInputState.applyCompletion(
    replacementRange: TextRange,
    item: ChatCompletionItem,
) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
private fun QuickMessageButton(
    quickMessages: List<QuickMessage>,
    state: ChatInputState,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            expanded = !expanded
        }) {
        Icon(HugeIcons.Zap, null)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = rememberScreenEdgeCornerShape(),
            modifier = Modifier
                .widthIn(min = 200.dp, max = 360.dp)
        ) {
            quickMessages.forEach { quickMessage ->
                Surface(
                    onClick = {
                        state.appendText(quickMessage.content)
                        expanded = false
                    },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = quickMessage.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
