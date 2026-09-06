package me.rerere.rikkahub.ui.context

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.writeClipboardText

class SystemToaster(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var currentToast: Toast? = null
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun show(message: String, type: ToastType? = null) {
        handler.post {
            if (type == ToastType.Error) {
                currentToast?.cancel()
                currentToast = null
                errorMessage = message
            } else {
                currentToast?.cancel()
                currentToast = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
            }
        }
    }

    fun dismissError() {
        errorMessage = null
    }

    companion object {
        private var current: SystemToaster? = null

        fun showError(message: String) {
            current?.show(message, ToastType.Error)
        }

        internal fun setCurrent(toaster: SystemToaster) {
            current = toaster
        }
    }
}

@Composable
fun rememberSystemToaster(): SystemToaster {
    val context = LocalContext.current
    return remember(context) { SystemToaster(context) }.also(SystemToaster::setCurrent)
}

@Composable
fun SystemErrorDialog(toaster: SystemToaster) {
    val message = toaster.errorMessage ?: return
    val context = LocalContext.current
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = toaster::dismissError,
        title = { Text(stringResource(R.string.common_error)) },
        text = {
            SelectionContainer {
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = toaster::dismissError) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { context.writeClipboardText(message) }) {
                Text(stringResource(R.string.copy))
            }
        },
    )
}

val LocalToaster = staticCompositionLocalOf<SystemToaster> { error("Not provided") }
