package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.compose.rememberAsyncImagePainter
import com.dokar.sonner.ToastType
import com.jvziyaoyao.scale.image.previewer.ImagePreviewer
import com.jvziyaoyao.scale.zoomable.pager.PagerGestureScope
import com.jvziyaoyao.scale.zoomable.previewer.rememberPreviewerState
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Download01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.compose.koinInject

@Composable
fun ImagePreviewDialog(
    images: List<String>,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val filesManager: FilesManager = koinInject()
    val toaster = LocalToaster.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state = rememberPreviewerState(
        defaultAnimationSpec = tween(180),
        pageCount = { images.size },
    ) { it }
    var opened by remember { mutableStateOf(false) }

    // 打开预览组件
    LaunchedEffect(Unit) {
        state.open()
        opened = true
    }

    // 单击图片或向下拖动图片关闭后, 等关闭动画结束再通知调用方移除组件
    LaunchedEffect(state.visible, state.animating) {
        if (opened && !state.visible && !state.animating) {
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ImagePreviewer(
                state = state,
                enter = scaleIn(tween(120)) + fadeIn(tween(120)),
                exit = scaleOut(tween(140)) + fadeOut(tween(110)),
                detectGesture = PagerGestureScope(
                    onTap = { scope.launch { state.close() } },
                ),
                imageLoader = { page ->
                    val painter = rememberAsyncImagePainter(images[page])
                    Pair(painter, painter.intrinsicSize)
                },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        lifecycleOwner.lifecycleScope.launch {
                            runCatching {
                                toaster.show(context.getString(R.string.image_saving))
                                val imgUrl = images[state.currentPage]
                                filesManager.saveMessageImage(context, imgUrl)
                                toaster.show(message = context.getString(R.string.image_saved), type = ToastType.Success)
                            }.onFailure {
                                it.printStackTrace()
                                toaster.show(
                                    message = it.toString(),
                                    type = ToastType.Error
                                )
                            }
                        }
                    }
                ) {
                    Icon(HugeIcons.Download01, null, tint = Color.White)
                }
            }
        }
    }
}
