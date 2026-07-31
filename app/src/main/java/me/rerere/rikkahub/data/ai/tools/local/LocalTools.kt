package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.Logging
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.tts.provider.TTSManager

class LocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val ttsManager: TTSManager,
    private val settingsStore: SettingsStore,
) {
    val javascriptTool by lazy { buildJavascriptTool() }

    val timeTool by lazy { buildTimeInfoTool() }

    val clipboardTool by lazy { buildClipboardTool(context) }

    val batteryTool by lazy { buildBatteryTool(context) }

    val locationTool by lazy { buildLocationTool(context) }

    val ttsTool by lazy { buildTextToSpeechTool(eventBus, ttsManager, settingsStore) }

    val askUserTool by lazy { buildAskUserTool() }

    val screenTimeTool by lazy { buildScreenTimeTool(context, eventBus) }

    val calendarQueryTool by lazy { buildCalendarQueryTool(context) }

    val calendarCreateTool by lazy { buildCalendarCreateTool(context) }

    fun getTools(
        options: List<LocalToolOption>,
        manualAuthorizationTools: Set<LocalToolOption> = emptySet(),
    ): List<Tool> {
        val tools = mutableListOf<Tool>()
        fun add(option: LocalToolOption, tool: Tool) {
            if (option in options) {
                tools += if (option in manualAuthorizationTools) {
                    tool.withManualAuthorization(option)
                } else {
                    tool.withAlwaysAllowedAuthorizationLog()
                }
            }
        }
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        add(LocalToolOption.Clipboard, clipboardTool)
        add(LocalToolOption.Battery, batteryTool)
        add(LocalToolOption.Location, locationTool)
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        add(LocalToolOption.ScreenTime, screenTimeTool)
        add(LocalToolOption.Calendar, calendarQueryTool)
        add(LocalToolOption.Calendar, calendarCreateTool)
        return tools
    }

    private fun Tool.withAlwaysAllowedAuthorizationLog(): Tool {
        val originalExecute = execute
        return copy(
            execute = { arguments ->
                Logging.withAlwaysAllowedPermissionLogging {
                    originalExecute(arguments)
                }
            }
        )
    }

    private fun Tool.withManualAuthorization(option: LocalToolOption): Tool {
        val originalExecute = execute
        return copy(
            execute = { arguments ->
                val action = authorizationAction(option, name, arguments)
                val decision = CompletableDeferred<Boolean>()
                eventBus.emit(
                    AppEvent.LocalToolAuthorization(
                        title = context.getString(R.string.local_tool_authorization_title, action),
                        decision = decision,
                    )
                )
                val granted = decision.await()
                if (granted) {
                    originalExecute(arguments)
                } else {
                    Logging.logPermission(
                        type = action,
                        toolName = name,
                        rawData = arguments.toString(),
                        granted = false,
                    )
                    listOf(
                        UIMessagePart.Text(
                            buildJsonObject {
                                put("error", "USER_DENIED_PERMISSION")
                            }.toString()
                        )
                    )
                }
            }
        )
    }

    private fun authorizationAction(
        option: LocalToolOption,
        toolName: String,
        arguments: kotlinx.serialization.json.JsonElement,
    ): String = context.getString(
        when (option) {
            LocalToolOption.Clipboard -> {
                if (arguments.jsonObject["action"]?.jsonPrimitive?.contentOrNull == "write") {
                    R.string.local_tool_authorization_write_clipboard
                } else {
                    R.string.local_tool_authorization_read_clipboard
                }
            }
            LocalToolOption.Battery -> R.string.local_tool_authorization_get_battery
            LocalToolOption.Location -> R.string.local_tool_authorization_get_location
            LocalToolOption.ScreenTime -> R.string.local_tool_authorization_get_screen_time
            LocalToolOption.Calendar -> {
                if (toolName == calendarCreateTool.name) {
                    R.string.local_tool_authorization_create_calendar
                } else {
                    R.string.local_tool_authorization_read_calendar
                }
            }
            else -> error("Manual authorization is unsupported for $option")
        }
    )
}
