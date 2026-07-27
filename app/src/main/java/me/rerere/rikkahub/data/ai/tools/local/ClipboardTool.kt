package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.readClipboardText
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.common.android.Logging

internal fun buildClipboardTool(context: Context): Tool = Tool(
    name = "clipboard_tool",
    description = """
        Read or write plain text from the device clipboard.
        Use action: read or write. For write, provide text.
        Do NOT write to the clipboard unless the user has explicitly requested it.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put(
                        "enum",
                        buildJsonArray {
                            add("read")
                            add("write")
                        }
                    )
                    put("description", "Operation to perform: read or write")
                })
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to write to the clipboard (required for write)")
                })
            },
            required = listOf("action")
        )
    },
    execute = {
        val params = it.jsonObject
        val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        val type = if (action == "read") "读取剪贴板" else "写入剪贴板"
        val payload = when (action) {
            "read" -> {
                buildJsonObject {
                    put("text", context.readClipboardText())
                }
            }

            "write" -> {
                val text = params["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")
                context.writeClipboardText(text)
                buildJsonObject {
                    put("success", true)
                    put("text", text)
                }
            }

            else -> error("unknown action: $action, must be one of [read, write]")
        }
        Logging.logPermission(
            type = type,
            rawData = params.toString(),
            resultData = payload.toString(),
            granted = true,
        )
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
