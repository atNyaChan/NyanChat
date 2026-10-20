package me.rerere.rikkahub.ui.pages.setting.components

import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.OAuthCredential
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.QuotaQuery
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ProviderConfigureConvertToTest {
    @Test
    fun `convertTo should keep common fields and switch official endpoint to target default`() {
        val model = Model(
            id = Uuid.random(),
            modelId = "gpt-custom",
            displayName = "GPT Custom"
        )
        val quotaQuery = QuotaQuery(
            enabled = true,
            url = "{{baseUrl}}/custom/credits",
            resultPath = "data.balance"
        )
        val original = ProviderSetting.OpenAI(
            id = Uuid.random(),
            enabled = false,
            name = "My Provider",
            models = listOf(model),
            quotaQuery = quotaQuery,
            customHeaders = listOf(CustomHeader("User-Agent", "test-agent")),
            apiKey = "sk-test",
            baseUrl = "https://api.openai.com/v1"
        )

        val converted = original.convertTo(ProviderSetting.Google::class)
        assertTrue(converted is ProviderSetting.Google)
        val google = converted as ProviderSetting.Google

        assertEquals(original.id, google.id)
        assertEquals(original.enabled, google.enabled)
        assertEquals(original.name, google.name)
        assertEquals(original.models, google.models)
        assertEquals(original.quotaQuery, google.quotaQuery)
        assertEquals(original.customHeaders, google.customHeaders)
        assertEquals(original.apiKey, google.apiKey)
        assertEquals("https://generativelanguage.googleapis.com/v1beta", google.baseUrl)
    }

    @Test
    fun `convertTo should preserve third-party host and replace version suffix`() {
        val original = ProviderSetting.OpenAI(
            name = "Proxy OpenAI",
            apiKey = "proxy-key",
            baseUrl = "https://gateway.example.com/api/v1"
        )

        val converted = original.convertTo(ProviderSetting.Google::class) as ProviderSetting.Google
        assertEquals("https://gateway.example.com/api/v1beta", converted.baseUrl)
        assertEquals("gateway.example.com", converted.baseUrl.toHttpUrlOrNull()?.host)
    }

    @Test
    fun `convertTo should preserve third-party host and append target path when needed`() {
        val original = ProviderSetting.Google(
            name = "Proxy Google",
            apiKey = "proxy-google-key",
            baseUrl = "https://proxy.example.com/vendor/gemini"
        )

        val converted = original.convertTo(ProviderSetting.OpenAI::class) as ProviderSetting.OpenAI
        assertEquals("https://proxy.example.com/vendor/gemini/v1", converted.baseUrl)
        assertEquals("proxy.example.com", converted.baseUrl.toHttpUrlOrNull()?.host)
    }

    @Test
    fun `convertTo should preserve third-party host when switching to claude`() {
        val original = ProviderSetting.OpenAI(
            name = "Proxy OpenAI",
            apiKey = "proxy-key",
            baseUrl = "https://gateway.example.com/proxy/v1beta"
        )

        val converted = original.convertTo(ProviderSetting.Claude::class) as ProviderSetting.Claude
        assertEquals("https://gateway.example.com/proxy/v1", converted.baseUrl)
        assertEquals("gateway.example.com", converted.baseUrl.toHttpUrlOrNull()?.host)
    }

    @Test
    fun `convertTo should return same instance for same type`() {
        val original = ProviderSetting.OpenAI(
            name = "Same Type",
            apiKey = "same-key",
            baseUrl = "https://api.openai.com/v1"
        )

        val converted = original.convertTo(ProviderSetting.OpenAI::class)
        assertSame(original, converted)
    }

    @Test
    fun `convertTo drops built-in tools the target provider does not support`() {
        val model = Model(
            id = Uuid.random(),
            modelId = "gpt-custom",
            displayName = "GPT Custom",
            tools = setOf(BuiltInTools.Search, BuiltInTools.ImageGeneration),
        )
        val original = ProviderSetting.OpenAI(
            name = "Responses Provider",
            useResponseApi = true,
            models = listOf(model),
        )

        val google = original.convertTo(ProviderSetting.Google::class) as ProviderSetting.Google
        assertEquals(setOf(BuiltInTools.Search), google.models.single().tools)

        val openai = google.convertTo(ProviderSetting.OpenAI::class) as ProviderSetting.OpenAI
        assertEquals(emptySet<BuiltInTools>(), openai.models.single().tools)
    }

    @Test
    fun `convertTo keeps tools supported by the model's provider override`() {
        val model = Model(
            id = Uuid.random(),
            modelId = "gpt-custom",
            displayName = "GPT Custom",
            tools = setOf(BuiltInTools.Search, BuiltInTools.UrlContext),
            providerOverwrite = ProviderSetting.Google(),
        )
        val original = ProviderSetting.OpenAI(name = "Responses Provider", models = listOf(model))

        val claude = original.convertTo(ProviderSetting.Claude::class) as ProviderSetting.Claude
        assertEquals(
            setOf(BuiltInTools.Search, BuiltInTools.UrlContext),
            claude.models.single().tools,
        )
    }

    @Test
    fun `convertTo should keep original base url when source url is invalid`() {
        val original = ProviderSetting.Claude(
            name = "Invalid URL Provider",
            apiKey = "invalid-key",
            baseUrl = "not-a-url"
        )

        val converted = original.convertTo(ProviderSetting.OpenAI::class) as ProviderSetting.OpenAI
        assertEquals("not-a-url", converted.baseUrl)
    }

    @Test
    fun `convertTo from account provider uses target default endpoint without credential`() {
        val original = ProviderSetting.ChatGPT(
            name = "ChatGPT",
            credential = OAuthCredential(accessToken = "secret-access", refreshToken = "secret-refresh"),
        )

        val converted = original.convertTo(ProviderSetting.OpenAI::class) as ProviderSetting.OpenAI
        assertEquals(original.id, converted.id)
        assertEquals("", converted.apiKey)
        assertEquals("https://api.openai.com/v1", converted.baseUrl)
        assertEquals(ProviderSetting.OpenAI().quotaQuery, converted.quotaQuery)
    }
}
