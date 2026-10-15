package me.rerere.rikkahub.ui.components.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.skills.SkillManager
import me.rerere.rikkahub.data.skills.SkillMetadata
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.LorebooksContent
import me.rerere.rikkahub.ui.components.ai.ModeInjectionsContent
import me.rerere.rikkahub.ui.components.ai.QuickMessagesContent
import me.rerere.rikkahub.ui.components.ai.SkillsContent
import org.koin.compose.koinInject

private enum class ExtensionTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    QUICK_MESSAGES(HugeIcons.Zap, R.string.quick_messages_page_title),
    MODE_INJECTIONS(HugeIcons.MagicWand01, R.string.mode_injection_page_title),
    LOREBOOKS(HugeIcons.Book01, R.string.lorebook_page_title),
    SKILLS(HugeIcons.Puzzle, R.string.skills_page_title);

    override val label: String
        @Composable get() = stringResource(labelRes)
}


@Composable
fun ExtensionSelector(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    settings: Settings,
    onUpdate: (Assistant) -> Unit,
    onNavigateToQuickMessages: () -> Unit = {},
    onNavigateToModeInjections: () -> Unit = {},
    onNavigateToLorebooks: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
) {
    val skillManager: SkillManager = koinInject()
    var skills by remember { mutableStateOf<List<SkillMetadata>>(emptyList()) }

    LaunchedEffect(Unit) {
        // 打开扩展面板时清理运行时被删除的技能（残留的 enabledSkills 引用），
        // prune 顺带返回现存技能列表，避免重复读盘
        skills = skillManager.pruneOrphanedEnabledSkills()
    }

    val pagerState = rememberPagerState { ExtensionTab.entries.size }

    Column(
        modifier = modifier
    ) {
        PagerTabs(
            pagerState = pagerState,
            tabs = ExtensionTab.entries,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val tab = ExtensionTab.entries[page]
            val manageText = stringResource(R.string.extension_selector_manage, tab.label)
            when (tab) {
                ExtensionTab.QUICK_MESSAGES -> {
                    if (settings.quickMessages.isNotEmpty()) {
                        QuickMessagesContent(
                            quickMessages = settings.quickMessages,
                            selectedIds = assistant.quickMessageIds,
                            onToggle = { id, checked ->
                                val newIds = if (checked) {
                                    assistant.quickMessageIds + id
                                } else {
                                    assistant.quickMessageIds - id
                                }
                                onUpdate(assistant.copy(quickMessageIds = newIds))
                            },
                            onManage = onNavigateToQuickMessages,
                            manageText = manageText,
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_quick_messages_empty),
                            description = stringResource(R.string.extension_selector_quick_messages_empty_desc),
                            buttonText = manageText,
                            onAction = onNavigateToQuickMessages,
                            icon = tab.icon,
                        )
                    }
                }

                ExtensionTab.MODE_INJECTIONS -> {
                    if (settings.modeInjections.isNotEmpty()) {
                        ModeInjectionsContent(
                            modeInjections = settings.modeInjections,
                            selectedIds = assistant.modeInjectionIds,
                            onToggle = { id, checked ->
                                val newIds = if (checked) {
                                    assistant.modeInjectionIds + id
                                } else {
                                    assistant.modeInjectionIds - id
                                }
                                onUpdate(assistant.copy(modeInjectionIds = newIds))
                            },
                            onManage = onNavigateToModeInjections,
                            manageText = manageText,
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_mode_injections_empty),
                            description = stringResource(R.string.extension_selector_mode_injections_empty_desc),
                            buttonText = manageText,
                            onAction = onNavigateToModeInjections,
                            icon = tab.icon,
                        )
                    }
                }

                ExtensionTab.LOREBOOKS -> {
                    if (settings.lorebooks.isNotEmpty()) {
                        LorebooksContent(
                            lorebooks = settings.lorebooks,
                            selectedIds = assistant.lorebookIds,
                            onToggle = { id, checked ->
                                val newIds = if (checked) {
                                    assistant.lorebookIds + id
                                } else {
                                    assistant.lorebookIds - id
                                }
                                onUpdate(assistant.copy(lorebookIds = newIds))
                            },
                            onManage = onNavigateToLorebooks,
                            manageText = manageText,
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_lorebooks_empty),
                            description = stringResource(R.string.extension_selector_lorebooks_empty_desc),
                            buttonText = manageText,
                            onAction = onNavigateToLorebooks,
                            icon = tab.icon,
                        )
                    }
                }

                ExtensionTab.SKILLS -> {
                    if (skills.isNotEmpty()) {
                        SkillsContent(
                            skills = skills,
                            enabledSkills = assistant.enabledSkills,
                            onToggle = { name, checked ->
                                val newSkills = if (checked) {
                                    assistant.enabledSkills + name
                                } else {
                                    assistant.enabledSkills - name
                                }
                                onUpdate(assistant.copy(enabledSkills = newSkills))
                            },
                            onManage = onNavigateToSkills,
                            manageText = manageText,
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_skills_empty),
                            description = stringResource(R.string.extension_selector_skills_empty_desc),
                            buttonText = manageText,
                            onAction = onNavigateToSkills,
                            icon = tab.icon,
                        )
                    }
                }
            }
        }
    }
}
