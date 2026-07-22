package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.R

val base = Typography()
val Typography = Typography()

fun Typography.withFontFamily(fontFamily: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily),
    displayLargeEmphasized = displayLargeEmphasized.copy(fontFamily = fontFamily),
    displayMediumEmphasized = displayMediumEmphasized.copy(fontFamily = fontFamily),
    displaySmallEmphasized = displaySmallEmphasized.copy(fontFamily = fontFamily),
    headlineLargeEmphasized = headlineLargeEmphasized.copy(fontFamily = fontFamily),
    headlineMediumEmphasized = headlineMediumEmphasized.copy(fontFamily = fontFamily),
    headlineSmallEmphasized = headlineSmallEmphasized.copy(fontFamily = fontFamily),
    titleLargeEmphasized = titleLargeEmphasized.copy(fontFamily = fontFamily),
    titleMediumEmphasized = titleMediumEmphasized.copy(fontFamily = fontFamily),
    titleSmallEmphasized = titleSmallEmphasized.copy(fontFamily = fontFamily),
    bodyLargeEmphasized = bodyLargeEmphasized.copy(fontFamily = fontFamily),
    bodyMediumEmphasized = bodyMediumEmphasized.copy(fontFamily = fontFamily),
    bodySmallEmphasized = bodySmallEmphasized.copy(fontFamily = fontFamily),
    labelLargeEmphasized = labelLargeEmphasized.copy(fontFamily = fontFamily),
    labelMediumEmphasized = labelMediumEmphasized.copy(fontFamily = fontFamily),
    labelSmallEmphasized = labelSmallEmphasized.copy(fontFamily = fontFamily),
)

// Set of Material typography styles to start with
//val Typography = Typography(
//    displayLargeEmphasized = base.displayLargeEmphasized.copy(
//        fontFamily = GoogleSansFlex.Display.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    displayMediumEmphasized = base.displayMediumEmphasized.copy(
//        fontFamily = GoogleSansFlex.Display.Emphasized.Medium,
//        fontWeight = FontWeight.Bold
//    ),
//    displaySmallEmphasized = base.displaySmallEmphasized.copy(
//        fontFamily = GoogleSansFlex.Display.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    headlineLargeEmphasized = base.headlineLargeEmphasized.copy(
//        fontFamily = GoogleSansFlex.Headline.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    headlineMediumEmphasized = base.headlineMediumEmphasized.copy(
//        fontFamily = GoogleSansFlex.Headline.Emphasized.Medium,
//        fontWeight = FontWeight.Bold
//    ),
//    headlineSmallEmphasized = base.headlineSmallEmphasized.copy(
//        fontFamily = GoogleSansFlex.Headline.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    titleLargeEmphasized = base.titleLargeEmphasized.copy(
//        fontFamily = GoogleSansFlex.Title.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    titleMediumEmphasized = base.titleMediumEmphasized.copy(
//        fontFamily = GoogleSansFlex.Title.Emphasized.Medium,
//        fontWeight = FontWeight.Bold
//    ),
//    titleSmallEmphasized = base.titleSmallEmphasized.copy(
//        fontFamily = GoogleSansFlex.Title.Emphasized.Small,
//        fontWeight = FontWeight.Bold
//    ),
//    bodyLargeEmphasized = base.bodyLargeEmphasized.copy(
//        fontFamily = GoogleSansFlex.Body.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    bodyMediumEmphasized = base.bodyMediumEmphasized.copy(
//        fontFamily = GoogleSansFlex.Body.Emphasized.Medium,
//        fontWeight = FontWeight.Bold
//    ),
//    bodySmallEmphasized = base.bodySmallEmphasized.copy(
//        fontFamily = GoogleSansFlex.Body.Emphasized.Small,
//        fontWeight = FontWeight.Bold
//    ),
//    labelLargeEmphasized = base.labelLargeEmphasized.copy(
//        fontFamily = GoogleSansFlex.Label.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    labelMediumEmphasized = base.labelMediumEmphasized.copy(
//        fontFamily = GoogleSansFlex.Label.Emphasized.Medium,
//        fontWeight = FontWeight.Bold
//    ),
//    labelSmallEmphasized = base.labelSmallEmphasized.copy(
//        fontFamily = GoogleSansFlex.Label.Emphasized.Small,
//        fontWeight = FontWeight.Bold
//    ),
//)

@OptIn(ExperimentalTextApi::class)
val JetbrainsMono = FontFamily(
    Font(
        resId = R.font.jetbrains_mono,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Normal.weight),
        )
    )
)
