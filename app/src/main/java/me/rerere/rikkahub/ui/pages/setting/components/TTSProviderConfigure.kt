package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.ui.components.OutlinedNumberInput
import me.rerere.rikkahub.ui.components.ui.SelectTextField
import me.rerere.tts.provider.TTSProviderSetting

@Composable
fun TTSProviderConfigure(
    setting: TTSProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (TTSProviderSetting) -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        // Provider type selector
        val providers = remember { TTSProviderSetting.Types }

        CardGroup(modifier = Modifier.fillMaxWidth()) {
            item(headlineContent = {
                SelectTextField(
                    value = when (setting) {
                        is TTSProviderSetting.OpenAI -> "OpenAI"
                        is TTSProviderSetting.Gemini -> "Gemini"
                        is TTSProviderSetting.SystemTTS -> "System TTS"
                        is TTSProviderSetting.MiniMax -> "MiniMax"
                        is TTSProviderSetting.Qwen -> "Qwen"
                        is TTSProviderSetting.Groq -> "Groq"
                        is TTSProviderSetting.XAI -> "xAI"
                        is TTSProviderSetting.MiMo -> "MiMo"
                        is TTSProviderSetting.Step -> "Step"
                        is TTSProviderSetting.ElevenLabs -> "ElevenLabs"
                        is TTSProviderSetting.FishAudio -> "Fish Audio"
                        is TTSProviderSetting.Volcengine -> "火山引擎"
                    },
                    options = providers,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { providerClass ->
                        when (providerClass) {
                            TTSProviderSetting.OpenAI::class -> "OpenAI"
                            TTSProviderSetting.Gemini::class -> "Gemini"
                            TTSProviderSetting.SystemTTS::class -> "System TTS"
                            TTSProviderSetting.MiniMax::class -> "MiniMax"
                            TTSProviderSetting.Qwen::class -> "Qwen"
                            TTSProviderSetting.Groq::class -> "Groq"
                            TTSProviderSetting.XAI::class -> "xAI"
                            TTSProviderSetting.MiMo::class -> "MiMo"
                            TTSProviderSetting.ElevenLabs::class -> "ElevenLabs"
                            TTSProviderSetting.FishAudio::class -> "Fish Audio"
                            TTSProviderSetting.Step::class -> "Step"
                            TTSProviderSetting.Volcengine::class -> "火山引擎"
                            else -> providerClass.simpleName ?: "Unknown"                    }
                    },
                    onOptionSelected = { providerClass ->
                        val newSetting = when (providerClass) {
                            TTSProviderSetting.OpenAI::class -> TTSProviderSetting.OpenAI(
                                id = setting.id,
                                name = "OpenAI TTS"
                            )

                            TTSProviderSetting.Gemini::class -> TTSProviderSetting.Gemini(
                                id = setting.id,
                                name = "Gemini TTS"
                            )

                            TTSProviderSetting.SystemTTS::class -> TTSProviderSetting.SystemTTS(
                                id = setting.id,
                                name = "System TTS"
                            )

                            TTSProviderSetting.MiniMax::class -> TTSProviderSetting.MiniMax(
                                id = setting.id,
                                name = "MiniMax TTS"
                            )

                            TTSProviderSetting.Qwen::class -> TTSProviderSetting.Qwen(
                                id = setting.id,
                                name = "Qwen TTS"
                            )

                            TTSProviderSetting.Groq::class -> TTSProviderSetting.Groq(
                                id = setting.id,
                                name = "Groq TTS"
                            )

                            TTSProviderSetting.XAI::class -> TTSProviderSetting.XAI(
                                id = setting.id,
                                name = "xAI TTS"
                            )

                            TTSProviderSetting.MiMo::class -> TTSProviderSetting.MiMo(
                                id = setting.id,
                                name = "MiMo TTS"
                            )

                            TTSProviderSetting.ElevenLabs::class -> TTSProviderSetting.ElevenLabs(
                                id = setting.id,
                                name = "ElevenLabs TTS"
                            )

                            TTSProviderSetting.FishAudio::class -> TTSProviderSetting.FishAudio(
                                id = setting.id,
                                name = "Fish Audio TTS"
                            )

                            TTSProviderSetting.Step::class -> TTSProviderSetting.Step(
                                id = setting.id,
                                name = "Step TTS"
                            )

                            TTSProviderSetting.Volcengine::class -> TTSProviderSetting.Volcengine(id = setting.id)

                            else -> setting
                        }
                        onValueChange(newSetting)
                    },
                    label = { Text(stringResource(R.string.setting_tts_page_provider_type)) },
                    supportingText = { Text(stringResource(R.string.setting_tts_page_provider_type_description)) },
                )
            })

            // Name
            item(headlineContent = {
                OutlinedTextField(
                    value = setting.name,
                    onValueChange = { newName ->
                        onValueChange(setting.copyProvider(name = newName))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.setting_tts_page_name_placeholder)) },
                    label = { Text(stringResource(R.string.setting_tts_page_name)) },
                    supportingText = { Text(stringResource(R.string.setting_tts_page_name_description)) },
                )
            })
            // Provider-specific fields
            when (setting) {
                is TTSProviderSetting.OpenAI -> OpenAITTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.Gemini -> GeminiTTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.MiniMax -> MiniMaxTTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.SystemTTS -> SystemTTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.Qwen -> QwenTTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.Groq -> GroqTTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.XAI -> XAITTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.MiMo -> MiMoTTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.ElevenLabs -> ElevenLabsTTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.FishAudio -> FishAudioTTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.Step -> StepTTSConfiguration(setting, onValueChange)
                is TTSProviderSetting.Volcengine -> VolcengineTTSConfiguration(setting, onValueChange)
            }
        }
        footer?.invoke()
    }
}

private fun CardGroupScope.OpenAITTSConfiguration(
    setting: TTSProviderSetting.OpenAI,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_api_key_placeholder_openai)) },
            label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_api_key_description)) },
        )
    })

    // Base URL
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) },
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_base_url_description)) },
        )
    })

    // Model
    item(headlineContent = {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_model_placeholder_openai)) },
            label = { Text(stringResource(R.string.setting_tts_page_model)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_model_description)) },
        )
    })

    // Voice
    val voices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")

    item(headlineContent = {
        SelectTextField(
            value = setting.voice,
            options = voices,
            onValueChange = { newVoice ->
                onValueChange(setting.copy(voice = newVoice))
            },
            onOptionSelected = { voice ->
                onValueChange(setting.copy(voice = voice))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_voice)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_voice_description)) },
        )
    })
}

private fun CardGroupScope.MiMoTTSConfiguration(
    setting: TTSProviderSetting.MiMo,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // MiMo 配置均为自由输入 默认值只是占位
    // API Key
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mimo-xxx") },
            label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_api_key_description)) },
        )
    })

    // Base URL
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.xiaomimimo.com/v1") },
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_base_url_description)) },
        )
    })

    // Model
    item(headlineContent = {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mimo-v2-tts") },
            label = { Text(stringResource(R.string.setting_tts_page_model)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_model_description)) },
        )
    })

    // Voice
    val voices = listOf(
        "mimo_default",
        "冰糖",
        "茉莉",
        "苏打",
        "白桦",
        "Mia",
        "Chloe",
        "Milo",
        "Dean"
    )

    item(headlineContent = {
        SelectTextField(
            value = setting.voice,
            options = voices,
            onValueChange = { newVoice ->
                onValueChange(setting.copy(voice = newVoice))
            },
            onOptionSelected = { voice ->
                onValueChange(setting.copy(voice = voice))
            },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mimo_default") },
            label = { Text(stringResource(R.string.setting_tts_page_voice)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_voice_description)) },
        )
    })
}

private fun CardGroupScope.MiniMaxTTSConfiguration(
    setting: TTSProviderSetting.MiniMax,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_api_key_description)) },
        )
    })

    // Base URL
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) },
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_base_url_description)) },
        )
    })

    // Model
    item(headlineContent = {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("speech-2.8-hd") },
            label = { Text(stringResource(R.string.setting_tts_page_model)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_model_description)) },
        )
    })

    // Voice ID
    val voiceIds = listOf(
        "male-qn-qingse",
        "male-qn-jingying",
        "male-qn-badao",
        "male-qn-daxuesheng",
        "female-shaonv",
        "female-yujie",
        "female-chengshu",
        "female-tianmei",
        "audiobook_male_1",
        "audiobook_female_1",
        "cartoon_pig"
    )

    item(headlineContent = {
        SelectTextField(
            value = setting.voiceId,
            options = voiceIds,
            onValueChange = { newVoiceId ->
                onValueChange(setting.copy(voiceId = newVoiceId))
            },
            onOptionSelected = { voiceId ->
                onValueChange(setting.copy(voiceId = voiceId))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_voice_id)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_voice_id_description)) },
        )
    })

    // Speed
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.speed,
            onValueChange = { newSpeed ->
                if (newSpeed in 0.25f..4.0f) {
                    onValueChange(setting.copy(speed = newSpeed))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speed),
            supportingText = { Text(stringResource(R.string.setting_tts_page_speed_description)) },
        )
    })
}

private fun CardGroupScope.GeminiTTSConfiguration(
    setting: TTSProviderSetting.Gemini,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_api_key_placeholder_gemini)) },
            label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_api_key_description)) },
        )
    })

    // Base URL
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) },
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_base_url_description)) },
        )
    })

    // Model
    item(headlineContent = {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_model_placeholder_gemini)) },
            label = { Text(stringResource(R.string.setting_tts_page_model)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_model_description)) },
        )
    })

    // Voice Name
    item(headlineContent = {
        OutlinedTextField(
            value = setting.voiceName,
            onValueChange = { newVoiceName ->
                onValueChange(setting.copy(voiceName = newVoiceName))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_voice_name_placeholder)) },
            label = { Text(stringResource(R.string.setting_tts_page_voice_name)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_voice_name_description)) },
        )
    })
}

private fun CardGroupScope.SystemTTSConfiguration(
    setting: TTSProviderSetting.SystemTTS,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // Speech Rate
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.speechRate,
            onValueChange = { newRate ->
                if (newRate in 0.1f..3.0f) {
                    onValueChange(setting.copy(speechRate = newRate))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speech_rate),
            supportingText = { Text(stringResource(R.string.setting_tts_page_speech_rate_description)) },
        )
    })

    // Pitch
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.pitch,
            onValueChange = { newPitch ->
                if (newPitch in 0.1f..2.0f) {
                    onValueChange(setting.copy(pitch = newPitch))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_pitch),
            supportingText = { Text(stringResource(R.string.setting_tts_page_pitch_description)) },
        )
    })
}

private fun CardGroupScope.QwenTTSConfiguration(
    setting: TTSProviderSetting.Qwen,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-xxx") },
            label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_api_key_description)) },
        )
    })

    // Base URL
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) },
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_base_url_description)) },
        )
    })

    // Model
    val models = listOf(
        "qwen-audio-3.0-tts-flash",
        "qwen-audio-3.0-tts-plus",
    )

    item(headlineContent = {
        SelectTextField(
            value = setting.model,
            options = models,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            onOptionSelected = { model ->
                val defaultVoice = when (model) {
                    "qwen-audio-3.0-tts-plus" -> "longanlingxin"
                    "qwen-audio-3.0-tts-flash" -> "longanhuan_v3.6"
                    else -> setting.voice
                }
                onValueChange(setting.copy(model = model, voice = defaultVoice))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("qwen-audio-3.0-tts-flash") },
            label = { Text(stringResource(R.string.setting_tts_page_model)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_model_description)) },
        )
    })

    // Voice
    val voices = when (setting.model) {
        "qwen-audio-3.0-tts-plus" -> listOf("longanlingxin", "longanlufeng")
        "qwen-audio-3.0-tts-flash" -> listOf(
            "longanfengyue", "longanyuanfei", "longanlingxi", "longanxiaoxin",
            "longanhuan_v3.6", "longjielidou_v3.6", "longpaopao_v3.6",
            "longhuohuo_v3.6", "longchuanshu_v3.6", "loongmary",
            "loongeva_v3.6", "loongjohn",
        )

        else -> emptyList()
    }

    item(headlineContent = {
        SelectTextField(
            value = setting.voice,
            options = voices,
            onValueChange = { newVoice ->
                onValueChange(setting.copy(voice = newVoice))
            },
            onOptionSelected = { voice ->
                onValueChange(setting.copy(voice = voice))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_voice)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_voice_description)) },
        )
    })

    // Audio Format
    val formats = listOf("wav", "mp3", "pcm", "opus")

    item(headlineContent = {
        SelectTextField(
            value = setting.format,
            options = formats,
            readOnly = true,
            onOptionSelected = { format ->
                onValueChange(setting.copy(format = format))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Audio Format") },
            supportingText = { Text("Audio encoding returned by Qwen TTS") },
        )
    })

    // Sample Rate
    val sampleRates = listOf(8000, 16000, 22050, 24000, 44100, 48000)

    item(headlineContent = {
        SelectTextField(
            value = setting.sampleRate.toString(),
            options = sampleRates,
            readOnly = true,
            onOptionSelected = { sampleRate ->
                onValueChange(setting.copy(sampleRate = sampleRate))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Sample Rate") },
            supportingText = { Text("Audio sample rate in Hz") },
        )
    })
}

private fun CardGroupScope.GroqTTSConfiguration(
    setting: TTSProviderSetting.Groq,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("gsk_xxx") },
            label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_api_key_description)) },
        )
    })

    // Base URL
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) },
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_base_url_description)) },
        )
    })

    // Model
    item(headlineContent = {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("canopylabs/orpheus-v1-english") },
            label = { Text(stringResource(R.string.setting_tts_page_model)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_model_description)) },
        )
    })

    // Voice
    val voices = listOf("austin", "natalie", "kailin")

    item(headlineContent = {
        SelectTextField(
            value = setting.voice,
            options = voices,
            onValueChange = { newVoice ->
                onValueChange(setting.copy(voice = newVoice))
            },
            onOptionSelected = { voice ->
                onValueChange(setting.copy(voice = voice))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_voice)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_voice_description)) },
        )
    })
}

private fun CardGroupScope.XAITTSConfiguration(
    setting: TTSProviderSetting.XAI,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("xai-xxx") },
            label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_api_key_description)) },
        )
    })

    // Base URL
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.x.ai/v1") },
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_base_url_description)) },
        )
    })

    // Voice ID
    val voices = listOf(
        "eve" to "Eve",
        "ara" to "Ara",
        "rex" to "Rex",
        "sal" to "Sal",
        "leo" to "Leo"
    )

    item(headlineContent = {
        SelectTextField(
            value = setting.voiceId,
            options = voices,
            onValueChange = { newVoiceId ->
                onValueChange(setting.copy(voiceId = newVoiceId))
            },
            onOptionSelected = { (voiceId, _) ->
                onValueChange(setting.copy(voiceId = voiceId))
            },
            optionToString = { (_, description) -> description },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_voice)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_voice_description)) },
        )
    })

    // Language
    val languages = listOf(
        "auto" to "Auto-detect",
        "en" to "English",
        "zh" to "Chinese (Simplified)",
        "ja" to "Japanese",
        "ko" to "Korean",
        "fr" to "French",
        "de" to "German",
        "es-ES" to "Spanish (Spain)",
        "es-MX" to "Spanish (Mexico)",
        "pt-BR" to "Portuguese (Brazil)",
        "pt-PT" to "Portuguese (Portugal)",
        "it" to "Italian",
        "ru" to "Russian",
        "ar-EG" to "Arabic (Egypt)",
        "hi" to "Hindi",
        "tr" to "Turkish",
        "vi" to "Vietnamese",
        "id" to "Indonesian",
        "bn" to "Bengali"
    )

    item(headlineContent = {
        SelectTextField(
            value = setting.language,
            options = languages,
            onValueChange = { newLanguage ->
                onValueChange(setting.copy(language = newLanguage))
            },
            onOptionSelected = { (code, _) ->
                onValueChange(setting.copy(language = code))
            },
            optionToString = { (code, displayName) -> "$displayName ($code)" },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Language") },
        )
    })
}

private fun CardGroupScope.ElevenLabsTTSConfiguration(
    setting: TTSProviderSetting.ElevenLabs,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk_...") },
            label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_api_key_description)) },
        )
    })

    // Base URL
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.elevenlabs.io") },
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_base_url_description)) },
        )
    })

    // Model
    val models = listOf(
        "eleven_multilingual_v2" to "Eleven Multilingual v2",
        "eleven_v3" to "Eleven v3",
        "eleven_flash_v2_5" to "Eleven Flash v2.5"
    )

    item(headlineContent = {
        SelectTextField(
            value = setting.model,
            options = models,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            onOptionSelected = { (modelId, _) ->
                onValueChange(setting.copy(model = modelId))
            },
            optionToString = { (modelId, displayName) -> "$displayName ($modelId)" },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_model)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_model_description)) },
        )
    })

    // Voice ID
    item(headlineContent = {
        OutlinedTextField(
            value = setting.voiceId,
            onValueChange = { newVoiceId ->
                onValueChange(setting.copy(voiceId = newVoiceId))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("JBFqnCBsd6RMkjVDRZzb") },
            label = { Text(stringResource(R.string.setting_tts_page_voice)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_voice_description)) },
        )
    })

    // Stability
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.stability,
            onValueChange = { newStability ->
                onValueChange(setting.copy(stability = newStability.coerceIn(0f, 1f)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_stability),
            supportingText = { Text(stringResource(R.string.setting_tts_page_stability_description)) },
        )
    })

    // Similarity Boost
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.similarityBoost,
            onValueChange = { newSimilarityBoost ->
                onValueChange(setting.copy(similarityBoost = newSimilarityBoost.coerceIn(0f, 1f)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_similarity_boost),
            supportingText = { Text(stringResource(R.string.setting_tts_page_similarity_boost_description)) },
        )
    })
}

private fun CardGroupScope.FishAudioTTSConfiguration(
    setting: TTSProviderSetting.FishAudio,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://fish.audio/app/api-keys") },
            label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_api_key_description)) },
        )
    })

    // Base URL
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.fish.audio") },
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_base_url_description)) },
        )
    })

    // Model (下拉选择框 + 文本输入框，完全同 ElevenLabs 格式)
    item(headlineContent = {
        val recommended = stringResource(R.string.setting_tts_page_model_recommended)
        val free = stringResource(R.string.setting_tts_page_model_free)
        val models = listOf(
            "s2.1-pro" to "S2.1-Pro ($recommended)",
            "s2.1-pro-free" to "S2.1-Pro Free ($free)",
            "s2-pro" to "S2-Pro",
            "s1" to "S1"
        )
        SelectTextField(
            value = setting.model,
            options = models,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            onOptionSelected = { (modelId, _) ->
                onValueChange(setting.copy(model = modelId))
            },
            optionToString = { (modelId, displayName) -> "$displayName ($modelId)" },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_model)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_model_description)) },
        )
    })

    // Voice ID (reference_id)
    item(headlineContent = {
        OutlinedTextField(
            value = setting.referenceId,
            onValueChange = { newReferenceId ->
                onValueChange(setting.copy(referenceId = newReferenceId))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("802e3bc2b27e49c2995d23ef70e6ac89") },
            label = { Text(stringResource(R.string.setting_tts_page_voice_id)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_voice_id_description)) },
        )
    })

    // Temperature
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.temperature,
            onValueChange = { newTemperature ->
                onValueChange(setting.copy(temperature = newTemperature.coerceIn(0f, 1f)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_temperature),
            supportingText = { Text(stringResource(R.string.setting_tts_page_temperature_description)) },
        )
    })

    // Speed
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.speed,
            onValueChange = { newSpeed ->
                onValueChange(setting.copy(speed = newSpeed.coerceIn(0.5f, 2f)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speed),
            supportingText = { Text(stringResource(R.string.setting_tts_page_fish_audio_speed_description)) },
        )
    })
}

private fun CardGroupScope.StepTTSConfiguration(
    setting: TTSProviderSetting.Step,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    item(headlineContent = {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_step_api_key_placeholder)) },
            label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_step_api_key_desc)) },
        )
    })

    // Base URL
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.stepfun.com") },
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_base_url_description)) },
        )
    })

    // Model
    val models = listOf(
        "step-tts-mini",
        "step-tts-vivid",
        "stepaudio-2.5-tts",
        "step-tts-2",
    )

    item(headlineContent = {
        SelectTextField(
            value = setting.model,
            options = models,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            onOptionSelected = { modelId ->
                onValueChange(setting.copy(model = modelId))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_model)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_model_description)) },
        )
    })

    // Voice
    // 部分常用 voice-id, 完整列表见官方开发指南
    // https://platform.stepfun.com/docs/zh/guides/developer/tts
    val voices = listOf(
        "elegantgentle-female", "livelybreezy-female", "energeticconfident-female",
        "jingdiannvsheng", "wenroushunv", "tianmeinvsheng", "qingchunshaonv",
        "wenrounvsheng", "ruanmengnvsheng", "youyanvsheng", "lengyanyujie",
        "shuangkuaijiejie", "wenjingxuejie", "linjiajiejie", "linjiameimei",
        "zhixingjiejie", "cixingnansheng", "wenrounansheng", "yuanqinansheng",
        "zhengpaiqingnian", "ruyananshi", "boyinnansheng", "shenchennanyin",
        "shuangkuainansheng", "ganliannvsheng", "qinhenvsheng", "huolinvsheng",
        "jilingshaonv", "yuanqishaonv", "wenrougongzi", "qingniandaxuesheng",
    )

    item(headlineContent = {
        SelectTextField(
            value = setting.voice,
            options = voices,
            onValueChange = { newVoice ->
                onValueChange(setting.copy(voice = newVoice))
            },
            onOptionSelected = { voiceId ->
                onValueChange(setting.copy(voice = voiceId))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_voice)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_voice_description)) },
        )
    })

    // Response Format
    val formats = listOf("mp3", "wav", "pcm", "opus", "flac")

    item(headlineContent = {
        SelectTextField(
            value = setting.responseFormat,
            options = formats,
            onValueChange = { newFormat ->
                onValueChange(setting.copy(responseFormat = newFormat))
            },
            onOptionSelected = { format ->
                onValueChange(setting.copy(responseFormat = format))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_response_format)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_response_format_desc)) },
        )
    })

    // Speed
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.speed,
            onValueChange = { newSpeed ->
                if (newSpeed in 0.5f..2.0f) {
                    onValueChange(setting.copy(speed = newSpeed))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speed),
            supportingText = { Text(stringResource(R.string.setting_tts_page_step_speed_desc)) },
        )
    })

    // Volume
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.volume,
            onValueChange = { newVolume ->
                if (newVolume in 0.1f..2.0f) {
                    onValueChange(setting.copy(volume = newVolume))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_volume),
            supportingText = { Text(stringResource(R.string.setting_tts_page_volume_desc)) },
        )
    })

    // Sample Rate
    val sampleRates = listOf(8000, 16000, 22050, 24000)

    item(headlineContent = {
        SelectTextField(
            value = setting.sampleRate.toString(),
            options = sampleRates,
            readOnly = true,
            onOptionSelected = { rate ->
                onValueChange(setting.copy(sampleRate = rate))
            },
            optionToString = { rate -> "$rate Hz" },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_asr_configure_sample_rate)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_sample_rate_desc)) },
        )
    })

    // Instruction (仅 stepaudio-2.5-tts 生效)
    item(headlineContent = {
        OutlinedTextField(
            value = setting.instruction,
            onValueChange = { newInstruction ->
                // 服务端上限 200 字符, 客户端做一层保护
                if (newInstruction.length <= 200) {
                    onValueChange(setting.copy(instruction = newInstruction))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_instruction_placeholder)) },
            minLines = 2,
            maxLines = 4,
            label = { Text(stringResource(R.string.setting_tts_page_instruction)) },
            supportingText = { Text(stringResource(R.string.setting_tts_page_instruction_desc)) },
        )
    })
}

private fun CardGroupScope.VolcengineTTSConfiguration(
    setting: TTSProviderSetting.Volcengine,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    item(headlineContent = {
        var keyVisible by remember(setting.id) { mutableStateOf(false) }
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) HugeIcons.ViewOff else HugeIcons.View,
                        contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key",
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            supportingText = { Text("请填写豆包语音控制台的 API Key，不是火山方舟控制台的 API Key。") },
        )
    })
    item(headlineContent = {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            placeholder = { Text("https://openspeech.bytedance.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        )
    })
    item(headlineContent = {
        OutlinedTextField(
            value = setting.resourceId,
            onValueChange = { onValueChange(setting.copy(resourceId = it)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("资源 ID") },
            supportingText = { Text("需与已开通的服务和音色匹配，默认 seed-tts-2.0。") },
        )
    })
    item(headlineContent = {
        OutlinedTextField(
            value = setting.speaker,
            onValueChange = { onValueChange(setting.copy(speaker = it)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("音色 ID") },
            supportingText = { Text("填写控制台中的音色 ID，默认使用 VV 音色。") },
        )
    })
    item(headlineContent = {
        OutlinedNumberInput(
            value = setting.speechRate,
            onValueChange = { onValueChange(setting.copy(speechRate = it.coerceIn(-50, 100))) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speed),
            supportingText = { Text("范围 -50～100，0 为正常语速，-50 为半速，100 为两倍速。") },
        )
    })
}
