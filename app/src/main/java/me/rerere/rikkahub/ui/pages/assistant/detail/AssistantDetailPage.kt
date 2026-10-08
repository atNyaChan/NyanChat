package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BookOpen01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Message02
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Api
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.rikkahub.ui.pages.assistant.AssistantVM
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantDetailPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val assistantVm: AssistantVM = koinViewModel()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    // 头部滚出屏幕后，名字改在顶栏里显示
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = showTitle,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                    ) {
                        Text(
                            text = name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            state = listState,
            contentPadding = PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AssistantHeader(
                    assistant = assistant,
                    name = name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                )
            }

            item {
                val containerColor = MaterialTheme.colorScheme.primaryContainer
                val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = { navController.navigate(Screen.AssistantBasic(id)) },
                        leadingContent = { CardGroupIcon(HugeIcons.Settings03, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_basic_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_basic)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantRequest(id)) },
                        leadingContent = { CardGroupIcon(HugeIcons.Api, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_request_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_request)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantPrompt(id)) },
                        leadingContent = { CardGroupIcon(HugeIcons.Message02, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_prompt_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_prompt)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantMemory(id)) },
                        leadingContent = { CardGroupIcon(HugeIcons.Brain02, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_memory_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_memory)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantLocalTool(id)) },
                        leadingContent = { CardGroupIcon(HugeIcons.BookOpen01, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_local_tools_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_local_tools)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantMcp(id)) },
                        leadingContent = { CardGroupIcon(HugeIcons.Wrench01, containerColor = containerColor, contentColor = contentColor) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_mcp_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_mcp)) },
                    )
                }
            }
            item {
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(HugeIcons.Delete01, contentDescription = null)
                    Text(
                        stringResource(R.string.assistant_page_delete_assistant),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = showDeleteConfirm,
        title = stringResource(R.string.assistant_page_delete_assistant),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            showDeleteConfirm = false
            assistantVm.removeAssistant(assistant) { fallbackChatId ->
                if (fallbackChatId != null) {
                    navigateToChatPage(navController, fallbackChatId)
                } else {
                    navController.popBackStack()
                }
            }
        },
        onDismiss = { showDeleteConfirm = false },
    ) {
        Text(stringResource(R.string.assistant_page_delete_dialog_text))
    }
}

@Composable
private fun AssistantHeader(
    assistant: Assistant,
    name: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UIAvatar(
            value = assistant.avatar,
            name = name,
            invertDefaultAvatarInDarkMode = true,
            shape = MaterialShapes.Cookie12Sided.toShape(),
            onUpdate = null,
            modifier = Modifier
                .size(136.dp)
                .heroAnimation("assistant_${assistant.id}")
        )

        Text(
            text = name,
            // 名字长短不一，放不下两行时自动缩小字号
            autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 32.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineLargeEmphasized.copy(
                fontWeight = FontWeight.Black,
                lineHeight = 1.2.em,
                lineBreak = LineBreak.Heading,
            ),
        )

        if (assistant.systemPrompt.isNotBlank()) {
            Text(
                text = assistant.systemPrompt.take(100),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
