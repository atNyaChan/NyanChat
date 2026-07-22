package me.rerere.rikkahub.ui.pages.log

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
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

@Composable
fun LogPage() {
    var logs by remember { mutableStateOf(Logging.getRequestLogs()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Logs") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = {
                            Logging.clear()
                            logs = Logging.getRequestLogs()
                        }
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
        UnifiedLogList(
            logs = logs,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
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
                text = "暂无请求记录",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetbrainsMono,
                maxLines = 2
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                log.responseCode?.let { code ->
                    Text(
                        text = "Status: $code",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (code in 200..299) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
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
                Text(
                    text = "Error: $error",
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
    val responseMessage = remember(log.responseBody) {
        log.responseBody?.let(::extractResponseMessage).orEmpty()
    }

    SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CollapsibleLogSection("Details") {
                    DetailSection("Time", dateFormat.format(Date(log.timestamp)))
                    DetailSection("URL", log.url)
                    DetailSection("Method", log.method)
                    log.responseCode?.let { code ->
                        DetailSection("Status Code", code.toString())
                    }
                    log.durationMs?.let { duration ->
                        DetailSection("Duration", "${duration}ms")
                    }
                    log.error?.let { error -> DetailSection("Error", error) }
                }
            }

            if (log.requestHeaders.isNotEmpty()) item {
                CollapsibleLogSection(
                    title = "Request Headers",
                    onCopy = { context.writeClipboardText(headersText(log.requestHeaders)) },
                ) {
                    HighlightedHeaders(log.requestHeaders)
                }
            }

            log.requestBody?.let { body -> item {
                CollapsibleLogSection(
                    title = "Request Body",
                    onCopy = { context.writeClipboardText(body) },
                ) {
                    val jsonElement = remember(body) { parseResponseJson(body) }
                    if (jsonElement != null) {
                        JsonTree(jsonElement, initialExpandLevel = 0)
                    } else {
                        Text(body, fontFamily = JetbrainsMono)
                    }
                }
            } }

            if (log.responseHeaders.isNotEmpty()) item {
                CollapsibleLogSection(
                    title = "Response Headers",
                    onCopy = { context.writeClipboardText(headersText(log.responseHeaders)) },
                ) {
                    HighlightedHeaders(log.responseHeaders)
                }
            }

            log.responseBody?.let { body -> item {
                CollapsibleLogSection(
                    title = "Response Body",
                    onCopy = { context.writeClipboardText(body) },
                ) {
                    val jsonElement = remember(body) { parseResponseJson(body) }
                    if (jsonElement != null) {
                        JsonTree(jsonElement, initialExpandLevel = 0)
                    } else {
                        Text(body, fontFamily = JetbrainsMono)
                    }
                }
            } }

            if (responseMessage.isNotBlank()) item {
                CollapsibleLogSection(
                    title = "Content",
                    initiallyExpanded = true,
                    onCopy = { context.writeClipboardText(responseMessage) },
                ) {
                    Text(responseMessage, fontFamily = JetbrainsMono)
                }
            }
        }
    }
}

@Composable
private fun CollapsibleLogSection(
    title: String,
    initiallyExpanded: Boolean = false,
    onCopy: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column {
        HorizontalDivider()
        Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 8.dp),
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
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

private fun headersText(headers: Map<String, String>): String =
    headers.entries.joinToString("\n") { (name, value) -> "$name: $value" }

private fun extractResponseMessage(body: String): String? {
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
    if (elements.isEmpty()) return null
    val hasLlmEvents = elements.any(::looksLikeLlmEvent)
    if (!hasLlmEvents) return null

    return LlmTextBuilder().also { builder ->
        elements.forEach { appendLlmContent(it, builder) }
    }.toString().trim().takeIf { it.isNotEmpty() }
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

private enum class LlmContentKind { TEXT, REASONING }

private fun appendLlmContent(
    element: JsonElement,
    output: LlmTextBuilder,
    inheritedKind: LlmContentKind? = null,
) {
    when (element) {
        JsonNull -> Unit
        is JsonArray -> element.forEach { appendLlmContent(it, output, inheritedKind) }
        is JsonObject -> {
            val type = (element["type"] as? JsonPrimitive)?.contentOrNull.orEmpty().lowercase()
            val isTool = type.contains("tool_use") || type.contains("tool_call") ||
                type.contains("function_call") || type.contains("tool_result") ||
                type.contains("input_json") || element.containsKey("tool_calls") ||
                element.containsKey("function_call") || element.containsKey("functionCall") ||
                element.containsKey("functionResponse")
            if (isTool) {
                output.appendLabeled("工具", element.withoutNulls().toString() + "\n")
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
                        jsonText(value)?.let { output.appendLabeled("思考", it) }
                    (key == "text" || key == "content" || key == "output_text") && value is JsonPrimitive ->
                        value.contentOrNull?.let {
                            if (kind == LlmContentKind.REASONING) output.appendLabeled("思考", it)
                            else output.appendText(it)
                        }
                    key == "arguments" || key == "input_json_delta" ->
                        jsonText(value)?.let { output.appendLabeled("工具", it) }
                    key == "delta" && value is JsonPrimitive -> value.contentOrNull?.let {
                        if (kind == LlmContentKind.REASONING) output.appendLabeled("思考", it)
                        else if (kind == LlmContentKind.TEXT) output.appendText(it)
                    }
                    key !in setOf("type", "id", "index", "role", "model", "usage") ->
                        appendLlmContent(value, output, kind)
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

private class LlmTextBuilder {
    private val output = StringBuilder()
    private var section: String? = null

    fun appendText(value: String) {
        if (value.isEmpty()) return
        if (section != null) appendSectionBreak()
        if (section != null || output.isEmpty()) output.appendLine("[正文]")
        section = null
        output.append(value)
    }

    fun appendLabeled(label: String, value: String) {
        if (value.isBlank()) return
        if (section != label) {
            appendSectionBreak()
            output.append("[").append(label).appendLine("]")
            section = label
        }
        output.append(value)
    }

    private fun appendSectionBreak() {
        if (output.isEmpty()) return
        if (output.endsWith("\n\n")) return
        if (output.last() != '\n') output.appendLine()
        output.appendLine()
    }

    override fun toString(): String = output.toString()
}

private fun parseResponseJson(body: String): JsonElement? {
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
private fun DetailSection(label: String, value: String) {
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
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )
}

@Composable
private fun HighlightedHeaders(headers: Map<String, String>) {
    val keyColor = MaterialTheme.colorScheme.primary
    val colonColor = MaterialTheme.colorScheme.tertiary
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            headers.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) appendLine()
                withStyle(SpanStyle(color = keyColor, fontWeight = FontWeight.SemiBold)) { append(key) }
                withStyle(SpanStyle(color = colonColor)) { append(":") }
                withStyle(SpanStyle(color = valueColor)) { append(" $value") }
            }
        },
        fontFamily = JetbrainsMono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )
}
