package com.tulipskun.aixodia.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The dark scheme is the console's palette, copied from the one place the web
// defines it (`:root` in styles.css). Two products, one set of readings: a
// neutral grey scale and a single blue accent, with green for up, amber for
// waiting and red for a refusal. Nothing here is a mood of its own.
//
// The greys are a reading order rather than decoration: the page is nearly
// black, a surface sits a step above it, and each step up is a surface the
// reader is meant to notice. There is no elevation anywhere — the console draws
// a hairline where two darks meet instead of a shadow, and so does this.
private val Page = Color(0xFF060607)
private val Surface1 = Color(0xFF0D0D0F)
private val Surface2 = Color(0xFF141417)
private val Surface3 = Color(0xFF1D1D21)
/** The hairline that keeps two darks meeting each other from reading as one. */
private val Line = Color(0x17FFFFFF)
/** The same line, said louder, for a control's own edge. */
private val LineStrong = Color(0x2BFFFFFF)
private val Ink = Color(0xFFF2F2F3)
private val Muted = Color(0xFFA3A3AB)
private val Dim = Color(0xFF6F6F78)

/** The accent: icons, the owner's own words, the one button worth hitting. */
private val Accent = Color(0xFF7FA6D3)
private val Signal = Color(0xFF4FBF8B)
private val Warn = Color(0xFFD3B27F)
private val Danger = Color(0xFFE0636E)

private val Blue900 = Color(0xFF060607)
private val Blue200 = Color(0xFFF2F2F3)
private val Mint200 = Color(0xFF9FFFE0)
private val Sky300 = Color(0xFFB8D8FF)
private val Sky900 = Color(0xFF102A43)
private val Sky800 = Color(0xFF284B70)
private val Sky200 = Color(0xFFD7EBFF)
private val Amber300 = Warn
private val Amber900 = Color(0xFF432C00)
private val Amber800 = Color(0xFF5F4100)
private val Amber200 = Color(0xFFFFE1A3)
private val Paper = Color(0xFFF5FBF8)
private val PaperContainer = Color(0xFFE9EFEC)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Page,
    // The bubble a message sits in: a step above the page, never a colour of
    // its own. The console draws the owner's words on the louder of two greys.
    primaryContainer = Surface3,
    onPrimaryContainer = Ink,
    secondary = Muted,
    onSecondary = Page,
    secondaryContainer = Surface3,
    onSecondaryContainer = Ink,
    tertiary = Warn,
    onTertiary = Page,
    tertiaryContainer = Surface2,
    onTertiaryContainer = Ink,
    error = Danger,
    onError = Page,
    errorContainer = Surface2,
    onErrorContainer = Danger,
    background = Page,
    onBackground = Ink,
    surface = Page,
    onSurface = Ink,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceTint = Accent,
    inverseSurface = Ink,
    inverseOnSurface = Page,
    outline = Dim,
    outlineVariant = Line,
    surfaceBright = Surface3,
    surfaceDim = Page,
    surfaceContainerLowest = Page,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Mint200,
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A6360),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E4),
    onSecondaryContainer = Color(0xFF06201E),
    tertiary = Color(0xFF7B5800),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF261A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Paper,
    onBackground = Color(0xFF171D1C),
    surface = Paper,
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceTint = Color(0xFF006B58),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFECF2EF),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = Color(0xFFE3E9E7),
    surfaceContainerHighest = Color(0xFFDDE4E1),
)

/**
 * The colours that are not Material roles.
 *
 * A status dot is not `primary` and a send button is not `secondary`: the dot
 * has to be green/amber/red in a way a user recognises before reading, and send
 * is the one control meant to be hit without reading, so it wears the accent the
 * console's own send button wears. They live here, next to the scheme, instead
 * of as raw hex inside two composables: one place to change them, and no second
 * palette growing in the UI layer.
 */
@Immutable
data class AixodiaColors(
    val send: Color,
    val online: Color,
    val connecting: Color,
    val offline: Color,
    /** The hairline a bordered surface is drawn with, since the app has no shadows. */
    val line: Color,
    val lineStrong: Color,
)

private val DarkExtras = AixodiaColors(
    send = Accent,
    online = Signal,
    connecting = Warn,
    offline = Danger,
    line = Line,
    lineStrong = LineStrong,
)

private val LightExtras = AixodiaColors(
    send = Color(0xFF1F5FD6),
    online = Color(0xFF12833F),
    connecting = Color(0xFF8A5A00),
    offline = Color(0xFFB3261E),
    line = Color(0x14000000),
    lineStrong = Color(0x26000000),
)

private val LocalAixodiaColors = staticCompositionLocalOf { DarkExtras }

/** The non-role colours of the current theme. */
val MaterialTheme.aixodia: AixodiaColors
    @Composable get() = LocalAixodiaColors.current

/* Four sizes, no more: 20 / 16 / 13 / 11. The console is read at the same sizes
   on a phone, so a thread here and a terminal there are the same text at the
   same weight. Nothing functional goes below 11. */
private val Display = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
private val Title = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
private val Body = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp)
private val Label = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp)

private val AppTypography = Typography(
    displaySmall = Display,
    headlineSmall = Display,
    titleLarge = Title,
    titleMedium = Title,
    titleSmall = Body.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Body,
    bodyMedium = Body,
    bodySmall = Body,
    labelLarge = Body,
    labelMedium = Label,
    labelSmall = Label,
)

/* The console's radius is 12 and its gaps are 4 / 8 / 12 / 16 / 24. These are the
   same steps in dp, so a card here has the corners of a card there. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** The console's spacing steps, so no screen has a reason to invent a number. */
object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
}

/**
 * The app theme. Dark, with the corner radius that the app's own shapes are
 * built from. Nothing here is remote: the look is part of the app.
 */
@Composable
fun AIxodiaTheme(
    dark: Boolean = true,
    cornerRadius: Int = 12,
    primary: String = "",
    content: @Composable () -> Unit,
) {
    val base = if (dark) DarkColors else LightColors
    val accent = parseHexColor(primary)
    val scheme = if (accent == null) base else base.copy(primary = accent, surfaceTint = accent)
    val radius = cornerRadius.coerceIn(0, 32)
    val shapes = AppShapes.copy(
        small = RoundedCornerShape((radius * 0.66f).toInt().dp),
        medium = RoundedCornerShape(radius.dp),
        large = RoundedCornerShape((radius * 1.33f).toInt().dp),
        extraLarge = RoundedCornerShape((radius * 2).toInt().dp),
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = shapes,
    ) {
        CompositionLocalProvider(
            LocalAixodiaColors provides if (dark) DarkExtras else LightExtras,
            content = content,
        )
    }
}

/** Parses "#RRGGBB" (or "#AARRGGBB"), returning null when it is not a colour. */
fun parseHexColor(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val long = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) Color(0xFF000000L or long) else Color(long)
}
