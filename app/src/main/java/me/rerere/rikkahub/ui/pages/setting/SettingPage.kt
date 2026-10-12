package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.ImageUpload
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Megaphone01
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.SecurityKeyUsb
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.isNotConfigured
import me.rerere.rikkahub.data.files.CasStorageStats
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val filesManager: FilesManager = koinInject()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings))
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (settings.isNotConfigured()) {
                item {
                    ProviderConfigWarningCard(navController)
                }
            }

            item("generalSettings") {
                val (containerColor, contentColor) = groupIconColors(0)
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferences) },
                        leadingContent = { CardGroupIcon(HugeIcons.Settings03, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Assistant) },
                        leadingContent = { CardGroupIcon(HugeIcons.LookTop, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_assistant_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_title)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingModels) },
                        leadingContent = { CardGroupIcon(HugeIcons.AiMagic, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_default_model_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_model_page_title)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingWeb) },
                        leadingContent = { CardGroupIcon(HugeIcons.ServerStack01, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_web_server_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_web_server)) },
                    )
                }
            }

            item("modelServices") {
                val (containerColor, contentColor) = groupIconColors(1)
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_model_and_services)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingProvider) },
                        leadingContent = { CardGroupIcon(HugeIcons.Brain02, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_providers_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_provider_page_title)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSearch) },
                        leadingContent = { CardGroupIcon(HugeIcons.GlobalSearch, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_search_service_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_search_title)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingMcp) },
                        leadingContent = { CardGroupIcon(HugeIcons.McpServer, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_mcp_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_mcp_page_title)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSpeech) },
                        leadingContent = { CardGroupIcon(HugeIcons.Megaphone01, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_tts_service_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_tts_service)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingMedia) },
                        leadingContent = { CardGroupIcon(HugeIcons.Image02, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_media_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_media)) },
                    )
                }
            }

            item("chatAndPrompts") {
                val (containerColor, contentColor) = groupIconColors(2)
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_chat_and_prompts)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.QuickMessages) },
                        leadingContent = { CardGroupIcon(HugeIcons.Zap, containerColor = containerColor, contentColor = contentColor) },
                        headlineContent = { Text(stringResource(R.string.quick_messages_page_title)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_quick_messages_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.ModeInjections) },
                        leadingContent = { CardGroupIcon(HugeIcons.MagicWand01, containerColor = containerColor, contentColor = contentColor) },
                        headlineContent = { Text(stringResource(R.string.mode_injection_page_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_mode_injection_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Lorebooks) },
                        leadingContent = { CardGroupIcon(HugeIcons.Book01, containerColor = containerColor, contentColor = contentColor) },
                        headlineContent = { Text(stringResource(R.string.lorebook_page_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_lorebook_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Skills) },
                        leadingContent = { CardGroupIcon(HugeIcons.Puzzle, containerColor = containerColor, contentColor = contentColor) },
                        headlineContent = { Text(stringResource(R.string.skills_page_title)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_agent_skills_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Workspaces) },
                        leadingContent = { CardGroupIcon(HugeIcons.Folder01, containerColor = containerColor, contentColor = contentColor) },
                        headlineContent = { Text(stringResource(R.string.workspace_page_title)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_workspace_desc)) },
                    )
                }
            }

            item("dataSettings") {
                val storageState by produceState<CasStorageStats?>(null) {
                    value = filesManager.countChatFiles()
                }
                val (containerColor, contentColor) = groupIconColors(3)
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_data_settings)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.Backup) },
                        leadingContent = { CardGroupIcon(HugeIcons.Database02, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_data_backup_desc)) },
                        headlineContent = { Text(stringResource(R.string.backup_page_title)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingFiles) },
                        leadingContent = { CardGroupIcon(HugeIcons.ImageUpload, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = {
                            val stats = storageState
                            if (stats == null) {
                                Text(stringResource(R.string.calculating))
                            } else {
                                Text(
                                    stringResource(
                                        R.string.setting_page_chat_storage_desc,
                                        stats.fileCount,
                                        stats.referenceCount,
                                        stats.sizeBytes / 1048576.0
                                    )
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_chat_storage)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Log) },
                        leadingContent = { CardGroupIcon(HugeIcons.Bookshelf01, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_request_logs_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_request_logs)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.PermissionLog) },
                        leadingContent = { CardGroupIcon(HugeIcons.SecurityKeyUsb, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_permission_logs_desc)) },
                        headlineContent = { Text(stringResource(R.string.permission_log_page_title)) },
                    )
                }
            }

            item("aboutSettings") {
                val (containerColor, contentColor) = groupIconColors(4)
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.about_page_title)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingAbout) },
                        onLongClick = { navController.navigate(Screen.Debug) },
                        leadingContent = { CardGroupIcon(HugeIcons.Clapping01, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.setting_page_about_desc)) },
                        headlineContent = { Text(stringResource(R.string.about_page_title)) },
                    )
                }
            }
        }
    }
}

// 每个分组用不同的容器色，按顺序交替
@Composable
private fun groupIconColors(index: Int): Pair<Color, Color> = when (index % 3) {
    0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    1 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun ProviderConfigWarningCard(navController: Navigator) {
    Surface(
        onClick = { navController.navigate(Screen.SettingProvider) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = rememberScreenEdgeCornerShape(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Alert01,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_page_config_api_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.setting_page_config_api_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(
                onClick = { navController.navigate(Screen.SettingProvider) },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.setting_page_config))
            }
        }
    }
}
