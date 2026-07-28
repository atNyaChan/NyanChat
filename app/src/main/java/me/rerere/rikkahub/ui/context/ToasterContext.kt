package me.rerere.rikkahub.ui.context

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.dokar.sonner.ToastType

class SystemToaster(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var currentToast: Toast? = null

    @Suppress("UNUSED_PARAMETER")
    fun show(message: String, type: ToastType? = null) {
        handler.post {
            currentToast?.cancel()
            currentToast = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
        }
    }
}

@Composable
fun rememberSystemToaster(): SystemToaster {
    val context = LocalContext.current
    return remember(context) { SystemToaster(context) }
}

val LocalToaster = staticCompositionLocalOf<SystemToaster> { error("Not provided") }
