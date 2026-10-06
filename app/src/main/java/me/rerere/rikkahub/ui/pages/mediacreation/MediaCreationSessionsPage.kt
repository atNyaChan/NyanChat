package me.rerere.rikkahub.ui.pages.mediacreation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
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
            FloatingActionButton(onClick = { vm.newSession(open) }) {
                Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.media_creation_page_new_session))
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sessions?.isEmpty() == true) {
                item {
                    EmptySessionsState()
                }
            }

            items(sessions.orEmpty(), key = { it.id.toString() }) { session ->
                SessionCard(
                    session = session,
                    onOpen = { open(session.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptySessionsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = HugeIcons.ImageToVideo,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.media_creation_page_sessions_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.media_creation_page_sessions_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionCard(
    session: MediaCreationSession,
    onOpen: () -> Unit,
) {
    OutlinedItemCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                )
            }
        }
    }
}
