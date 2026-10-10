package me.rerere.rikkahub.data.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 把本分支的设置 JSON 改写成上游 RikkaHub 能反序列化的形状，供旧格式 ZIP 备份使用。
 */
internal fun makeRikkaHubCompatible(settings: JsonElement): JsonElement {
    fun stripForkFields(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { it != "price" }.mapValues { (_, value) -> stripForkFields(value) }
        )
        is JsonArray -> JsonArray(element.map(::stripForkFields))
        else -> element
    }

    val root = stripForkFields(settings) as? JsonObject ?: return settings
    val compatibleRoot = root - setOf("skillOrder", "workspaceOrder")

    val displaySetting = (compatibleRoot["displaySetting"] as? JsonObject)?.let { display ->
        val stripped = display - setOf(
            "enableCodeLigatures",
            "useChatFontGlobally",
            "screenCornerAdaptation",
            "showThinkingContentPreview",
            "parseMidThink",
            "defaultFontWeight",
            "boldFontWeight",
        )
        // 上游不认识 OUTFIT，回落成它认识的 SERIF
        if ((stripped["chatFontFamily"] as? JsonPrimitive)?.content == "outfit") {
            stripped + ("chatFontFamily" to JsonPrimitive("serif"))
        } else {
            stripped
        }
    }
    val assistants = (compatibleRoot["assistants"] as? JsonArray)?.let { array ->
        JsonArray(array.map { assistantElement ->
            val assistant = assistantElement as? JsonObject ?: return@map assistantElement
            val compatibleAssistant = assistant - setOf(
                "contextCache",
                "manualAuthorizationTools",
                "includeHistoryReasoning",
                "useBuiltInSearch",
            )
            val localTools = (compatibleAssistant["localTools"] as? JsonArray)?.let { tools ->
                JsonArray(tools.filterNot { tool ->
                    val type = (tool as? JsonObject)?.get("type")?.toString()?.trim('"')
                    type == "battery" || type == "location"
                })
            }
            JsonObject(
                if (localTools == null) {
                    compatibleAssistant
                } else {
                    compatibleAssistant + ("localTools" to localTools)
                }
            )
        })
    }

    val result = compatibleRoot.toMutableMap()
    displaySetting?.let { result["displaySetting"] = JsonObject(it) }
    assistants?.let { result["assistants"] = it }
    // 上游的 BackupItem 没有 WORKSPACE
    for (configKey in listOf("webDavConfig", "s3Config", "uploadS3Config")) {
        val config = result[configKey] as? JsonObject ?: continue
        val items = config["items"] as? JsonArray ?: continue
        result[configKey] = JsonObject(
            config + ("items" to JsonArray(items.filterNot { (it as? JsonPrimitive)?.content == "WORKSPACE" }))
        )
    }
    return JsonObject(result)
}
