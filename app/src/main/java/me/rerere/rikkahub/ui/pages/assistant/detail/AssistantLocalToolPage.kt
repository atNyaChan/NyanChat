package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BatteryFull
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.ChartLineData01
import me.rerere.hugeicons.stroke.ClipboardPaste
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.JavaScript
import me.rerere.hugeicons.stroke.Location01
import me.rerere.hugeicons.stroke.MessageQuestion
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.hugeicons.stroke.Tap01
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.phoneuse.PhoneUseManager
import me.rerere.rikkahub.phoneuse.isFloatingChatWindowOn
import me.rerere.rikkahub.ui.components.ai.SearchPickerIcon
import me.rerere.rikkahub.ui.components.ai.SearchPickerSheet
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.permission.PermissionInfo
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import me.rerere.rikkahub.utils.openAccessibilitySettings
import me.rerere.rikkahub.utils.openUsageAccessSettings
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

private enum class LocalToolAuthorizationMode {
    DENIED,
    MANUAL,
    ALWAYS,
}

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_local_tools))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantLocalToolContent(
            innerPadding = innerPadding,
            assistant = assistant,
            settings = settings,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
            onSelectSearch = { mode, serviceIndex -> vm.selectSearchMode(assistant, mode, serviceIndex) },
        )
    }
}

@Composable
private fun AssistantLocalToolContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    settings: Settings,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
    onSelectSearch: (mode: SearchMode, serviceIndex: Int?) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val navController = LocalNavController.current
    val settingsStore: SettingsStore = koinInject()
    val phoneUseManager: PhoneUseManager = koinInject()
    val phoneUseServiceOn = phoneUseManager.controller.collectAsStateWithLifecycle().value != null
    val permissionRequiredText =
        stringResource(R.string.assistant_page_local_tools_screen_time_permission_required)
    val floatingWindowRequiredText =
        stringResource(R.string.assistant_page_local_tools_phone_use_floating_window_required)
    val enableServiceText = stringResource(R.string.assistant_page_local_tools_phone_use_enable_service)
    var showSearchPicker by remember { mutableStateOf(false) }

    val calendarPermissionState = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.READ_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_read)) },
                usage = { Text(stringResource(R.string.permission_calendar_read_desc)) },
                required = true
            ),
            PermissionInfo(
                permission = Manifest.permission.WRITE_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_write)) },
                usage = { Text(stringResource(R.string.permission_calendar_write_desc)) },
                required = true
            ),
        )
    )
    PermissionManager(permissionState = calendarPermissionState)
    val locationPermissionState = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.ACCESS_COARSE_LOCATION,
                displayName = { Text(stringResource(R.string.permission_location)) },
                usage = { Text(stringResource(R.string.permission_location_desc)) },
                required = true,
            ),
            PermissionInfo(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                displayName = { Text(stringResource(R.string.permission_precise_location)) },
                usage = { Text(stringResource(R.string.permission_precise_location_desc)) },
                required = false,
            ),
        )
    )
    PermissionManager(permissionState = locationPermissionState)

    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        // 没有悬浮窗就看不到 AI 在做什么、也不方便随时停下，先去把它打开，这次不开启工具
        if (enabled && option == LocalToolOption.PhoneUse &&
            !isFloatingChatWindowOn(context, settingsStore.settingsFlow.value)
        ) {
            toaster.show(message = floatingWindowRequiredText, type = ToastType.Warning)
            navController.navigate(Screen.SettingPreferencesMore)
            return
        }
        // 无障碍服务还没开时带用户去系统设置，开关照常打开，开完服务就能用
        if (enabled && option == LocalToolOption.PhoneUse && !phoneUseServiceOn) {
            toaster.show(message = enableServiceText, type = ToastType.Warning)
            context.openAccessibilitySettings()
        }
        if (enabled && option == LocalToolOption.ScreenTime && !context.hasUsageStatsPermission()) {
            toaster.show(message = permissionRequiredText, type = ToastType.Warning)
            context.openUsageAccessSettings()
        }
        if (enabled && option == LocalToolOption.Calendar && !calendarPermissionState.allPermissionsGranted) {
            calendarPermissionState.requestPermissions()
            return
        }
        if (enabled && option == LocalToolOption.Location && !locationPermissionState.allPermissionsGranted) {
            locationPermissionState.requestPermissions()
            return
        }
        val newLocalTools = if (enabled) {
            assistant.localTools + option
        } else {
            assistant.localTools - option
        }
        onUpdate(
            assistant.copy(
                localTools = newLocalTools.distinct(),
                manualAuthorizationTools = if (enabled) {
                    assistant.manualAuthorizationTools
                } else {
                    assistant.manualAuthorizationTools - option
                },
            )
        )
    }

    fun setAuthorizationMode(option: LocalToolOption, mode: LocalToolAuthorizationMode) {
        if (mode == LocalToolAuthorizationMode.DENIED) {
            toggleLocalTool(option, false)
            return
        }
        if (option !in assistant.localTools) {
            // 没有悬浮窗就看不到 AI 在做什么、也不方便随时停下，先去把它打开，这次不开启工具
            if (option == LocalToolOption.PhoneUse &&
                !isFloatingChatWindowOn(context, settingsStore.settingsFlow.value)
            ) {
                toaster.show(message = floatingWindowRequiredText, type = ToastType.Warning)
                navController.navigate(Screen.SettingPreferencesMore)
                return
            }
            // 无障碍服务还没开时带用户去系统设置，开关照常打开，开完服务就能用
            if (option == LocalToolOption.PhoneUse && !phoneUseServiceOn) {
                toaster.show(message = enableServiceText, type = ToastType.Warning)
                context.openAccessibilitySettings()
            }
            if (option == LocalToolOption.ScreenTime && !context.hasUsageStatsPermission()) {
                toaster.show(message = permissionRequiredText, type = ToastType.Warning)
                context.openUsageAccessSettings()
            }
            if (option == LocalToolOption.Calendar && !calendarPermissionState.allPermissionsGranted) {
                calendarPermissionState.requestPermissions()
                return
            }
            if (option == LocalToolOption.Location && !locationPermissionState.allPermissionsGranted) {
                locationPermissionState.requestPermissions()
                return
            }
        }
        onUpdate(
            assistant.copy(
                localTools = (assistant.localTools + option).distinct(),
                manualAuthorizationTools = if (mode == LocalToolAuthorizationMode.MANUAL) {
                    assistant.manualAuthorizationTools + option
                } else {
                    assistant.manualAuthorizationTools - option
                },
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .padding(top = 8.dp)
            .padding(bottom = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        fun CardGroupScope.toolItem(
            option: LocalToolOption,
            icon: ImageVector,
            title: @Composable () -> Unit,
            description: @Composable () -> Unit,
        ) {
            val checked = assistant.localTools.contains(option)
            switchItem(
                checked = checked,
                onCheckedChange = { toggleLocalTool(option, it) },
                // 图标底色跟着开关状态变化
                leadingContent = {
                    CardGroupIcon(
                        icon = icon,
                        containerColor = if (checked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (checked) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                supportingContent = description,
                headlineContent = title,
            )
        }

        fun CardGroupScope.toolItem(
            option: LocalToolOption,
            icon: ImageVector,
            @StringRes title: Int,
            @StringRes description: Int,
        ) = toolItem(
            option = option,
            icon = icon,
            title = { Text(stringResource(title)) },
            description = { Text(stringResource(description)) },
        )

        fun CardGroupScope.authorizedToolItem(
            option: LocalToolOption,
            icon: ImageVector,
            title: @Composable () -> Unit,
            description: @Composable () -> Unit,
        ) {
            val mode = when {
                option !in assistant.localTools -> LocalToolAuthorizationMode.DENIED
                option in assistant.manualAuthorizationTools -> LocalToolAuthorizationMode.MANUAL
                else -> LocalToolAuthorizationMode.ALWAYS
            }
            val checked = option in assistant.localTools
            FormItem(
                leadingContent = {
                    CardGroupIcon(
                        icon = icon,
                        containerColor = if (checked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (checked) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                label = title,
                description = description,
            ) {
                ConnectedChoiceButtons(
                    options = LocalToolAuthorizationMode.entries,
                    selected = mode,
                    onSelect = { setAuthorizationMode(option, it) },
                ) { authorizationMode, selected ->
                    ConnectedChoiceLabel(
                        text = stringResource(
                            when (authorizationMode) {
                                LocalToolAuthorizationMode.DENIED ->
                                    R.string.local_tool_authorization_mode_denied
                                LocalToolAuthorizationMode.MANUAL ->
                                    R.string.local_tool_authorization_mode_manual
                                LocalToolAuthorizationMode.ALWAYS ->
                                    R.string.local_tool_authorization_mode_always
                            }
                        ),
                        selected = selected,
                    )
                }
            }
        }

        fun CardGroupScope.authorizedToolItem(
            option: LocalToolOption,
            icon: ImageVector,
            @StringRes title: Int,
            @StringRes description: Int,
        ) = authorizedToolItem(
            option = option,
            icon = icon,
            title = { Text(stringResource(title)) },
            description = { Text(stringResource(description)) },
        )

        CardGroup {
            toolItem(
                LocalToolOption.TimeInfo,
                HugeIcons.Clock01,
                R.string.assistant_page_local_tools_time_info_title,
                R.string.assistant_page_local_tools_time_info_desc,
            )
            toolItem(
                LocalToolOption.AskUser,
                HugeIcons.MessageQuestion,
                R.string.assistant_page_local_tools_ask_user_title,
                R.string.assistant_page_local_tools_ask_user_desc,
            )
            toolItem(
                LocalToolOption.Tts,
                HugeIcons.VolumeHigh,
                R.string.assistant_page_local_tools_tts_title,
                R.string.assistant_page_local_tools_tts_desc,
            )
            toolItem(
                LocalToolOption.JavascriptEngine,
                HugeIcons.JavaScript,
                R.string.assistant_page_local_tools_javascript_engine_title,
                R.string.assistant_page_local_tools_javascript_engine_desc,
            )
            toolItem(
                LocalToolOption.ChartDisplay,
                HugeIcons.ChartLineData01,
                R.string.assistant_page_local_tools_chart_display_title,
                R.string.assistant_page_local_tools_chart_display_desc,
            )
            item(
                onClick = { showSearchPicker = true },
                headlineContent = { Text(stringResource(R.string.search_ability_search)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_web_search_desc)) },
                trailingContent = {
                    SearchPickerIcon(
                        enableSearch = assistant.enableWebSearch,
                        useBuiltInSearch = assistant.useBuiltInSearch,
                        settings = settings,
                        model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId),
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_workspace)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_workspace_desc)) },
                trailingContent = {
                    val selectedWorkspace = workspaces.find { it.id == assistant.workspaceId?.toString() }
                    Select(
                        options = listOf<WorkspaceEntity?>(null) + workspaces,
                        selectedOption = selectedWorkspace,
                        onOptionSelected = { workspace ->
                            onUpdate(
                                assistant.copy(
                                    workspaceId = workspace?.id?.let { Uuid.parse(it) },
                                )
                            )
                        },
                        fitToOptions = true,
                        optionToString = { workspace ->
                            workspace?.name ?: stringResource(R.string.workspace_no_binding)
                        },
                    )
                },
            )
        }
        CardGroup {
            authorizedToolItem(
                LocalToolOption.Battery,
                HugeIcons.BatteryFull,
                R.string.assistant_page_local_tools_battery_title,
                R.string.assistant_page_local_tools_battery_desc,
            )
            authorizedToolItem(
                LocalToolOption.Clipboard,
                HugeIcons.ClipboardPaste,
                R.string.assistant_page_local_tools_clipboard_title,
                R.string.assistant_page_local_tools_clipboard_desc,
            )
            authorizedToolItem(
                LocalToolOption.Calendar,
                HugeIcons.Calendar03,
                R.string.assistant_page_local_tools_calendar_title,
                R.string.assistant_page_local_tools_calendar_desc,
            )
            authorizedToolItem(
                LocalToolOption.Location,
                HugeIcons.Location01,
                R.string.assistant_page_local_tools_location_title,
                R.string.assistant_page_local_tools_location_desc,
            )
            authorizedToolItem(
                LocalToolOption.ScreenTime,
                HugeIcons.SmartPhone01,
                R.string.assistant_page_local_tools_screen_time_title,
                R.string.assistant_page_local_tools_screen_time_desc,
            )
            // 只有 Android 11 及以上才有
            if (PhoneUseManager.isSupported) {
                authorizedToolItem(
                    option = LocalToolOption.PhoneUse,
                    icon = HugeIcons.Tap01,
                    title = { Text(stringResource(R.string.assistant_page_local_tools_phone_use_title)) },
                    description = {
                        Column {
                            Text(stringResource(R.string.assistant_page_local_tools_phone_use_desc))
                            // 开了工具但服务没开时模型调用会直接报错，在这里说明原因
                            if (LocalToolOption.PhoneUse in assistant.localTools && !phoneUseServiceOn) {
                                Text(
                                    text = stringResource(R.string.assistant_page_local_tools_phone_use_service_off),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
    SearchPickerSheet(
        show = showSearchPicker,
        enableSearch = assistant.enableWebSearch,
        useBuiltInSearch = assistant.useBuiltInSearch,
        settings = settings,
        onSelectSearch = onSelectSearch,
        model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId),
        onDismiss = { showSearchPicker = false },
    )
}
