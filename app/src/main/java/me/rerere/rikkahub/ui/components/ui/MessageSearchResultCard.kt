package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.utils.toLocalDateTime

/**
 * 搜索结果与附件引用预览共用的列表卡片: 标题在顶部, 中间是消息片段, 底部是时间与来源
 * (助手/文件夹)。
 *
 * [parseSnippetHighlight] 为 true 时会把片段里的 `[关键词]` 渲染为高亮, 用于搜索页;
 * 附件引用预览传入普通文本, 保持原样显示。
 * [sourceLineLimit] 限制片段保留的源码行数 (搜索页无关键词且带消息筛选时只保留 5 行);
 * [snippetMaxLines] 限制卡片实际显示的行数 (附件引用预览显示 3 行)。
 */
@Composable
fun MessageSearchResultCard(
    result: MessageSearchResult,
    assistantName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    highlightTitle: Boolean = false,
    parseSnippetHighlight: Boolean = false,
    sourceLineLimit: Int? = null,
    snippetMaxLines: Int = Int.MAX_VALUE,
) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val untitled = stringResource(R.string.conversation_untitled)
    val snippetText = buildAnnotatedString {
        val snippet = sourceLineLimit?.let { limit ->
            result.snippet.split('\n').take(limit).joinToString("\n")
        } ?: result.snippet
        if (!parseSnippetHighlight) {
            append(snippet)
        } else {
            var index = 0
            while (index < snippet.length) {
                val start = snippet.indexOf('[', index)
                if (start == -1) {
                    append(snippet.substring(index))
                    break
                }
                if (start > index) {
                    append(snippet.substring(index, start))
                }
                val end = snippet.indexOf(']', start + 1)
                if (end == -1) {
                    append(snippet.substring(start))
                    break
                }
                val matched = snippet.substring(start + 1, end)
                withStyle(SpanStyle(background = highlightColor)) {
                    append(matched)
                }
                index = end + 1
            }
        }
    }
    val formattedTime = remember(result.timeAt) {
        result.timeAt.toLocalDateTime()
    }
    // 结果来源：助手/文件夹，例如 "Assistant/Folder"；缺失的部分会被省略。
    val sourceLabel = remember(assistantName, result.folderName) {
        listOfNotNull(
            assistantName?.takeIf { it.isNotBlank() },
            result.folderName?.takeIf { it.isNotBlank() },
        ).joinToString("/")
    }
    val displayTitle = result.title.ifBlank { untitled }
    val titleText = if (highlightTitle && result.title.isNotBlank() && query.isNotEmpty()) {
        buildAnnotatedString {
            var index = 0
            while (index < displayTitle.length) {
                val matchStart = displayTitle.indexOf(query, startIndex = index)
                if (matchStart < 0) {
                    append(displayTitle.substring(index))
                    break
                }
                append(displayTitle.substring(index, matchStart))
                withStyle(SpanStyle(background = highlightColor)) {
                    append(displayTitle.substring(matchStart, matchStart + query.length))
                }
                index = matchStart + query.length
            }
        }
    } else {
        buildAnnotatedString { append(displayTitle) }
    }

    OutlinedItemCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (result.snippet.isNotEmpty()) {
                Text(
                    text = snippetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = snippetMaxLines,
                )
            }
            Text(
                text = if (sourceLabel.isNotEmpty()) {
                    "$formattedTime · $sourceLabel"
                } else {
                    formattedTime
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
