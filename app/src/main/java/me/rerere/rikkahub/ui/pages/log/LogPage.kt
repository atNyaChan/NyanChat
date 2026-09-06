package me.rerere.rikkahub.ui.pages.log

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.FilterHorizontal
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.RequestInterceptController
import me.rerere.rikkahub.data.ai.RequestInterceptMode
import me.rerere.rikkahub.data.ai.isLlmRequest
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.writeClipboardText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter { ALL, LLM_ONLY, NON_LLM_ONLY }

@Composable
fun LogPage() {
    var logs by remember { mutableStateOf(Logging.getRequestLogs()) }
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        while (true) {
            logs = Logging.getRequestLogs()
            delay(500)
        }
    }

    val visibleLogs = remember(logs, filter) {
        when (filter) {
            LogFilter.ALL -> logs
            LogFilter.LLM_ONLY -> logs.filter(::isLlmRequest)
            LogFilter.NON_LLM_ONLY -> logs.filterNot(::isLlmRequest)
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_request_logs)) },
                navigationIcon = { BackButton() },
                actions = {
                    Box {
                        IconButton(
                            onClick = { filterMenuExpanded = true }
                        ) {
                            Icon(
                                HugeIcons.FilterHorizontal,
                                contentDescription = stringResource(R.string.log_page_filter),
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false },
                            shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.log_page_filter_all)) },
                                leadingIcon = {
                                    RadioButton(selected = filter == LogFilter.ALL, onClick = null)
                                },
                                onClick = {
                                    filter = LogFilter.ALL
                                    filterMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.log_page_filter_llm_only)) },
                                leadingIcon = {
                                    RadioButton(selected = filter == LogFilter.LLM_ONLY, onClick = null)
                                },
                                onClick = {
                                    filter = LogFilter.LLM_ONLY
                                    filterMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.log_page_filter_non_llm_only)) },
                                leadingIcon = {
                                    RadioButton(selected = filter == LogFilter.NON_LLM_ONLY, onClick = null)
                                },
                                onClick = {
                                    filter = LogFilter.NON_LLM_ONLY
                                    filterMenuExpanded = false
                                },
                            )
                        }
                    }
                    IconButton(
                        onClick = { showClearConfirm = true }
                    ) {
                        Icon(HugeIcons.Delete01, null)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            InterceptRequestCard(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            )
            UnifiedLogList(
                logs = visibleLogs,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.log_page_clear_confirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        Logging.clear()
                        logs = Logging.getRequestLogs()
                        showClearConfirm = false
                    }
                ) { Text(stringResource(R.string.common_confirm_action)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun InterceptRequestCard(modifier: Modifier = Modifier) {
    val interceptMode = RequestInterceptController.mode
    CardGroup(modifier = modifier) {
        item(
            headlineContent = {
                Text(stringResource(R.string.log_page_intercept_title))
            },
            supportingContent = {
                Text(stringResource(R.string.log_page_intercept_desc))
            },
            trailingContent = {
                Select(
                    options = RequestInterceptMode.entries,
                    selectedOption = interceptMode,
                    onOptionSelected = { RequestInterceptController.mode = it },
                    optionToString = {
                        stringResource(
                            when (it) {
                                RequestInterceptMode.OFF -> R.string.log_page_intercept_off
                                RequestInterceptMode.LLM_ONLY -> R.string.log_page_intercept_llm_only
                                RequestInterceptMode.ALL -> R.string.log_page_intercept_all
                            }
                        )
                    },
                    fitToOptions = true,
                )
            },
        )
    }
}

@Composable
private fun UnifiedLogList(
    logs: List<LogEntry.RequestLog>,
    modifier: Modifier = Modifier
) {
    var selectedLog by remember { mutableStateOf<LogEntry.RequestLog?>(null) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val sortedLogs = remember(logs) { logs.sortedByDescending { it.timestamp } }

    if (sortedLogs.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.log_page_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(sortedLogs, key = { it.id }, contentType = { it.javaClass.simpleName }) { log ->
                RequestLogCard(
                    log = log,
                    onClick = {
                        selectedLog = log
                        scope.launch { sheetState.show() }
                    }
                )
            }
        }
    }

    selectedLog?.let { log ->
        ModalBottomSheet(
            onDismissRequest = { selectedLog = null },
            sheetState = sheetState
        ) {
            RequestLogDetail(log)
        }
    }
}

@Composable
private fun RequestLogCard(log: LogEntry.RequestLog, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val codeFontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings
    val statusCodeColor = if (log.responseCode == null || log.responseCode in 200..299) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val requestModel = remember(log.requestBody) {
        log.requestBody?.let(::extractRequestModel)
    }

    OutlinedItemCard(
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.method,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dateFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = log.url,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = codeFontFeatureSettings,
                ),
                fontFamily = JetbrainsMono,
                maxLines = 2
            )

            if (isLlmRequest(log) && requestModel != null) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("Model:")
                        }
                        append(" ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = JetbrainsMono,
                                fontFeatureSettings = codeFontFeatureSettings,
                                fontWeight = FontWeight.Bold,
                            )
                        ) {
                            append(requestModel)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                log.responseCode?.let { code ->
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append("Status:")
                            }
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = statusCodeColor,
                                    fontFamily = JetbrainsMono,
                                    fontFeatureSettings = codeFontFeatureSettings,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            ) {
                                append(code.toString())
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                log.durationMs?.let { duration ->
                    Text(
                        text = "${duration}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            log.error?.let { error ->
                val displayError = if (error == "stream_response_interrupted") {
                    stringResource(R.string.log_page_stream_interrupted)
                } else {
                    error
                }
                Text(
                    text = "Error: $displayError",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RequestLogDetail(log: LogEntry.RequestLog) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    val context = LocalContext.current
    val labels = LlmLabels(
        text = stringResource(R.string.log_page_content_text),
        reasoning = stringResource(R.string.log_page_content_reasoning),
        tool = stringResource(R.string.log_page_content_tool),
    )
    val responseSections = remember(log.url, log.method, log.responseBody, labels) {
        log.responseBody
            ?.takeIf { isLlmRequest(log) }
            ?.let { extractResponseSections(it, labels) }
            .orEmpty()
    }
    val requestModel = remember(log.requestBody) {
        log.requestBody?.let(::extractRequestModel)
    }
    val codeFontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings

    SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CollapsibleLogSection("Details") {
                    DetailSection("Time", dateFormat.format(Date(log.timestamp)), codeFontFeatureSettings)
                    DetailSection("URL", log.url, codeFontFeatureSettings)
                    DetailSection("Method", log.method, codeFontFeatureSettings)
                    log.responseCode?.let { code ->
                        DetailSection("Status Code", code.toString(), codeFontFeatureSettings)
                    }
                    log.durationMs?.let { duration ->
                        DetailSection("Duration", "${duration}ms", codeFontFeatureSettings)
                    }
                    log.error?.let { error -> DetailSection("Error", error, codeFontFeatureSettings) }
                }
            }

            if (log.requestHeaders.isNotEmpty()) item {
                CollapsibleLogSection(
                    title = "Request Headers",
                    onCopy = { context.writeClipboardText(headersText(log.requestHeaders)) },
                ) {
                    HighlightedHeaders(log.requestHeaders, codeFontFeatureSettings)
                }
            }

            log.requestBody?.let { body -> item {
                CollapsibleLogSection(
                    title = "Request Body",
                    initiallyExpanded = true,
                    onCopy = { context.writeClipboardText(body) },
                ) {
                    val jsonElement = remember(body) { parseResponseJson(body) }
                    if (jsonElement != null) {
                        JsonTree(jsonElement, initialExpandLevel = 0, fontFeatureSettings = codeFontFeatureSettings)
                    } else {
                        Text(body, fontFamily = JetbrainsMono, style = LocalTextStyle.current.copy(fontFeatureSettings = codeFontFeatureSettings))
                    }
                }
            } }

            if (log.responseHeaders.isNotEmpty()) item {
                CollapsibleLogSection(
                    title = "Response Headers",
                    onCopy = { context.writeClipboardText(headersText(log.responseHeaders)) },
                ) {
                    HighlightedHeaders(log.responseHeaders, codeFontFeatureSettings)
                }
            }

            log.responseBody?.let { body -> item {
                CollapsibleLogSection(
                    title = "Response Body",
                    initiallyExpanded = true,
                    onCopy = { context.writeClipboardText(body) },
                ) {
                    val jsonElement = remember(body) { parseResponseJson(body) }
                    if (jsonElement != null) {
                        JsonTree(jsonElement, initialExpandLevel = 0, fontFeatureSettings = codeFontFeatureSettings)
                    } else {
                        Text(body, fontFamily = JetbrainsMono, style = LocalTextStyle.current.copy(fontFeatureSettings = codeFontFeatureSettings))
                    }
                }
            } }

            if (responseSections.isNotEmpty()) item {
                CollapsibleLogSection(
                    title = "Content",
                    initiallyExpanded = true,
                ) {
                    requestModel?.let { model ->
                        Text(
                            text = buildAnnotatedString {
                                append(stringResource(R.string.log_page_model))
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = JetbrainsMono,
                                        fontFeatureSettings = codeFontFeatureSettings,
                                        fontWeight = FontWeight.Bold,
                                    )
                                ) {
                                    append(model)
                                }
                            }
                        )
                    }
                    responseSections.forEach { section ->
                        if (section.kind == LlmContentKind.TOOL) {
                            LogSection(
                                title = section.label,
                                showDivider = false,
                                onCopy = { context.writeClipboardText(section.content) },
                            ) {
                                val jsonElement = remember(section.content) {
                                    parseToolJson(section.content)
                                }
                                if (jsonElement != null) {
                                    JsonTree(jsonElement, initialExpandLevel = 0, fontFeatureSettings = codeFontFeatureSettings)
                                } else {
                                    Text(
                                        text = section.content,
                                        fontFamily = JetbrainsMono,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFeatureSettings = codeFontFeatureSettings,
                                        ),
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = section.content,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(section.label) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { context.writeClipboardText(section.content) }
                                    ) {
                                        Icon(
                                            HugeIcons.Copy01,
                                            contentDescription = stringResource(R.string.copy),
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetbrainsMono,
                                    fontFeatureSettings = codeFontFeatureSettings,
                                ),
                                minLines = 2,
                                maxLines = 12,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CollapsibleLogSection(
    title: String,
    initiallyExpanded: Boolean = false,
    showDivider: Boolean = true,
    onCopy: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column {
        if (showDivider) {
            HorizontalDivider()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 8.dp))
            onCopy?.let { copy ->
                IconButton(onClick = copy, modifier = Modifier.size(32.dp)) {
                    Icon(HugeIcons.Copy01, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
            }
        }
        AnimatedVisibility(expanded) {
            ProvideTextStyle(
                MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            ) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun LogSection(
    title: String,
    showDivider: Boolean = true,
    onCopy: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column {
        if (showDivider) {
            HorizontalDivider()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            onCopy?.let { copy ->
                IconButton(onClick = copy, modifier = Modifier.size(32.dp)) {
                    Icon(HugeIcons.Copy01, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
            }
        }
        ProvideTextStyle(
            MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = { content() },
            )
        }
    }
}

internal fun headersText(headers: Map<String, String>): String =
    headers.entries.joinToString("\n") { (name, value) -> "$name: $value" }

private fun extractResponseSections(body: String, labels: LlmLabels): List<LlmContentSection> {
    val payloads = body.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("data:") }
        .map { it.removePrefix("data:").trim() }
        .filter { it.isNotBlank() && it != "[DONE]" }
        .toList()
        .ifEmpty { listOf(body) }
    val elements = payloads.mapNotNull {
        runCatching { JsonInstantPretty.parseToJsonElement(it) }.getOrNull()
    }
    if (elements.isEmpty()) return emptyList()
    val hasLlmEvents = elements.any(::looksLikeLlmEvent)
    if (!hasLlmEvents) return emptyList()

    return LlmTextBuilder(labels).also { builder ->
        elements
            .filterNot(::isCompletedResponseSnapshot)
            .forEach { element ->
                collectToolCalls(element, builder)
                appendLlmContent(element, builder, labels = labels)
            }
    }.build()
}

private fun isCompletedResponseSnapshot(element: JsonElement): Boolean {
    val type = (element as? JsonObject)
        ?.get("type")
        ?.let { it as? JsonPrimitive }
        ?.contentOrNull
        ?.lowercase()
    return type == "response.completed" ||
        type == "response.incomplete" ||
        type == "response.failed" ||
        type?.endsWith(".done") == true
}

private fun looksLikeLlmEvent(element: JsonElement): Boolean = when (element) {
    is JsonArray -> element.any(::looksLikeLlmEvent)
    is JsonObject -> {
        val type = (element["type"] as? JsonPrimitive)?.contentOrNull.orEmpty().lowercase()
        val role = (element["role"] as? JsonPrimitive)?.contentOrNull.orEmpty().lowercase()
        element.keys.any { it in setOf("choices", "candidates", "content_block", "tool_calls", "reasoning") } ||
            type.contains("content_block") || type.contains("output_text") ||
            type.contains("tool_use") || type.contains("reasoning") || type.contains("thinking") ||
            (role == "assistant" && element.containsKey("content")) ||
            element.values.any(::looksLikeLlmEvent)
    }
    else -> false
}

private enum class LlmContentKind { TEXT, REASONING, TOOL }

private fun appendLlmContent(
    element: JsonElement,
    output: LlmTextBuilder,
    inheritedKind: LlmContentKind? = null,
    labels: LlmLabels,
) {
    when (element) {
        JsonNull -> Unit
        is JsonArray -> element.forEach { appendLlmContent(it, output, inheritedKind, labels) }
        is JsonObject -> {
            val type = (element["type"] as? JsonPrimitive)?.contentOrNull.orEmpty().lowercase()
            val isTool = type.contains("tool_use") || type.contains("tool_call") ||
                type.contains("function_call") || type.contains("tool_result") ||
                type.contains("input_json") || element.containsKey("tool_calls") ||
                element.containsKey("function_call") || element.containsKey("functionCall") ||
                element.containsKey("functionResponse")
            if (isTool) {
                return
            }
            val kind = when {
                type.contains("reasoning") || type.contains("thinking") -> LlmContentKind.REASONING
                (element["thought"] as? JsonPrimitive)?.contentOrNull == "true" -> LlmContentKind.REASONING
                type.contains("text_delta") || type.contains("output_text") || type == "text" -> LlmContentKind.TEXT
                else -> inheritedKind
            }
            element.forEach { (key, value) ->
                when {
                    key == "reasoning" || key == "reasoning_content" || key == "thinking" ->
                        jsonText(value)?.let { output.appendLabeled(labels.reasoning, it) }
                    (key == "text" || key == "content" || key == "output_text") && value is JsonPrimitive ->
                        value.contentOrNull?.let {
                            if (kind == LlmContentKind.REASONING) output.appendLabeled(labels.reasoning, it)
                            else output.appendText(it)
                        }
                    key == "arguments" || key == "input_json_delta" ->
                        Unit
                    key == "delta" && value is JsonPrimitive -> value.contentOrNull?.let {
                        if (kind == LlmContentKind.REASONING) output.appendLabeled(labels.reasoning, it)
                        else if (kind == LlmContentKind.TEXT) output.appendText(it)
                    }
                    key !in setOf("type", "id", "index", "role", "model", "usage") ->
                        appendLlmContent(value, output, kind, labels)
                }
            }
        }
        else -> Unit
    }
}

private fun jsonText(element: JsonElement): String? = when (element) {
    JsonNull -> null
    is JsonPrimitive -> element.contentOrNull
    else -> element.withoutNulls().toString()
}

private fun JsonElement.withoutNulls(): JsonElement = when (this) {
    JsonNull -> JsonNull
    is JsonArray -> JsonArray(mapNotNull { child ->
        child.takeUnless { it is JsonNull }?.withoutNulls()
    })
    is JsonObject -> JsonObject(mapNotNull { (key, child) ->
        child.takeUnless { it is JsonNull }?.let { key to it.withoutNulls() }
    }.toMap())
    else -> this
}

private data class LlmLabels(val text: String, val reasoning: String, val tool: String)

private data class LlmContentSection(
    val label: String,
    val content: String,
    val kind: LlmContentKind,
)

private class LlmTextBuilder(private val labels: LlmLabels) {
    private val sections = mutableListOf<LlmContentSection>()
    private val toolCalls = linkedMapOf<String, ToolCallState>()

    fun appendText(value: String) {
        append(labels.text, value, LlmContentKind.TEXT)
    }

    fun appendLabeled(label: String, value: String) {
        append(label, value, LlmContentKind.REASONING)
    }

    fun updateToolCall(
        key: String,
        id: String? = null,
        name: String? = null,
        argumentsDelta: String? = null,
        arguments: JsonElement? = null,
    ) {
        val state = toolCalls.getOrPut(key) { ToolCallState() }
        if (!id.isNullOrBlank()) state.id = id
        if (!name.isNullOrBlank()) state.name = name
        if (arguments != null) state.arguments = arguments
        if (!argumentsDelta.isNullOrEmpty()) state.argumentsText.append(argumentsDelta)
    }

    private fun append(label: String, value: String, kind: LlmContentKind) {
        if (value.isEmpty()) return
        val last = sections.lastOrNull()
        if (last?.kind == kind) {
            sections[sections.lastIndex] = last.copy(content = mergeChunks(last.content, value))
        } else {
            sections += LlmContentSection(label = label, content = value, kind = kind)
        }
    }

    /**
     * 合并同一 section 的新分块。
     * 部分供应商或代理/中继会把同一段思考/文本内容以多种方式重复下发：
     * - 累积式分块（每个分块都包含到目前为止的完整内容）
     * - 快照事件 + 增量 delta 事件重复
     * - 结束阶段重发完整内容
     * 直接拼接会导致日志中思考内容变成两段一样的内容，这里只保留不重叠的新增部分。
     */
    private fun mergeChunks(existing: String, incoming: String): String {
        if (incoming.isEmpty()) return existing
        if (existing.isEmpty()) return incoming
        if (incoming.startsWith(existing)) return incoming
        if (existing.contains(incoming)) return existing
        return existing + incoming
    }

    fun build(): List<LlmContentSection> {
        val contentSections = sections.mapNotNull { section ->
            section.copy(content = section.content.trim()).takeIf { it.content.isNotEmpty() }
        }.toMutableList()
        if (toolCalls.isNotEmpty()) {
            val calls = toolCalls.values.map(ToolCallState::toJson)
            val json = if (calls.size == 1) calls.single() else JsonArray(calls)
            contentSections += LlmContentSection(
                label = labels.tool,
                content = json.toString(),
                kind = LlmContentKind.TOOL,
            )
        }
        return contentSections
    }
}

private class ToolCallState {
    var id: String? = null
    var name: String? = null
    var arguments: JsonElement? = null
    val argumentsText = StringBuilder()

    fun toJson(): JsonObject {
        val fields = linkedMapOf<String, JsonElement>()
        id?.let { fields["id"] = JsonPrimitive(it) }
        name?.let { fields["name"] = JsonPrimitive(it) }
        val streamedArguments = argumentsText.toString()
        val resolvedArguments = streamedArguments
            .takeIf { it.isNotEmpty() }
            ?.let { raw ->
                runCatching { JsonInstantPretty.parseToJsonElement(raw) }
                    .getOrElse { JsonPrimitive(raw) }
            }
            ?: arguments
        resolvedArguments?.let { fields["arguments"] = it }
        return JsonObject(fields)
    }
}

private fun collectToolCalls(
    element: JsonElement,
    output: LlmTextBuilder,
    inheritedKey: String? = null,
) {
    when (element) {
        is JsonArray -> element.forEachIndexed { index, child ->
            collectToolCalls(child, output, inheritedKey ?: index.toString())
        }
        is JsonObject -> {
            val type = element.stringValue("type").orEmpty().lowercase()
            val eventKey = element.primitiveValue("output_index")
                ?: element.primitiveValue("index")
                ?: inheritedKey
                ?: element.stringValue("call_id")
                ?: element.stringValue("item_id")
                ?: element.stringValue("id")

            (element["tool_calls"] as? JsonArray)?.forEachIndexed { index, call ->
                val callObject = call as? JsonObject ?: return@forEachIndexed
                val function = callObject["function"] as? JsonObject
                val key = callObject.primitiveValue("index")
                    ?: callObject.stringValue("id")
                    ?: eventKey?.let { "$it:$index" }
                    ?: "tool:$index"
                output.updateToolCall(
                    key = key,
                    id = callObject.stringValue("id"),
                    name = function?.stringValue("name"),
                    argumentsDelta = function?.stringValue("arguments"),
                )
            }

            when {
                type.contains("function_call_arguments") -> {
                    output.updateToolCall(
                        key = eventKey ?: "function_call",
                        id = element.stringValue("call_id"),
                        argumentsDelta = element.stringValue("delta"),
                    )
                    return
                }
                type.contains("input_json_delta") -> {
                    output.updateToolCall(
                        key = eventKey ?: "tool_use",
                        argumentsDelta = element.stringValue("partial_json")
                            ?: element.stringValue("delta"),
                    )
                    return
                }
                type == "tool_use" || type == "function_call" -> {
                    output.updateToolCall(
                        key = eventKey ?: type,
                        id = element.stringValue("call_id") ?: element.stringValue("id"),
                        name = element.stringValue("name"),
                        argumentsDelta = element.stringValue("arguments"),
                        arguments = element["input"],
                    )
                    return
                }
            }

            (element["functionCall"] as? JsonObject)?.let { function ->
                output.updateToolCall(
                    key = eventKey ?: function.stringValue("name") ?: "functionCall",
                    name = function.stringValue("name"),
                    arguments = function["args"],
                )
            }
            (element["function_call"] as? JsonObject)?.let { function ->
                output.updateToolCall(
                    key = eventKey ?: function.stringValue("name") ?: "function_call",
                    name = function.stringValue("name"),
                    argumentsDelta = function.stringValue("arguments"),
                )
            }

            element.forEach { (key, value) ->
                if (key !in setOf("tool_calls", "functionCall", "function_call", "functionResponse")) {
                    collectToolCalls(value, output, eventKey)
                }
            }
        }
        else -> Unit
    }
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.primitiveValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun extractRequestModel(body: String): String? =
    runCatching { JsonInstantPretty.parseToJsonElement(body) }
        .getOrNull()
        ?.let { it as? JsonObject }
        ?.get("model")
        ?.let { it as? JsonPrimitive }
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

private fun parseToolJson(content: String): JsonElement? {
    runCatching { JsonInstantPretty.parseToJsonElement(content) }.getOrNull()?.let { return it }
    val elements = content.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { runCatching { JsonInstantPretty.parseToJsonElement(it) }.getOrNull() }
        .toList()
    return elements.takeIf { it.isNotEmpty() }?.let { values ->
        if (values.size == 1) values.single() else JsonArray(values)
    }
}

internal fun parseResponseJson(body: String): JsonElement? {
    runCatching { JsonInstantPretty.parseToJsonElement(body) }.getOrNull()?.let { return it }
    val events = body.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("data:") }
        .map { it.removePrefix("data:").trim() }
        .filter { it.isNotBlank() && it != "[DONE]" }
        .mapNotNull { runCatching { JsonInstantPretty.parseToJsonElement(it) }.getOrNull() }
        .toList()
    return events.takeIf { it.isNotEmpty() }?.let(::JsonArray)
}

@Composable
internal fun DetailSection(label: String, value: String, fontFeatureSettings: String? = null) {
    val keyColor = MaterialTheme.colorScheme.primary
    val colonColor = MaterialTheme.colorScheme.tertiary
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = keyColor, fontWeight = FontWeight.SemiBold)) { append(label) }
            withStyle(SpanStyle(color = colonColor)) { append(": ") }
            withStyle(SpanStyle(color = valueColor)) { append(value) }
        },
        fontFamily = JetbrainsMono,
        style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

@Composable
internal fun HighlightedHeaders(
    headers: Map<String, String>,
    fontFeatureSettings: String? = null,
) {
    HighlightedKeyValues(headers, fontFeatureSettings)
}

@Composable
private fun HighlightedKeyValues(
    values: Map<String, String>,
    fontFeatureSettings: String? = null,
) {
    val keyColor = MaterialTheme.colorScheme.primary
    val colonColor = MaterialTheme.colorScheme.tertiary
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            values.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) appendLine()
                withStyle(SpanStyle(color = keyColor, fontWeight = FontWeight.SemiBold)) { append(key) }
                withStyle(SpanStyle(color = colonColor)) { append(":") }
                withStyle(SpanStyle(color = valueColor)) { append(" $value") }
            }
        },
        fontFamily = JetbrainsMono,
        style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}
