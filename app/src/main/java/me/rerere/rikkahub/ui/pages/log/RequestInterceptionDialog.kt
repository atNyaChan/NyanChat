package me.rerere.rikkahub.ui.pages.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape
import me.rerere.rikkahub.utils.writeClipboardText

@Composable
internal fun RequestInterceptionDialog(
    request: AppEvent.RequestInterception,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val jsonElement = remember(request.body) {
        request.body?.let(::parseResponseJson)
    }
    Dialog(
        onDismissRequest = onCancel,
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
                    text = stringResource(R.string.request_intercept_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RequestMethodLine(method = request.method, url = request.url)

                    if (request.headers.isNotEmpty()) {
                        CollapsibleLogSection(
                            title = "Request Headers",
                            onCopy = { context.writeClipboardText(headersText(request.headers)) },
                        ) {
                            HighlightedHeaders(request.headers)
                        }
                    }

                    request.body?.let { body ->
                        CollapsibleLogSection(
                            title = "Request Body",
                            initiallyExpanded = true,
                            onCopy = { context.writeClipboardText(body) },
                        ) {
                            if (jsonElement != null) {
                                JsonTree(jsonElement, initialExpandLevel = 1)
                            } else {
                                Text(body, fontFamily = JetbrainsMono, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.request_intercept_cancel))
                    }
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(R.string.request_intercept_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestMethodLine(method: String, url: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                append("[$method]")
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                append(": ")
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                append(url)
            }
        },
        fontFamily = JetbrainsMono,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        style = MaterialTheme.typography.bodySmall,
    )
}
