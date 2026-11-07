package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownDocument
import me.rerere.bettertext.markdown.MarkdownInline
import me.rerere.bettertext.markdown.TableCell

/**
 * 把显示设置里的加粗字重和代码连字作用到解析结果上。
 *
 * BetterText 的主题只能配置行内代码、标题这类整体样式：加粗是解析器里固定的 `SpanStyle`，
 * HTML 行内样式产生的等宽字体也拿不到 `fontFeatureSettings`。所以在渲染前改写对应样式：
 * - 加粗（`fontWeight = Bold`，包括 `**…**`、`<b>` 和 CSS 的 `font-weight: bold`）换成本应用设置的字重
 * - 等宽字体补上代码连字的 `fontFeatureSettings`
 * - 关闭 LaTeX 渲染时把行内公式换成等宽源码，和禁用公式渲染时的显示一致
 *
 * 设置都保持默认（Bold、开启连字、渲染 LaTeX）时直接返回原文档，不做任何遍历。
 */
internal fun MarkdownDocument.applyDisplaySettings(
    boldFontWeight: FontWeight,
    enableCodeLigatures: Boolean,
    mathAsText: Boolean,
    codeFontFeatureSettings: String,
): MarkdownDocument {
    val rewriteBold = boldFontWeight != FontWeight.Bold
    val rewriteCode = !enableCodeLigatures
    if (!rewriteBold && !rewriteCode && !mathAsText) return this

    fun SpanStyle.adjust(): SpanStyle {
        val adjustedWeight = if (rewriteBold && fontWeight == FontWeight.Bold) boldFontWeight else fontWeight
        val adjustedFeatures =
            if (rewriteCode && fontFamily == FontFamily.Monospace) codeFontFeatureSettings else fontFeatureSettings
        if (adjustedWeight == fontWeight && adjustedFeatures == fontFeatureSettings) return this
        return copy(fontWeight = adjustedWeight, fontFeatureSettings = adjustedFeatures)
    }

    fun TextStyle.adjust(): TextStyle {
        val adjusted = SpanStyle(
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            fontFeatureSettings = fontFeatureSettings,
        ).adjust()
        if (adjusted.fontWeight == fontWeight && adjusted.fontFeatureSettings == fontFeatureSettings) return this
        return copy(
            fontWeight = adjusted.fontWeight,
            fontFeatureSettings = adjusted.fontFeatureSettings,
        )
    }

    fun List<MarkdownInline>.adjustInlines(): List<MarkdownInline> = map { inline ->
        when (inline) {
            is MarkdownInline.Text, is MarkdownInline.Code, is MarkdownInline.Image -> inline
            is MarkdownInline.Styled -> {
                val children = inline.children.adjustInlines()
                val style = inline.style.adjust()
                if (children == inline.children && style == inline.style) {
                    inline
                } else {
                    MarkdownInline.Styled(style, children)
                }
            }

            is MarkdownInline.Link -> {
                val children = inline.children.adjustInlines()
                if (children == inline.children) inline else inline.copy(children = children)
            }

            is MarkdownInline.Math -> if (mathAsText) {
                MarkdownInline.Styled(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = codeFontFeatureSettings,
                    ),
                    listOf(MarkdownInline.Text(inline.formula)),
                )
            } else {
                inline
            }
        }
    }

    fun List<TableCell>.adjustCells(): List<TableCell> = map { cell ->
        val content = cell.content.adjustInlines()
        val style = cell.style?.adjust()
        if (content == cell.content && style == cell.style) {
            cell
        } else {
            cell.copy(content = content, style = style)
        }
    }

    fun List<MarkdownBlock>.adjustBlocks(): List<MarkdownBlock> = map { block ->
        when (block) {
            is MarkdownBlock.Paragraph -> {
                val content = block.content.adjustInlines()
                val style = block.style?.adjust()
                if (content == block.content && style == block.style) block else block.copy(content, style)
            }

            is MarkdownBlock.Heading -> {
                val content = block.content.adjustInlines()
                val style = block.style?.adjust()
                if (content == block.content &&
                    style == block.style) {
                    block
                } else {
                    block.copy(content = content, style = style)
                }
            }

            is MarkdownBlock.Quote -> {
                val children = block.children.adjustBlocks()
                if (children == block.children) block else block.copy(children)
            }

            is MarkdownBlock.ListBlock -> {
                val items = block.items.map { item ->
                    val children = item.children.adjustBlocks()
                    if (children == item.children) item else item.copy(children = children)
                }
                if (items == block.items) block else block.copy(items = items)
            }

            is MarkdownBlock.Table -> {
                val header = block.header.adjustCells()
                val rows = block.rows.map { it.adjustCells() }
                if (header == block.header && rows == block.rows) block else block.copy(header = header, rows = rows)
            }

            is MarkdownBlock.Details -> {
                val summary = block.summary.adjustInlines()
                val children = block.children.adjustBlocks()
                if (summary == block.summary && children == block.children) {
                    block
                } else {
                    block.copy(summary = summary, children = children)
                }
            }

            is MarkdownBlock.Container -> {
                val children = block.children.adjustBlocks()
                val style = block.style.adjust()
                if (children == block.children && style == block.style) block else block.copy(children, style)
            }

            is MarkdownBlock.CodeBlock, MarkdownBlock.Divider -> block
        }
    }

    val adjusted = blocks.adjustBlocks()
    return if (adjusted == blocks) this else MarkdownDocument(source = source, blocks = adjusted)
}
