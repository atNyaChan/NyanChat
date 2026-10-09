package me.rerere.rikkahub.ui.pages.mediacreation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ImageToVideo
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.MediaCreationSession
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toMessageTimeString
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * 媒体创作的入口：列出所有会话，点进去是这个会话的创作页（[MediaCreationPage]）。
 */
@Composable
fun MediaCreationSessionsPage(vm: MediaCreationSessionsVM = koinViewModel()) {
    val navController = LocalNavController.current
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    val open: (Uuid) -> Unit = { id ->
        navController.navigate(Screen.MediaCreation(id.toString())) { launchSingleTop = true }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.media_creation_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.newSession(open) },
            ) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            // 底部留出悬浮按钮的位置，最后一项才点得到
            contentPadding = innerPadding + PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (sessions?.isEmpty() == true) {
                item {
                    EmptySessionsState(modifier = Modifier.fillParentMaxHeight(0.7f))
                }
            }

            val items = sessions.orEmpty()
            itemsIndexed(items, key = { _, session -> session.id.toString() }) { _, session ->
                SessionItem(
                    session = session,
                    resolve = vm::resolve,
                    onOpen = { open(session.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun EmptySessionsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .size(128.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.ImageToVideo,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.media_creation_page_sessions_empty),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.media_creation_page_sessions_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val SessionLeadingSize = 48.dp

// 每个会话按 id 固定分到一个形状，列表看起来不那么整齐划一
private val sessionShapes = listOf(
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.Flower,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Gem,
    MaterialShapes.Sunny,
)

@Composable
private fun SessionItem(
    session: MediaCreationSession,
    resolve: (String) -> File,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedItemCard(
        onClick = onOpen,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shape = sessionShapes[session.id.hashCode().mod(sessionShapes.size)].toShape()
            val cover = session.cover
            when {
                // 有任务在生成时换成变形的加载指示器
                session.activeCount > 0 -> ContainedLoadingIndicator(modifier = Modifier.size(SessionLeadingSize))

                // 最近的产出裁成这个会话的形状当封面
                cover != null -> MediaThumbnail(
                    file = remember(cover.path) { resolve(cover.path) },
                    isVideo = cover.isVideo,
                    poster = cover.posterPath?.let(resolve),
                    playIconSize = 8.dp,
                    modifier = Modifier
                        .size(SessionLeadingSize)
                        .clip(shape),
                )

                else -> Box(
                    modifier = Modifier
                        .size(SessionLeadingSize)
                        .background(color = MaterialTheme.colorScheme.secondaryContainer, shape = shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.ImageToVideo,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = session.title.ifBlank { stringResource(R.string.media_creation_page_new_session) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val todayLabel = stringResource(R.string.chat_page_today)
                val yesterdayLabel = stringResource(R.string.chat_page_yesterday)
                val time = remember(session.updateAt, todayLabel, yesterdayLabel) {
                    session.updateAt.atZone(ZoneId.systemDefault()).toLocalDateTime()
                        .toMessageTimeString(todayLabel, yesterdayLabel)
                }
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.media_creation_page_record_count, session.nodeCount),
                        stringResource(R.string.media_creation_page_active_count, session.activeCount)
                            .takeIf { session.activeCount > 0 },
                        time,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
