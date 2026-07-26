package me.rerere.common.android

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

private const val MAX_RECENT_LOGS = 100

@Serializable
sealed class LogEntry {
    abstract val id: Uuid
    abstract val timestamp: Long
    abstract val tag: String

    @Serializable
    data class TextLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val message: String
    ) : LogEntry()

    @Serializable
    data class RequestLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val url: String,
        val method: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestBody: String? = null,
        val responseCode: Int? = null,
        val responseHeaders: Map<String, String> = emptyMap(),
        val responseBody: String? = null,
        val durationMs: Long? = null,
        val error: String? = null
    ) : LogEntry()

    @Serializable
    data class PermissionLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String = "Permission",
        val type: String,
        val rawData: String,
        val resultData: String? = null,
    ) : LogEntry()
}

object Logging {
    private val recentLogs = arrayListOf<LogEntry>()
    fun log(tag: String, message: String) {
        addLog(LogEntry.TextLog(tag = tag, message = message))
    }

    fun logRequest(entry: LogEntry.RequestLog) {
        addLog(entry)
    }

    fun logPermission(type: String, rawData: String, resultData: String? = null) {
        addLog(LogEntry.PermissionLog(type = type, rawData = rawData, resultData = resultData))
    }

    private fun addLog(entry: LogEntry) {
        synchronized(recentLogs) {
            recentLogs.add(0, entry)
            if (recentLogs.size > MAX_RECENT_LOGS) {
                recentLogs.removeLastOrNull()
            }
        }
    }

    fun getRecentLogs(): List<LogEntry> {
        synchronized(recentLogs) {
            return recentLogs.toList()
        }
    }

    fun getTextLogs(): List<LogEntry.TextLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.TextLog>()
        }
    }

    fun getRequestLogs(): List<LogEntry.RequestLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.RequestLog>()
        }
    }

    fun getPermissionLogs(): List<LogEntry.PermissionLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.PermissionLog>()
        }
    }

    fun clearRequests() {
        synchronized(recentLogs) {
            recentLogs.removeAll { it is LogEntry.RequestLog }
        }
    }

    fun clearPermissions() {
        synchronized(recentLogs) {
            recentLogs.removeAll { it is LogEntry.PermissionLog }
        }
    }

    fun clear() = clearRequests()
}
