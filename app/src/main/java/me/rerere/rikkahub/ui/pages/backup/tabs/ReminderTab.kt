package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackupReminderConfig
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.utils.toLocalDateTime
import java.time.Instant

private val ReminderIntervals = listOf(1, 3, 7, 14, 30)

@Composable
fun ReminderTab(vm: BackupVM) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val config = settings.backupReminderConfig

    fun updateConfig(update: BackupReminderConfig) {
        vm.updateSettings { it.copy(backupReminderConfig = update) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardGroup {
            FormItem(
                label = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.backup_page_reminder_interval))
                        CardGroupValue(
                            if (config.enabled) {
                                stringResource(R.string.backup_page_reminder_interval_days, config.intervalDays)
                            } else {
                                stringResource(R.string.common_off)
                            }
                        )
                    }
                },
            ) {
                // 第一个是「关」，选中后不启用备份提醒；按钮里只放数字，带单位的完整取值显示在标题行右侧
                ConnectedChoiceButtons(
                    options = listOf<Int?>(null) + ReminderIntervals,
                    selected = config.intervalDays.takeIf { config.enabled },
                    onSelect = { days ->
                        updateConfig(
                            config.copy(
                                enabled = days != null,
                                intervalDays = days ?: config.intervalDays,
                            )
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { days, selected ->
                    ConnectedChoiceLabel(
                        text = days?.toString() ?: stringResource(R.string.common_off),
                        selected = selected,
                    )
                }
            }

            item(
                headlineContent = {
                    Text(
                        if (config.lastBackupTime == 0L) {
                            stringResource(R.string.backup_page_reminder_no_record)
                        } else {
                            stringResource(
                                R.string.backup_page_reminder_last_time,
                                Instant.ofEpochMilli(config.lastBackupTime).toLocalDateTime()
                            )
                        }
                    )
                },
                leadingContent = { CardGroupIcon(HugeIcons.Clock01) },
            )
        }
    }
}
