package com.yujian.minis.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─── Design tokens: spacing + corner radius ────────────────────────────────────
//
// WHY A SEPARATE FILE. Theme.kt owns the Material 3 ColorScheme / Typography /
// Shapes and is being edited alongside this change; ChatColors.kt owns the
// chat-specific Color palette published through LocalChatPalette. Neither is a
// natural home for layout metrics, and both are off-limits for this pass, so
// the metrics live here rather than being folded into a file that does not
// describe them.
//
// WHY `object` CONSTANTS AND NOT A CompositionLocal. Tokens here are plain
// fixed dp. Spacing in this codebase does NOT track the user's font scale:
// MinisTheme's `fontScale` parameter feeds `scaledTypography()` only, i.e. it
// scales `sp` (TextStyle), never `dp`. Grepping the tree for a dp value derived
// from a scale factor turns up exactly one site, a runtime pixel conversion in
// MinisTextKitGesture (`14.dp * 2 + 1.dp` via `with(density)`), which is not a
// theme metric. So a token has nothing to react to, and an object is the
// simpler, allocation-free choice. A `LocalSpacing` would only be required if
// spacing ever became density- or font-scale-aware; if that ever happens, the
// migration is confined to this file plus the call sites, since the values are
// already named.
//
// TIER SELECTION IS STATISTICAL, NOT AESTHETIC. Counts come from scanning all
// 534 main-source files for `.dp` literals with comments and string literals
// excluded (2825 occurrences). The eight tiers below are exactly the eight most
// frequent values, covering 66.4% of all spacing literals project-wide and
// 73.5% within ui/settings. Anything outside the top eight (18, 22, 30, 36…)
// stays a literal on purpose: a token used once or twice is indirection without
// a payoff, and inventing names for the long tail is what makes a token system
// decay. Note the deliberate absence of odd values like 13.dp and off-grid
// 5.dp — they are real but rare (≤12 occurrences each).
//
// VALUES ARE FROZEN. Each token is a byte-for-byte stand-in for the literal it
// replaces. Changing a number here is a visual regression, not a refactor.

// Spacing scale, ascending. Named by t-shirt size, the convention already used
// by Material's Shapes.
object Spacing {
    /** 4dp — hairline gaps: icon↔label inset, divider-to-edge nudges. */
    val Tiny: Dp = 4.dp

    /** 6dp — tight inner gaps between closely related elements. */
    val ExtraSmall: Dp = 6.dp

    /** 8dp — the workhorse intra-component gap (list item internal spacing). */
    val Small: Dp = 8.dp

    /** 10dp — between a control and its own sub-label. */
    val Medium: Dp = 10.dp

    /** 12dp — standard inner padding for cards and rows. */
    val Large: Dp = 12.dp

    /** 14dp — row horizontal inset, aligned to the 30dp icon + 14dp gap. */
    val ExtraLarge: Dp = 14.dp

    /** 16dp — screen gutter: the standard page horizontal margin. */
    val Huge: Dp = 16.dp

    /** 20dp — outer block separation where a section needs more air. */
    val Giant: Dp = 20.dp
}

// Corner radii, ascending. Covers 70.3% of the 229 `.dp` corner arguments in
// the tree. These are the Dp-taking RoundedCornerShape sites only; the 37
// `RoundedCornerShape(50)` call sites are Compose's PERCENT overload (a circle),
// not a dp measurement, and are deliberately not touched.
object Radius {
    /** 8dp — small chips and icon plates. */
    val Small: Dp = 8.dp

    /** 10dp — compact controls. */
    val Medium: Dp = 10.dp

    /** 12dp — cards and list containers (matches Shapes.small). */
    val Large: Dp = 12.dp

    /** 14dp — the grouped settings card, the app's signature inset radius. */
    val ExtraLarge: Dp = 14.dp
}
