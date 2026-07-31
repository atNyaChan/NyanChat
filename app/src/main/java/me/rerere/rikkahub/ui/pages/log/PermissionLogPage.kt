package me.rerere.rikkahub.ui.pages.log

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.JsonInstantPretty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun PermissionLogPage() {
    var logs by remember { mutableStateOf(Logging.getPermissionLogs()) }
    var selectedLog by remember { mutableStateOf<LogEntry.PermissionLog?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.permission_log_page_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(HugeIcons.Delete01, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.permission_log_page_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs, key = { it.id }) { log ->
                    OutlinedItemCard(
                        onClick = { selectedLog = log },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(log.type, style = MaterialTheme.typography.titleSmall)
                                log.granted?.let { granted ->
                                    Text(
                                        text = stringResource(
                                            if (log.alwaysAllowed) {
                                                R.string.permission_log_page_always_allowed
                                            } else if (granted) {
                                                R.string.permission_log_page_granted
                                            } else {
                                                R.string.permission_log_page_denied
                                            }
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (granted) {
                                            androidx.compose.ui.graphics.Color(0xFF2E7D32)
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                            }
                            Text(
                                timeFormat.format(Date(log.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    selectedLog?.let { log ->
        ModalBottomSheet(
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
            onDismissRequest = { selectedLog = null },
        ) {
            PermissionToolDetailContent(
                title = log.type,
                toolName = log.toolName,
                input = log.rawData,
                output = log.resultData,
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.permission_log_page_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        Logging.clearPermissions()
                        logs = emptyList()
                        showClearConfirm = false
                    }
                ) { Text(stringResource(R.string.common_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
internal fun PermissionToolDetailContent(
    title: String,
    toolName: String,
    input: String,
    output: String?,
    headerActions: (@Composable () -> Unit)? = null,
    additionalContent: (@Composable () -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = toolName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = JetbrainsMono,
            )
            headerActions?.invoke()
        }
        PermissionLogDataField(
            label = stringResource(R.string.permission_log_page_input),
            value = input,
            onCopy = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("permission-input", input)))
                }
            },
        )
        output?.let { result ->
            PermissionLogDataField(
                label = stringResource(R.string.permission_log_page_output),
                value = result,
                onCopy = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("permission-output", result)))
                    }
                },
            )
        }
        additionalContent?.invoke()
    }
}

@Composable
private fun PermissionLogDataField(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    CollapsibleLogSection(
        title = label,
        initiallyExpanded = true,
        onCopy = onCopy,
    ) {
        val json = remember(value) {
            runCatching { JsonInstantPretty.parseToJsonElement(value) }.getOrNull()
        }
        if (json != null) {
            JsonTree(json = json, initialExpandLevel = Int.MAX_VALUE)
        } else {
            Text(value, fontFamily = JetbrainsMono)
        }
    }
}
