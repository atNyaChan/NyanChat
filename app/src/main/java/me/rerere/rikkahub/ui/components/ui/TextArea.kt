package me.rerere.rikkahub.ui.components.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.modifier.onClick

/**
 * A multi-line text input component with a header, expand/collapse and file import
 * functionality.
 *
 * @param state The TextFieldState to manage the text input
 * @param modifier Modifier for the component
 * @param label The header label text
 * @param labelInField Whether to render the label inside the text field instead of as a header row
 * @param placeholder Placeholder text for the input field
 * @param minLines Minimum number of lines to display
 * @param maxLines Maximum number of lines to display
 * @param enabled Whether the text field is enabled
 * @param readOnly Whether the text field is read-only
 * @param supportedFileTypes Array of MIME types to filter in file picker (default: text files)
 * @param collapsible Whether to collapse to [minLines] and show an expand/collapse toggle when the
 * content exceeds the collapsed height. When expanded, the field adapts to the content height.
 * @param enableImport Whether to enable file import functionality
 * @param onImportError Callback when file import fails (optional)
 */
@Composable
fun TextArea(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    label: String = "",
    labelInField: Boolean = false,
    placeholder: String = "",
    minLines: Int = 5,
    maxLines: Int = 10,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    supportedFileTypes: Array<String> = arrayOf("text/*", "application/json"),
    collapsible: Boolean = false,
    enableImport: Boolean = true,
    onImportError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    var expanded by remember { mutableStateOf(false) }
    var textLineCount by remember { mutableStateOf<Int?>(null) }
    val showExpandCollapse = collapsible && (expanded || (textLineCount ?: 0) > minLines)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()
                            ?.use { reader -> reader.readText() }
                            ?: error("Failed to read file")
                    }
                    state.setTextAndPlaceCursorAtEnd(content)
                    toaster.show(context.getString(R.string.text_area_import_success), type = ToastType.Success)
                } catch (e: Exception) {
                    e.printStackTrace()
                    val errorMessage = e.message ?: context.getString(R.string.text_area_import_failed)
                    onImportError?.invoke(errorMessage) ?: toaster.show(
                        message = errorMessage,
                        type = ToastType.Error
                    )
                }
            }
        }
    }


    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header with label, expand/collapse and import button
        if (!labelInField && label.isNotEmpty() || showExpandCollapse || enableImport) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!labelInField) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (showExpandCollapse) {
                        Crossfade(
                            targetState = expanded,
                            label = "textAreaExpandCollapseIcon"
                        ) { isExpanded ->
                            Icon(
                                painter = painterResource(
                                    if (isExpanded) R.drawable.collapse_all_24px
                                    else R.drawable.expand_all_24px
                                ),
                                contentDescription = stringResource(
                                    if (isExpanded) R.string.text_area_collapse else R.string.text_area_expand
                                ),
                                modifier = Modifier
                                    .onClick(onClick = { expanded = !expanded })
                                    .size(24.dp)
                            )
                        }
                    }

                    if (enableImport) {
                        Icon(
                            imageVector = HugeIcons.FileImport,
                            contentDescription = stringResource(R.string.text_area_import_from_file),
                            modifier = Modifier
                                .onClick(onClick = {
                                    filePickerLauncher.launch(supportedFileTypes)
                                })
                                .size(24.dp)
                        )
                    }
                }
            }
        }

        // Multi-line text input
        OutlinedTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            label = if (labelInField && label.isNotEmpty()) {
                { Text(label) }
            } else null,
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder) }
            } else null,
            lineLimits = if (collapsible) {
                TextFieldLineLimits.MultiLine(
                    minHeightInLines = minLines,
                    maxHeightInLines = if (expanded) Int.MAX_VALUE else minLines
                )
            } else {
                TextFieldLineLimits.MultiLine(
                    minHeightInLines = minLines,
                    maxHeightInLines = maxLines
                )
            },
            onTextLayout = { layout ->
                textLineCount = layout()?.lineCount
            },
            enabled = enabled,
            readOnly = readOnly,
        )
    }
}
