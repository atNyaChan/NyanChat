package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderQuota
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.supportsQuota
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.DashboardSpeed02
import me.rerere.hugeicons.stroke.Login03
import me.rerere.hugeicons.stroke.Logout03
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.oauth.CustomTabsOAuthAuthorizationLauncher
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.account.AccountLoginStatus
import me.rerere.rikkahub.data.ai.account.ChatGPTAuth
import me.rerere.rikkahub.data.ai.account.ProviderAccountManager
import me.rerere.rikkahub.data.datastore.sanitizeBuiltInTools
import me.rerere.rikkahub.ui.components.ai.longLabel
import me.rerere.rikkahub.ui.components.ai.rememberProviderQuota
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings
import me.rerere.rikkahub.utils.toMessageTimeString
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.compose.koinInject
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.reflect.KClass
import kotlin.time.toJavaInstant

fun ProviderSetting.convertTo(type: KClass<out ProviderSetting>): ProviderSetting {
    if (this::class == type) return this

    // 账号凭据不能带到可以自填地址的类型上，从账号类转过来时 key、地址和额度查询都用目标类型的默认值
    val source = this as? ProviderSetting.ApiKey
    val apiKey = source?.apiKey.orEmpty()
    val targetDefaultBaseUrl = when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI().baseUrl
        ProviderSetting.Google::class -> ProviderSetting.Google().baseUrl
        ProviderSetting.Claude::class -> ProviderSetting.Claude().baseUrl
        else -> error("Unsupported provider type: $type")
    }
    val convertedBaseUrl = source?.baseUrl?.convertToTargetBaseUrl(targetDefaultBaseUrl) ?: targetDefaultBaseUrl

    val converted: ProviderSetting.ApiKey = when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Google::class -> ProviderSetting.Google(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Claude::class -> ProviderSetting.Claude(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        else -> error("Unsupported provider type: $type")
    }
    // 换类型后新 API 不支持的内置工具直接关掉，避免留下开了却不会发送的开关
    val withQuota = source?.let { converted.withQuotaQuery(it.quotaQuery) } ?: converted
    return withQuota.sanitizeBuiltInTools()
}

private fun String.convertToTargetBaseUrl(targetDefaultBaseUrl: String): String {
    val sourceUrl = this.toHttpUrlOrNull() ?: return this
    val sourceHost = sourceUrl.host.lowercase()
    if (sourceHost in OFFICIAL_PROVIDER_HOSTS) return targetDefaultBaseUrl
    val targetUrl = targetDefaultBaseUrl.toHttpUrlOrNull() ?: return this
    val convertedPath = sourceUrl.encodedPath.convertToTargetPath(targetUrl.encodedPath)
    return sourceUrl.newBuilder().encodedPath(convertedPath).build().toString()
}

private fun String.convertToTargetPath(targetPath: String): String {
    val source = this.normalizePath()
    val target = targetPath.normalizePath()
    val replaced = when {
        source.lowercase().endsWith(V1_BETA_SUFFIX) -> source.dropLast(V1_BETA_SUFFIX.length) + target
        source.lowercase().endsWith(V1_SUFFIX) -> source.dropLast(V1_SUFFIX.length) + target
        source.isBlank() -> target
        else -> source + target
    }
    return replaced.normalizePath()
}

private fun String.normalizePath(): String {
    val value = this.trim()
    if (value.isEmpty() || value == "/") return ""
    val path = if (value.startsWith("/")) value else "/$value"
    return path.trimEnd('/')
}

private const val OPENAI_OFFICIAL_HOST = "api.openai.com"
private const val GOOGLE_OFFICIAL_HOST = "generativelanguage.googleapis.com"
private const val CLAUDE_OFFICIAL_HOST = "api.anthropic.com"
private const val V1_SUFFIX = "/v1"
private const val V1_BETA_SUFFIX = "/v1beta"
private val OFFICIAL_PROVIDER_HOSTS = setOf(
    OPENAI_OFFICIAL_HOST,
    GOOGLE_OFFICIAL_HOST,
    CLAUDE_OFFICIAL_HOST
)

/** 账号类提供商的登录、用量与说明，供配置页和模型覆盖编辑共用 */
@Composable
internal fun ProviderAccountConfig(
    provider: ProviderSetting.Account,
    onEdit: (ProviderSetting) -> Unit,
) {
    when (provider) {
        is ProviderSetting.ChatGPT -> ProviderConfigureAccount(
            provider = provider,
            onEdit = onEdit,
            serviceName = "ChatGPT",
            plan = provider.credential?.plan?.let { "ChatGPT ${ChatGPTAuth.planDisplayName(it)}" },
            disclaimer = stringResource(R.string.setting_provider_page_chatgpt_disclaimer),
        )

        is ProviderSetting.Grok -> ProviderConfigureAccount(
            provider = provider,
            onEdit = onEdit,
            serviceName = "Grok",
            plan = null,
            disclaimer = stringResource(R.string.setting_provider_page_grok_disclaimer),
        )
    }
}

// 账号类提供商：名称、登录状态、用量和说明
@Composable
private fun ProviderConfigureAccount(
    provider: ProviderSetting.Account,
    onEdit: (provider: ProviderSetting) -> Unit,
    serviceName: String,
    plan: String?,
    disclaimer: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountManager = koinInject<ProviderAccountManager>()
    val loginStatus by accountManager.loginStatus.collectAsStateWithLifecycle()
    val status = loginStatus[provider.id]
    val credential = provider.credential

    CardGroup {
        item(headlineContent = {
            OutlinedTextField(
                value = provider.name,
                onValueChange = { onEdit(provider.copyProvider(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.setting_provider_page_name)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
            )
        })
    }

    CardGroup(title = { Text(stringResource(R.string.setting_provider_page_account)) }) {
        if (credential != null) {
            item(
                leadingContent = { CardGroupIcon(HugeIcons.UserCircle) },
                supportingContent = plan?.let { { Text(it) } },
                headlineContent = { Text(credential.email ?: stringResource(R.string.setting_provider_page_signed_in)) },
            )
            item(
                onClick = { scope.launch { accountManager.logout(provider.id) } },
                leadingContent = {
                    CardGroupIcon(
                        icon = HugeIcons.Logout03,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                },
                headlineContent = { Text(stringResource(R.string.setting_provider_page_sign_out)) },
            )
        } else if (status is AccountLoginStatus.DeviceCode) {
            item(
                onClick = { CustomTabsOAuthAuthorizationLauncher.launch(context, status.verificationUrl) },
                leadingContent = { ContainedLoadingIndicator(modifier = Modifier.size(40.dp)) },
                overlineContent = { Text(stringResource(R.string.setting_provider_page_device_code_hint)) },
                supportingContent = { Text(stringResource(R.string.setting_provider_page_device_code_reopen)) },
                headlineContent = {
                    Text(
                        text = status.userCode,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetbrainsMono,
                            fontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings,
                        ),
                    )
                },
            )
            item(
                onClick = { accountManager.cancelLogin(provider.id) },
                leadingContent = { CardGroupIcon(HugeIcons.Cancel01) },
                headlineContent = { Text(stringResource(R.string.common_cancel)) },
            )
        } else if (status == AccountLoginStatus.Authorizing) {
            item(
                onClick = { accountManager.cancelLogin(provider.id) },
                leadingContent = { ContainedLoadingIndicator(modifier = Modifier.size(40.dp)) },
                supportingContent = { Text(stringResource(R.string.setting_provider_page_waiting_authorization_desc)) },
                headlineContent = { Text(stringResource(R.string.setting_provider_page_waiting_authorization)) },
            )
        } else {
            item(
                onClick = { accountManager.startLogin(provider, context) },
                leadingContent = { CardGroupIcon(HugeIcons.Login03) },
                supportingContent = (status as? AccountLoginStatus.Failed)?.let { failed ->
                    { Text(failed.message, color = MaterialTheme.colorScheme.error) }
                },
                trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.setting_provider_page_sign_in_with, serviceName)) },
            )
        }
    }

    if (provider.supportsQuota) {
        AccountUsage(provider)
    }

    Text(
        text = disclaimer,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

// 订阅用量：每个窗口一条进度和重置时间
@Composable
private fun AccountUsage(provider: ProviderSetting.Account) {
    val result = rememberProviderQuota(provider)
    val limits = result?.getOrNull() as? ProviderQuota.UsageLimits
    val todayLabel = stringResource(R.string.chat_page_today)
    val yesterdayLabel = stringResource(R.string.chat_page_yesterday)
    CardGroup(title = { Text(stringResource(R.string.setting_provider_page_usage)) }) {
        when {
            result == null -> item(
                leadingContent = { ContainedLoadingIndicator(modifier = Modifier.size(40.dp)) },
                headlineContent = { Text(stringResource(R.string.setting_provider_page_usage_loading)) },
            )

            limits == null -> item(
                leadingContent = { CardGroupIcon(HugeIcons.DashboardSpeed02) },
                supportingContent = {
                    Text(
                        text = result.exceptionOrNull()?.message.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                headlineContent = { Text(stringResource(R.string.setting_provider_page_usage_failed)) },
            )

            limits.windows.isEmpty() -> item(
                leadingContent = { CardGroupIcon(HugeIcons.DashboardSpeed02) },
                headlineContent = { Text(stringResource(R.string.setting_provider_page_usage_none)) },
            )

            else -> limits.windows.forEach { window ->
                val reached = limits.limitReached || window.usedPercent >= 100
                FormItem(
                    label = { Text(window.longLabel()) },
                    description = window.resetsAt?.let { resetsAt ->
                        {
                            val time = LocalDateTime.ofInstant(resetsAt.toJavaInstant(), ZoneId.systemDefault())
                            Text(stringResource(R.string.setting_provider_page_usage_resets, time.toMessageTimeString(todayLabel, yesterdayLabel)))
                        }
                    },
                    tail = {
                        Text(
                            text = "${window.usedPercent.roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (reached) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                    },
                ) {
                    LinearWavyProgressIndicator(
                        progress = { (window.usedPercent / 100).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (reached) MaterialTheme.colorScheme.error else ProgressIndicatorDefaults.linearColor,
                    )
                }
            }
        }
    }
}
