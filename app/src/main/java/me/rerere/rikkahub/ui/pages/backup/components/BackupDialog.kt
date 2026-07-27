package me.rerere.rikkahub.ui.pages.backup.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import kotlin.system.exitProcess

@Composable
fun BackupDialog(
    importing: Boolean = false,
    exporting: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                when {
                    exporting -> stringResource(R.string.backup_page_exporting)
                    importing -> stringResource(R.string.backup_page_importing)
                    else -> stringResource(R.string.backup_page_restore_success)
                }
            )
        },
        text = {
            if (importing || exporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.backup_page_restart_desc))
            }
        },
        confirmButton = {
            if (!importing && !exporting) {
                Button(
                    onClick = {
                        exitProcess(0)
                    }
                ) {
                    Text(stringResource(R.string.backup_page_restart_app))
                }
            }
        },
    )
}

@Composable
fun RestoreWarningDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_page_restore_warning_title)) },
        text = { Text(stringResource(R.string.backup_page_restore_warning_desc)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_page_restore_warning_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
