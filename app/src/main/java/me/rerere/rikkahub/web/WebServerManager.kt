package me.rerere.rikkahub.web

import android.content.Context
import android.util.Log
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.web.startWebServer
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections

private const val TAG = "WebServerManager"
private const val HOST_ALL_INTERFACES = "0.0.0.0"
private const val HOST_LOOPBACK = "127.0.0.1"

data class WebServerState(
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val port: Int = 8080,
    val serviceName: String = DEFAULT_SERVICE_NAME,
    val localhostOnly: Boolean = false,
    val hostname: String? = null,
    val addresses: List<String> = emptyList(),
    val error: String? = null
)

fun formatWebServerUrl(address: String, port: Int): String {
    val host = if (':' in address) {
        "[${address.replace("%", "%25")}]"
    } else {
        address
    }
    return "http://$host:$port"
}

class WebServerManager(
    private val context: Context,
    private val appScope: AppScope,
    private val chatService: ChatService,
    private val conversationRepo: ConversationRepository,
    private val folderRepo: FolderRepository,
    private val settingsStore: SettingsStore,
    private val filesManager: FilesManager
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val nsdRegistrar = NsdServiceRegistrar(context)

    private val _state = MutableStateFlow(WebServerState())
    val state: StateFlow<WebServerState> = _state.asStateFlow()

    fun start(
        port: Int = 8080,
        serviceName: String = DEFAULT_SERVICE_NAME,
        localhostOnly: Boolean = false
    ) {
        if (server != null) {
            Log.w(TAG, "Server already running")
            return
        }

        appScope.launch {
            // 仅本机模式绑定回环地址
            val host = if (localhostOnly) HOST_LOOPBACK else HOST_ALL_INTERFACES
            val baseState = WebServerState(
                port = port,
                serviceName = serviceName,
                localhostOnly = localhostOnly,
                addresses = if (localhostOnly) emptyList() else getLocalIpAddresses(),
            )
            try {
                _state.value = _state.value.copy(isLoading = true)
                Log.i(TAG, "Starting web server on $host:$port")
                if (!isPortAvailable(port)) {
                    Log.w(TAG, "Port $port is already in use")
                    _state.value = baseState.copy(error = "Port $port is already in use")
                    return@launch
                }
                server = startWebServer(port = port, host = host) {
                    configureWebApi(context, chatService, conversationRepo, folderRepo, settingsStore, filesManager)
                }.start(wait = false)

                _state.value = baseState.copy(isRunning = true)
                // 仅局域网模式注册 mDNS
                if (!localhostOnly) {
                    runCatching {
                        nsdRegistrar.register(
                            port = port,
                            serviceName = serviceName,
                            onRegistered = { info ->
                                _state.value = _state.value.copy(
                                    serviceName = info.serviceName,
                                    hostname = info.hostname,
                                )
                            }
                        )
                    }.onFailure {
                        Log.w(TAG, "NSD register failed", it)
                        _state.value = _state.value.copy(
                            error = "mDNS registration failed: ${it.message}",
                        )
                    }
                }
                Log.i(TAG, "Web server started successfully on $host:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start web server", e)
                _state.value = baseState.copy(error = e.message)
            }
        }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(isRunning = false, isLoading = false, error = message)
    }

    fun stop() {
        _state.value =
            _state.value.copy(
                isRunning = false,
                isLoading = true,
                hostname = null,
                addresses = emptyList(),
                error = null,
            )
        appScope.launch {
            try {
                Log.i(TAG, "Stopping web server")
                server?.stop(1000, 2000)
                server = null
                runCatching {
                    nsdRegistrar.unregister()
                }.onFailure {
                    Log.w(TAG, "NSD unregister failed", it)
                }
                _state.value = _state.value.copy(isLoading = false)
                Log.i(TAG, "Web server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop web server", e)
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun restart(
        port: Int = _state.value.port,
        serviceName: String = _state.value.serviceName,
        localhostOnly: Boolean = _state.value.localhostOnly
    ) {
        stop()
        start(port, serviceName, localhostOnly)
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    private fun getLocalIpAddresses(): List<String> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses).asSequence()
            }
            .filter { address ->
                !address.isLoopbackAddress &&
                    !address.isAnyLocalAddress &&
                    !address.isMulticastAddress
            }
            .sortedWith(compareBy({ it !is Inet4Address }, { it.hostAddress }))
            .mapNotNull { it.hostAddress }
            .distinct()
            .toList()
    }.onFailure {
        Log.w(TAG, "Failed to enumerate local IP addresses", it)
    }.getOrDefault(emptyList())
}
