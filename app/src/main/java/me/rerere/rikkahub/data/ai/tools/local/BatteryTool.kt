package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.os.BatteryManager
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.Logging

internal fun buildBatteryTool(context: Context): Tool = Tool(
    name = "battery_level",
    description = "Get the device battery level as a percentage.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {},
            required = emptyList(),
        )
    },
    execute = {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val result = buildJsonObject {
            put("level", level)
            put("unit", "percent")
        }.toString()
        Logging.logPermission(
            type = "读取电池电量",
            rawData = "{}",
            resultData = result,
            granted = true,
        )
        listOf(
            UIMessagePart.Text(result)
        )
    },
)
