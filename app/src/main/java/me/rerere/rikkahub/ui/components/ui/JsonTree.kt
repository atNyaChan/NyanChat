package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape

@Composable
fun JsonTree(
    json: JsonElement,
    modifier: Modifier = Modifier,
    initialExpandLevel: Int = 1,
    fontFeatureSettings: String? = null,
) {
    var selectedString by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.horizontalScroll(rememberScrollState())) {
        JsonNode(
            element = json,
            key = null,
            depth = 0,
            initialExpandLevel = initialExpandLevel,
            onStringClick = { selectedString = it },
            fontFeatureSettings = fontFeatureSettings,
        )
    }

    selectedString?.let { content ->
        PreviewStringDialog(
            content = content,
            onDismiss = { selectedString = null },
            fontFeatureSettings = fontFeatureSettings,
        )
    }
}

@Composable
private fun PreviewStringDialog(
    content: String,
    onDismiss: () -> Unit,
    fontFeatureSettings: String? = null,
) {
    var enableMarkdown by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp),
            shape = rememberScreenEdgeCornerShape(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.log_page_preview_string),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = rememberScreenEdgeCornerShape(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.log_page_enable_markdown_rendering),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = enableMarkdown,
                            onCheckedChange = { enableMarkdown = it },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (enableMarkdown) {
                        CompositionLocalProvider(LocalSettings provides Settings()) {
                            SelectionContainer {
                                MarkdownBlock(content)
                            }
                        }
                    } else {
                        BasicTextField(
                            value = content,
                            onValueChange = {},
                            readOnly = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetbrainsMono,
                                fontFeatureSettings = fontFeatureSettings,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonNode(
    element: JsonElement,
    key: String?,
    depth: Int,
    initialExpandLevel: Int,
    onStringClick: (String) -> Unit,
    fontFeatureSettings: String? = null,
) {
    when (element) {
        is JsonObject -> JsonObjectNode(
            element, key, depth, initialExpandLevel, onStringClick, fontFeatureSettings,
        )
        is JsonArray -> JsonArrayNode(
            element, key, depth, initialExpandLevel, onStringClick, fontFeatureSettings,
        )
        is JsonPrimitive -> JsonPrimitiveNode(element, key, depth, onStringClick, fontFeatureSettings)
        is JsonNull -> JsonNullNode(key, depth, fontFeatureSettings)
    }
}

@Composable
private fun JsonObjectNode(
    obj: JsonObject,
    key: String?,
    depth: Int,
    initialExpandLevel: Int,
    onStringClick: (String) -> Unit,
    fontFeatureSettings: String? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(depth < initialExpandLevel) }
    val entries = remember(obj) { obj.entries.toList() }

    Column {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = (depth * 16).dp)
                    .size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (key != null) {
                KeyText(key, fontFeatureSettings)
                Text(": ", fontFamily = JetbrainsMono, style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings))
            }
            Text(
                text = if (expanded) "{" else "{ ... } (${entries.size})",
                fontFamily = JetbrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                entries.forEach { (childKey, childElement) ->
                    JsonNode(
                        element = childElement,
                        key = childKey,
                        depth = depth + 1,
                        initialExpandLevel = initialExpandLevel,
                        onStringClick = onStringClick,
                        fontFeatureSettings = fontFeatureSettings,
                    )
                }
                Row(modifier = Modifier.padding(start = (depth * 16 + 14).dp)) {
                    Text(
                        text = "}",
                        fontFamily = JetbrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings),
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonArrayNode(
    array: JsonArray,
    key: String?,
    depth: Int,
    initialExpandLevel: Int,
    onStringClick: (String) -> Unit,
    fontFeatureSettings: String? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(depth < initialExpandLevel) }

    Column {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = (depth * 16).dp)
                    .size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (key != null) {
                KeyText(key, fontFeatureSettings)
                Text(": ", fontFamily = JetbrainsMono, style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings))
            }
            Text(
                text = if (expanded) "[" else "[ ... ] (${array.size})",
                fontFamily = JetbrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                array.forEachIndexed { index, childElement ->
                    JsonNode(
                        element = childElement,
                        key = index.toString(),
                        depth = depth + 1,
                        initialExpandLevel = initialExpandLevel,
                        onStringClick = onStringClick,
                        fontFeatureSettings = fontFeatureSettings,
                    )
                }
                Row(modifier = Modifier.padding(start = (depth * 16 + 14).dp)) {
                    Text(
                        text = "]",
                        fontFamily = JetbrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings),
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonPrimitiveNode(
    primitive: JsonPrimitive,
    key: String?,
    depth: Int,
    onStringClick: (String) -> Unit,
    fontFeatureSettings: String? = null,
) {
    Row(
        modifier = Modifier.padding(start = (depth * 16 + 14).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (key != null) {
            KeyText(key, fontFeatureSettings)
            Text(": ", fontFamily = JetbrainsMono, style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings))
        }
        ValueText(
            primitive = primitive,
            onClick = if (primitive.isString) {
                { onStringClick(primitive.contentOrNull ?: "") }
            } else null,
            fontFeatureSettings = fontFeatureSettings,
        )
    }
}

@Composable
private fun JsonNullNode(
    key: String?,
    depth: Int,
    fontFeatureSettings: String? = null,
) {
    Row(
        modifier = Modifier.padding(start = (depth * 16 + 14).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (key != null) {
            KeyText(key, fontFeatureSettings)
            Text(": ", fontFamily = JetbrainsMono, style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings))
        }
        Text(
            text = "null",
            fontFamily = JetbrainsMono,
            color = MaterialTheme.colorScheme.outline,
            style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings),
        )
    }
}

@Composable
private fun KeyText(key: String, fontFeatureSettings: String? = null) {
    Text(
        text = "\"$key\"",
        fontFamily = JetbrainsMono,
        color = MaterialTheme.colorScheme.primary,
        style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings),
    )
}

@Composable
private fun ValueText(
    primitive: JsonPrimitive,
    onClick: (() -> Unit)? = null,
    fontFeatureSettings: String? = null,
) {
    val (text, color) = when {
        primitive.isString -> {
            val content = (primitive.contentOrNull ?: "")
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            "\"$content\"" to Color(0xFF6A8759)
        }

        primitive.booleanOrNull != null -> {
            primitive.content to Color(0xFFCC7832)
        }

        primitive.longOrNull != null || primitive.doubleOrNull != null -> {
            primitive.content to Color(0xFF6897BB)
        }

        else -> {
            primitive.content to MaterialTheme.colorScheme.onSurface
        }
    }

    Text(
        text = text,
        fontFamily = JetbrainsMono,
        color = color,
        textDecoration = if (onClick != null) TextDecoration.Underline else null,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings),
    )
}
