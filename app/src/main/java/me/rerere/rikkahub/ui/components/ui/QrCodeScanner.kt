package me.rerere.rikkahub.ui.components.ui

import android.graphics.ImageFormat
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QrCodeScanner"

private val QR_DECODE_HINTS = mapOf<DecodeHintType, Any>(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.TRY_HARDER to true,
)

/**
 * 全屏二维码扫描对话框。
 *
 * 使用 CameraX 预览 + ZXing 解码，只依赖 `zxing-core`，不引入 MLKit 的原生库与模型。
 */
@Composable
fun QrCodeScannerDialog(
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit,
) {
    val permissionState = rememberPermissionState(setOf(PermissionCamera))
    val granted = permissionState.allRequiredPermissionsGranted

    LaunchedEffect(granted) {
        if (!granted) {
            permissionState.requestPermissions()
        }
    }

    PermissionManager(permissionState)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (granted) {
                    QrCameraPreview(onDecoded = onScanned)
                    Text(
                        text = stringResource(R.string.setting_provider_page_scan_qr_code),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.permission_camera_desc),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { permissionState.requestPermissions() }) {
                            Text(text = stringResource(R.string.common_confirm_action))
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.common_cancel),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun QrCameraPreview(onDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDecoded by rememberUpdatedState(onDecoded)
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val decoded = remember { AtomicBoolean(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            cameraProvider = null
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = runCatching { providerFuture.get() }.getOrElse {
                    Log.e(TAG, "Failed to obtain camera provider", it)
                    return@addListener
                }
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                )
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val text = runCatching { imageProxy.decodeQrCode() }.getOrNull()
                    imageProxy.close()
                    if (text != null && decoded.compareAndSet(false, true)) {
                        mainExecutor.execute { currentOnDecoded(text) }
                    }
                }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure {
                    Log.e(TAG, "Failed to bind camera use cases", it)
                }
            }, mainExecutor)

            previewView
        },
    )
}

/** 从 YUV 图像帧中解码二维码内容。 */
private fun ImageProxy.decodeQrCode(): String? {
    if (format != ImageFormat.YUV_420_888) {
        return null
    }
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    buffer.rewind()

    val frameWidth = width
    val frameHeight = height
    val rowStride = plane.rowStride
    val limit = buffer.limit()

    // Y 平面每行可能带有 padding，先按行拷成紧凑数组再交给 ZXing。
    val luminance = ByteArray(frameWidth * frameHeight)
    val row = ByteArray(rowStride)
    var sourceOffset = 0
    var targetOffset = 0
    for (y in 0 until frameHeight) {
        val length = minOf(rowStride, limit - sourceOffset)
        if (length <= 0 || targetOffset + frameWidth > luminance.size) {
            break
        }
        buffer.position(sourceOffset)
        buffer.get(row, 0, length)
        System.arraycopy(row, 0, luminance, targetOffset, frameWidth)
        sourceOffset += rowStride
        targetOffset += frameWidth
    }

    val source = PlanarYUVLuminanceSource(
        luminance,
        frameWidth,
        frameHeight,
        0,
        0,
        frameWidth,
        frameHeight,
        false,
    )
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    val reader = MultiFormatReader()
    reader.setHints(QR_DECODE_HINTS)
    return runCatching { reader.decodeWithState(binaryBitmap).text }.getOrNull()
}
