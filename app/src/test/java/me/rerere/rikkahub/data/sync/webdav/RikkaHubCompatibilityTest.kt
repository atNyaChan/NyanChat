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
                "showModelIcon": true
              },
              "assistants": [{
                "contextCache": "ONE_HOUR",
                "manualAuthorizationTools": [{"type": "battery"}],
                "localTools": [
                  {"type": "battery"},
                  {"type": "location"},
                  {"type": "clipboard"}
                ]
              }],
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
            assertEquals("true", it["showModelIcon"].toString())
        }
        result["assistants"]!!.jsonArray.single().jsonObject.let {
            assertFalse("contextCache" in it)
            assertFalse("manualAuthorizationTools" in it)
            assertEquals(
                listOf("clipboard"),
                it["localTools"]!!.jsonArray.map { tool ->
                    tool.jsonObject["type"].toString().trim('"')
                }
            )
        }
        result["providers"]!!.jsonArray.single().jsonObject["models"]!!
            .jsonArray.single().jsonObject.let {
                assertFalse("price" in it)
                assertEquals("\"model\"", it["name"].toString())
            }
    }
}
