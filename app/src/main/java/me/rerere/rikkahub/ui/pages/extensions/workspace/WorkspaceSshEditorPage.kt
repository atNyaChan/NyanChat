package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.annotation.StringRes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.FingerPrint
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Key01
import me.rerere.hugeicons.stroke.Route01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppLoadingIndicator
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings
import me.rerere.workspace.ssh.SshAuthType
import me.rerere.workspace.ssh.SshHostKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_PRIVATE_KEY_BYTES = 64 * 1024

@Composable
fun WorkspaceSshEditorPage(id: String?) {
    val navController = LocalNavController.current
    val vm: WorkspaceSshEditorVM = koinViewModel(key = "ssh-editor-$id", parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    // 测试连接和保存针对开始时的表单快照, 期间锁定表单
    val editable = !state.busy

    LaunchedEffect(state.saved) {
        if (state.saved) navController.popBackStack()
    }

    val privateKeyPicker = rememberPrivateKeyPicker { key -> vm.update { it.copy(privateKey = key) } }
    val jumpPrivateKeyPicker = rememberPrivateKeyPicker { key ->
        vm.update { it.copy(jumpHost = it.jumpHost.copy(privateKey = key)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) stringResource(R.string.workspace_ssh_editor_title_new) else stringResource(R.string.workspace_ssh_editor_title_edit)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = vm::save, enabled = !state.busy) {
                        Icon(HugeIcons.Tick02, contentDescription = stringResource(R.string.workspace_ssh_editor_save))
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(title = { Text(stringResource(R.string.workspace_ssh_editor_basic)) }) {
                    item(headlineContent = {
                        OutlinedTextField(
                            enabled = editable,
                            value = state.name,
                            onValueChange = { value -> vm.update { it.copy(name = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.workspace_ssh_editor_name)) },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = { Text(stringResource(R.string.workspace_ssh_editor_name_placeholder)) },
                            singleLine = true,
                        )
                    })
                }
            }

            item {
                CardGroup(title = { Text(stringResource(R.string.workspace_ssh_editor_connection)) }) {
                    item(headlineContent = {
                        OutlinedTextField(
                            enabled = editable,
                            value = state.host,
                            onValueChange = { value -> vm.update { it.copy(host = value.trim()) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.workspace_ssh_editor_host)) },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = { Text(stringResource(R.string.workspace_ssh_editor_host_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                    })
                    item(headlineContent = {
                        OutlinedTextField(
                            enabled = editable,
                            value = state.port,
                            onValueChange = { value ->
                                vm.update { it.copy(port = value.filter(Char::isDigit).take(5)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.workspace_ssh_editor_port)) },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    })
                    item(headlineContent = {
                        OutlinedTextField(
                            enabled = editable,
                            value = state.username,
                            onValueChange = { value -> vm.update { it.copy(username = value.trim()) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.workspace_ssh_editor_username)) },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                        )
                    })
                }
            }

            item {
                val savedKeep = stringResource(R.string.workspace_ssh_editor_saved_keep)
                CardGroup(title = { Text(stringResource(R.string.workspace_ssh_editor_auth)) }) {
                    authItems(
                        enabled = editable,
                        authType = state.authType,
                        onAuthTypeChange = { type -> vm.update { it.copy(authType = type) } },
                        password = state.password,
                        onPasswordChange = { value -> vm.update { it.copy(password = value) } },
                        privateKey = state.privateKey,
                        onPrivateKeyChange = { value -> vm.update { it.copy(privateKey = value) } },
                        onImportPrivateKey = { privateKeyPicker.launch(arrayOf("*/*")) },
                        passphrase = state.passphrase,
                        onPassphraseChange = { value -> vm.update { it.copy(passphrase = value) } },
                        passwordPlaceholder = savedKeep.takeIf { state.hasSavedPassword },
                        privateKeyPlaceholder = savedKeep.takeIf { state.hasSavedPrivateKey },
                    )
                }
            }

            item {
                JumpHostSection(
                    jump = state.jumpHost,
                    enabled = editable,
                    onUpdate = { transform -> vm.update { it.copy(jumpHost = transform(it.jumpHost)) } },
                    onImportPrivateKey = { jumpPrivateKeyPicker.launch(arrayOf("*/*")) },
                )
            }

            item {
                CardGroup(title = { Text(stringResource(R.string.workspace_ssh_editor_working_dir)) }) {
                    item(headlineContent = {
                        OutlinedTextField(
                            enabled = editable,
                            value = state.remoteDir,
                            onValueChange = { value -> vm.update { it.copy(remoteDir = value.trim()) } },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("/home/user/project") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            label = { Text(stringResource(R.string.workspace_ssh_editor_remote_dir)) },
                            supportingText = { Text(stringResource(R.string.workspace_ssh_editor_remote_dir_desc)) },
                        )
                    })
                }
            }

            item {
                CardGroup(title = { Text(stringResource(R.string.workspace_ssh_editor_security)) }) {
                    // 经跳板机时两台机器的密钥分开确认, 目标主机的指纹仍然是目标主机自己的
                    hostKeyItem(
                        title = if (state.jumpHost.enabled) {
                            R.string.workspace_ssh_editor_target_host_key
                        } else {
                            R.string.workspace_ssh_editor_host_key
                        },
                        hostKey = state.hostKey,
                    )
                    if (state.jumpHost.enabled) {
                        hostKeyItem(
                            title = R.string.workspace_ssh_editor_jump_host_key,
                            hostKey = state.jumpHost.hostKey,
                        )
                    }
                }
            }

            item {
                ProbeSection(
                    state = state,
                    onTest = vm::testConnection,
                )
            }
        }
    }

    state.pendingHostKey?.let { pending ->
        HostKeyDialog(
            pending = pending,
            address = pending.address,
            onTrust = vm::trustHostKey,
            onDismiss = vm::dismissHostKey,
        )
    }

    state.error?.let { error ->
        val message = when (error) {
            is SshEditorError.Resource -> stringResource(error.id)
            is SshEditorError.Message -> error.text
        }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = vm::dismissError,
            title = { Text(
                    if (state.canSaveAnyway) {
                        stringResource(R.string.workspace_ssh_editor_connect_failed)
                    } else {
                        stringResource(R.string.workspace_ssh_editor_error)
                    }
                ) },
            text = {
                Text(
                    if (state.canSaveAnyway) stringResource(R.string.workspace_ssh_editor_save_anyway_message, message) else message
                )
            },
            confirmButton = {
                if (state.canSaveAnyway) {
                    TextButton(onClick = vm::saveAnyway) { Text(stringResource(R.string.workspace_ssh_editor_save_anyway)) }
                } else {
                    TextButton(onClick = vm::dismissError) { Text(stringResource(R.string.common_ok)) }
                }
            },
            dismissButton = if (state.canSaveAnyway) {
                { TextButton(onClick = vm::dismissError) { Text(stringResource(R.string.common_cancel)) } }
            } else {
                null
            },
        )
    }
}

@Composable
private fun rememberPrivateKeyPicker(onPicked: (String) -> Unit): ManagedActivityResultLauncher<Array<String>, Uri?> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readLimited(MAX_PRIVATE_KEY_BYTES) }
        }.getOrNull()
        if (text != null) onPicked(text.trim())
    }
}

@Composable
private fun JumpHostSection(
    jump: JumpHostForm,
    enabled: Boolean,
    onUpdate: ((JumpHostForm) -> JumpHostForm) -> Unit,
    onImportPrivateKey: () -> Unit,
) {
    val savedKeep = stringResource(R.string.workspace_ssh_editor_saved_keep)
    CardGroup(title = { Text(stringResource(R.string.workspace_ssh_editor_jump_host)) }) {
        switchItem(
            checked = jump.enabled,
            onCheckedChange = { checked -> onUpdate { it.copy(enabled = checked) } },
            enabled = enabled,
            leadingContent = { CardGroupIcon(HugeIcons.Route01) },
            headlineContent = { Text(stringResource(R.string.workspace_ssh_editor_jump_host_enable)) },
            supportingContent = { Text(stringResource(R.string.workspace_ssh_editor_jump_host_enable_desc)) },
        )
        if (!jump.enabled) return@CardGroup
        item(headlineContent = {
            OutlinedTextField(
                enabled = enabled,
                value = jump.host,
                onValueChange = { value -> onUpdate { it.copy(host = value.trim()) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.workspace_ssh_editor_host)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("bastion.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        })
        item(headlineContent = {
            OutlinedTextField(
                enabled = enabled,
                value = jump.port,
                onValueChange = { value -> onUpdate { it.copy(port = value.filter(Char::isDigit).take(5)) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.workspace_ssh_editor_port)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        })
        item(headlineContent = {
            OutlinedTextField(
                enabled = enabled,
                value = jump.username,
                onValueChange = { value -> onUpdate { it.copy(username = value.trim()) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.workspace_ssh_editor_username)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
            )
        })
        authItems(
            enabled = enabled,
            authType = jump.authType,
            onAuthTypeChange = { type -> onUpdate { it.copy(authType = type) } },
            password = jump.password,
            onPasswordChange = { value -> onUpdate { it.copy(password = value) } },
            privateKey = jump.privateKey,
            onPrivateKeyChange = { value -> onUpdate { it.copy(privateKey = value) } },
            onImportPrivateKey = onImportPrivateKey,
            passphrase = jump.passphrase,
            onPassphraseChange = { value -> onUpdate { it.copy(passphrase = value) } },
            passwordPlaceholder = savedKeep.takeIf { jump.hasSavedPassword },
            privateKeyPlaceholder = savedKeep.takeIf { jump.hasSavedPrivateKey },
        )
    }
}

/** 认证方式和对应的凭据输入; 目标主机和跳板机共用。占位文字不为空表示已保存过, 留空沿用 */
private fun CardGroupScope.authItems(
    enabled: Boolean,
    authType: SshAuthType,
    onAuthTypeChange: (SshAuthType) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    privateKey: String,
    onPrivateKeyChange: (String) -> Unit,
    onImportPrivateKey: () -> Unit,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    passwordPlaceholder: String?,
    privateKeyPlaceholder: String?,
) {
    FormItem(label = { Text(stringResource(R.string.workspace_ssh_editor_auth_method)) }) {
        ConnectedChoiceButtons(
            options = listOf(SshAuthType.PASSWORD, SshAuthType.PRIVATE_KEY),
            selected = authType,
            onSelect = onAuthTypeChange,
            enabled = enabled,
        ) { type, selected ->
            ConnectedChoiceLabel(
                text = stringResource(
                    when (type) {
                        SshAuthType.PASSWORD -> R.string.workspace_ssh_editor_password
                        SshAuthType.PRIVATE_KEY -> R.string.workspace_ssh_editor_private_key
                    }
                ),
                selected = selected,
            )
        }
    }
    when (authType) {
        SshAuthType.PASSWORD -> item(headlineContent = {
            SecretTextField(
                enabled = enabled,
                value = password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.workspace_ssh_editor_password),
                placeholder = passwordPlaceholder,
            )
        })

        SshAuthType.PRIVATE_KEY -> {
            item(headlineContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val codeFontFeatureSettings = LocalSettings.current.displaySetting
                        .enableCodeLigatures.codeFontFeatureSettings
                    OutlinedTextField(
                        enabled = enabled,
                        value = privateKey,
                        onValueChange = onPrivateKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.workspace_ssh_editor_private_key)) },
                        supportingText = { Text(stringResource(R.string.workspace_ssh_editor_private_key_desc)) },
                        placeholder = {
                            Text(privateKeyPlaceholder ?: "-----BEGIN OPENSSH PRIVATE KEY-----")
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = codeFontFeatureSettings,
                        ),
                        minLines = 3,
                        maxLines = 8,
                    )
                    FilledTonalButton(
                        enabled = enabled,
                        onClick = onImportPrivateKey,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            HugeIcons.FileImport,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.workspace_ssh_editor_import_key))
                    }
                }
            })
            item(headlineContent = {
                SecretTextField(
                    enabled = enabled,
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    label = stringResource(R.string.workspace_ssh_editor_passphrase),
                    placeholder = stringResource(R.string.workspace_ssh_editor_passphrase_placeholder),
                )
            })
        }
    }
}

private fun CardGroupScope.hostKeyItem(@StringRes title: Int, hostKey: SshHostKey?) = item(
    leadingContent = { CardGroupIcon(HugeIcons.FingerPrint) },
    headlineContent = { Text(stringResource(title)) },
    supportingContent = {
        if (hostKey == null) {
            Text(stringResource(R.string.workspace_ssh_editor_host_key_unconfirmed))
        } else {
            Text(
                text = "${hostKey.type}\n${hostKey.fingerprint}",
                style = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = LocalSettings.current.displaySetting
                        .enableCodeLigatures.codeFontFeatureSettings,
                ),
            )
        }
    },
)

@Composable
private fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String?,
    enabled: Boolean,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) HugeIcons.ViewOff else HugeIcons.View, contentDescription = null)
            }
        },
    )
}

@Composable
private fun ProbeSection(
    state: WorkspaceSshEditorState,
    onTest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(
            onClick = onTest,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.testing) {
                AppLoadingIndicator(modifier = Modifier.size(ButtonDefaults.IconSize))
            } else {
                Icon(HugeIcons.Connect, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                if (state.testing) {
                    stringResource(R.string.workspace_ssh_editor_connecting)
                } else {
                    stringResource(R.string.workspace_ssh_editor_test)
                }
            )
        }
        state.probeResult?.let { result ->
            CardGroup(title = { Text(stringResource(R.string.workspace_ssh_editor_connected)) }) {
                item(
                    leadingContent = { CardGroupIcon(HugeIcons.CloudServer) },
                    headlineContent = { Text(stringResource(R.string.workspace_ssh_editor_shell)) },
                    supportingContent = { Text(result.shell) },
                )
                item(
                    leadingContent = { CardGroupIcon(HugeIcons.Folder01) },
                    headlineContent = { Text(stringResource(R.string.workspace_ssh_editor_working_dir)) },
                    supportingContent = {
                        Text(
                            when {
                                state.remoteDir.isBlank() || state.remoteDir == result.homeDir ->
                                    stringResource(R.string.workspace_ssh_editor_home_dir, result.homeDir)
                                result.remoteDirExists -> state.remoteDir
                                else -> stringResource(R.string.workspace_ssh_editor_dir_missing, state.remoteDir)
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HostKeyDialog(
    pending: PendingHostKey,
    address: String,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
) {
    val changed = pending.expected != null
    val codeFontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        icon = { Icon(HugeIcons.Key01, contentDescription = null) },
        title = {
            Text(
                stringResource(
                    when {
                        pending.jumpHost && changed -> R.string.workspace_ssh_editor_jump_host_key_changed
                        pending.jumpHost -> R.string.workspace_ssh_editor_jump_host_key_confirm
                        changed -> R.string.workspace_ssh_editor_host_key_changed
                        else -> R.string.workspace_ssh_editor_host_key_confirm
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (changed) {
                        stringResource(R.string.workspace_ssh_editor_host_key_changed_message, address)
                    } else {
                        val keyFile = pending.actual.type.removePrefix("ssh-").substringBefore('-')
                        stringResource(
                            R.string.workspace_ssh_editor_host_key_first_message,
                            address,
                            "ssh-keygen -lf /etc/ssh/ssh_host_${keyFile}_key.pub",
                        )
                    }
                )
                Text(
                    text = "${pending.actual.type}\n${pending.actual.fingerprint}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = codeFontFeatureSettings,
                    ),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        },
        confirmButton = {
            if (changed) {
                Button(
                    onClick = onTrust,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.workspace_ssh_editor_trust_new_key)) }
            } else {
                TextButton(onClick = onTrust) { Text(stringResource(R.string.workspace_ssh_editor_trust_continue)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

// 私钥文件很小, 超过上限的多半是选错了文件, 不整个读进内存
private fun InputStream.readLimited(limit: Int): String? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (output.size() + read > limit) return null
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}
