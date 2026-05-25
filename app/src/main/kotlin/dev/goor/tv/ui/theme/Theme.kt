package dev.goor.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// GoorTV is a dark-only brand (it's a TV/player app), so we ship one carefully
// tuned dark scheme instead of the stock `darkColorScheme()` defaults — those
// defaults are Google's lavender-tinted baseline, which fought the black/white
// wordmark and left every interactive element a flat grey. This scheme makes
// Signal Amber the single accent and steps the ink surfaces explicitly.
private val GoorDarkColors = darkColorScheme(
    primary = GoorAmber,
    onPrimary = OnAmber,
    primaryContainer = AmberContainer,
    onPrimaryContainer = OnAmberContainer,
    inversePrimary = GoorAmberDim,

    secondary = WarmNeutral,
    onSecondary = OnWarmNeutral,
    secondaryContainer = WarmNeutralCont,
    onSecondaryContainer = OnWarmNeutralCont,

    tertiary = GoorAmberBright,
    onTertiary = OnAmber,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,

    background = Ink,
    onBackground = InkOn,
    surface = Ink,
    onSurface = InkOn,
    surfaceVariant = InkVariant,
    onSurfaceVariant = InkOnVariant,

    surfaceContainerLowest = InkLowest,
    surfaceContainerLow = InkLow,
    surfaceContainer = InkContainer,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = InkHighest,
    surfaceBright = InkHighest,
    surfaceDim = Ink,

    // Keep elevation overlays neutral — without this, M3 tints every raised
    // surface with `primary` (amber), which would wash cards in gold.
    surfaceTint = Ink,

    outline = Outline,
    outlineVariant = OutlineVariant,

    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,

    inverseSurface = InkOn,
    inverseOnSurface = Ink,
    scrim = Scrim,
)

@Composable
fun GoorTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GoorDarkColors,
        typography = GoorTypography,
        shapes = GoorShapes,
        content = content,
    )
}
