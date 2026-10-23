package me.rerere.rikkahub.ui.pages.setting

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.highlight.LocalCodeHighlighter
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeVisualTransformation
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.DeleteConfirmDialog
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings
import me.rerere.rikkahub.utils.plus
import me.rerere.search.DoubaoSearchMode
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun SettingSearchDetailPage(
    serviceId: Uuid,
    vm: SettingVM = koinViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val nav = LocalNavController.current

    val service = settings.searchServices.find { it.id == serviceId } ?: return
    val serviceIndex = settings.searchServices.indexOf(service)
    var options by remember(service) { mutableStateOf(service) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun save(updated: SearchServiceOptions) {
        options = updated
        val newServices = settings.searchServices.toMutableList()
        newServices[serviceIndex] = updated
        vm.updateSettings { it.copy(searchServices = newServices) }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(options.displayName)
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (settings.searchServices.size > 1) {
                        IconButton(
                            onClick = { showDeleteConfirm = true }
                        ) {
                            Icon(
                                imageVector = HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.common_delete)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = padding + PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("config") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Bing 没有任何配置项，不显示分组标题也不加卡片组，只留一行灰色说明
                    if (options !is SearchServiceOptions.BingLocalOptions) {
                        CardGroup(
                            title = {
                                Text(stringResource(R.string.setting_page_search_config))
                            },
                        ) {
                            SearchServiceOptionsEditor(
                                options = options,
                                onUpdateOptions = { save(it) }
                            )
                        }
                    }

                    SearchServiceDescription(options = options)
                }
            }
        }
    }
    DeleteConfirmDialog(
        show = showDeleteConfirm,
        type = stringResource(R.string.delete_type_search_service),
        name = options.displayName,
        onConfirm = {
            showDeleteConfirm = false
            val newServices = settings.searchServices.toMutableList()
            newServices.removeAt(serviceIndex)
            vm.updateSettings { it.copy(searchServices = newServices) }
            nav.popBackStack()
        },
        onDismiss = { showDeleteConfirm = false },
    )
}

/** 服务说明，作为卡片组下方的灰色小字 */
@Composable
private fun SearchServiceDescription(
    options: SearchServiceOptions,
    modifier: Modifier = Modifier,
) {
    ProvideTextStyle(MaterialTheme.typography.bodySmall) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Column(
                modifier = modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SearchService.getService(options).Description()
            }
        }
    }
}

private fun CardGroupScope.SearchServiceOptionsEditor(
    options: SearchServiceOptions,
    onUpdateOptions: (SearchServiceOptions) -> Unit
) {
    when (options) {
        is SearchServiceOptions.TavilyOptions -> {
            TavilyOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.ExaOptions -> {
            ExaOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.ZhipuOptions -> {
            ZhipuOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.DoubaoOptions -> {
            DoubaoOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.SearXNGOptions -> {
            SearXNGOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.LinkUpOptions -> {
            SearchLinkUpOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.BraveOptions -> {
            BraveOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.MetasoOptions -> {
            MetasoOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.OllamaOptions -> {
            OllamaOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.PerplexityOptions -> {
            PerplexityOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.BingLocalOptions -> {}
        is SearchServiceOptions.FirecrawlOptions -> {
            FirecrawlOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.JinaOptions -> {
            JinaOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.BochaOptions -> {
            BochaOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.RikkaHubOptions -> {
            RikkaHubOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.GrokOptions -> {
            GrokOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.TinyfishOptions -> {
            TinyfishOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.SerperOptions -> {
            SerperOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.DeepSeekOptions -> {
            DeepSeekOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.CustomJsOptions -> {
            CustomJsOptions(options) { onUpdateOptions(it) }
        }
    }
}

// 每个配置项独立一张卡片；文本项的字段名用 OutlinedTextField 自己的 label
private fun CardGroupScope.textItem(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    placeholder: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    item(headlineContent = {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    })
}

// 带代码高亮的脚本输入框，等宽字体与连字跟随设置
@Composable
private fun CodeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    minLines: Int,
    maxLines: Int,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
) {
    val highlighter = LocalCodeHighlighter.current
    val darkMode = LocalDarkMode.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = HighlightCodeVisualTransformation(
            language = "javascript",
            highlighter = highlighter,
            darkMode = darkMode,
        ),
        textStyle = MaterialTheme.typography.bodySmall.merge(
            fontFamily = JetbrainsMono,
            fontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// 选项型配置项：标题在上，连接式单选按钮组在下（与设置里的颜色模式一致）
private fun <T> CardGroupScope.choiceItem(
    label: @Composable () -> Unit,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
) {
    FormItem(
        label = label,
        content = {
            ConnectedChoiceButtons(
                options = options,
                selected = selected,
                onSelect = onSelect,
            ) { option, isSelected ->
                ConnectedChoiceLabel(text = optionLabel(option), selected = isSelected)
            }
        },
    )
}

private fun CardGroupScope.DoubaoOptions(
    options: SearchServiceOptions.DoubaoOptions,
    onUpdateOptions: (SearchServiceOptions.DoubaoOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }

    choiceItem(
        label = { Text(stringResource(R.string.search_detail_mode)) },
        options = DoubaoSearchMode.entries,
        selected = options.mode,
        onSelect = { onUpdateOptions(options.copy(mode = it)) },
        optionLabel = { mode -> mode.name.lowercase().replaceFirstChar { it.uppercase() } },
    )
}

private fun CardGroupScope.TavilyOptions(
    options: SearchServiceOptions.TavilyOptions,
    onUpdateOptions: (SearchServiceOptions.TavilyOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }

    choiceItem(
        label = { Text(stringResource(R.string.search_detail_depth)) },
        options = listOf("basic", "advanced"),
        selected = options.depth,
        onSelect = { onUpdateOptions(options.copy(depth = it)) },
        optionLabel = { value -> value.replaceFirstChar { it.uppercase() } },
    )

    switchItem(
        checked = options.includeAnswer,
        onCheckedChange = { onUpdateOptions(options.copy(includeAnswer = it)) },
        headlineContent = {
            Text(stringResource(R.string.search_detail_include_answer))
        },
        supportingContent = {
            Text(stringResource(R.string.search_detail_include_answer_desc))
        },
    )
}

private fun CardGroupScope.ExaOptions(
    options: SearchServiceOptions.ExaOptions,
    onUpdateOptions: (SearchServiceOptions.ExaOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }
}

private fun CardGroupScope.ZhipuOptions(
    options: SearchServiceOptions.ZhipuOptions,
    onUpdateOptions: (SearchServiceOptions.ZhipuOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }
}

private fun CardGroupScope.SearXNGOptions(
    options: SearchServiceOptions.SearXNGOptions,
    onUpdateOptions: (SearchServiceOptions.SearXNGOptions) -> Unit
) {
    textItem(
        value = options.url,
        onValueChange = { onUpdateOptions(options.copy(url = it)) },
        label = { Text(stringResource(R.string.search_detail_api_url)) },
    )

    textItem(
        value = options.engines,
        onValueChange = { onUpdateOptions(options.copy(engines = it)) },
        label = { Text(stringResource(R.string.search_detail_engines)) },
    )

    textItem(
        value = options.language,
        onValueChange = { onUpdateOptions(options.copy(language = it)) },
        label = { Text(stringResource(R.string.search_detail_language)) },
    )

    textItem(
        value = options.username,
        onValueChange = { onUpdateOptions(options.copy(username = it)) },
        label = { Text(stringResource(R.string.search_detail_username)) },
    )

    textItem(
        value = options.password,
        onValueChange = { onUpdateOptions(options.copy(password = it)) },
        label = { Text(stringResource(R.string.search_detail_password)) },
    )
}

private fun CardGroupScope.SearchLinkUpOptions(
    options: SearchServiceOptions.LinkUpOptions,
    onUpdateOptions: (SearchServiceOptions.LinkUpOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }

    choiceItem(
        label = { Text(stringResource(R.string.search_detail_depth)) },
        options = listOf("standard", "deep"),
        selected = options.depth,
        onSelect = { onUpdateOptions(options.copy(depth = it)) },
        optionLabel = { value -> value.replaceFirstChar { it.uppercase() } },
    )
}

private fun CardGroupScope.BraveOptions(
    options: SearchServiceOptions.BraveOptions,
    onUpdateOptions: (SearchServiceOptions.BraveOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }
}

private fun CardGroupScope.SerperOptions(
    options: SearchServiceOptions.SerperOptions,
    onUpdateOptions: (SearchServiceOptions.SerperOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }
}

private fun CardGroupScope.DeepSeekOptions(
    options: SearchServiceOptions.DeepSeekOptions,
    onUpdateOptions: (SearchServiceOptions.DeepSeekOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }
}

private fun CardGroupScope.MetasoOptions(
    options: SearchServiceOptions.MetasoOptions,
    onUpdateOptions: (SearchServiceOptions.MetasoOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }
}

private fun CardGroupScope.OllamaOptions(
    options: SearchServiceOptions.OllamaOptions,
    onUpdateOptions: (SearchServiceOptions.OllamaOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }
}

private fun CardGroupScope.PerplexityOptions(
    options: SearchServiceOptions.PerplexityOptions,
    onUpdateOptions: (SearchServiceOptions.PerplexityOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }

    textItem(
        value = options.maxTokens?.takeIf { it > 0 }?.toString() ?: "",
        onValueChange = { onUpdateOptions(options.copy(maxTokens = it.toIntOrNull())) },
        label = { Text(stringResource(R.string.search_detail_max_tokens)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )

    textItem(
        value = options.maxTokensPerPage?.takeIf { it > 0 }?.toString() ?: "",
        onValueChange = { onUpdateOptions(options.copy(maxTokensPerPage = it.toIntOrNull())) },
        label = { Text(stringResource(R.string.search_detail_max_tokens_per_page)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private fun CardGroupScope.FirecrawlOptions(
    options: SearchServiceOptions.FirecrawlOptions,
    onUpdateOptions: (SearchServiceOptions.FirecrawlOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }
}

private fun CardGroupScope.JinaOptions(
    options: SearchServiceOptions.JinaOptions,
    onUpdateOptions: (SearchServiceOptions.JinaOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }

    textItem(
        value = options.searchUrl,
        onValueChange = { onUpdateOptions(options.copy(searchUrl = it.trim())) },
        label = { Text(stringResource(R.string.search_detail_search_url)) },
        placeholder = { Text("https://s.jina.ai/") },
    )

    textItem(
        value = options.scrapeUrl,
        onValueChange = { onUpdateOptions(options.copy(scrapeUrl = it.trim())) },
        label = { Text(stringResource(R.string.search_detail_scrape_url)) },
        placeholder = { Text("https://r.jina.ai/") },
    )
}

private fun CardGroupScope.BochaOptions(
    options: SearchServiceOptions.BochaOptions,
    onUpdateOptions: (SearchServiceOptions.BochaOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }

    switchItem(
        checked = options.summary,
        onCheckedChange = { onUpdateOptions(options.copy(summary = it)) },
        headlineContent = {
            Text(stringResource(R.string.search_detail_summary))
        },
        supportingContent = {
            Text(stringResource(R.string.search_detail_summary_desc))
        },
    )
}

private fun CardGroupScope.RikkaHubOptions(
    options: SearchServiceOptions.RikkaHubOptions,
    onUpdateOptions: (SearchServiceOptions.RikkaHubOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }

    choiceItem(
        label = { Text(stringResource(R.string.search_detail_depth)) },
        options = listOf("standard", "deep"),
        selected = options.depth,
        onSelect = { onUpdateOptions(options.copy(depth = it)) },
        optionLabel = { value -> value.replaceFirstChar { it.uppercase() } },
    )
}

private fun CardGroupScope.TinyfishOptions(
    options: SearchServiceOptions.TinyfishOptions,
    onUpdateOptions: (SearchServiceOptions.TinyfishOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }
}

private fun CardGroupScope.GrokOptions(
    options: SearchServiceOptions.GrokOptions,
    onUpdateOptions: (SearchServiceOptions.GrokOptions) -> Unit
) {
    ApiKeyItem(options.apiKey) { onUpdateOptions(options.copy(apiKey = it)) }

    textItem(
        value = options.model,
        onValueChange = { onUpdateOptions(options.copy(model = it)) },
        label = { Text(stringResource(R.string.search_detail_model)) },
    )

    textItem(
        value = options.customUrl,
        onValueChange = { onUpdateOptions(options.copy(customUrl = it)) },
        label = { Text(stringResource(R.string.search_detail_custom_url)) },
    )

    textItem(
        value = options.systemPrompt,
        onValueChange = { onUpdateOptions(options.copy(systemPrompt = it)) },
        label = { Text(stringResource(R.string.search_detail_system_prompt)) },
        singleLine = false,
        minLines = 3,
    )
}

private fun CardGroupScope.CustomJsOptions(
    options: SearchServiceOptions.CustomJsOptions,
    onUpdateOptions: (SearchServiceOptions.CustomJsOptions) -> Unit
) {
    textItem(
        value = options.name,
        onValueChange = { onUpdateOptions(options.copy(name = it)) },
        label = { Text(stringResource(R.string.search_detail_name)) },
        placeholder = { Text(stringResource(R.string.search_detail_custom_search_placeholder)) },
    )

    item(headlineContent = {
        CodeTextField(
            value = options.searchScript,
            onValueChange = { onUpdateOptions(options.copy(searchScript = it)) },
            label = { Text(stringResource(R.string.search_detail_search_script)) },
            minLines = 8,
            maxLines = 20,
        )
    })

    item(headlineContent = {
        val codeStyle = MaterialTheme.typography.bodySmall.merge(
            fontFamily = JetbrainsMono,
            fontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings,
        )
        CodeTextField(
            value = options.scrapeScript,
            onValueChange = { onUpdateOptions(options.copy(scrapeScript = it)) },
            label = { Text(stringResource(R.string.search_detail_scrape_script)) },
            minLines = 4,
            maxLines = 20,
            supportingText = {
                Text(stringResource(R.string.search_detail_scrape_script_desc))
            },
            placeholder = {
                Text(
                    text = SearchServiceOptions.CustomJsOptions.DEFAULT_SCRAPE_SCRIPT.trimIndent(),
                    style = codeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            },
        )
    })
}

// 所有 API Key 配置项长得一样，只需要内容和一个更新回调
private fun CardGroupScope.ApiKeyItem(
    apiKey: String,
    onUpdate: (String) -> Unit,
) {
    textItem(
        value = apiKey,
        onValueChange = onUpdate,
        label = { Text(stringResource(R.string.search_detail_api_key)) },
        singleLine = false,
        maxLines = 3,
    )
}
