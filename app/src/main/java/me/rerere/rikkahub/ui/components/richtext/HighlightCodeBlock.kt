package me.rerere.rikkahub.ui.components.richtext

import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rerere.bettertext.highlight.HighlightTextColorPalette
import me.rerere.bettertext.highlight.buildHighlightText
import me.rerere.bettertext.highlight.CodeHighlighter
import me.rerere.bettertext.highlight.rememberHighlightedCode
import me.rerere.bettertext.text.BetterText
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.Eye
import me.rerere.hugeicons.stroke.View
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.modifier.onClick
import me.rerere.rikkahub.ui.theme.AtomOneDarkPalette
import me.rerere.rikkahub.ui.theme.AtomOneLightPalette
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings
import me.rerere.rikkahub.utils.toDp
import me.rerere.ui.webview.WebView
import me.rerere.ui.webview.WebViewContentCache
import me.rerere.ui.webview.rememberWebViewState
import kotlin.time.Clock

private const val COLLAPSE_LINES = 10
private val PREVIEWABLE_LANGUAGES = setOf("html", "svg")

@Composable
fun HighlightCodeBlock(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
    completeCodeBlock: Boolean = true,
    style: TextStyle? = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
) {
    val darkMode = LocalDarkMode.current
    val colorPalette = if (darkMode) AtomOneDarkPalette else AtomOneLightPalette
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val settings = LocalSettings.current
    val normalizedLanguage = remember(language) { language.lowercase() }
    val canInlinePreview = completeCodeBlock && normalizedLanguage in PREVIEWABLE_LANGUAGES
    var previewMode by remember(canInlinePreview, code, normalizedLanguage) {
        mutableStateOf(false)
    }

    var isExpanded by remember(settings.displaySetting.codeBlockAutoCollapse) {
        mutableStateOf(!settings.displaySetting.codeBlockAutoCollapse)
    }
    val autoWrap = settings.displaySetting.codeBlockAutoWrap
    val showLineNumbers = settings.displaySetting.showLineNumbers
    // 代码连字开关同样作用于代码块、行号和语言标签
    val fontFeatureSettings = settings.displaySetting.enableCodeLigatures.codeFontFeatureSettings

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it, "wt")?.use { outputStream ->
                        outputStream.write(code.toByteArray(Charsets.UTF_8))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            HighlightCodeActions(
                language = language,
                scope = scope,
                clipboardManager = clipboardManager,
                code = code,
                createDocumentLauncher = createDocumentLauncher,
                navController = navController,
                completeCodeBlock = completeCodeBlock,
                previewMode = previewMode,
                canInlinePreview = canInlinePreview,
                onTogglePreviewMode = {
                    previewMode = !previewMode
                },
            )
        }
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            when {
                canInlinePreview && previewMode -> {
                    CodeBlockPreview(
                        code = code,
                        language = normalizedLanguage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    )
                }
                completeCodeBlock && normalizedLanguage == "mermaid" -> {
                    Mermaid(
                        code = code,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    val textStyle = LocalTextStyle.current.merge(style)
                    val codeLines = remember(code) { code.lines() }
                    val collapsedCode = remember(codeLines) { codeLines.take(COLLAPSE_LINES).joinToString("\n") }
                    val displayCode = if (isExpanded) code else collapsedCode
                    val displayLines = remember(displayCode) { displayCode.lines() }

                    // 如果显示行号且自动换行，需要逐行渲染以保持对齐
                    when {
                        showLineNumbers && autoWrap -> {
                            CodeBlockWithLineNumbersWrapped(
                                displayLines = displayLines,
                                language = language,
                                textStyle = textStyle,
                                colorPalette = colorPalette,
                                fontFeatureSettings = fontFeatureSettings,
                            )
                        }
                        else -> {
                            CodeBlockDefault(
                                displayCode = displayCode,
                                displayLines = displayLines,
                                language = language,
                                textStyle = textStyle,
                                colorPalette = colorPalette,
                                autoWrap = autoWrap,
                                showLineNumbers = showLineNumbers,
                                scrollState = scrollState,
                                fontFeatureSettings = fontFeatureSettings,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    // 代码折叠按钮
                    if (settings.displaySetting.codeBlockAutoCollapse && codeLines.size > COLLAPSE_LINES) {
                        Box(
                            modifier = Modifier
                                .onClick {
                                    isExpanded = !isExpanded
                                }
                                .fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(textStyle.fontSize.toDp())
                                )
                                Text(
                                    text = if (isExpanded) {
                                        stringResource(id = R.string.code_block_collapse)
                                    } else {
                                        stringResource(id = R.string.code_block_expand)
                                    },
                                    fontSize = textStyle.fontSize,
                                    lineHeight = textStyle.lineHeight,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockWithLineNumbersWrapped(
    displayLines: List<String>,
    language: String,
    textStyle: TextStyle,
    colorPalette: HighlightTextColorPalette,
    fontFeatureSettings: String,
) {
    val lineNumberWidth = remember(displayLines.size) {
        displayLines.size.toString().length
    }
    val codeStyle = codeTextStyle(textStyle, fontFeatureSettings)
    val lineNumberStyle = codeStyle.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val lineNumberHeight = rememberLineNumberHeight(codeStyle)
    // 行号用普通 Text，不参与代码的选择；布局时让行高完全由代码行决定，
    // 并把行号的基线对齐到同行代码的 FirstBaseline，避免两套排版引擎的行高/基线差异
    SelectableContent {
        Column {
            displayLines.forEachIndexed { index, line ->
                CodeLineWithNumber(
                    lineNumber = (index + 1).toString().padStart(lineNumberWidth, ' '),
                    code = rememberHighlightedCode(line, language, colorPalette),
                    lineNumberStyle = lineNumberStyle,
                    codeStyle = codeStyle,
                    lineNumberHeight = lineNumberHeight,
                )
            }
        }
    }
}

@Composable
private fun CodeBlockDefault(
    displayCode: String,
    displayLines: List<String>,
    language: String,
    textStyle: TextStyle,
    colorPalette: HighlightTextColorPalette,
    autoWrap: Boolean,
    showLineNumbers: Boolean,
    scrollState: ScrollState,
    fontFeatureSettings: String,
) {
    Row(
        modifier = Modifier.then(
            if (autoWrap) {
                Modifier
            } else {
                Modifier.horizontalScroll(scrollState)
            }
        )
    ) {
        // 行号列
        if (showLineNumbers) {
            val lineNumberWidth = remember(displayLines.size) {
                displayLines.size.toString().length
            }
            val lineNumberStyle = codeTextStyle(textStyle, fontFeatureSettings).copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            val lineNumbers = remember(displayLines.size, lineNumberWidth) {
                // 末尾留一个空格：BetterText 会按最宽一行收紧布局宽度，空格保证居中的字体
                // 字形不会因宽度取整比实际字宽少 1px 而被迫换行，否则每行行号下面会多出一行
                displayLines.indices.joinToString("\n") { index ->
                    (index + 1).toString().padStart(lineNumberWidth, ' ') + " "
                }
            }
            // 行号和代码都用 BetterText 渲染同一套 StaticLayout：行高、基线与行数完全一致，
            // 不会出现两套排版引擎取整方式不同导致的逐行错位和底部留白。
            BetterText(
                text = AnnotatedString(lineNumbers),
                style = lineNumberStyle,
                modifier = Modifier.padding(end = 8.dp),
            )
        }

        // 代码列：在横向滚动里宽度不受限，不会折行；自动换行时按可用宽度折行
        SelectableContent {
            BetterText(
                text = rememberHighlightedCode(displayCode, language, colorPalette),
                modifier = Modifier.animateContentSize(),
                style = codeTextStyle(textStyle, fontFeatureSettings),
            )
        }
    }
}

/**
 * 一行代码与它的行号。行高只取代码行的最大高度（行号不参与行高），因此行号不会把每行
 * 抬高；行号固定在代码的单行像素行高内并按代码的 `FirstBaseline` 垂直对齐，两套排版引擎
 * 的行高取整或字体留白差异都不会累积成逐行错位。
 */
@Composable
private fun CodeLineWithNumber(
    lineNumber: String,
    code: AnnotatedString,
    lineNumberStyle: TextStyle,
    codeStyle: TextStyle,
    lineNumberHeight: Dp?,
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Text(
                text = lineNumber,
                style = lineNumberStyle,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.padding(end = 8.dp),
            )
            BetterText(
                text = code,
                style = codeStyle,
            )
        },
    ) { measurables, constraints ->
        val numberPlaceable = measurables[0].measure(
            if (lineNumberHeight != null) {
                val px = lineNumberHeight.roundToPx()
                Constraints(minHeight = px, maxHeight = px)
            } else {
                Constraints()
            }
        )
        val codePlaceable = measurables[1].measure(
            Constraints(
                maxWidth = (constraints.maxWidth - numberPlaceable.width).coerceAtLeast(0)
            )
        )
        val height = maxOf(codePlaceable.height, numberPlaceable.height)
        layout(constraints.maxWidth, height) {
            val codeBaseline = codePlaceable[FirstBaseline]
            val numberBaseline = numberPlaceable[FirstBaseline]
            val offset = if (
                codeBaseline != AlignmentLine.Unspecified &&
                numberBaseline != AlignmentLine.Unspecified
            ) {
                codeBaseline - numberBaseline
            } else {
                0
            }
            numberPlaceable.place(0, offset)
            codePlaceable.place(numberPlaceable.width, 0)
        }
    }
}

/**
 * 行号的单行像素高度。行号用普通 `Text` 单行渲染，把它固定为代码用的整数像素行高，
 * 使它不会比同一行的 BetterText 更高而在行与行之间留出空白。
 */
@Composable
private fun rememberLineNumberHeight(textStyle: TextStyle): Dp? {
    val lineHeight = textStyle.lineHeight
    if (!lineHeight.isSp) return null
    val density = LocalDensity.current
    return remember(lineHeight, density) {
        with(density) { lineHeight.toPx().roundToInt().toFloat().toDp() }
    }
}

/** 代码的文字样式：等宽字体、按设置决定连字；没有颜色时用 `LocalContentColor`，和 `Text` 一样 */
@Composable
private fun codeTextStyle(textStyle: TextStyle, fontFeatureSettings: String): TextStyle = textStyle.merge(
    TextStyle(
        color = textStyle.color.takeOrElse { LocalContentColor.current },
        fontFamily = JetbrainsMono,
        fontFeatureSettings = fontFeatureSettings,
    )
)

@Composable
private fun HighlightCodeActions(
    language: String,
    scope: CoroutineScope,
    clipboardManager: Clipboard,
    code: String,
    createDocumentLauncher: ManagedActivityResultLauncher<String, Uri?>,
    navController: Navigator,
    completeCodeBlock: Boolean = true,
    previewMode: Boolean = false,
    canInlinePreview: Boolean = false,
    onTogglePreviewMode: () -> Unit = {},
) {
    val fontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = language,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontFamily = JetbrainsMono,
            color = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = 0.5f),
            style = LocalTextStyle.current.copy(fontFeatureSettings = fontFeatureSettings),
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconSize = 16.dp
            val iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

            Icon(
                imageVector = HugeIcons.Download04,
                contentDescription = stringResource(id = R.string.common_save),
                tint = iconTint,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .onClick {
                        val extension = when (language.lowercase()) {
                            "kotlin" -> "kt"
                            "java" -> "java"
                            "python" -> "py"
                            "javascript" -> "js"
                            "typescript" -> "ts"
                            "cpp", "c++" -> "cpp"
                            "c" -> "c"
                            "html" -> "html"
                            "css" -> "css"
                            "xml" -> "xml"
                            "json" -> "json"
                            "yaml", "yml" -> "yml"
                            "markdown", "md" -> "md"
                            "sql" -> "sql"
                            "sh", "bash" -> "sh"
                            "svg" -> "svg"
                            else -> "txt"
                        }
                        createDocumentLauncher.launch(
                            "code_${
                                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                            }.$extension"
                        )
                    }
                    .padding(4.dp)
                    .size(iconSize)
            )

            Icon(
                imageVector = HugeIcons.Copy01,
                contentDescription = stringResource(id = R.string.code_block_copy),
                tint = iconTint,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .onClick {
                        scope.launch {
                            clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("code", code)))
                        }
                    }
                    .padding(4.dp)
                    .size(iconSize)
            )

            val normalizedLanguage = language.lowercase()
            if (canInlinePreview) {
                Icon(
                    imageVector = if (previewMode) HugeIcons.Code else HugeIcons.View,
                    contentDescription = if (previewMode) "Code" else stringResource(id = R.string.code_block_preview),
                    tint = iconTint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .onClick {
                            onTogglePreviewMode()
                        }
                        .padding(4.dp)
                        .size(iconSize)
                )
            }

            if (completeCodeBlock && normalizedLanguage in PREVIEWABLE_LANGUAGES) {
                Icon(
                    imageVector = HugeIcons.Eye,
                    contentDescription = stringResource(id = R.string.code_block_preview),
                    tint = iconTint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .onClick {
                            val content = buildCodePreviewHtml(code = code, language = normalizedLanguage)
                            val contentId = WebViewContentCache.store(context.cacheDir, content)
                            navController.navigate(Screen.WebView(contentId = contentId))
                        }
                        .padding(4.dp)
                        .size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun CodeBlockPreview(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberWebViewState(
        data = buildCodePreviewHtml(code = code, language = language),
        baseUrl = "https://rikkahub.local",
        mimeType = "text/html",
        settings = {
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
    )

    WebView(
        state = state,
        modifier = modifier.clip(RoundedCornerShape(4.dp)),
    )
}

private fun buildCodePreviewHtml(code: String, language: String): String {
    return if (language == "svg") {
        """<!DOCTYPE html><html><body style="margin:0;display:flex;justify-content:center;align-items:center;min-height:100vh;">$code</body></html>"""
    } else {
        code
    }
}

class HighlightCodeVisualTransformation(
    val language: String,
    val highlighter: CodeHighlighter,
    val darkMode: Boolean
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val annotatedString = try {
            val colorPalette = if (darkMode) AtomOneDarkPalette else AtomOneLightPalette
            if (text.text.isEmpty()) {
                AnnotatedString("")
            } else {
                val tokens = highlighter.highlight(text.text, language)
                buildAnnotatedString {
                    tokens.forEach { token ->
                        buildHighlightText(token, colorPalette)
                    }
                }
            }
        } catch (e: Exception) {
            AnnotatedString(text.text)
        }

        return TransformedText(
            text = annotatedString,
            offsetMapping = OffsetMapping.Identity
        )
    }
}
