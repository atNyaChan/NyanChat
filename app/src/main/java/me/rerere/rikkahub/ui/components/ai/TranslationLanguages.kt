package me.rerere.rikkahub.ui.components.ai

import java.util.Locale

// 翻译功能可选的目标语言，消息翻译和翻译页共用
val TranslationLanguages: List<Locale> = listOf(
    Locale.SIMPLIFIED_CHINESE,
    Locale.ENGLISH,
    Locale.TRADITIONAL_CHINESE,
    Locale.JAPANESE,
    Locale.KOREAN,
    Locale.FRENCH,
    Locale.GERMAN,
    Locale.ITALIAN,
    Locale.forLanguageTag("es-ES"),
)

// 带国旗 emoji 的语言名，Locale.displayName 拿不到 emoji
fun translationLanguageName(locale: Locale): String = when (locale) {
    Locale.SIMPLIFIED_CHINESE -> "🇨🇳 简体中文"
    Locale.ENGLISH -> "🇺🇸 English"
    Locale.TRADITIONAL_CHINESE -> "🇨🇳 繁體中文"
    Locale.JAPANESE -> "🇯🇵 日本語"
    Locale.KOREAN -> "🇰🇷 한국어"
    Locale.FRENCH -> "🇫🇷 Français"
    Locale.GERMAN -> "🇩🇪 Deutsch"
    Locale.ITALIAN -> "🇮🇹 Italiano"
    Locale.forLanguageTag("es-ES") -> "🇪🇸 Español"
    else -> locale.getDisplayLanguage(Locale.getDefault())
}
