package com.docscan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Values mirror replica/design/tokens.json; every role pairing there passes WCAG AA in both modes.
// The indigo accent is a placeholder until replica-brand.
private object Palette {
    val Neutral0 = Color(0xFFFFFFFF)
    val Neutral50 = Color(0xFFF8F9FC)
    val Neutral100 = Color(0xFFEEF0F6)
    val Neutral200 = Color(0xFFE3E5EE)
    val Neutral300 = Color(0xFFC6C8D2)
    val Neutral400 = Color(0xFF9091A0)
    val Neutral500 = Color(0xFF767786)
    val Neutral700 = Color(0xFF45464F)
    val Neutral800 = Color(0xFF2C2E36)
    val Neutral900 = Color(0xFF1B1C22)
    val Neutral950 = Color(0xFF121318)
    val Accent100 = Color(0xFFDEE0FF)
    val Accent200 = Color(0xFFB9C3FF)
    val Accent600 = Color(0xFF3949AB)
    val Accent700 = Color(0xFF2A3BA3)
    val Accent900 = Color(0xFF00105C)
    val Danger300 = Color(0xFFFFB4AB)
    val Danger600 = Color(0xFFBA1A1A)
}

private val LightColors = with(Palette) {
    lightColorScheme(
        primary = Accent600,
        onPrimary = Neutral0,
        primaryContainer = Accent100,
        onPrimaryContainer = Accent900,
        secondary = Accent600,
        onSecondary = Neutral0,
        secondaryContainer = Accent100,
        onSecondaryContainer = Accent900,
        background = Neutral50,
        onBackground = Neutral900,
        surface = Neutral50,
        onSurface = Neutral900,
        surfaceVariant = Neutral200,
        onSurfaceVariant = Neutral700,
        surfaceContainerLowest = Neutral0,
        surfaceContainerLow = Neutral50,
        surfaceContainer = Neutral100,
        surfaceContainerHigh = Neutral100,
        surfaceContainerHighest = Neutral200,
        inverseSurface = Neutral800,
        inverseOnSurface = Neutral100,
        outline = Neutral500,
        outlineVariant = Neutral300,
        error = Danger600,
        onError = Neutral0,
    )
}

private val DarkColors = with(Palette) {
    darkColorScheme(
        primary = Accent200,
        onPrimary = Accent900,
        primaryContainer = Accent700,
        onPrimaryContainer = Accent100,
        secondary = Accent200,
        onSecondary = Accent900,
        secondaryContainer = Accent700,
        onSecondaryContainer = Accent100,
        background = Neutral950,
        onBackground = Neutral100,
        surface = Neutral950,
        onSurface = Neutral100,
        surfaceVariant = Neutral800,
        onSurfaceVariant = Neutral300,
        surfaceContainerLowest = Neutral950,
        surfaceContainerLow = Neutral900,
        surfaceContainer = Neutral900,
        surfaceContainerHigh = Neutral800,
        surfaceContainerHighest = Neutral800,
        inverseSurface = Neutral100,
        inverseOnSurface = Neutral900,
        outline = Neutral400,
        outlineVariant = Neutral700,
        error = Danger300,
        onError = Neutral950,
    )
}

/** Colours that must not follow the theme: paper stays white so dark ink is visible in dark mode. */
object FixedColors {
    val paper = Color(0xFFFFFFFF)
}

object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

object Sizes {
    val listThumbWidth = 56.dp
    val listThumbHeight = 72.dp
    val pageGridCell = 140.dp
    val emptyIcon = 72.dp
    val hairline = 1.dp
    val signatureThumbWidth = 160.dp
    val signatureThumbHeight = 64.dp
    val cropHandle = 48.dp
    val signaturePadAspect = 2.5f
}

@Composable
fun DocScanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
