package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ui.CardGroupScope

internal fun CardGroupScope.promptSettingItem(type: PromptType, onClick: () -> Unit) {
    item(
        onClick = onClick,
        headlineContent = { Text(stringResource(type.titleRes)) },
        trailingContent = { PromptArrow() },
    )
}

@Composable
internal fun PromptEditor(
    type: PromptType,
    settings: Settings,
    vm: SettingVM,
    onDismiss: () -> Unit,
) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        val title = stringResource(type.titleRes)
        val description = stringResource(type.descriptionRes)
        val value = when (type) {
            PromptType.TRANSLATION -> settings.translatePrompt
            PromptType.TITLE -> settings.titlePrompt
            PromptType.SUGGESTION -> settings.suggestionPrompt
            PromptType.OCR -> settings.ocrPrompt
            PromptType.COMPRESS -> settings.compressPrompt
        }
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { prompt ->
                        vm.updateSettings(
                            when (type) {
                                PromptType.TRANSLATION -> settings.copy(translatePrompt = prompt)
                                PromptType.TITLE -> settings.copy(titlePrompt = prompt)
                                PromptType.SUGGESTION -> settings.copy(suggestionPrompt = prompt)
                                PromptType.OCR -> settings.copy(ocrPrompt = prompt)
                                PromptType.COMPRESS -> settings.copy(compressPrompt = prompt)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 15,
                )
                TextButton(
                    onClick = {
                        vm.updateSettings(
                            when (type) {
                                PromptType.TRANSLATION ->
                                    settings.copy(translatePrompt = DEFAULT_TRANSLATION_PROMPT)
                                PromptType.TITLE -> settings.copy(titlePrompt = DEFAULT_TITLE_PROMPT)
                                PromptType.SUGGESTION ->
                                    settings.copy(suggestionPrompt = DEFAULT_SUGGESTION_PROMPT)
                                PromptType.OCR -> settings.copy(ocrPrompt = DEFAULT_OCR_PROMPT)
                                PromptType.COMPRESS -> settings.copy(compressPrompt = DEFAULT_COMPRESS_PROMPT)
                            }
                        )
                    }
                ) {
                    Text(stringResource(R.string.setting_model_page_reset_to_default))
                }
            }
        }
}

@Composable
private fun PromptArrow() {
    Icon(
        HugeIcons.ArrowRight01,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
    )
}

internal enum class PromptType(
    val titleRes: Int,
    val descriptionRes: Int,
) {
    TRANSLATION(
        R.string.setting_model_page_prompt_translation,
        R.string.setting_model_page_translate_prompt_vars,
    ),
    TITLE(
        R.string.setting_model_page_prompt_title,
        R.string.setting_model_page_suggestion_prompt_vars,
    ),
    SUGGESTION(
        R.string.setting_model_page_prompt_suggestion,
        R.string.setting_model_page_suggestion_prompt_vars,
    ),
    OCR(
        R.string.setting_model_page_prompt_ocr,
        R.string.setting_model_page_ocr_prompt_vars,
    ),
    COMPRESS(
        R.string.setting_model_page_prompt_compress,
        R.string.setting_model_page_compress_prompt_vars,
    ),
}
