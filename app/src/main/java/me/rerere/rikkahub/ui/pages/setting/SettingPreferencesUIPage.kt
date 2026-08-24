package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.ScreenCornerAdaptation
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.network.toProxyOrNull
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CompactNumberField
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
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
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.rikkahub.utils.plus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
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

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
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
        userAgent = value
        vm.updateSettings(
            settings.copy(
                networkSetting = networkSetting.copy(userAgent = value),
            )
        )
    }

    fun saveProxy() {
        vm.updateSettings(
            settings.copy(
                networkSetting = networkSetting.copy(
                    proxyUrl = proxyUrlDraft,
                    proxyUsername = proxyUsernameDraft,
                    proxyPassword = proxyPasswordDraft,
                ),
            )
        )
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
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc)) },
                        trailingContent = {
                            Switch(
                                checked = createNewConversationOnStart,
                                onCheckedChange = { createNewConversationOnStart = it },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.sendOnEnter,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(sendOnEnter = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableAutoScroll,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableAutoScroll = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableMessageGenerationHapticEffect,
                                onCheckedChange = {
                                    updateDisplaySetting(
                                        displaySetting.copy(enableMessageGenerationHapticEffect = it)
                                    )
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.skipCropImage,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(skipCropImage = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_parse_mid_think_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_parse_mid_think_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.parseMidThink,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(parseMidThink = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_notification_message_generated)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_notification_message_generated_desc)) },
                        trailingContent = {
                            Select(
                                options = NotificationMode.entries,
                                selectedOption = notificationMode,
                                onOptionSelected = { mode ->
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
                                optionToString = { stringResource(it.labelRes) },
                                fitToOptions = true,
                            )
                        },
                    )
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
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_blur_effect_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_blur_effect_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableBlurEffect,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableBlurEffect = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            Text(stringResource(R.string.setting_display_page_square_input_on_keyboard_title))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_display_page_square_input_on_keyboard_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.squareChatInputBottomWhenKeyboardVisible,
                                onCheckedChange = {
                                    updateDisplaySetting(
                                        displaySetting.copy(squareChatInputBottomWhenKeyboardVisible = it)
                                    )
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_assistant_bubble_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_assistant_bubble_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.showAssistantBubble,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showAssistantBubble = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_bubble_opacity_title)) },
                        supportingContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Slider(
                                    value = displaySetting.bubbleOpacity,
                                    onValueChange = {
                                        updateDisplaySetting(displaySetting.copy(bubbleOpacity = it))
                                    },
                                    valueRange = 0.1f..1.0f,
                                    steps = 8,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(text = "${(displaySetting.bubbleOpacity * 100).toInt()}%")
                            }
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_thinking_content_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_thinking_content_desc)) },
                        trailingContent = {
                            val thinkingMode = when {
                                !displaySetting.showThinkingContent -> ThinkingDisplayMode.COLLAPSED
                                displaySetting.showThinkingContentPreview -> ThinkingDisplayMode.PREVIEW
                                else -> ThinkingDisplayMode.EXPANDED
                            }
                            Select(
                                options = ThinkingDisplayMode.entries,
                                selectedOption = thinkingMode,
                                onOptionSelected = { mode ->
                                    val newSetting = when (mode) {
                                        ThinkingDisplayMode.COLLAPSED -> displaySetting.copy(showThinkingContent = false)
                                        ThinkingDisplayMode.PREVIEW -> displaySetting.copy(showThinkingContent = true, showThinkingContentPreview = true)
                                        ThinkingDisplayMode.EXPANDED -> displaySetting.copy(showThinkingContent = true, showThinkingContentPreview = false)
                                    }
                                    updateDisplaySetting(newSetting)
                                },
                                optionToString = { stringResource(it.labelRes) },
                                fitToOptions = true,
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_auto_collapse_thinking_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_auto_collapse_thinking_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.autoCloseThinking,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(autoCloseThinking = it))
                                }
                            )
                        },
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
                                optionToString = { stringResource(it.labelRes) },
                                fitToOptions = true,
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_page_color_mode)) },
                        supportingContent = {
                            Text(
                                when (colorMode) {
                                    ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                    ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                    ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                                }
                            )
                        },
                        trailingContent = {
                            Select(
                                options = ColorMode.entries,
                                selectedOption = colorMode,
                                onOptionSelected = { colorMode = it },
                                optionToString = {
                                    when (it) {
                                        ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                        ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                        ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                                    }
                                },
                                fitToOptions = true,
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            Text(stringResource(R.string.setting_display_page_screen_corner_adaptation_title))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_display_page_screen_corner_adaptation_desc))
                        },
                        trailingContent = {
                            Select(
                                options = ScreenCornerAdaptation.entries,
                                selectedOption = displaySetting.screenCornerAdaptation,
                                onOptionSelected = {
                                    updateDisplaySetting(displaySetting.copy(screenCornerAdaptation = it))
                                },
                                optionToString = { stringResource(it.labelRes) },
                                fitToSelectedOption = true,
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_desc)) },
                        trailingContent = {
                            Switch(checked = amoledDarkMode, onCheckedChange = { amoledDarkMode = it })
                        },
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
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_chat_font_family_title)) },
                        trailingContent = {
                            Select(
                                options = ChatFontFamily.entries,
                                selectedOption = displaySetting.chatFontFamily,
                                onOptionSelected = { family ->
                                    if (family == ChatFontFamily.CUSTOM && displaySetting.chatCustomFontPath.isBlank()) {
                                        fontPickerLauncher.launch(CustomFontMimeTypesUI)
                                    } else {
                                        updateDisplaySetting(displaySetting.copy(chatFontFamily = family))
                                    }
                                },
                                fitToOptions = true,
                                optionToString = { it.labelUI() },
                                leading = {
                                    Text(
                                        text = "Aa",
                                        fontFamily = displaySetting.chatFontFamily.toFontFamilyUI(customChatFontFamily),
                                    )
                                },
                                optionLeading = { family ->
                                    Text(
                                        text = "Aa",
                                        fontFamily = family.toFontFamilyUI(customChatFontFamily),
                                    )
                                }
                            )
                        }
                    )
                    item(
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
                    item(
                        headlineContent = {
                            Text(stringResource(R.string.setting_display_page_use_chat_font_globally_title))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_display_page_use_chat_font_globally_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.useChatFontGlobally,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(useChatFontGlobally = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_font_size_title)) },
                        supportingContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Slider(
                                    value = displaySetting.fontSizeRatio,
                                    onValueChange = {
                                        updateDisplaySetting(displaySetting.copy(fontSizeRatio = it))
                                    },
                                    valueRange = 0.5f..2f,
                                    steps = 11,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(text = "${(displaySetting.fontSizeRatio * 100).toInt()}%")
                            }
                        }
                    )
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
                                displaySetting.defaultFontWeight,
                                displaySetting.boldFontWeight,
                                chatFontFamily,
                            ) {
                                buildAnnotatedString {
                                    append(previewPart1)
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight(
                                                displaySetting.boldFontWeight ?: FontWeight.Bold.weight
                                            )
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
                                    fontWeight = displaySetting.defaultFontWeight?.let { FontWeight(it) },
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
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_latex_rendering_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_latex_rendering_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableLatexRendering,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableLatexRendering = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.codeBlockAutoWrap,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.codeBlockAutoCollapse,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_line_numbers_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_line_numbers_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.showLineNumbers,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showLineNumbers = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_ligatures_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_ligatures_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableCodeLigatures,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableCodeLigatures = it))
                                }
                            )
                        },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_tts_settings)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.ttsOnlyReadQuoted,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(ttsOnlyReadQuoted = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_tts_read_outside_brackets_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_tts_read_outside_brackets_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.ttsOnlyReadOutsideBrackets,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(ttsOnlyReadOutsideBrackets = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.autoPlayTTSAfterGeneration,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(autoPlayTTSAfterGeneration = it))
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    if (proxyDialogVisible) {
        AlertDialog(
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

    RikkaConfirmDialog(
        show = showDeleteFontConfirm,
        title = stringResource(R.string.setting_display_page_delete_font_title),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
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
    ) {
        Text(stringResource(R.string.setting_display_page_delete_font_confirm))
    }
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
    ChatFontFamily.SERIF -> stringResource(R.string.setting_display_page_chat_font_family_serif)
    ChatFontFamily.MONOSPACE -> stringResource(R.string.setting_display_page_chat_font_family_monospace)
    ChatFontFamily.CUSTOM -> stringResource(R.string.setting_display_page_chat_font_family_custom)
}

private fun ChatFontFamily.toFontFamilyUI(customFontFamily: FontFamily): FontFamily = when (this) {
    ChatFontFamily.DEFAULT -> FontFamily.Default
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
        ScreenCornerAdaptation.ALL -> R.string.setting_display_page_screen_corner_adaptation_all
        ScreenCornerAdaptation.SQUARE -> R.string.setting_display_page_screen_corner_adaptation_square
    }

internal enum class AppLanguage(val tag: String, val labelRes: Int) {
    SYSTEM("", R.string.setting_general_language_system),
    ENGLISH("en", R.string.language_english),
    SIMPLIFIED_CHINESE("zh-CN", R.string.language_simplified_chinese),
    TRADITIONAL_CHINESE("zh-TW", R.string.language_traditional_chinese),
    JAPANESE("ja", R.string.language_japanese),
    KOREAN("ko-KR", R.string.language_korean),
    RUSSIAN("ru", R.string.language_russian),
}
