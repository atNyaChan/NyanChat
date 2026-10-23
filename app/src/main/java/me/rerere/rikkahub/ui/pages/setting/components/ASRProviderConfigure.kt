package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.ui.components.OutlinedNumberInput
import me.rerere.rikkahub.ui.components.ui.SelectTextField
import me.rerere.rikkahub.ui.components.ui.switchItem

val ASRProviderSetting.typeName: String
    get() = when (this) {
        is ASRProviderSetting.OpenAIRealtime -> "OpenAI Realtime"
        is ASRProviderSetting.DashScope -> "DashScope"
        is ASRProviderSetting.Volcengine -> "Volcengine"
        is ASRProviderSetting.MiMo -> "MiMo"
        is ASRProviderSetting.Step -> "Step"
    }

@Composable
fun ASRProviderConfigure(
    setting: ASRProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (ASRProviderSetting) -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        val providers = remember { ASRProviderSetting.Types }

        CardGroup(modifier = Modifier.fillMaxWidth()) {
            item(headlineContent = {
                SelectTextField(
                    value = when (setting) {
                        is ASRProviderSetting.OpenAIRealtime -> "OpenAI Realtime"
                        is ASRProviderSetting.DashScope -> "DashScope"
                        is ASRProviderSetting.Volcengine -> "Volcengine"
                        is ASRProviderSetting.MiMo -> "MiMo"
                        is ASRProviderSetting.Step -> "Step"
                    },
                    options = providers,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { providerClass ->
                        when (providerClass) {
                            ASRProviderSetting.OpenAIRealtime::class -> "OpenAI Realtime"
                            ASRProviderSetting.DashScope::class -> "DashScope"
                            ASRProviderSetting.Volcengine::class -> "Volcengine"
                            ASRProviderSetting.MiMo::class -> "MiMo"
                            ASRProviderSetting.Step::class -> "Step"
                            else -> providerClass.simpleName ?: "Unknown"
                        }
                    },
                    onOptionSelected = { providerClass ->
                        val newSetting = when (providerClass) {
                            ASRProviderSetting.OpenAIRealtime::class ->
                                ASRProviderSetting.OpenAIRealtime(id = setting.id)

                            ASRProviderSetting.DashScope::class ->
                                ASRProviderSetting.DashScope(id = setting.id)

                            ASRProviderSetting.Volcengine::class ->
                                ASRProviderSetting.Volcengine(id = setting.id)

                            ASRProviderSetting.MiMo::class ->
                                ASRProviderSetting.MiMo(id = setting.id)

                            ASRProviderSetting.Step::class ->
                                ASRProviderSetting.Step(id = setting.id)

                            else -> setting
                        }
                        onValueChange(newSetting)
                    },
                    label = { Text(stringResource(R.string.setting_asr_configure_provider_type)) },
                    supportingText = { Text(stringResource(R.string.setting_asr_configure_provider_type_desc)) },
                )
            })

            item(headlineContent = {
                OutlinedTextField(
                    value = setting.name,
                    onValueChange = { onValueChange(setting.copyProvider(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("OpenAI Realtime") },
                    label = { Text(stringResource(R.string.setting_asr_configure_name)) },
                    supportingText = { Text(stringResource(R.string.setting_asr_configure_name_desc)) },
                )
            })
            when (setting) {
                is ASRProviderSetting.OpenAIRealtime -> OpenAIRealtimeASRConfiguration(setting, onValueChange)
                is ASRProviderSetting.DashScope -> DashScopeASRConfiguration(setting, onValueChange)
                is ASRProviderSetting.Volcengine -> VolcengineASRConfiguration(setting, onValueChange)
                is ASRProviderSetting.MiMo -> MiMoASRConfiguration(setting, onValueChange)
                is ASRProviderSetting.Step -> StepASRConfiguration(setting, onValueChange)
            }
        }
        footer?.invoke()
    }
}

private fun CardGroupScope.OpenAIRealtimeASRConfiguration(
    setting: ASRProviderSetting.OpenAIRealtime,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-...") },
            label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_openai_api_key_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("wss://api.openai.com/v1/realtime?intent=transcription") },
            label = { Text(stringResource(R.string.setting_asr_configure_websocket_url)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_openai_websocket_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("gpt-4o-transcribe") },
            label = { Text(stringResource(R.string.setting_asr_configure_model)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_model_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") },
            label = { Text(stringResource(R.string.setting_asr_configure_language)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_language_iso_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.prompt,
            onValueChange = { onValueChange(setting.copy(prompt = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Optional") },
            label = { Text(stringResource(R.string.setting_asr_configure_prompt)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_prompt_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.vadThreshold,
            onValueChange = { value ->
                if (value in 0.0f..1.0f) {
                    onValueChange(setting.copy(vadThreshold = value))
                }
            },
            label = stringResource(R.string.setting_asr_configure_vad_threshold),
            supportingText = { Text(stringResource(R.string.setting_asr_configure_vad_desc)) },
            modifier = Modifier.fillMaxWidth(),
        )
    })

    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.prefixPaddingMs,
            onValueChange = { value ->
                if (value in 0..2000) {
                    onValueChange(setting.copy(prefixPaddingMs = value))
                }
            },
            label = stringResource(R.string.setting_asr_configure_prefix_padding),
            supportingText = { Text(stringResource(R.string.setting_asr_configure_prefix_padding_desc)) },
            modifier = Modifier.fillMaxWidth(),
        )
    })

    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.silenceDurationMs,
            onValueChange = { value ->
                if (value in 100..5000) {
                    onValueChange(setting.copy(silenceDurationMs = value))
                }
            },
            label = stringResource(R.string.setting_asr_configure_silence_duration),
            supportingText = { Text(stringResource(R.string.setting_asr_configure_silence_duration_desc)) },
            modifier = Modifier.fillMaxWidth(),
        )
    })
}

private fun CardGroupScope.DashScopeASRConfiguration(
    setting: ASRProviderSetting.DashScope,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-...") },
            label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_dashscope_api_key_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("wss://dashscope.aliyuncs.com/api-ws/v1/realtime") },
            label = { Text(stringResource(R.string.setting_asr_configure_websocket_url)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_dashscope_websocket_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("qwen3-asr-flash-realtime-2026-02-10") },
            label = { Text(stringResource(R.string.setting_asr_configure_model)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_model_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("zh") },
            label = { Text(stringResource(R.string.setting_asr_configure_language)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_language_iso_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.vadThreshold,
            onValueChange = { value ->
                if (value in -1.0f..1.0f) {
                    onValueChange(setting.copy(vadThreshold = value))
                }
            },
            label = stringResource(R.string.setting_asr_configure_vad_threshold),
            supportingText = { Text(stringResource(R.string.setting_asr_configure_dashscope_vad_desc)) },
            modifier = Modifier.fillMaxWidth(),
        )
    })

    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.silenceDurationMs,
            onValueChange = { value ->
                if (value in 200..6000) {
                    onValueChange(setting.copy(silenceDurationMs = value))
                }
            },
            label = stringResource(R.string.setting_asr_configure_silence_duration),
            supportingText = { Text(stringResource(R.string.setting_asr_configure_silence_duration_desc)) },
            modifier = Modifier.fillMaxWidth(),
        )
    })
}

private fun CardGroupScope.VolcengineASRConfiguration(
    setting: ASRProviderSetting.Volcengine,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("your-api-key") },
            label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_volcengine_api_key_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async") },
            label = { Text(stringResource(R.string.setting_asr_configure_websocket_url)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_volcengine_websocket_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.resourceId,
            onValueChange = { onValueChange(setting.copy(resourceId = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("volc.seedasr.sauc.duration") },
            label = { Text(stringResource(R.string.setting_asr_configure_resource_id)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_resource_id_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") },
            label = { Text(stringResource(R.string.setting_asr_configure_language)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_language_code_desc)) },
        )
    })
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.silenceDurationMs,
            onValueChange = { value ->
                if (value in 300..5000) onValueChange(setting.copy(silenceDurationMs = value))
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_page_asr_silence_duration),
            supportingText = { Text(stringResource(R.string.setting_page_asr_silence_duration_desc)) },
        )
    })

}

private fun CardGroupScope.MiMoASRConfiguration(
    setting: ASRProviderSetting.MiMo,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-... or tp-...") },
            label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_mimo_api_key_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.xiaomimimo.com/v1") },
            label = { Text(stringResource(R.string.setting_asr_configure_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_mimo_base_url_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mimo-v2.5-asr") },
            label = { Text(stringResource(R.string.setting_asr_configure_model)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_mimo_model_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") },
            label = { Text(stringResource(R.string.setting_asr_configure_language)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_mimo_language_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            label = stringResource(R.string.setting_asr_configure_sample_rate),
            supportingText = { Text(stringResource(R.string.setting_asr_configure_mimo_sample_rate_desc)) },
            modifier = Modifier.fillMaxWidth(),
        )
    })

    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.segmentDurationSec,
            onValueChange = { value ->
                if (value in 0..300) {
                    onValueChange(setting.copy(segmentDurationSec = value))
                }
            },
            label = stringResource(R.string.setting_asr_configure_segment_duration),
            supportingText = { Text(stringResource(R.string.setting_asr_configure_mimo_segment_desc)) },
            modifier = Modifier.fillMaxWidth(),
        )
    })
}

private fun CardGroupScope.StepASRConfiguration(
    setting: ASRProviderSetting.Step,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("your-stepfun-api-key") },
            label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_step_api_key_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.stepfun.com") },
            label = { Text(stringResource(R.string.setting_asr_configure_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_step_base_url_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("stepaudio-2.5-asr") },
            label = { Text(stringResource(R.string.setting_asr_configure_model)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_step_model_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") },
            label = { Text(stringResource(R.string.setting_asr_configure_language)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_step_language_desc)) },
        )
    })

    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            label = stringResource(R.string.setting_asr_configure_sample_rate),
            supportingText = { Text(stringResource(R.string.setting_asr_configure_step_sample_rate_desc)) },
            modifier = Modifier.fillMaxWidth(),
        )
    })

    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.segmentDurationSec,
            onValueChange = { value ->
                if (value in 0..300) {
                    onValueChange(setting.copy(segmentDurationSec = value))
                }
            },
            label = stringResource(R.string.setting_asr_configure_segment_duration),
            supportingText = { Text(stringResource(R.string.setting_asr_configure_step_segment_desc)) },
            modifier = Modifier.fillMaxWidth(),
        )
    })

    switchItem(
        checked = setting.enableItn,
        onCheckedChange = { onValueChange(setting.copy(enableItn = it)) },
        headlineContent = { Text(stringResource(R.string.setting_asr_configure_step_itn)) },
        supportingContent = { Text(stringResource(R.string.setting_asr_configure_step_itn_desc)) },
    )

    switchItem(
        checked = setting.enableTimestamp,
        onCheckedChange = { onValueChange(setting.copy(enableTimestamp = it)) },
        headlineContent = { Text(stringResource(R.string.setting_asr_configure_step_timestamp)) },
        supportingContent = { Text(stringResource(R.string.setting_asr_configure_step_timestamp_desc)) },
    )

    item(headlineContent = {
        OutlinedTextField(
            // 用逗号分隔展示, 输入时按逗号 split 回 List
            value = setting.hotwords.joinToString(","),
            onValueChange = { text ->
                val list = text.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                onValueChange(setting.copy(hotwords = list))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_asr_configure_step_hotwords_placeholder)) },
            label = { Text(stringResource(R.string.setting_asr_configure_step_hotwords)) },
            supportingText = { Text(stringResource(R.string.setting_asr_configure_step_hotwords_desc)) },
        )
    })
}
