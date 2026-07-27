package me.rerere.rikkahub.data.ai.tools.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.Logging
import kotlin.coroutines.resume

private suspend fun LocationManager.requestCurrentLocation(
    context: Context,
    provider: String,
): Location? = withTimeoutOrNull(15_000) {
    suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }
        runCatching {
            LocationManagerCompat.getCurrentLocation(
                this@requestCurrentLocation,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            }
        }.onFailure {
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }
}

internal fun buildLocationTool(context: Context): Tool = Tool(
    name = "get_location",
    description = """
        Get the device's most recently known geographic location.
        Returns latitude, longitude, accuracy, provider, and fix time.
        Location permission must be granted by the user.
    """.trimIndent().replace("\n", " "),
    parameters = { InputSchema.Obj(properties = JsonObject(emptyMap())) },
    execute = { arguments ->
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val result = if (!hasFine && !hasCoarse) {
            buildJsonObject {
                put("error", "NO_PERMISSION")
                put("message", "Location permission is not granted.")
            }
        } else {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = manager.getProviders(true)
                .filterNot { it == LocationManager.PASSIVE_PROVIDER }
            val location = coroutineScope {
                val results = Channel<Location?>(providers.size)
                val requests = providers.map { provider ->
                    launch {
                        results.send(manager.requestCurrentLocation(context, provider))
                    }
                }
                repeat(providers.size) {
                    val result = results.receive()
                    if (result != null) {
                        requests.forEach { request -> request.cancel() }
                        return@coroutineScope result
                    }
                }
                null
            }
            if (location == null) {
                buildJsonObject {
                    put("error", "LOCATION_UNAVAILABLE")
                    put(
                        "message",
                        if (providers.isEmpty()) {
                            "System location is disabled or no location provider is enabled."
                        } else {
                            "Unable to acquire the current device location."
                        }
                    )
                }
            } else {
                buildJsonObject {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("accuracyMeters", location.accuracy)
                    put("provider", location.provider.orEmpty())
                    put("timestamp", location.time)
                }
            }
        }
        Logging.logPermission(
            type = "获取位置信息",
            rawData = arguments.toString(),
            resultData = result.toString(),
            granted = true,
        )
        listOf(UIMessagePart.Text(result.toString()))
    },
)
