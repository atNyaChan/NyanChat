package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val messageJumperMode = when {
        !displaySetting.showMessageJumper -> MessageJumperMode.OFF
        displaySetting.messageJumperOnLeft -> MessageJumperMode.LEFT
        else -> MessageJumperMode.RIGHT
    }

    fun updateDisplaySetting(value: DisplaySetting) {
        displaySetting = value
        vm.updateSettings(settings.copy(displaySetting = value))
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_preferences)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_user_avatar_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_user_avatar_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.showUserAvatar,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showUserAvatar = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_chat_list_model_icon_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_chat_list_model_icon_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.showModelIcon,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showModelIcon = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_model_name_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_model_name_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.showModelName,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showModelName = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_datetime_in_message_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_datetime_in_message_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.showDateTimeInMessage,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showDateTimeInMessage = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_token_usage_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_token_usage_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.showTokenUsage,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showTokenUsage = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_desc)) },
                        trailingContent = {
                            Select(
                                options = MessageJumperMode.entries,
                                selectedOption = messageJumperMode,
                                onOptionSelected = { mode ->
                                    updateDisplaySetting(
                                        displaySetting.copy(
                                            showMessageJumper = mode != MessageJumperMode.OFF,
                                            messageJumperOnLeft = mode == MessageJumperMode.LEFT,
                                        )
                                    )
                                },
                                optionToString = { stringResource(it.labelRes) },
                                fitToOptions = true,
                            )
                        },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingTheme) },
                        headlineContent = { Text(stringResource(R.string.setting_page_theme_setting)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesMore) },
                        headlineContent = { Text(stringResource(R.string.setting_page_more_settings)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                }
            }
        }
    }
}

private enum class MessageJumperMode(val labelRes: Int) {
    OFF(R.string.common_off),
    RIGHT(R.string.setting_display_page_message_jumper_right),
    LEFT(R.string.setting_display_page_message_jumper_left),
}
