package me.rerere.rikkahub.ui.pages.backup.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.compose.koinInject
import java.io.File

@Composable
fun BackupItemsDescriptionText() {
    val context = LocalContext.current
    val workspaceRepository = koinInject<WorkspaceRepository>()

    val hasFiles = remember(context) {
        listOf(FileFolders.UPLOAD, FileFolders.SKILLS, FileFolders.FONTS)
            .any { folderName ->
                val folder = File(context.filesDir, folderName)
                folder.isDirectory && folder.listFiles().orEmpty().isNotEmpty()
            }
    }

    val hasWorkspace by produceState(initialValue = true) {
        value = runCatching {
            workspaceRepository.list()
                .any { it.shellStatus != WorkspaceShellStatus.DISABLED.name }
        }.getOrDefault(false)
    }

    val suffix = when {
        !hasFiles && !hasWorkspace ->
            stringResource(R.string.backup_page_no_files_or_workspace_suffix)
        !hasFiles ->
            stringResource(R.string.backup_page_no_files_suffix)
        !hasWorkspace ->
            stringResource(R.string.backup_page_no_workspace_suffix)
        else -> ""
    }

    Text(
        text = stringResource(R.string.backup_page_chat_records_always) + suffix,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
