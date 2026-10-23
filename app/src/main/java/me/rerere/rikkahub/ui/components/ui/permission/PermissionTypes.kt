package me.rerere.rikkahub.ui.components.ui.permission

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R

/**
 * 权限信息数据类
 * @param permission Android权限字符串 (如 android.permission.CAMERA)
 * @param usage 权限使用说明的Composable内容
 * @param required 是否为必需权限
 */
data class PermissionInfo(
    val permission: String,
    val displayName: @Composable () -> Unit,
    val usage: @Composable () -> Unit,
    val required: Boolean = false
)

/**
 * 权限状态枚举
 */
enum class PermissionStatus {
    /** 未请求 */
    NotRequested,

    /** 已授权 */
    Granted,

    /** 被拒绝但可以再次请求 */
    Denied,

    /** 被拒绝且用户选择"不再询问" */
    DeniedPermanently
}

val PermissionCamera = PermissionInfo(
    permission = Manifest.permission.CAMERA,
    displayName = { Text(stringResource(R.string.permission_camera)) },
    usage = { Text(stringResource(R.string.permission_camera_desc)) },
    required = true
)

val PermissionRecordAudio = PermissionInfo(
    permission = Manifest.permission.RECORD_AUDIO,
    displayName = { Text(stringResource(R.string.permission_microphone)) },
    usage = { Text(stringResource(R.string.permission_microphone_desc)) },
    required = true
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
val PermissionNotification = PermissionInfo(
    permission = Manifest.permission.POST_NOTIFICATIONS,
    displayName = { Text(stringResource(R.string.permission_notification)) },
    usage = { Text(stringResource(R.string.permission_notification_desc)) },
    required = true
)

@RequiresApi(37)
val PermissionLocalNetwork = PermissionInfo(
    permission = Manifest.permission.ACCESS_LOCAL_NETWORK,
    displayName = { Text(stringResource(R.string.permission_local_network)) },
    usage = { Text(stringResource(R.string.permission_local_network_desc)) },
    required = true
)
