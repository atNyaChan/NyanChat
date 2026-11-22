package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
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
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.phoneuse.PhoneUseManager
import me.rerere.rikkahub.phoneuse.isFloatingChatWindowOn
import me.rerere.rikkahub.phoneuse.isPhoneUseCheckedInSystem
import me.rerere.tts.provider.TTSManager

class LocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val ttsManager: TTSManager,
    private val settingsStore: SettingsStore,
    private val phoneUseManager: PhoneUseManager,
    private val filesManager: FilesManager,
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

    val chartDisplayTool by lazy { buildChartDisplayTool() }

    val phoneUseTool by lazy {
        buildPhoneUseTool(
            ownPackageName = context.packageName,
            manager = phoneUseManager,
            isFloatingWindowOn = { isFloatingChatWindowOn(context, settingsStore.settingsFlow.value) },
            isCheckedInSystem = { isPhoneUseCheckedInSystem(context) },
            saveScreenshot = { bytes ->
                filesManager.createChatFilesByByteArrays(
                    byteArrays = listOf(bytes),
                    mimeType = "image/jpeg",
                ).first().toString()
            },
        )
    }

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
        if (options.contains(LocalToolOption.ChartDisplay)) {
            tools.add(chartDisplayTool)
        }
        // 系统版本不支持时不提供（比如导入的备份里带着这个开关）
        if (options.contains(LocalToolOption.PhoneUse) && PhoneUseManager.isSupported) {
            // Phone Use 一次回答里会连续调用很多次操作手机，逐次授权只在第一次操作时询问
            tools += if (LocalToolOption.PhoneUse in manualAuthorizationTools) {
                phoneUseTool.withFirstUseManualAuthorization(LocalToolOption.PhoneUse)
            } else {
                phoneUseTool
            }
        }
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
                if (awaitAuthorization(action)) {
                    originalExecute(arguments)
                } else {
                    deniedResult(action, name, arguments)
                }
            }
        )
    }

    /**
     * 同一次回答里的后续调用沿用第一次的授权结果。
     * 工具实例由每轮生成单独创建，所以状态只覆盖这一条回答。
     */
    private fun Tool.withFirstUseManualAuthorization(option: LocalToolOption): Tool {
        val originalExecute = execute
        val mutex = Mutex()
        var granted: Boolean? = null
        return copy(
            execute = { arguments ->
                val action = authorizationAction(option, name, arguments)
                val allowed = mutex.withLock {
                    granted ?: awaitAuthorization(action).also { granted = it }
                }
                if (allowed) {
                    originalExecute(arguments)
                } else {
                    deniedResult(action, name, arguments)
                }
            }
        )
    }

    private suspend fun awaitAuthorization(action: String): Boolean {
        val decision = CompletableDeferred<Boolean>()
        eventBus.emit(
            AppEvent.LocalToolAuthorization(
                title = context.getString(R.string.local_tool_authorization_title, action),
                decision = decision,
            )
        )
        return decision.await()
    }

    private suspend fun deniedResult(
        action: String,
        toolName: String,
        arguments: JsonElement,
    ): List<UIMessagePart> {
        Logging.logPermission(
            type = action,
            toolName = toolName,
            rawData = arguments.toString(),
            granted = false,
        )
        return listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("error", "USER_DENIED_PERMISSION")
                }.toString()
            )
        )
    }

    private fun authorizationAction(
        option: LocalToolOption,
        toolName: String,
        arguments: JsonElement,
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
            LocalToolOption.PhoneUse -> R.string.local_tool_authorization_phone_use
            else -> error("Manual authorization is unsupported for $option")
        }
    )
}
