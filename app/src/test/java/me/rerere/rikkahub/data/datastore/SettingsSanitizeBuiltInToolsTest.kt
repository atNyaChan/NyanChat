package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsSanitizeBuiltInToolsTest {
    @Test
    fun `unsupported built-in tools are dropped per provider type`() {
        val model = Model(
            id = Uuid.random(),
            modelId = "gpt-custom",
            displayName = "GPT Custom",
            tools = setOf(BuiltInTools.Search, BuiltInTools.ImageGeneration),
        )
        val settings = Settings(
            providers = listOf(
                ProviderSetting.OpenAI(name = "Chat Completions", models = listOf(model)),
                ProviderSetting.Claude(name = "Claude", models = listOf(model.copy(id = Uuid.random()))),
                ProviderSetting.Google(name = "Gemini", models = listOf(model.copy(id = Uuid.random()))),
            )
        )

        val sanitized = settings.sanitizeBuiltInTools()

        // Chat Completions 不支持任何内置工具，Claude 只有搜索，Google 支持搜索与 URL 上下文
        assertEquals(emptySet<BuiltInTools>(), sanitized.providers[0].models.single().tools)
        assertEquals(setOf(BuiltInTools.Search), sanitized.providers[1].models.single().tools)
        assertEquals(setOf(BuiltInTools.Search), sanitized.providers[2].models.single().tools)
    }

    @Test
    fun `model provider override takes precedence when sanitizing`() {
        val model = Model(
            id = Uuid.random(),
            modelId = "gpt-custom",
            displayName = "GPT Custom",
            tools = setOf(BuiltInTools.Search, BuiltInTools.UrlContext),
            providerOverwrite = ProviderSetting.Google(),
        )
        val settings = Settings(
            providers = listOf(ProviderSetting.Claude(models = listOf(model)))
        )

        val sanitized = settings.sanitizeBuiltInTools()

        assertEquals(
            setOf(BuiltInTools.Search, BuiltInTools.UrlContext),
            sanitized.providers.single().models.single().tools,
        )
    }
}
