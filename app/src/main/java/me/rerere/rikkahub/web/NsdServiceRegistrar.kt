package me.rerere.rikkahub.web

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

private const val TAG = "NsdServiceRegistrar"
private const val DEFAULT_SERVICE_TYPE = "_http._tcp.local."
const val DEFAULT_SERVICE_NAME = "nyanchat"

data class RegisteredServiceInfo(
    val serviceName: String,
    val hostname: String,
    val port: Int,
    val address: InetAddress
)

class NsdServiceRegistrar(
    private val context: Context
) {
    private var jmdns: JmDNS? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    suspend fun register(
        port: Int,
        serviceName: String = DEFAULT_SERVICE_NAME,
        serviceType: String = DEFAULT_SERVICE_TYPE,
        onRegistered: ((RegisteredServiceInfo) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (jmdns != null) {
            unregister()
        }

        try {
            // Acquire multicast lock for mDNS
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("jmdns-lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            val address = getLocalIpAddress()
            if (address == null) {
                error("No usable local IPv4 address is available for mDNS")
            }

            Log.i(TAG, "Creating JmDNS with hostname=$serviceName, address=$address")

            // Create JmDNS instance with custom hostname
            // This will register hostname.local -> IP address
            val mdns = JmDNS.create(address, serviceName)
            jmdns = mdns

            // Register HTTP service
            val serviceInfo = ServiceInfo.create(
                serviceType,
                serviceName,
                port,
                "NyanChat Web Server"
            )
            mdns.registerService(serviceInfo)

            Log.i(
                TAG,
                "Service registered: $serviceName.$serviceType port=$port, hostname=${mdns.hostName}"
            )

            val registeredHostname = mdns.hostName.removeSuffix(".")
            onRegistered?.invoke(
                RegisteredServiceInfo(
                    serviceName = serviceInfo.name,
                    hostname = registeredHostname,
                    port = port,
                    address = address
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register service", e)
            cleanup()
            throw e
        }
    }

    suspend fun unregister() = withContext(Dispatchers.IO) {
        cleanup()
    }

    private fun cleanup() {
        runCatching {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        }.onFailure {
            Log.w(TAG, "Failed to close JmDNS", it)
        }
        jmdns = null

        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }.onFailure {
            Log.w(TAG, "Failed to release multicast lock", it)
        }
        multicastLock = null

        Log.i(TAG, "Service unregistered")
    }

    private fun getLocalIpAddress(): InetAddress? {
        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { networkInterface ->
                    val name = networkInterface.name.lowercase()
                    when {
                        name.startsWith("wlan") || name.startsWith("wifi") -> 0
                        name.startsWith("ap") || name.startsWith("swlan") -> 1
                        else -> 2
                    }
                }
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { address ->
                    address.isSiteLocalAddress &&
                        !address.isLoopbackAddress &&
                        !address.isAnyLocalAddress
                }
        }.onFailure { e ->
            Log.e(TAG, "Failed to get local IP address", e)
        }.getOrNull()
    }
}
