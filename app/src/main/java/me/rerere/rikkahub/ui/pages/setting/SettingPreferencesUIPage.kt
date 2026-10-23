package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffectType
import me.rerere.rikkahub.data.datastore.filterHeaderValueChars
import me.rerere.rikkahub.data.datastore.ScreenCornerAdaptation
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.remote.toProxyOrNull
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.CompactNumberField
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.components.ui.DeleteConfirmDialog
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionNotification
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceString
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.ui.theme.MIN_TRUSTED_CORNER_RADIUS_DP
import me.rerere.rikkahub.ui.theme.outfitFontFamily
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.rikkahub.ui.theme.rememberScreenCornerRadiusDp
import me.rerere.rikkahub.ui.theme.resolvedBoldFontWeight
import me.rerere.rikkahub.ui.theme.resolvedDefaultFontWeight
import me.rerere.rikkahub.utils.plus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import kotlin.math.roundToInt
import java.io.IOException
import java.util.concurrent.TimeUnit

@Composable
fun SettingPreferencesMorePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val chatFontFamily = rememberChatFontFamily(displaySetting)
    val customChatFontFamily = rememberChatFontFamily(
        displaySetting.copy(chatFontFamily = ChatFontFamily.CUSTOM)
    )
    var amoledDarkMode by rememberAmoledDarkMode()
    var appLanguage by rememberSharedPreferenceString(APP_LANGUAGE_KEY, "")
    var showDeleteFontConfirm by remember { mutableStateOf(false) }
    val selectedLanguage = AppLanguage.entries.firstOrNull { it.tag == appLanguage } ?: AppLanguage.SYSTEM
    var colorMode by rememberColorMode()
    val notificationMode = when {
        !displaySetting.enableNotificationOnMessageGeneration -> NotificationMode.OFF
        displaySetting.enableLiveUpdateNotification -> NotificationMode.REALTIME
        else -> NotificationMode.AFTER_GENERATION
    }
    val pasteLongTextThreshold = if (displaySetting.pasteLongTextAsFile) {
        displaySetting.pasteLongTextThreshold
    } else {
        null
    }
    var pasteLongTextThresholdInput by remember(pasteLongTextThreshold) {
        mutableStateOf(pasteLongTextThreshold?.toString().orEmpty())
    }
    var defaultFontWeightInput by remember(displaySetting.defaultFontWeight) {
        mutableStateOf(displaySetting.defaultFontWeight?.toString().orEmpty())
    }
    var boldFontWeightInput by remember(displaySetting.boldFontWeight) {
        mutableStateOf(displaySetting.boldFontWeight?.toString().orEmpty())
    }
    val ttsPlaybackSpeedState = remember(settings.defaultTTSPlaybackSpeed) {
        SliderState(
            value = settings.defaultTTSPlaybackSpeed,
            trackRange = 0.5f..2.0f,
            steps = 14,
        )
    }
    val volumeKeyScrollMode = if (!displaySetting.enableVolumeKeyScroll) {
        VolumeKeyScrollMode.OFF
    } else {
        VolumeKeyScrollMode.entries.minBy { mode ->
            kotlin.math.abs((mode.ratio ?: 0f) - displaySetting.volumeKeyScrollRatio)
        }
    }
    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setOf(PermissionNotification)
        } else {
            emptySet()
        },
    )
    PermissionManager(permissionState = permissionState)

    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    // 从系统设置页授权回来后刷新
    LifecycleResumeEffect(Unit) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        onPauseOrDispose {}
    }

    fun requestOverlayPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        )
    }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    val networkSetting = settings.networkSetting
    var userAgent by remember(networkSetting.userAgent) {
        mutableStateOf(networkSetting.userAgent)
    }
    var proxyUrl by remember(networkSetting.proxyUrl) {
        mutableStateOf(networkSetting.proxyUrl)
    }
    var proxyUsername by remember(networkSetting.proxyUsername) {
        mutableStateOf(networkSetting.proxyUsername)
    }
    var proxyPassword by remember(networkSetting.proxyPassword) {
        mutableStateOf(networkSetting.proxyPassword)
    }
    var proxyUrlDraft by remember { mutableStateOf("") }
    var proxyUsernameDraft by remember { mutableStateOf("") }
    var proxyPasswordDraft by remember { mutableStateOf("") }
    var proxyPasswordVisible by remember { mutableStateOf(false) }
    var proxyDialogVisible by remember { mutableStateOf(false) }
    var proxyTesting by remember { mutableStateOf(false) }
    val httpClient = koinInject<OkHttpClient>()
    val defaultUserAgent = "NyanChat-Android/${BuildConfig.VERSION_NAME}"
    val proxyUrlInvalid = proxyUrlDraft.isNotBlank() && proxyUrlDraft.toProxyOrNull() == null

    fun updateUserAgent(value: String) {
        // User-Agent 只能是 OkHttp 允许的 ASCII 字符，输入框里直接过滤，不让非法字符落进设置
        val filtered = value.filterHeaderValueChars()
        userAgent = filtered
        vm.updateSettings {
            it.copy(
                networkSetting = it.networkSetting.copy(userAgent = filtered),
            )
        }
    }

    fun saveProxy() {
        vm.updateSettings {
            it.copy(
                networkSetting = it.networkSetting.copy(
                    proxyUrl = proxyUrlDraft,
                    proxyUsername = proxyUsernameDraft,
                    proxyPassword = proxyPasswordDraft,
                ),
            )
        }
        proxyUrl = proxyUrlDraft
        proxyUsername = proxyUsernameDraft
        proxyPassword = proxyPasswordDraft
    }

    fun resetProxy() {
        proxyUrlDraft = ""
        proxyUsernameDraft = ""
        proxyPasswordDraft = ""
    }

    fun testProxy() {
        val proxy = proxyUrlDraft.toProxyOrNull() ?: return
        if (proxyTesting) return
        proxyTesting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val testClient = httpClient.newBuilder()
                        .proxy(proxy)
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .callTimeout(15, TimeUnit.SECONDS)
                        .build()
                    testClient.newCall(
                        Request.Builder()
                            .url(PROXY_TEST_URL)
                            .head()
                            .build()
                    ).execute().use { response ->
                        if (response.code != 204) {
                            throw IOException("HTTP ${response.code} ${response.message}")
                        }
                    }
                }
            }
            result.onSuccess {
                toaster.show(
                    context.getString(R.string.backup_page_connection_success),
                    type = ToastType.Success,
                )
            }.onFailure { error ->
                toaster.show(
                    context.getString(
                        R.string.backup_page_connection_failed,
                        error.message.orEmpty(),
                    ),
                    type = ToastType.Error,
                )
            }
            proxyTesting = false
        }
    }

    val importSuccessMsg = stringResource(R.string.setting_display_page_custom_font_import_success)
    val importFailedMsg = stringResource(R.string.setting_display_page_custom_font_import_failed)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    importCustomChatFontInternal(context, uri)
                }
            }.onSuccess { importedFont ->
                updateDisplaySetting(
                    displaySetting.copy(
                        chatFontFamily = ChatFontFamily.CUSTOM,
                        chatCustomFontPath = importedFont.relativePath,
                        chatCustomFontName = importedFont.displayName,
                    )
                )
                toaster.show(importSuccessMsg, type = ToastType.Success)
            }.onFailure { error ->
                toaster.show(importFailedMsg.format(error.message.orEmpty()), type = ToastType.Error)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_more_settings))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                var createNewConversationOnStart by rememberSharedPreferenceBoolean(
                    "create_new_conversation_on_start",
                    true,
                )
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_chat_settings)) },
                ) {
                    switchItem(
                        checked = createNewConversationOnStart,
                        onCheckedChange = { createNewConversationOnStart = it },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_title)) },
                    )
                    switchItem(
                        checked = displaySetting.sendOnEnter,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(sendOnEnter = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_title)) },
                    )
                    switchItem(
                        checked = displaySetting.enableAutoScroll,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(enableAutoScroll = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_title)) },
                    )
                    switchItem(
                        checked = displaySetting.enableMessageGenerationHapticEffect,
                        onCheckedChange = {
                            updateDisplaySetting(
                                displaySetting.copy(enableMessageGenerationHapticEffect = it)
                            )
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title)) },
                    )
                    switchItem(
                        checked = displaySetting.skipCropImage,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(skipCropImage = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_title)) },
                    )
                    switchItem(
                        checked = displaySetting.parseMidThink,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(parseMidThink = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_parse_mid_think_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_parse_mid_think_title)) },
                    )
                    switchItem(
                        checked = displaySetting.enableFloatingChatWindow,
                        onCheckedChange = {
                            if (it && !canDrawOverlays) requestOverlayPermission()
                            updateDisplaySetting(displaySetting.copy(enableFloatingChatWindow = it))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_page_preferences_notification_floating_window_desc))
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification_floating_window)) },
                    )
                    if (displaySetting.enableFloatingChatWindow && !canDrawOverlays) {
                        item(
                            onClick = ::requestOverlayPermission,
                            headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification_overlay_permission)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_preferences_notification_overlay_permission_desc)) },
                            trailingContent = {
                                Icon(HugeIcons.ArrowRight01, contentDescription = null)
                            },
                        )
                    }
                    FormItem(
                        label = { Text(stringResource(R.string.setting_display_page_notification_message_generated)) },
                        description = { Text(stringResource(R.string.setting_display_page_notification_message_generated_desc)) },
                    ) {
                        ConnectedChoiceButtons(
                            options = NotificationMode.entries,
                            selected = notificationMode,
                            onSelect = { mode ->
                                if (mode != NotificationMode.OFF && !permissionState.allPermissionsGranted) {
                                    permissionState.requestPermissions()
                                }
                                updateDisplaySetting(
                                    displaySetting.copy(
                                        enableNotificationOnMessageGeneration = mode != NotificationMode.OFF,
                                        enableLiveUpdateNotification = mode == NotificationMode.REALTIME,
                                    )
                                )
                            },
                        ) { mode, selected ->
                            ConnectedChoiceLabel(text = stringResource(mode.labelRes), selected = selected)
                        }
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_desc)) },
                        trailingContent = {
                            Select(
                                options = VolumeKeyScrollMode.entries,
                                selectedOption = volumeKeyScrollMode,
                                onOptionSelected = { mode ->
                                    updateDisplaySetting(
                                        displaySetting.copy(
                                            enableVolumeKeyScroll = mode != VolumeKeyScrollMode.OFF,
                                            volumeKeyScrollRatio = mode.ratio
                                                ?: displaySetting.volumeKeyScrollRatio,
                                        )
                                    )
                                },
                                optionToString = { mode ->
                                    mode.ratio?.let { ratio ->
                                        "${(ratio * 100).toInt()}%"
                                    } ?: stringResource(R.string.common_off)
                                },
                                fitToOptions = true,
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_title)) },
                        supportingContent = {
                            Column {
                                Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_desc))
                                OutlinedTextField(
                                    value = pasteLongTextThresholdInput,
                                    onValueChange = { value ->
                                        if (value.all(Char::isDigit)) {
                                            pasteLongTextThresholdInput = value
                                            if (value.isBlank()) {
                                                updateDisplaySetting(
                                                    displaySetting.copy(
                                                        pasteLongTextAsFile = false,
                                                    )
                                                )
                                            } else {
                                                value.toIntOrNull()?.takeIf { it > 0 }?.let { threshold ->
                                                    updateDisplaySetting(
                                                        displaySetting.copy(
                                                            pasteLongTextAsFile = true,
                                                            pasteLongTextThreshold = threshold,
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    placeholder = {
                                        Text(
                                            stringResource(
                                                R.string.common_off
                                            )
                                        )
                                    },
                                    singleLine = true,
                                    isError = pasteLongTextThresholdInput.isNotBlank() &&
                                        pasteLongTextThresholdInput.toIntOrNull()?.takeIf { it > 0 } == null,
                                )
                            }
                        },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_interface_settings)) },
                ) {
                    FormItem(
                        label = { Text(stringResource(R.string.setting_display_page_enable_blur_effect_title)) },
                        description = { Text(stringResource(R.string.setting_display_page_enable_blur_effect_desc)) },
                    ) {
                        ConnectedChoiceButtons(
                            options = listOf(null, BackgroundEffectType.BLUR, BackgroundEffectType.GLASS),
                            selected = displaySetting.effectiveBackgroundEffectType(),
                            onSelect = { selected ->
                                updateDisplaySetting(
                                    displaySetting.copy(
                                        enableBlurEffect = selected != null,
                                        backgroundEffectType = selected ?: BackgroundEffectType.BLUR,
                                    )
                                )
                            },
                        ) { selected, isSelected ->
                            ConnectedChoiceLabel(
                                text = when (selected) {
                                    null -> stringResource(R.string.common_off)
                                    BackgroundEffectType.BLUR -> stringResource(R.string.setting_display_page_background_effect_blur)
                                    BackgroundEffectType.GLASS -> stringResource(R.string.setting_display_page_background_effect_glass)
                                },
                                selected = isSelected,
                            )
                        }
                    }
                    switchItem(
                        checked = displaySetting.enableQuoteHighlight,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(enableQuoteHighlight = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_quote_highlight_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_quote_highlight_title)) },
                    )
                    switchItem(
                        checked = displaySetting.showAssistantBubble,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(showAssistantBubble = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_assistant_bubble_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_assistant_bubble_title)) },
                    )
                    FormItem(
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.setting_display_page_bubble_opacity_title),
                                    modifier = Modifier.weight(1f),
                                )
                                CardGroupValue("${(displaySetting.bubbleOpacity * 100).roundToInt()}%")
                            }
                        },
                    ) {
                        val bubbleOpacityState = remember {
                            SliderState(value = displaySetting.bubbleOpacity, trackRange = 0.1f..1.0f, steps = 8)
                        }
                        LaunchedEffect(displaySetting.bubbleOpacity) {
                            if (bubbleOpacityState.value != displaySetting.bubbleOpacity) {
                                bubbleOpacityState.value = displaySetting.bubbleOpacity
                            }
                        }
                        Slider(
                            state = bubbleOpacityState,
                            onValueChange = {
                                bubbleOpacityState.value = it
                                updateDisplaySetting(displaySetting.copy(bubbleOpacity = it))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    FormItem(
                        label = { Text(stringResource(R.string.setting_display_page_show_thinking_content_title)) },
                        description = { Text(stringResource(R.string.setting_display_page_show_thinking_content_desc)) },
                    ) {
                        val thinkingMode = when {
                            !displaySetting.showThinkingContent -> ThinkingDisplayMode.COLLAPSED
                            displaySetting.showThinkingContentPreview -> ThinkingDisplayMode.PREVIEW
                            else -> ThinkingDisplayMode.EXPANDED
                        }
                        ConnectedChoiceButtons(
                            options = ThinkingDisplayMode.entries,
                            selected = thinkingMode,
                            onSelect = { mode ->
                                val newSetting = when (mode) {
                                    ThinkingDisplayMode.COLLAPSED -> displaySetting.copy(showThinkingContent = false)
                                    ThinkingDisplayMode.PREVIEW -> displaySetting.copy(showThinkingContent = true, showThinkingContentPreview = true)
                                    ThinkingDisplayMode.EXPANDED -> displaySetting.copy(showThinkingContent = true, showThinkingContentPreview = false)
                                }
                                updateDisplaySetting(newSetting)
                            },
                        ) { mode, selected ->
                            ConnectedChoiceLabel(text = stringResource(mode.labelRes), selected = selected)
                        }
                    }
                    switchItem(
                        checked = displaySetting.autoCloseThinking,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(autoCloseThinking = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_auto_collapse_thinking_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_auto_collapse_thinking_title)) },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_system_settings)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_general_language)) },
                        trailingContent = {
                            Select(
                                options = AppLanguage.entries,
                                selectedOption = selectedLanguage,
                                onOptionSelected = { language ->
                                    appLanguage = language.tag
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.forLanguageTags(language.tag)
                                    )
                                },
                                optionToString = { it.displayName() },
                                fitToOptions = true,
                            )
                        },
                    )
                    FormItem(
                        label = { Text(stringResource(R.string.setting_page_color_mode)) },
                    ) {
                        ConnectedChoiceButtons(
                            options = ColorMode.entries,
                            selected = colorMode,
                            onSelect = { colorMode = it },
                        ) { mode, selected ->
                            ConnectedChoiceLabel(
                                text = when (mode) {
                                    ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                    ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                    ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                                },
                                selected = selected,
                                icon = mode.icon(),
                            )
                        }
                    }
                    FormItem(
                        label = { Text(stringResource(R.string.setting_display_page_screen_corner_adaptation_title)) },
                        description = {
                            val cornerRadiusDp = rememberScreenCornerRadiusDp()
                            Text(
                                buildString {
                                    append(stringResource(R.string.setting_display_page_screen_corner_adaptation_desc))
                                    if (cornerRadiusDp == null) {
                                        append(
                                            stringResource(
                                                R.string.setting_display_page_screen_corner_adaptation_cannot_read
                                            )
                                        )
                                    } else {
                                        append(
                                            stringResource(
                                                R.string.setting_display_page_screen_corner_adaptation_value_read,
                                                formatCornerRadiusDp(cornerRadiusDp),
                                            )
                                        )
                                        if (cornerRadiusDp <= MIN_TRUSTED_CORNER_RADIUS_DP) {
                                            append(
                                                stringResource(
                                                    R.string.setting_display_page_screen_corner_adaptation_small_value
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        },
                    ) {
                        val cornerRadiusDp = rememberScreenCornerRadiusDp()
                        val canAdapt =
                            cornerRadiusDp != null &&
                                cornerRadiusDp > MIN_TRUSTED_CORNER_RADIUS_DP
                        LaunchedEffect(canAdapt, displaySetting.screenCornerAdaptation) {
                            if (!canAdapt &&
                                displaySetting.screenCornerAdaptation == ScreenCornerAdaptation.ALL
                            ) {
                                updateDisplaySetting(
                                    displaySetting.copy(
                                        screenCornerAdaptation = ScreenCornerAdaptation.DISABLED
                                    )
                                )
                            }
                        }
                        val options = if (canAdapt) {
                            ScreenCornerAdaptation.entries
                        } else {
                            ScreenCornerAdaptation.entries.filterNot {
                                it == ScreenCornerAdaptation.ALL
                            }
                        }
                        ConnectedChoiceButtons(
                            options = options,
                            selected = displaySetting.screenCornerAdaptation,
                            onSelect = {
                                updateDisplaySetting(displaySetting.copy(screenCornerAdaptation = it))
                            },
                        ) { mode, selected ->
                            ConnectedChoiceLabel(text = stringResource(mode.labelRes), selected = selected)
                        }
                    }
                    switchItem(
                        checked = amoledDarkMode,
                        onCheckedChange = { amoledDarkMode = it },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_title)) },
                    )
                    switchItem(
                        checked = settings.networkSetting.enableAutoRetry,
                        onCheckedChange = { enabled ->
                            vm.updateSettings {
                                it.copy(
                                    networkSetting = it.networkSetting.copy(
                                        enableAutoRetry = enabled,
                                    ),
                                )
                            }
                        },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_network_auto_retry_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_network_auto_retry)) },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_network_user_agent)) },
                        supportingContent = {
                            Column {
                                Text(stringResource(R.string.setting_page_preferences_network_user_agent_desc))
                                OutlinedTextField(
                                    value = userAgent,
                                    onValueChange = ::updateUserAgent,
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .fillMaxWidth(),
                                    placeholder = { Text(defaultUserAgent) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Ascii,
                                        autoCorrectEnabled = false,
                                    ),
                                )
                            }
                        },
                    )
                    item(
                        onClick = {
                            proxyUrlDraft = proxyUrl
                            proxyUsernameDraft = proxyUsername
                            proxyPasswordDraft = proxyPassword
                            proxyPasswordVisible = false
                            proxyDialogVisible = true
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_network_proxy)) },
                        supportingContent = {
                            Text(
                                if (proxyUrl.isBlank()) {
                                    stringResource(R.string.setting_page_preferences_network_proxy_list_desc)
                                } else {
                                    proxyUrl
                                }
                            )
                        },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_font_settings)) },
                ) {
                    FormItem(
                        label = { Text(stringResource(R.string.setting_display_page_chat_font_family_title)) },
                    ) {
                        ConnectedChoiceButtons(
                            options = ChatFontFamily.entries,
                            selected = displaySetting.chatFontFamily,
                            onSelect = { family ->
                                if (family == ChatFontFamily.CUSTOM && displaySetting.chatCustomFontPath.isBlank()) {
                                    fontPickerLauncher.launch(CustomFontMimeTypesUI)
                                } else {
                                    updateDisplaySetting(displaySetting.copy(chatFontFamily = family))
                                }
                            },
                            minHeight = 64.dp,
                        ) { family, _ ->
                            // 每个按钮用对应的字体写一个样字
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Aa",
                                    fontFamily = family.toFontFamilyUI(context, customChatFontFamily),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = family.labelUI(),
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    item(
                        // 整卡可点击：点击卡片任意位置即可导入自定义字体
                        onClick = { fontPickerLauncher.launch(CustomFontMimeTypesUI) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_custom_font_title)) },
                        supportingContent = {
                            Text(
                                if (displaySetting.chatCustomFontName.isNotBlank()) {
                                    displaySetting.chatCustomFontName
                                } else {
                                    stringResource(R.string.setting_display_page_custom_font_not_imported)
                                }
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { fontPickerLauncher.launch(CustomFontMimeTypesUI) }
                                ) {
                                    Icon(
                                        HugeIcons.FileImport,
                                        contentDescription = stringResource(
                                            R.string.setting_display_page_custom_font_import
                                        )
                                    )
                                }
                                if (displaySetting.chatCustomFontPath.isNotBlank()) {
                                    IconButton(
                                        onClick = { showDeleteFontConfirm = true }
                                    ) {
                                        Icon(
                                            HugeIcons.Delete02,
                                            contentDescription = stringResource(
                                                R.string.setting_display_page_custom_font_remove
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    )
                    switchItem(
                        checked = displaySetting.useChatFontGlobally,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(useChatFontGlobally = it))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_display_page_use_chat_font_globally_desc))
                        },
                        headlineContent = {
                            Text(stringResource(R.string.setting_display_page_use_chat_font_globally_title))
                        },
                    )
                    FormItem(
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.setting_display_page_font_size_title),
                                    modifier = Modifier.weight(1f),
                                )
                                CardGroupValue("${(displaySetting.fontSizeRatio * 100).toInt()}%")
                            }
                        },
                    ) {
                        val fontSizeRatioState = remember {
                            SliderState(value = displaySetting.fontSizeRatio, trackRange = 0.5f..2f, steps = 11)
                        }
                        LaunchedEffect(displaySetting.fontSizeRatio) {
                            if (fontSizeRatioState.value != displaySetting.fontSizeRatio) {
                                fontSizeRatioState.value = displaySetting.fontSizeRatio
                            }
                        }
                        Slider(
                            state = fontSizeRatioState,
                            onValueChange = {
                                fontSizeRatioState.value = it
                                updateDisplaySetting(displaySetting.copy(fontSizeRatio = it))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_default_font_weight_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_default_font_weight_desc)) },
                        trailingContent = {
                            CompactNumberField(
                                value = defaultFontWeightInput,
                                onValueChange = { value ->
                                    val filtered = value.filter(Char::isDigit)
                                    defaultFontWeightInput = filtered
                                    filtered.toIntOrNull()?.let { weight ->
                                        updateDisplaySetting(displaySetting.copy(defaultFontWeight = weight))
                                    }
                                },
                                isError = defaultFontWeightInput.isNotBlank() &&
                                    defaultFontWeightInput.toIntOrNull()?.let { it !in 100..900 } == true,
                                onFocusLost = {
                                    val weight = defaultFontWeightInput.toIntOrNull()
                                    val normalized = when {
                                        weight == null -> null
                                        weight < 100 -> 100
                                        weight > 900 -> 900
                                        else -> weight
                                    }
                                    defaultFontWeightInput = normalized?.toString().orEmpty()
                                    updateDisplaySetting(displaySetting.copy(defaultFontWeight = normalized))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_bold_font_weight_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_bold_font_weight_desc)) },
                        trailingContent = {
                            CompactNumberField(
                                value = boldFontWeightInput,
                                onValueChange = { value ->
                                    val filtered = value.filter(Char::isDigit)
                                    boldFontWeightInput = filtered
                                    filtered.toIntOrNull()?.let { weight ->
                                        updateDisplaySetting(displaySetting.copy(boldFontWeight = weight))
                                    }
                                },
                                isError = boldFontWeightInput.isNotBlank() &&
                                    boldFontWeightInput.toIntOrNull()?.let { it !in 100..900 } == true,
                                onFocusLost = {
                                    val weight = boldFontWeightInput.toIntOrNull()
                                    val normalized = when {
                                        weight == null -> null
                                        weight < 100 -> 100
                                        weight > 900 -> 900
                                        else -> weight
                                    }
                                    boldFontWeightInput = normalized?.toString().orEmpty()
                                    updateDisplaySetting(displaySetting.copy(boldFontWeight = normalized))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_font_preview)) },
                        supportingContent = {
                            val previewPart1 = stringResource(R.string.setting_display_page_font_size_preview_part1)
                            val previewPart2 = stringResource(R.string.setting_display_page_font_size_preview_part2)
                            val annotatedPreview = remember(
                                previewPart1,
                                previewPart2,
                                displaySetting.fontSizeRatio,
                                displaySetting.resolvedDefaultFontWeight(),
                                displaySetting.resolvedBoldFontWeight(),
                                chatFontFamily,
                            ) {
                                buildAnnotatedString {
                                    append(previewPart1)
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = displaySetting.resolvedBoldFontWeight()
                                        )
                                    ) {
                                        append(previewPart2)
                                    }
                                }
                            }
                            Text(
                                text = annotatedPreview,
                                style = LocalTextStyle.current.copy(
                                    fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                                    lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                                    fontFamily = chatFontFamily,
                                    fontWeight = displaySetting.resolvedDefaultFontWeight(),
                                ),
                            )
                        },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_formula_code_settings)) },
                ) {
                    switchItem(
                        checked = displaySetting.enableLatexRendering,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(enableLatexRendering = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_latex_rendering_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_latex_rendering_title)) },
                    )
                    switchItem(
                        checked = displaySetting.codeBlockAutoWrap,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_title)) },
                    )
                    switchItem(
                        checked = displaySetting.codeBlockAutoCollapse,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_title)) },
                    )
                    switchItem(
                        checked = displaySetting.showLineNumbers,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(showLineNumbers = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_line_numbers_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_line_numbers_title)) },
                    )
                    switchItem(
                        checked = displaySetting.enableCodeLigatures,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(enableCodeLigatures = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_ligatures_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_ligatures_title)) },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_tts_settings)) },
                ) {
                    FormItem(
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.setting_tts_page_default_playback_speed),
                                    modifier = Modifier.weight(1f),
                                )
                                CardGroupValue("x${"%.1f".format(ttsPlaybackSpeedState.value)}")
                            }
                        },
                        description = { Text(stringResource(R.string.setting_tts_page_default_playback_speed_description)) },
                    ) {
                        Slider(
                            state = ttsPlaybackSpeedState,
                            onValueChange = {
                                ttsPlaybackSpeedState.value = (it * 10).roundToInt() / 10f
                            },
                            onValueChangeFinished = {
                                vm.updateSettings {
                                    it.copy(defaultTTSPlaybackSpeed = ttsPlaybackSpeedState.value)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    switchItem(
                        checked = displaySetting.ttsOnlyReadQuoted,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(ttsOnlyReadQuoted = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_title)) },
                    )
                    switchItem(
                        checked = displaySetting.ttsOnlyReadOutsideBrackets,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(ttsOnlyReadOutsideBrackets = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_tts_read_outside_brackets_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_tts_read_outside_brackets_title)) },
                    )
                    switchItem(
                        checked = displaySetting.autoPlayTTSAfterGeneration,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(autoPlayTTSAfterGeneration = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_title)) },
                    )
                    switchItem(
                        checked = displaySetting.voiceModeListenWhileReplying,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(voiceModeListenWhileReplying = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_voice_listen_while_replying_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_voice_listen_while_replying_title)) },
                    )
                }
            }
        }
    }

    if (proxyDialogVisible) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { proxyDialogVisible = false },
            modifier = Modifier.imePadding(),
            title = {
                Text(stringResource(R.string.setting_page_preferences_network_proxy))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = proxyUrlDraft,
                        onValueChange = { proxyUrlDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.setting_page_preferences_network_proxy))
                        },
                        placeholder = { Text("http://127.0.0.1:7890") },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (proxyUrlInvalid) {
                                        R.string.setting_page_preferences_network_proxy_invalid
                                    } else {
                                        R.string.setting_page_preferences_network_proxy_desc
                                    }
                                )
                            )
                        },
                        isError = proxyUrlInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = proxyUsernameDraft,
                        onValueChange = { proxyUsernameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.backup_page_username)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = proxyPasswordDraft,
                        onValueChange = { proxyPasswordDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.backup_page_password)) },
                        visualTransformation = if (proxyPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { proxyPasswordVisible = !proxyPasswordVisible },
                            ) {
                                Icon(
                                    imageVector = if (proxyPasswordVisible) {
                                        HugeIcons.ViewOff
                                    } else {
                                        HugeIcons.View
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = ::resetProxy,
                        enabled = proxyUrlDraft.isNotEmpty() ||
                            proxyUsernameDraft.isNotEmpty() ||
                            proxyPasswordDraft.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                    TextButton(
                        onClick = ::testProxy,
                        enabled = proxyUrlDraft.toProxyOrNull() != null && !proxyTesting,
                    ) {
                        if (proxyTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.setting_provider_page_test))
                        }
                    }
                    TextButton(
                        onClick = {
                            saveProxy()
                            proxyDialogVisible = false
                        },
                        enabled = !proxyUrlInvalid,
                    ) {
                        Text(stringResource(R.string.common_confirm_action))
                    }
                }
            },
        )
    }

    DeleteConfirmDialog(
        show = showDeleteFontConfirm,
        type = stringResource(R.string.delete_type_font),
        onConfirm = {
            deleteCustomChatFontInternal(context, displaySetting.chatCustomFontPath)
            updateDisplaySetting(
                displaySetting.copy(
                    chatFontFamily = ChatFontFamily.DEFAULT,
                    chatCustomFontPath = "",
                    chatCustomFontName = "",
                )
            )
            showDeleteFontConfirm = false
        },
        onDismiss = { showDeleteFontConfirm = false },
    )
}

private val CustomFontMimeTypesUI = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream",
    "*/*",
)

private val CustomFontExtensionsUI = setOf("ttf", "otf")

private data class ImportedChatFontUI(
    val relativePath: String,
    val displayName: String,
)

@Composable
private fun ChatFontFamily.labelUI(): String = when (this) {
    ChatFontFamily.DEFAULT -> stringResource(R.string.setting_display_page_chat_font_family_default)
    ChatFontFamily.OUTFIT -> stringResource(R.string.setting_display_page_chat_font_family_thin)
    ChatFontFamily.SERIF -> stringResource(R.string.setting_display_page_chat_font_family_serif)
    ChatFontFamily.MONOSPACE -> stringResource(R.string.setting_display_page_chat_font_family_monospace)
    ChatFontFamily.CUSTOM -> stringResource(R.string.setting_display_page_chat_font_family_custom)
}

private fun ChatFontFamily.toFontFamilyUI(context: Context, customFontFamily: FontFamily): FontFamily = when (this) {
    ChatFontFamily.DEFAULT -> FontFamily.Default
    ChatFontFamily.OUTFIT -> outfitFontFamily(context.assets)
    ChatFontFamily.SERIF -> FontFamily.Serif
    ChatFontFamily.MONOSPACE -> FontFamily.Monospace
    ChatFontFamily.CUSTOM -> customFontFamily
}

private fun importCustomChatFontInternal(context: Context, uri: Uri): ImportedChatFontUI {
    val displayName = FileUtils.getFileNameFromUri(context, uri)?.takeIf { it.isNotBlank() } ?: "custom_font"
    val rawExtension = displayName.substringAfterLast('.', "").lowercase()
    if (rawExtension == "ttc") {
        throw IllegalArgumentException("TTC font files are not supported")
    }
    val extension = rawExtension
        .takeIf { it in CustomFontExtensionsUI }
        ?: "ttf"
    val fontDir = File(context.filesDir, FileFolders.FONTS).apply { mkdirs() }
    val targetFile = File(fontDir, "chat_font.${System.currentTimeMillis()}.$extension")
    val tempFile = File(fontDir, "chat_font_import.tmp")

    try {
        tempFile.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open selected font")

        runCatching {
            Typeface.createFromFile(tempFile)
        }.onFailure { error ->
            throw IllegalArgumentException(error.message ?: "Invalid font file", error)
        }

        replaceCustomChatFontInternal(fontDir, tempFile, targetFile)
    } catch (error: Throwable) {
        tempFile.delete()
        throw error
    }

    val relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, targetFile)
        ?: "${FileFolders.FONTS}/${targetFile.name}"
    return ImportedChatFontUI(relativePath = relativePath, displayName = displayName)
}

private fun replaceCustomChatFontInternal(fontDir: File, tempFile: File, targetFile: File) {
    val existingFiles = fontDir.listFiles { file ->
        file.isFile && file.name.startsWith("chat_font.") && file != tempFile
    }?.toList().orEmpty()
    val backups = existingFiles.map { file ->
        file to File(fontDir, "previous_${file.name}").also { it.delete() }
    }

    try {
        backups.forEach { (file, backup) ->
            check(file.renameTo(backup)) { "Unable to prepare existing font for replacement" }
        }
        check(tempFile.renameTo(targetFile)) { "Unable to save selected font" }
        backups.forEach { (_, backup) -> backup.delete() }
    } catch (error: Throwable) {
        tempFile.delete()
        backups.forEach { (file, backup) ->
            if (!file.exists() && backup.exists()) {
                backup.renameTo(file)
            }
        }
        throw error
    }
}

private fun deleteCustomChatFontInternal(context: Context, relativePath: String) {
    val filesDir = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return
    val fontFile = runCatching { File(filesDir, relativePath).canonicalFile }.getOrNull() ?: return
    if (fontFile.path.startsWith("${filesDir.path}${File.separator}")) {
        fontFile.delete()
    }
}

internal const val APP_LANGUAGE_KEY = "app_language"

private const val PROXY_TEST_URL = "https://www.google.com/generate_204"

private fun ColorMode.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    ColorMode.SYSTEM -> HugeIcons.SmartPhone01
    ColorMode.LIGHT -> HugeIcons.Sun01
    ColorMode.DARK -> HugeIcons.Moon02
}

private enum class NotificationMode(val labelRes: Int) {
    OFF(R.string.common_off),
    REALTIME(R.string.setting_notification_mode_realtime),
    AFTER_GENERATION(R.string.setting_notification_mode_after_generation),
}

private enum class ThinkingDisplayMode(val labelRes: Int) {
    COLLAPSED(R.string.setting_display_page_thinking_mode_collapsed),
    PREVIEW(R.string.setting_display_page_thinking_mode_preview),
    EXPANDED(R.string.setting_display_page_thinking_mode_expanded),
}

private enum class VolumeKeyScrollMode(val ratio: Float?) {
    OFF(null),
    PERCENT_25(0.25f),
    PERCENT_50(0.5f),
    PERCENT_75(0.75f),
    PERCENT_100(1f),
}

private val ScreenCornerAdaptation.labelRes: Int
    get() = when (this) {
        ScreenCornerAdaptation.DISABLED -> R.string.setting_display_page_screen_corner_adaptation_disabled
        ScreenCornerAdaptation.INPUT_ONLY -> R.string.setting_display_page_screen_corner_adaptation_input_only
        ScreenCornerAdaptation.ALL -> R.string.common_all
        ScreenCornerAdaptation.SQUARE -> R.string.setting_display_page_screen_corner_adaptation_square
    }

private fun formatCornerRadiusDp(value: Float): String {
    val tenths = (value * 10f).roundToInt()
    val rounded = tenths / 10f
    return if (rounded == rounded.toInt().toFloat()) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

internal enum class AppLanguage(val tag: String, val labelRes: Int? = null, val label: String? = null) {
    SYSTEM("", R.string.setting_general_language_system),
    ENGLISH("en", label = "🇺🇸 English"),
    SIMPLIFIED_CHINESE("zh-CN", label = "🇨🇳 简体中文"),
    TRADITIONAL_CHINESE("zh-TW", label = "🇨🇳 繁體中文"),
    JAPANESE("ja", label = "🇯🇵 日本語"),
    KOREAN("ko-KR", label = "🇰🇷 한국어"),
    RUSSIAN("ru", label = "🇷🇺 Русский"),
    ARABIC("ar", label = "🇸🇦 العربية"),
    GERMAN("de", label = "🇩🇪 Deutsch"),
}

@Composable
private fun AppLanguage.displayName(): String = label ?: stringResource(labelRes!!)
