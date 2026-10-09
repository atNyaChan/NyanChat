package me.rerere.rikkahub.data.sync.webdav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RikkaHubCompatibilityTest {
    @Test
    fun `fork-only settings are removed from compatible export`() {
        val source = Json.parseToJsonElement(
            """
            {
              "skillOrder": ["one"],
              "workspaceOrder": ["two"],
              "displaySetting": {
                "enableCodeLigatures": true,
                "useChatFontGlobally": true,
                "screenCornerAdaptation": "ALL",
                "parseMidThink": true,
                "defaultFontWeight": 700,
                "boldFontWeight": 800,
                "chatFontFamily": "outfit",
                "showModelIcon": true
              },
              "assistants": [{
                "contextCache": "ONE_HOUR",
                "manualAuthorizationTools": [{"type": "battery"}],
                "useBuiltInSearch": true,
                "localTools": [
                  {"type": "battery"},
                  {"type": "location"},
                  {"type": "clipboard"}
                ]
              }],
              "webDavConfig": {"items": ["DATABASE", "FILES", "WORKSPACE"]},
              "s3Config": {"items": ["DATABASE", "WORKSPACE"]},
              "uploadS3Config": {"items": ["FILES"]},
              "providers": [{"models": [{"price": {"input": 1.0}, "name": "model"}]}]
            }
            """.trimIndent()
        )

        val result = makeRikkaHubCompatible(source).jsonObject

        assertFalse("skillOrder" in result)
        assertFalse("workspaceOrder" in result)
        result["displaySetting"]!!.jsonObject.let {
            assertFalse("enableCodeLigatures" in it)
            assertFalse("useChatFontGlobally" in it)
            assertFalse("screenCornerAdaptation" in it)
            assertFalse("parseMidThink" in it)
            assertFalse("defaultFontWeight" in it)
            assertFalse("boldFontWeight" in it)
            assertEquals("\"serif\"", it["chatFontFamily"].toString())
            assertEquals("true", it["showModelIcon"].toString())
        }
        result["assistants"]!!.jsonArray.single().jsonObject.let {
            assertFalse("contextCache" in it)
            assertFalse("manualAuthorizationTools" in it)
            assertFalse("useBuiltInSearch" in it)
            assertEquals(
                listOf("clipboard"),
                it["localTools"]!!.jsonArray.map { tool ->
                    tool.jsonObject["type"].toString().trim('"')
                }
            )
        }
        assertEquals(
            listOf("\"DATABASE\"", "\"FILES\""),
            result["webDavConfig"]!!.jsonObject["items"]!!.jsonArray.map { it.toString() }
        )
        assertEquals(
            listOf("\"DATABASE\""),
            result["s3Config"]!!.jsonObject["items"]!!.jsonArray.map { it.toString() }
        )
        assertEquals(
            listOf("\"FILES\""),
            result["uploadS3Config"]!!.jsonObject["items"]!!.jsonArray.map { it.toString() }
        )
        result["providers"]!!.jsonArray.single().jsonObject["models"]!!
            .jsonArray.single().jsonObject.let {
                assertFalse("price" in it)
                assertEquals("\"model\"", it["name"].toString())
            }
    }

    @Test
    fun `compatible export keeps values upstream understands`() {
        val source = Json.parseToJsonElement(
            """
            {
              "displaySetting": {
                "chatFontFamily": "monospace"
              },
              "assistants": [{
                "localTools": [{"type": "clipboard"}]
              }],
              "webDavConfig": {"items": ["DATABASE"]},
              "s3Config": {"endpoint": "https://example.com"}
            }
            """.trimIndent()
        )

        val result = makeRikkaHubCompatible(source).jsonObject

        assertEquals("\"monospace\"", result["displaySetting"]!!.jsonObject["chatFontFamily"].toString())
        assertEquals(
            listOf("clipboard"),
            result["assistants"]!!.jsonArray.single().jsonObject["localTools"]!!.jsonArray
                .map { it.jsonObject["type"].toString().trim('"') }
        )
        assertEquals(
            listOf("\"DATABASE\""),
            result["webDavConfig"]!!.jsonObject["items"]!!.jsonArray.map { it.toString() }
        )
        // 没有 items 字段的配置原样保留
        assertEquals("\"https://example.com\"", result["s3Config"]!!.jsonObject["endpoint"].toString())
    }
}
