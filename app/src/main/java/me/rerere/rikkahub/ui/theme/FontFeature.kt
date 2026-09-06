package me.rerere.rikkahub.ui.theme

/**
 * 代码 / 等宽文本的字体特性 (font feature) 配置。
 *
 * 通过设置中的 "代码连字" 开关 (DisplaySetting.enableCodeLigatures) 控制:
 * - 开启: 启用连字 (liga) 与上下文替代 (calt)
 * - 关闭: 显式禁用
 *
 * 该属性用于所有使用等宽字体的 Compose 文本 / SpanStyle / TextStyle,
 * 保证连字开关对所有等宽文本全局生效。
 */
val Boolean.codeFontFeatureSettings: String
    get() = if (this) {
        "\"liga\", \"calt\""
    } else {
        "\"liga\" 0, \"calt\" 0"
    }
