package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.SearchPickerIcon
import me.rerere.rikkahub.ui.components.ai.SearchPickerSheet
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.permission.PermissionInfo
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import me.rerere.rikkahub.utils.openUsageAccessSettings
import org.koin.androidx.compose.koinViewModel
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
            onUpdateSearchMode = { mode -> vm.updateSearchMode(assistant, mode) },
            onUpdateSearchService = { vm.updateSearchService(it) },
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
    onUpdateSearchMode: (SearchMode) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val permissionRequiredText =
        stringResource(R.string.assistant_page_local_tools_screen_time_permission_required)
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
        fun CardGroupScope.toolItem(option: LocalToolOption, title: Int, description: Int) {
            item(
                headlineContent = { Text(stringResource(title)) },
                supportingContent = { Text(stringResource(description)) },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(option),
                        onCheckedChange = { toggleLocalTool(option, it) },
                    )
                },
            )
        }

        fun CardGroupScope.authorizedToolItem(option: LocalToolOption, title: Int, description: Int) {
            val mode = when {
                option !in assistant.localTools -> LocalToolAuthorizationMode.DENIED
                option in assistant.manualAuthorizationTools -> LocalToolAuthorizationMode.MANUAL
                else -> LocalToolAuthorizationMode.ALWAYS
            }
            item(
                headlineContent = { Text(stringResource(title)) },
                supportingContent = { Text(stringResource(description)) },
                trailingContent = {
                    Select(
                        options = LocalToolAuthorizationMode.entries,
                        selectedOption = mode,
                        onOptionSelected = { setAuthorizationMode(option, it) },
                        fitToOptions = true,
                        optionToString = {
                            stringResource(
                                when (it) {
                                    LocalToolAuthorizationMode.DENIED ->
                                        R.string.local_tool_authorization_mode_denied
                                    LocalToolAuthorizationMode.MANUAL ->
                                        R.string.local_tool_authorization_mode_manual
                                    LocalToolAuthorizationMode.ALWAYS ->
                                        R.string.local_tool_authorization_mode_always
                                }
                            )
                        },
                    )
                },
            )
        }

        CardGroup {
            toolItem(
                LocalToolOption.TimeInfo,
                R.string.assistant_page_local_tools_time_info_title,
                R.string.assistant_page_local_tools_time_info_desc,
            )
            toolItem(
                LocalToolOption.AskUser,
                R.string.assistant_page_local_tools_ask_user_title,
                R.string.assistant_page_local_tools_ask_user_desc,
            )
            toolItem(
                LocalToolOption.Tts,
                R.string.assistant_page_local_tools_tts_title,
                R.string.assistant_page_local_tools_tts_desc,
            )
            toolItem(
                LocalToolOption.JavascriptEngine,
                R.string.assistant_page_local_tools_javascript_engine_title,
                R.string.assistant_page_local_tools_javascript_engine_desc,
            )
            item(
                onClick = { showSearchPicker = true },
                headlineContent = { Text(stringResource(R.string.search_ability_search)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_web_search_desc)) },
                trailingContent = {
                    SearchPickerIcon(
                        enableSearch = assistant.enableWebSearch,
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
                LocalToolOption.Clipboard,
                R.string.assistant_page_local_tools_clipboard_title,
                R.string.assistant_page_local_tools_clipboard_desc,
            )
            authorizedToolItem(
                LocalToolOption.Battery,
                R.string.assistant_page_local_tools_battery_title,
                R.string.assistant_page_local_tools_battery_desc,
            )
            authorizedToolItem(
                LocalToolOption.Location,
                R.string.assistant_page_local_tools_location_title,
                R.string.assistant_page_local_tools_location_desc,
            )
            authorizedToolItem(
                LocalToolOption.ScreenTime,
                R.string.assistant_page_local_tools_screen_time_title,
                R.string.assistant_page_local_tools_screen_time_desc,
            )
            authorizedToolItem(
                LocalToolOption.Calendar,
                R.string.assistant_page_local_tools_calendar_title,
                R.string.assistant_page_local_tools_calendar_desc,
            )
        }
    }
    SearchPickerSheet(
        show = showSearchPicker,
        enableSearch = assistant.enableWebSearch,
        settings = settings,
        onUpdateSearchMode = onUpdateSearchMode,
        onUpdateSearchService = onUpdateSearchService,
        model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId),
        onDismiss = { showSearchPicker = false },
    )
}
