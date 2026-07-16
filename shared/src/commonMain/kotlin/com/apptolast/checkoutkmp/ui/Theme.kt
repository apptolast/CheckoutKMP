package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand palette lifted straight from the app icon (the violet→blue→teal payment card), so the UI and
 * the launcher icon read as one product. Defined once here; screens only reference Material roles.
 */
private val BrandViolet = Color(0xFF7C4DFF)
private val BrandBlue = Color(0xFF4C6FFF)
private val BrandTeal = Color(0xFF00C2A8)
private val ApprovalGreen = Color(0xFF00C853)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001258),
    secondary = Color(0xFF00A392),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA6F2E4),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = BrandViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF21005E),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF002784),
    primaryContainer = Color(0xFF2B4BB8),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF8AD8C8),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Color(0xFFA6F2E4),
    tertiary = Color(0xFFCFBCFF),
    onTertiary = Color(0xFF38146E),
    tertiaryContainer = Color(0xFF4E2FB0),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * Semantic colors Material3 has no slot for. [brandGradient] is the icon's diagonal sweep, reused for
 * the checkout header; the success roles style the approved/receipt state (green isn't an M3 role).
 */
@Immutable
data class CheckoutExtraColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val brandGradient: List<Color>,
)

private val LightExtraColors = CheckoutExtraColors(
    success = Color(0xFF007E33),
    onSuccess = Color.White,
    successContainer = Color(0xFFB7F4C6),
    onSuccessContainer = Color(0xFF00210E),
    brandGradient = listOf(BrandViolet, BrandBlue, BrandTeal),
)

private val DarkExtraColors = CheckoutExtraColors(
    success = Color(0xFF6EDD87),
    onSuccess = Color(0xFF003914),
    successContainer = Color(0xFF005322),
    onSuccessContainer = Color(0xFFB7F4C6),
    brandGradient = listOf(BrandViolet, BrandBlue, BrandTeal),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtraColors }

/** Accessor for the app's extra semantic colors, mirroring how `MaterialTheme.colorScheme` is used. */
val extraColors: CheckoutExtraColors
    @Composable @ReadOnlyComposable get() = LocalExtraColors.current

/**
 * App theme: a brand-consistent Material3 color scheme (it deliberately does NOT use dynamic color, so
 * the palette always matches the icon) plus the [CheckoutExtraColors]. Follows the system dark mode.
 */
@Composable
fun CheckoutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
