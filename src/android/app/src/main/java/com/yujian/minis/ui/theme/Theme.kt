package com.yujian.minis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Accent: iOS blue, desaturated. [T-android-accent-blue-parity]
//
// Was a teal (#2E8B8B / #4DD9D9) that predated iOS settling on blue. The hue
// now comes from iOS Assets.xcassets/AccentColor.colorset (sRGB components
// r0.212 g0.525 b0.933 -> #3686EE light, r0.329 g0.565 b0.894 -> #5490E4 dark),
// but iOS's saturation (84% / 73%) read as glaring on Android's darker
// surfaces, so SATURATION is dialled back ~30% with hue and lightness kept:
//   light  #3686EE  S84% L57%  ->  #528AD2  S59% L57%
//   dark   #5490E4  S73% L61%  ->  #6A94CE  S51% L61%
//
// Lightness is deliberately NOT raised, which is the other way to "lighten".
// It would have softened dark mode further but pushed light-mode contrast on
// white from 3.62 to 2.62 — below WCAG AA's 4.5 for text. Desaturating keeps
// dark mode at 5.93 (passing) and leaves light mode where it was.
//
// Names keep the `Teal` prefix only to avoid churning 90+ call sites; the
// value is the contract, not the name.
private val TealPrimary = Color(0xFF528AD2)
private val TealOnPrimary = Color(0xFFFFFFFF)
private val TealPrimaryContainer = Color(0xFFB2DFDB)
private val TealOnPrimaryContainer = Color(0xFF00332F)
private val TealSecondary = Color(0xFF4A6360)
private val TealOnSecondary = Color(0xFFFFFFFF)
private val TealSecondaryContainer = Color(0xFFCCE8E4)
private val TealOnSecondaryContainer = Color(0xFF05201D)
private val TealTertiary = Color(0xFF46617A)
private val TealOnTertiary = Color(0xFFFFFFFF)
private val TealTertiaryContainer = Color(0xFFCDE5FF)
private val TealOnTertiaryContainer = Color(0xFF001D32)
private val TealBackground = Color(0xFFF5FAFA)
private val TealOnBackground = Color(0xFF171D1C)
private val TealSurface = Color(0xFFF5FAFA)
private val TealOnSurface = Color(0xFF171D1C)
private val TealSurfaceVariant = Color(0xFFDAE5E2)
private val TealOnSurfaceVariant = Color(0xFF3F4947)
private val TealOutline = Color(0xFF6F7977)

private val TealDarkPrimary = Color(0xFF6A94CE)
private val TealDarkOnPrimary = Color(0xFF003737)
private val TealDarkPrimaryContainer = Color(0xFF1A6B6B)
private val TealDarkOnPrimaryContainer = Color(0xFFB2DFDB)
private val TealDarkSecondary = Color(0xFFB1CCC8)
private val TealDarkOnSecondary = Color(0xFF1C3532)
private val TealDarkSecondaryContainer = Color(0xFF334B48)
private val TealDarkOnSecondaryContainer = Color(0xFFCCE8E4)
private val TealDarkBackground = Color(0xFF0E1514)
private val TealDarkOnBackground = Color(0xFFDEE4E2)
private val TealDarkSurface = Color(0xFF0E1514)
private val TealDarkOnSurface = Color(0xFFDEE4E2)
private val TealDarkSurfaceVariant = Color(0xFF3F4947)
private val TealDarkOnSurfaceVariant = Color(0xFFBEC9C6)
private val TealDarkOutline = Color(0xFF899390)

// Neutral grouped-card surfaces (iOS-style system-grouped background).
// Override Material3's tonal `surfaceContainer*` so cards don't pick up the
// teal primary tint.
// Light: page = #F2F2F7 gray, card = white
// Dark:  page = #000, card = #1C1C1E
private val NeutralGroupedBg = Color(0xFFF2F2F7)
private val NeutralGroupedCard = Color(0xFFFFFFFF)
private val NeutralGroupedCardElevated = Color(0xFFF7F7FA)
private val NeutralOutline = Color(0xFFD1D1D6)

private val NeutralDarkGroupedBg = Color(0xFF000000)
private val NeutralDarkGroupedCard = Color(0xFF1C1C1E)
private val NeutralDarkGroupedCardElevated = Color(0xFF2C2C2E)
private val NeutralDarkOutline = Color(0xFF38383A)

// ---------------------------------------------------------------------------
// iOS system accents — theme-INVARIANT on purpose.
//
// These are the seven `UIColor.system*` values the Swift side reaches for
// directly (Color.blue, .green, .red, .orange, .yellow, .pink, .gray). Unlike
// the palette above they have no light/dark variant here: iOS ships one
// saturated accent per role and both appearances use the same hue, so these
// stay plain `val`s rather than becoming ChatPalette fields. Reading one from
// ChatColors would be wrong — that would silently re-tint them per appearance.
//
// They were previously re-typed as raw `Color(0x...)` at ~95 call sites across
// a dozen files (settings rows, session category chips, tool accents, status
// dots, terminal buttons). The values are the contract; the name is only a
// handle. Nothing here is derived — each is the byte-for-byte literal that
// stood at those call sites.
// ---------------------------------------------------------------------------
object IosAccents {
    val Blue = Color(0xFF007AFF)
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val Orange = Color(0xFFFF9500)
    val Yellow = Color(0xFFFFCC00)
    val Pink = Color(0xFFFF2D55)
    val Gray = Color(0xFF8E8E93)
}

// ---------------------------------------------------------------------------
// LLM-provider brand dots.
//
// One dot colour per ProviderType, used by the model picker, the entry picker
// and the agent-loop sheets so the same provider reads as the same colour
// everywhere. These are vendor brand hues, not iOS system colours, and they
// are deliberately NOT in IosAccents: OpenAI's green (#4CAF50) and Anthropic's
// purple are brand identities that happen to sit near system colours, and
// folding them in would invite someone to "unify" them later.
//
// Previously duplicated as two byte-identical `providerDotColor` functions
// (a private one in the picker sheet, a public one in the entry picker), each
// re-typing all eight literals.
// ---------------------------------------------------------------------------
object ProviderBrandColors {
    val Anthropic = Color(0xFFAB47BC)
    val Gemini = Color(0xFF42A5F5)
    val OpenAI = Color(0xFF4CAF50)
    val OpenRouter = Color(0xFF00BCD4)
    val Xai = Color(0xFFFF7043)
    val KimiCode = Color(0xFF5C6BC0)
    val GitHubCopilot = Color(0xFF6E5494)
}

// ---------------------------------------------------------------------------
// Session category chip hues.
//
// The 16 categories in ContentView.swift:1897-1916 do not map 1:1 onto
// colours — "code" and "travel" share one, "writing" and "education" share
// another — so these are named for the HUE they carry rather than the category
// that happens to use them, which is what lets both categories point at the
// same token without inventing a second name for the same value.
//
// Categories whose colour IS a system accent (creative/design → pink,
// chat → green, health → red, productivity → yellow, other → gray) keep using
// [IosAccents] directly and are deliberately absent here.
//
// Previously duplicated as two byte-identical `categoryStyle` functions in
// SessionListScreen and MoveToSessionSheet, each re-typing every literal.
// ---------------------------------------------------------------------------
object SessionCategoryColors {
    val Amber = Color(0xFFF09A37)
    val Azure = Color(0xFF3478F6)
    val Teal = Color(0xFF30B0C7)
    val Indigo = Color(0xFF5856D6)
    val Violet = Color(0xFF9B59B6)
    val Cyan = Color(0xFF00BCD4)
    val Mint = Color(0xFF00C7BE)
    val Bronze = Color(0xFF8B6914)
}

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,
    tertiary = TealTertiary,
    onTertiary = TealOnTertiary,
    tertiaryContainer = TealTertiaryContainer,
    onTertiaryContainer = TealOnTertiaryContainer,
    background = NeutralGroupedBg,
    onBackground = TealOnBackground,
    surface = NeutralGroupedBg,
    onSurface = TealOnSurface,
    surfaceVariant = NeutralGroupedCard,
    onSurfaceVariant = TealOnSurfaceVariant,
    surfaceContainerLowest = NeutralGroupedBg,
    surfaceContainerLow = NeutralGroupedCard,
    surfaceContainer = NeutralGroupedCard,
    surfaceContainerHigh = NeutralGroupedCardElevated,
    surfaceContainerHighest = NeutralGroupedCardElevated,
    outline = NeutralOutline,
    outlineVariant = NeutralOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = TealDarkPrimary,
    onPrimary = TealDarkOnPrimary,
    primaryContainer = TealDarkPrimaryContainer,
    onPrimaryContainer = TealDarkOnPrimaryContainer,
    secondary = TealDarkSecondary,
    onSecondary = TealDarkOnSecondary,
    secondaryContainer = TealDarkSecondaryContainer,
    onSecondaryContainer = TealDarkOnSecondaryContainer,
    background = NeutralDarkGroupedBg,
    onBackground = TealDarkOnBackground,
    surface = NeutralDarkGroupedBg,
    onSurface = TealDarkOnSurface,
    surfaceVariant = NeutralDarkGroupedCard,
    onSurfaceVariant = TealDarkOnSurfaceVariant,
    surfaceContainerLowest = NeutralDarkGroupedBg,
    surfaceContainerLow = NeutralDarkGroupedCard,
    surfaceContainer = NeutralDarkGroupedCard,
    surfaceContainerHigh = NeutralDarkGroupedCardElevated,
    surfaceContainerHighest = NeutralDarkGroupedCardElevated,
    outline = NeutralDarkOutline,
    outlineVariant = NeutralDarkOutline,
)

// App-wide FAB accent color (warm beige, matching iOS New Chat button).
// Reads from ChatPalette so it follows the in-app theme override (theme_mode pref),
// not android.isSystemInDarkTheme(), which only tracks the system setting.
@Composable
fun minisFabColor(): Color = LocalChatPalette.current.fabAccent

// App-wide shape system — larger corners for a modern, friendly feel
// DropdownMenu uses extraSmall, Dialog uses extraLarge, BottomSheet uses extraLarge
private val MinisShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),   // DropdownMenu, Tooltip, OutlinedTextField default
    small = RoundedCornerShape(12.dp),        // Chip, TextField
    medium = RoundedCornerShape(20.dp),       // Card, Snackbar
    large = RoundedCornerShape(24.dp),        // NavigationDrawer
    extraLarge = RoundedCornerShape(28.dp),   // Dialog, BottomSheet
)

@Composable
fun MinisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = scaledTypography(fontScale)
    val chatPalette = if (darkTheme) DarkChatPalette else LightChatPalette

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MinisShapes,
        typography = typography,
    ) {
        CompositionLocalProvider(LocalChatPalette provides chatPalette, content = content)
    }
}

private fun TextStyle.scale(factor: Float): TextStyle =
    if (factor == 1f) this else copy(fontSize = fontSize * factor)

private fun scaledTypography(factor: Float): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.scale(factor),
        displayMedium = base.displayMedium.scale(factor),
        displaySmall = base.displaySmall.scale(factor),
        headlineLarge = base.headlineLarge.scale(factor),
        headlineMedium = base.headlineMedium.scale(factor),
        headlineSmall = base.headlineSmall.scale(factor),
        titleLarge = base.titleLarge.scale(factor),
        titleMedium = base.titleMedium.scale(factor),
        titleSmall = base.titleSmall.scale(factor),
        bodyLarge = base.bodyLarge.scale(factor),
        bodyMedium = base.bodyMedium.scale(factor),
        bodySmall = base.bodySmall.scale(factor),
        labelLarge = base.labelLarge.scale(factor),
        labelMedium = base.labelMedium.scale(factor),
        labelSmall = base.labelSmall.scale(factor),
    )
}
