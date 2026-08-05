package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackupReminderConfig
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.utils.toLocalDateTime
import java.time.Instant

@Composable
fun ReminderTab(vm: BackupVM) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val config = settings.backupReminderConfig

    fun updateConfig(update: BackupReminderConfig) {
        vm.updateSettings(settings.copy(backupReminderConfig = update))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardGroup(
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(
                trailingContent = {
                    val intervals = listOf<Int?>(null, 1, 3, 7, 14, 30)
                    Select(
                        options = intervals,
                        selectedOption = config.intervalDays.takeIf { config.enabled },
                        onOptionSelected = { days ->
                            updateConfig(
                                config.copy(
                                    enabled = days != null,
                                    intervalDays = days ?: config.intervalDays,
                                )
                            )
                        },
                        optionToString = { days ->
                            if (days == null) {
                                stringResource(R.string.common_off)
                            } else {
                                stringResource(R.string.backup_page_reminder_interval_days, days)
                            }
                        },
                        fitToOptions = true,
                    )
                },
                headlineContent = { Text(stringResource(R.string.backup_page_reminder_enable)) },
            )

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
            )
        }
    }
}
