package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.highlight.LocalCodeHighlighter
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeVisualTransformation
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings

private val jsonLenient = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

@Composable
fun CustomHeaders(headers: List<CustomHeader>, onUpdate: (List<CustomHeader>) -> Unit) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        headers.forEachIndexed { index, header ->
            var headerName by remember(header.name) { mutableStateOf(header.name) }
            var headerValue by remember(header.value) { mutableStateOf(header.value) }

            CardGroup {
                item(
                    supportingContent = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = headerName,
                                onValueChange = {
                                    headerName = it
                                    val updatedHeaders = headers.toMutableList()
                                    updatedHeaders[index] = updatedHeaders[index].copy(name = it.trim())
                                    onUpdate(updatedHeaders)
                                },
                                label = { Text(stringResource(R.string.assistant_page_header_name)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = headerValue,
                                onValueChange = {
                                    headerValue = it
                                    val updatedHeaders = headers.toMutableList()
                                    updatedHeaders[index] =
                                        updatedHeaders[index].copy(value = it.trim())
                                    onUpdate(updatedHeaders)
                                },
                                label = { Text(stringResource(R.string.assistant_page_header_value)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { pendingDeleteIndex = index }) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.assistant_page_delete_header)
                            )
                        }
                    },
                    headlineContent = {},
                )
            }
        }

        Button(
            onClick = {
                val updatedHeaders = headers.toMutableList()
                updatedHeaders.add(CustomHeader("", ""))
                onUpdate(updatedHeaders)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.assistant_page_add_header))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.assistant_page_add_header))
        }
    }
    RikkaConfirmDialog(
        show = pendingDeleteIndex != null,
        title = stringResource(
            R.string.assistant_page_delete_custom_title,
            stringResource(R.string.assistant_page_custom_header_singular),
        ),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            pendingDeleteIndex?.let { index ->
                if (index in headers.indices) {
                    onUpdate(headers.filterIndexed { itemIndex, _ -> itemIndex != index })
                }
            }
            pendingDeleteIndex = null
        },
        onDismiss = { pendingDeleteIndex = null },
    ) {
        Text(
            stringResource(
                R.string.assistant_page_delete_custom_confirm,
                stringResource(R.string.assistant_page_custom_header_singular),
            )
        )
    }
}

@Composable
fun CustomBodies(customBodies: List<CustomBody>, onUpdate: (List<CustomBody>) -> Unit) {
    val context = LocalContext.current
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        customBodies.forEachIndexed { index, body ->
            var bodyKey by remember(body.key) { mutableStateOf(body.key) }
            var bodyValueString by remember(body.value) {
                mutableStateOf(jsonLenient.encodeToString(JsonElement.serializer(), body.value))
            }
            var jsonParseError by remember { mutableStateOf<String?>(null) }

            CardGroup {
                item(
                    supportingContent = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = bodyKey,
                                onValueChange = {
                                    bodyKey = it
                                    val updatedBodies = customBodies.toMutableList()
                                    updatedBodies[index] = updatedBodies[index].copy(key = it.trim())
                                    onUpdate(updatedBodies)
                                },
                                label = { Text(stringResource(R.string.assistant_page_body_key)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bodyValueString,
                                onValueChange = { newString ->
                                    bodyValueString = newString
                                    try {
                                        val newJsonValue = jsonLenient.parseToJsonElement(newString)
                                        val updatedBodies = customBodies.toMutableList()
                                        updatedBodies[index] =
                                            updatedBodies[index].copy(value = newJsonValue)
                                        onUpdate(updatedBodies)
                                        jsonParseError = null
                                    } catch (e: Exception) {
                                        jsonParseError =
                                            context.getString(
                                                R.string.assistant_page_invalid_json,
                                                e.message?.take(100) ?: ""
                                            )
                                    }
                                },
                                label = { Text(stringResource(R.string.assistant_page_body_value)) },
                                modifier = Modifier.fillMaxWidth(),
                                isError = jsonParseError != null,
                                supportingText = jsonParseError?.let { error ->
                                    { Text(error) }
                                },
                                minLines = 3,
                                maxLines = 5,
                                visualTransformation = HighlightCodeVisualTransformation(
                                    language = "json",
                                    highlighter = LocalCodeHighlighter.current,
                                    darkMode = LocalDarkMode.current
                                ),
                                textStyle = LocalTextStyle.current.merge(
                                    fontFamily = JetbrainsMono,
                                    fontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings,
                                ),
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { pendingDeleteIndex = index }) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.assistant_page_delete_body)
                            )
                        }
                    },
                    headlineContent = {},
                )
            }
        }

        Button(
            onClick = {
                val updatedBodies = customBodies.toMutableList()
                updatedBodies.add(CustomBody("", JsonPrimitive("")))
                onUpdate(updatedBodies)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.assistant_page_add_body))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.assistant_page_add_body))
        }
    }
    RikkaConfirmDialog(
        show = pendingDeleteIndex != null,
        title = stringResource(
            R.string.assistant_page_delete_custom_title,
            stringResource(R.string.assistant_page_custom_body_singular),
        ),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            pendingDeleteIndex?.let { index ->
                if (index in customBodies.indices) {
                    onUpdate(customBodies.filterIndexed { itemIndex, _ -> itemIndex != index })
                }
            }
            pendingDeleteIndex = null
        },
        onDismiss = { pendingDeleteIndex = null },
    ) {
        Text(
            stringResource(
                R.string.assistant_page_delete_custom_confirm,
                stringResource(R.string.assistant_page_custom_body_singular),
            )
        )
    }
}
