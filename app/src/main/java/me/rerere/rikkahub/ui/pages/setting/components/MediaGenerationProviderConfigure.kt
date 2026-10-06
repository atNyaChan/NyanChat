package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.SelectTextField
import kotlin.reflect.KClass

val MediaGenerationProviderSetting.typeName: String
    @Composable get() = when (this) {
        is MediaGenerationProviderSetting.OpenAI -> "OpenAI"
        is MediaGenerationProviderSetting.Aliyun -> stringResource(R.string.media_provider_type_aliyun)
        is MediaGenerationProviderSetting.Volcengine -> stringResource(R.string.media_provider_type_volcengine)
        is MediaGenerationProviderSetting.MiniMax -> "MiniMax"
        is MediaGenerationProviderSetting.OpenRouter -> "OpenRouter"
    }

val MediaKind.label: String
    @Composable get() = when (this) {
        MediaKind.IMAGE -> stringResource(R.string.media_kind_image)
        MediaKind.VIDEO -> stringResource(R.string.video)
    }

@Composable
private fun mediaProviderTypeName(providerClass: KClass<out MediaGenerationProviderSetting>): String =
    when (providerClass) {
        MediaGenerationProviderSetting.OpenAI::class -> "OpenAI"
        MediaGenerationProviderSetting.Aliyun::class -> stringResource(R.string.media_provider_type_aliyun)
        MediaGenerationProviderSetting.Volcengine::class -> stringResource(R.string.media_provider_type_volcengine)
        MediaGenerationProviderSetting.MiniMax::class -> "MiniMax"
        MediaGenerationProviderSetting.OpenRouter::class -> "OpenRouter"
        else -> providerClass.simpleName ?: "Unknown"
    }

/** 换厂商类型时保留 id，其余字段用该类型的默认值。 */
private fun MediaGenerationProviderSetting.switchTo(
    providerClass: KClass<out MediaGenerationProviderSetting>,
): MediaGenerationProviderSetting = when (providerClass) {
    MediaGenerationProviderSetting.OpenAI::class -> MediaGenerationProviderSetting.OpenAI(id = id)
    MediaGenerationProviderSetting.Aliyun::class -> MediaGenerationProviderSetting.Aliyun(id = id)
    MediaGenerationProviderSetting.Volcengine::class -> MediaGenerationProviderSetting.Volcengine(id = id)
    MediaGenerationProviderSetting.MiniMax::class -> MediaGenerationProviderSetting.MiniMax(id = id)
    MediaGenerationProviderSetting.OpenRouter::class -> MediaGenerationProviderSetting.OpenRouter(id = id)
    else -> this
}

@Composable
fun MediaGenerationProviderConfigure(
    setting: MediaGenerationProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (MediaGenerationProviderSetting) -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        CardGroup(modifier = Modifier.fillMaxWidth()) {
            FormItem(label = { Text(stringResource(R.string.setting_media_page_provider_type)) }) {
                SelectTextField(
                    value = setting.typeName,
                    options = MediaGenerationProviderSetting.Types,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { providerClass -> mediaProviderTypeName(providerClass) },
                    onOptionSelected = { providerClass -> onValueChange(setting.switchTo(providerClass)) },
                )
            }

            FormItem(label = {}) {
                OutlinedTextField(
                    value = setting.name,
                    onValueChange = { onValueChange(setting.copyProvider(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.setting_media_page_provider_name)) }
                )
            }

            FormItem(label = {}) {
                var passwordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = setting.apiKey,
                    onValueChange = { onValueChange(setting.copyProvider(apiKey = it.trim())) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) HugeIcons.ViewOff else HugeIcons.View,
                                contentDescription = null,
                            )
                        }
                    }
                )
            }

            FormItem(label = {}) {
                OutlinedTextField(
                    value = setting.baseUrl,
                    onValueChange = { onValueChange(setting.copyProvider(baseUrl = it.trim())) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") }
                )
            }

            if (setting is MediaGenerationProviderSetting.Aliyun) {
                FormItem(
                    label = {},
                    description = {
                        Text(
                            stringResource(
                                R.string.setting_media_page_workspace_id_desc,
                                MediaGenerationProviderSetting.Aliyun.WORKSPACE_PLACEHOLDER,
                            )
                        )
                    }
                ) {
                    OutlinedTextField(
                        value = setting.workspaceId,
                        onValueChange = { onValueChange(setting.copy(workspaceId = it.trim())) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.setting_media_page_workspace_id)) }
                    )
                }
            }

            FormItem(
                label = { Text(stringResource(R.string.setting_media_page_models)) },
                description = { Text(stringResource(R.string.setting_media_page_models_desc)) }
            ) {
                MediaGenerationModelList(
                    models = setting.models,
                    supportedKinds = setting.supportedKinds,
                    onValueChange = { onValueChange(setting.copyProvider(models = it)) }
                )
            }
        }

        footer?.invoke()
    }
}

@Composable
private fun MediaGenerationModelList(
    models: List<MediaGenerationModel>,
    supportedKinds: Set<MediaKind>,
    onValueChange: (List<MediaGenerationModel>) -> Unit
) {
    // 按枚举顺序排列，保证分段按钮的顺序稳定
    val kinds = MediaKind.entries.filter { it in supportedKinds }

    fun update(model: MediaGenerationModel) {
        onValueChange(models.map { if (it.id == model.id) model else it })
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { model ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = model.modelId,
                        onValueChange = { update(model.copy(modelId = it.trim())) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.setting_media_page_model_id)) }
                    )
                    // 只支持一种类型的厂商不需要选择
                    if (kinds.size > 1) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            kinds.forEachIndexed { index, kind ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size),
                                    selected = model.kind == kind,
                                    onClick = { update(model.copy(kind = kind)) },
                                ) {
                                    Text(kind.label)
                                }
                            }
                        }
                    }
                }
                IconButton(onClick = { onValueChange(models.filter { it.id != model.id }) }) {
                    Icon(HugeIcons.Delete01, stringResource(R.string.common_delete))
                }
            }
        }

        TextButton(
            onClick = { onValueChange(models + MediaGenerationModel(modelId = "", kind = kinds.first())) },
            enabled = kinds.isNotEmpty()
        ) {
            Icon(HugeIcons.Add01, null)
            Text(stringResource(R.string.setting_media_page_add_model))
        }
    }
}
