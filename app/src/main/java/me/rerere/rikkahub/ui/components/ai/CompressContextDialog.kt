package me.rerere.rikkahub.ui.components.ai

import android.os.SystemClock
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AppLoadingIndicator

// 按住步进按钮超过这个时间开始连按，之后按间隔连按
private const val STEPPER_HOLD_DELAY_MILLIS = 400L
private const val STEPPER_REPEAT_INTERVAL_MILLIS = 60L

// 连按中松手时系统还会补一次点击，距上一次连按不到这个时间的点击直接丢掉
private const val STEPPER_CLICK_GUARD_MILLIS = STEPPER_REPEAT_INTERVAL_MILLIS * 3

@Composable
fun CompressContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, keepRecentMessages: Int) -> Job
) {
    var additionalPrompt by remember { mutableStateOf("") }
    // 默认全部压缩，请求前缀与对话一致，缓存命中最好
    var keepRecentMessages by remember { mutableIntStateOf(0) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val isLoading = currentJob?.isActive == true

    // Monitor job completion
    LaunchedEffect(currentJob) {
        currentJob?.join()
        if (currentJob?.isCompleted == true && currentJob?.isCancelled == false) {
            onDismiss()
        }
        currentJob = null
    }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = {
            if (!isLoading) {
                onDismiss()
            }
        },
        title = {
            Text(stringResource(R.string.chat_page_compress_context_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    // Loading state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppLoadingIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_page_compressing))
                    }
                } else {
                    Text(stringResource(R.string.chat_page_compress_context_desc))

                    // Additional context input
                    OutlinedTextField(
                        value = additionalPrompt,
                        onValueChange = { additionalPrompt = it },
                        label = {
                            Text(stringResource(R.string.chat_page_compress_additional_prompt))
                        },
                        placeholder = {
                            Text(stringResource(R.string.chat_page_compress_additional_prompt_hint))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.chat_page_compress_keep_recent),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            StepperIconButton(
                                icon = HugeIcons.Remove01,
                                contentDescription = "Decrease",
                                enabled = keepRecentMessages > 0,
                                onStep = { keepRecentMessages-- },
                            )
                            Text(
                                text = keepRecentMessages.toString(),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 40.dp),
                            )
                            StepperIconButton(
                                icon = HugeIcons.Add01,
                                contentDescription = "Increase",
                                onStep = { keepRecentMessages++ },
                            )
                        }
                        Text(
                            text = stringResource(R.string.chat_page_compress_keep_recent_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                TextButton(onClick = {
                    currentJob?.cancel()
                    currentJob = null
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            } else {
                TextButton(onClick = {
                    currentJob = onConfirm(additionalPrompt, keepRecentMessages)
                }) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}

/** 步进器的 −/+ 按钮：单击走一步，按住超过 [STEPPER_HOLD_DELAY_MILLIS] 后开始连续增减 */
@Composable
private fun StepperIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onStep: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val currentOnStep by rememberUpdatedState(onStep)
    var lastRepeatAt by remember { mutableLongStateOf(0L) }

    // 按下不动时连按；松手（pressed 变 false）或按钮变为不可用时自动取消
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        delay(STEPPER_HOLD_DELAY_MILLIS)
        while (true) {
            currentOnStep()
            lastRepeatAt = SystemClock.uptimeMillis()
            delay(STEPPER_REPEAT_INTERVAL_MILLIS)
        }
    }

    FilledTonalIconButton(
        onClick = {
            // 连按结束时松手还会走一次点击，这里跳过，避免多走一步
            if (SystemClock.uptimeMillis() - lastRepeatAt > STEPPER_CLICK_GUARD_MILLIS) currentOnStep()
        },
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
