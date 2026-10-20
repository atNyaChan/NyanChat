package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.QuotaQuery
import me.rerere.ai.provider.renderQuotaTemplate
import me.rerere.common.http.isJsonExprValid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DEFAULT_PROVIDERS
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 自定义额度查询的设置项，只给 API Key 类提供商用 */
@Composable
fun SettingProviderQuotaQuery(
    provider: ProviderSetting.ApiKey,
    modifier: Modifier = Modifier,
    // 标题右侧显示的已查到的额度，参数是刷新次数
    supportingContent: (@Composable (refreshKey: Int) -> Unit)? = null,
    onEdit: (QuotaQuery) -> Unit,
) {
    val query = provider.quotaQuery
    var expand by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = JetbrainsMono,
        fontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings,
    )
    // animateContentSize 是 @Composable，需在 CardGroup 的 DSL 外创建
    val animatedSizeModifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
    CardGroup(modifier = modifier) {
        // 只有标题行可点击展开/收起，和助手设置的正则表达式卡片一致
        item(
            modifier = animatedSizeModifier,
            headlineContent = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 向外扩 8dp，抵消 ListItem 的 16dp 内容内边距，
                        // 让按压反馈和正则卡片一样距卡片边缘 8dp
                        .expandHorizontally(8.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { expand = !expand }
                        .padding(start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (expand) 180f else 0f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    )
                    Icon(
                        imageVector = HugeIcons.ArrowDown01,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .rotate(arrowRotation),
                    )
                    Text(stringResource(R.string.setting_provider_page_balance_info))
                    supportingContent?.invoke(refreshKey)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { refreshKey++ },
                        enabled = query.enabled,
                    ) {
                        Icon(HugeIcons.Refresh03, contentDescription = null)
                    }
                    Switch(
                        checked = query.enabled,
                        onCheckedChange = { onEdit(query.copy(enabled = it)) },
                    )
                }
            },
            // 收起时不传 supporting content，卡片高度只随标题行变化
            supportingContent = if (expand) {
                {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = query.url,
                            onValueChange = { onEdit(query.copy(url = it)) },
                            label = { Text("URL") },
                            isError = !isQuotaUrlValid(query.url),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = codeStyle,
                        )
                        query.headers.forEachIndexed { index, header ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                OutlinedTextField(
                                    value = header.name,
                                    onValueChange = { name ->
                                        onEdit(query.copy(headers = query.headers.replaced(index, header.copy(name = name))))
                                    },
                                    label = { Text(stringResource(R.string.assistant_page_header_name)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f),
                                    textStyle = codeStyle,
                                )
                                OutlinedTextField(
                                    value = header.value,
                                    onValueChange = { value ->
                                        onEdit(query.copy(headers = query.headers.replaced(index, header.copy(value = value))))
                                    },
                                    label = { Text(stringResource(R.string.assistant_page_header_value)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(3f),
                                    textStyle = codeStyle,
                                )
                                IconButton(
                                    onClick = {
                                        onEdit(query.copy(headers = query.headers.filterIndexed { i, _ -> i != index }))
                                    }
                                ) {
                                    Icon(
                                        HugeIcons.Delete01,
                                        contentDescription = stringResource(R.string.assistant_page_delete_header)
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = query.resultPath,
                            onValueChange = { onEdit(query.copy(resultPath = it)) },
                            label = { Text(stringResource(R.string.setting_provider_page_balance_json_key)) },
                            isError = !isJsonExprValid(query.resultPath),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = codeStyle,
                        )
                        Text(
                            text = stringResource(
                                R.string.setting_provider_page_quota_variables,
                                QuotaQuery.VARIABLES.joinToString { "{{$it}}" },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = { onEdit(query.copy(headers = query.headers + CustomHeader("", ""))) }
                            ) {
                                Icon(HugeIcons.Add01, contentDescription = null)
                                Text(stringResource(R.string.assistant_page_add_header))
                            }
                            TextButton(
                                onClick = {
                                    val builtIn = DEFAULT_PROVIDERS.find { it.id == provider.id }
                                    onEdit(builtIn?.quotaQuery ?: provider.defaultQuotaQuery())
                                },
                            ) {
                                Text(stringResource(R.string.setting_model_page_reset_to_default))
                            }
                        }
                    }
                }
            } else null,
        )
    }
}

private fun List<CustomHeader>.replaced(index: Int, header: CustomHeader): List<CustomHeader> =
    toMutableList().apply { set(index, header) }

// 向左右各扩 extra（不受内边距限制），用来让点击反馈跨出父容器的内容区
private fun Modifier.expandHorizontally(extra: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extraPx * 2,
            maxWidth = constraints.maxWidth + extraPx * 2,
        )
    )
    layout(placeable.width - extraPx * 2, placeable.height) {
        placeable.place(-extraPx, 0)
    }
}

// 鉴权头按类型不同，取各类型的默认配置
private fun ProviderSetting.ApiKey.defaultQuotaQuery(): QuotaQuery = when (this) {
    is ProviderSetting.OpenAI -> ProviderSetting.OpenAI().quotaQuery
    is ProviderSetting.Google -> ProviderSetting.Google().quotaQuery
    is ProviderSetting.Claude -> ProviderSetting.Claude().quotaQuery
}

// 用示例值代入变量后能解析成 http(s) 地址
private fun isQuotaUrlValid(url: String): Boolean = runCatching {
    renderQuotaTemplate(url.trim()) { name ->
        when (name) {
            QuotaQuery.VARIABLE_BASE_URL -> "https://example.com/v1"
            QuotaQuery.VARIABLE_API_KEY -> "key"
            else -> null
        }
    }.toHttpUrlOrNull() != null
}.getOrDefault(false)
