package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import me.rerere.ai.ui.DiffMetadata
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.metadataAs
import me.rerere.common.http.jsonObjectOrNull
import androidx.compose.ui.res.stringResource
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.FileAdd
import me.rerere.hugeicons.stroke.FileEdit
import me.rerere.hugeicons.stroke.FileView
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.DiffAddedColor
import me.rerere.rikkahub.ui.components.richtext.DiffRemovedColor
import me.rerere.rikkahub.ui.components.richtext.DiffView
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import me.rerere.rikkahub.ui.components.richtext.parseDiffStats
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

/**
 * 工作空间编辑文件: 完整 diff 内联展示在步骤中, 点击步骤展开/收起
 */
object EditFileToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_edit_file"
    override val inlineDetail: Boolean = true

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileEdit

    @Composable
    override fun title(context: ToolUIContext): String {
        val path = context.arguments.getStringContent("path")
        return if (path != null) stringResource(R.string.tool_ui_edit_file, path) else stringResource(R.string.tool_ui_edit_file_default)
    }

    /**
     * 执行后读取输出部件 metadata 中的全文件 diff;
     * 未执行 (如等待审批) 时基于入参的 old_text/new_text 片段生成预览 diff
     */
    private fun diffOf(context: ToolUIContext): String? {
        if (context.tool.isExecuted) {
            return context.tool.output.firstOrNull()?.metadataAs<DiffMetadata>()?.diff
        }
        val path = context.arguments.getStringContent("path") ?: return null
        val oldText = context.arguments.getStringContent("old_text") ?: return null
        val newText = context.arguments.getStringContent("new_text") ?: return null
        return generateUnifiedDiff(oldText, newText, path)
    }

    override fun hasSummary(context: ToolUIContext): Boolean = diffOf(context) != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val diff = remember(context) { diffOf(context) } ?: return
        val stats = remember(diff) { parseDiffStats(diff) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "+${stats.additions}",
                style = MaterialTheme.typography.labelSmall,
                color = DiffAddedColor,
            )
            Text(
                text = "-${stats.deletions}",
                style = MaterialTheme.typography.labelSmall,
                color = DiffRemovedColor,
            )
        }
        DiffView(
            diff = diff,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 工作空间读取文件: 完整文件内容内联展示在步骤中, 点击步骤展开/收起
 */
object ReadFileToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_read_file"
    override val inlineDetail: Boolean = true

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileView

    @Composable
    override fun title(context: ToolUIContext): String {
        val path = context.arguments.getStringContent("path")
        return if (path != null) stringResource(R.string.tool_ui_read_file, path) else stringResource(R.string.tool_ui_read_file_default)
    }

    /** 已执行时从输出 JSON 读取文件内容 */
    private fun textOf(context: ToolUIContext): String? =
        context.content.getStringContent("text")

    override fun hasSummary(context: ToolUIContext): Boolean = textOf(context) != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val text = remember(context) { textOf(context) } ?: return
        FileContentInline(
            path = context.arguments.getStringContent("path"),
            code = text,
        )
    }
}

/**
 * 工作空间写入文件: 完整写入内容内联展示在步骤中, 点击步骤展开/收起
 */
object WriteFileToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_write_file"
    override val inlineDetail: Boolean = true

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileAdd

    @Composable
    override fun title(context: ToolUIContext): String {
        val path = context.arguments.getStringContent("path")
        return if (path != null) stringResource(R.string.tool_ui_write_file, path) else stringResource(R.string.tool_ui_write_file_default)
    }

    private fun textOf(context: ToolUIContext): String? =
        context.arguments.getStringContent("text")

    override fun hasSummary(context: ToolUIContext): Boolean = textOf(context) != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val text = remember(context) { textOf(context) } ?: return
        FileContentInline(
            path = context.arguments.getStringContent("path"),
            code = text,
        )
    }
}

/** 内联文件内容: 路径 + 按扩展名语法高亮的完整内容 */
@Composable
private fun FileContentInline(path: String?, code: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!path.isNullOrBlank()) {
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HighlightCodeBlock(
            code = code,
            language = languageOf(path),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 工作空间执行 Shell: 命令、退出状态与 stdout/stderr 内联展示在步骤中, 点击步骤展开/收起 */
object ShellToolUI : ToolUIRenderer {
    private const val TITLE_MAX_CHARS = 40

    override val toolName: String = "workspace_shell"
    override val inlineDetail: Boolean = true

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.ComputerTerminal01

    @Composable
    override fun title(context: ToolUIContext): String {
        val command = context.arguments.getStringContent("command") ?: return stringResource(R.string.tool_ui_shell_default)
        val preview = command.replace("\n", " ").trim()
        val truncated = if (preview.length > TITLE_MAX_CHARS) preview.take(TITLE_MAX_CHARS) + "…" else preview
        return stringResource(R.string.tool_ui_shell, truncated)
    }

    override fun hasSummary(context: ToolUIContext): Boolean =
        context.content != null || !context.arguments.getStringContent("command").isNullOrBlank()

    /** Shell 未执行完成且未被拒绝/回答时视为正在运行, 标题行显示加载渐变 */
    override fun isRunning(context: ToolUIContext): Boolean {
        if (context.tool.isExecuted) return false
        return context.tool.approvalState is ToolApprovalState.Auto ||
            context.tool.approvalState is ToolApprovalState.Approved
    }

    @Composable
    override fun Summary(context: ToolUIContext) {
        val command = context.arguments.getStringContent("command").orEmpty()
        val cwd = context.arguments.getStringContent("cwd")
        val content = context.content
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.tool_ui_shell_default),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (content != null) {
                    ShellExitStatus(content, MaterialTheme.typography.labelSmall)
                }
            }
            HighlightCodeBlock(
                code = if (cwd.isNullOrBlank()) command else "# cwd: $cwd\n$command",
                language = "bash",
                modifier = Modifier.fillMaxWidth(),
            )
            if (content != null) {
                val stdout = content.getStringContent("stdout").orEmpty()
                val stderr = content.getStringContent("stderr").orEmpty()
                if (stdout.isNotEmpty()) {
                    Text(text = "stdout", style = MaterialTheme.typography.labelSmall)
                    HighlightCodeBlock(
                        code = stdout,
                        language = "plaintext",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (stderr.isNotEmpty()) {
                    Text(
                        text = "stderr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    HighlightCodeBlock(
                        code = stderr,
                        language = "plaintext",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Shell 退出状态文本: exit code 为 0 显示绿色, 超时或非零显示错误色 */
@Composable
private fun ShellExitStatus(content: JsonElement, style: androidx.compose.ui.text.TextStyle) {
    val exitCode = content.int("exitCode")
    val timedOut = content.boolean("timedOut") ?: false
    val ok = !timedOut && exitCode == 0
    Text(
        text = when {
            timedOut -> stringResource(R.string.tool_ui_shell_timeout)
            else -> stringResource(R.string.tool_ui_shell_exit, exitCode?.toString() ?: "?")
        },
        style = style,
        color = if (ok) DiffAddedColor else MaterialTheme.colorScheme.error,
    )
}

/** 从工具输出 JSON 读取布尔字段 */
private fun JsonElement?.boolean(key: String): Boolean? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitiveOrNull?.booleanOrNull

/** 从工具输出 JSON 读取整型字段 */
private fun JsonElement?.int(key: String): Int? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitiveOrNull?.intOrNull

/** 由文件扩展名推断语法高亮语言 */
private fun languageOf(path: String?): String = when (
    path?.substringAfterLast('.', "")?.lowercase().orEmpty()
) {
    "kt", "kts" -> "kotlin"
    "java" -> "java"
    "js", "mjs", "cjs" -> "javascript"
    "ts" -> "typescript"
    "tsx" -> "tsx"
    "jsx" -> "jsx"
    "py" -> "python"
    "rb" -> "ruby"
    "go" -> "go"
    "rs" -> "rust"
    "c", "h" -> "c"
    "cpp", "cc", "cxx", "hpp", "hxx" -> "cpp"
    "cs" -> "csharp"
    "swift" -> "swift"
    "php" -> "php"
    "sh", "bash", "zsh" -> "bash"
    "json" -> "json"
    "xml" -> "xml"
    "html", "htm" -> "html"
    "css" -> "css"
    "scss" -> "scss"
    "yaml", "yml" -> "yaml"
    "toml" -> "toml"
    "md", "markdown" -> "markdown"
    "sql" -> "sql"
    "gradle" -> "groovy"
    else -> "plaintext"
}
