package com.yujian.minis.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.minis.R
import com.yujian.minis.i18n.uppercaseForDisplay
import com.yujian.minis.ui.theme.Spacing
import com.yujian.minis.ui.theme.Radius

/**
 * Shared primitives for settings pages. Grouped-card layout (iOS inset-grouped style).
 *
 * Structure:
 *   SettingsScaffold(title, actions?) {
 *     SettingsSection(header?, footer?) { SettingsRow/SwitchRow/ValueRow(...) ... }
 *     SettingsSection(...) { ... }
 *   }
 */

// ─── Scaffold ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    // [T-android-settings-ui-md3] #11 onBack is nullable so an EDIT screen can
    // suppress the back arrow and use an explicit Cancel/Save action pair instead
    // (a back arrow + a "Cancel" action that both pop the screen is redundant and
    // semantically muddy). Non-edit screens keep passing a non-null onBack and get
    // the usual back arrow — unchanged.
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    // [T-android-modeldetail-savecancel-ios-parity] Optional custom
    // navigation slot — e.g. a leading Cancel text action on modal-style
    // edit screens. When null, the slot falls back to the back arrow iff
    // onBack is set, so every existing caller renders unchanged.
    navigation: @Composable (() -> Unit)? = null,
    // [T-android-modeldetail-savecancel-ios-parity] Center the title
    // (CenterAlignedTopAppBar) for iOS-modal-style edit screens. Default
    // keeps the start-aligned TopAppBar.
    centerTitle: Boolean = false,
    floatingActionButton: @Composable (() -> Unit)? = null,
    scrollable: Boolean = true,
    // [T-android-storage-usage-cache] Optional bottom action bar (e.g. a
    // selection mode's actions). Null keeps every existing caller unchanged.
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            val titleSlot: @Composable () -> Unit = {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val navigationSlot: @Composable () -> Unit = {
                when {
                    navigation != null -> navigation()
                    onBack != null -> IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            }
            if (centerTitle) {
                CenterAlignedTopAppBar(
                    title = titleSlot,
                    navigationIcon = navigationSlot,
                    actions = { actions?.invoke() },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            } else {
                TopAppBar(
                    title = titleSlot,
                    navigationIcon = navigationSlot,
                    actions = { actions?.invoke() },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
        floatingActionButton = { floatingActionButton?.invoke() },
        bottomBar = { bottomBar?.invoke() },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // T183: imePadding() shrinks the scroll container by the IME's
        // height while the keyboard is up, giving Modifier.bringIntoView()
        // (used by `bringIntoViewOnFocus`) a meaningful "above the
        // keyboard" rect to scroll a focused TextField into. Without it,
        // adjustResize + edge-to-edge leaves the scrollable column at
        // full height behind the IME and bringIntoView is a no-op.
        val baseMod = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
        Column(
            modifier = if (scrollable) baseMod.verticalScroll(rememberScrollState()) else baseMod,
            content = content,
        )
    }
}

// ─── Switch ────────────────────────────────────────────────────────────────────

/**
 * [T-android-switch-row-height] A [Switch] that does not inflate the row it
 * sits in. Use this instead of Material's `Switch` inside any settings row.
 *
 * ## The problem it solves
 *
 * Compose applies `LocalMinimumInteractiveComponentSize` (48dp) to Switch, so
 * although the control DRAWS at 32dp it MEASURES at 48. Inside a row whose
 * height is `heightIn(min = 56.dp)` + 12dp vertical padding, that produced:
 *
 *     with a switch:  48 + 12 + 12  = 72dp   (heightIn never applies)
 *     without one:    30 + 12 + 12  = 54dp   -> clamped to 56dp
 *
 * Measured on a Pixel 6: switch rows rendered 190px and plain rows 147px, a
 * visible 16dp step between neighbouring rows of the same card. It read as
 * "the Encryption section has odd spacing" when in fact every switch row in
 * the app was taller than every non-switch row.
 *
 * iOS has no equivalent problem — SwiftUI's List sizes all its rows alike and
 * a Toggle does not stretch one — so this was an Android-only artefact rather
 * than an intentional difference.
 *
 * ## Why waiving the minimum is safe here
 *
 * The 48dp floor exists so a small control is still easy to hit. In these rows
 * the WHOLE ROW is clickable and at least 56dp tall, and tapping anywhere on it
 * toggles the same state, so the effective touch target grows rather than
 * shrinks. Material's own guidance allows waiving the minimum exactly when a
 * control is embedded in a larger interactive surface.
 *
 * Do NOT use this for a standalone switch that is the only touch target.
 */
@Composable
fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.SwitchColors = SwitchDefaults.colors(),
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
        )
    }
}

// ─── Section ───────────────────────────────────────────────────────────────────

/**
 * The visual spec of a [SettingsSection]: every number that differs between the
 * settings screens that used to carry their own private copy of the section
 * layout. Grouping them here keeps [SettingsSection] a single implementation
 * while making each screen's geometry explicit and reviewable.
 *
 * @param topPadding gap above the whole section (also spaces it from the app bar).
 * @param cardCornerRadius corner radius of the rounded card behind [SettingsSection]'s rows.
 * @param cardHorizontalPadding horizontal inset of the card from the screen edge.
 * @param headerPadding padding around the uppercase header caption.
 * @param footerPadding padding around the optional footer caption.
 */
data class SettingsSectionSpec(
    val topPadding: Dp,
    val cardCornerRadius: Dp,
    val cardHorizontalPadding: Dp,
    val headerPadding: PaddingValues,
    val footerPadding: PaddingValues,
)

/**
 * The spec shared by every settings screen built on the standard
 * `SettingsRow` primitives: 24dp above the section, a 14dp card inset 16dp from
 * the edges, and captions aligned to the 32dp text column.
 */
val StandardSettingsSectionSpec = SettingsSectionSpec(
    topPadding = 24.dp,
    cardCornerRadius = Radius.ExtraLarge,
    cardHorizontalPadding = Spacing.Huge,
    headerPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = Spacing.Small),
    footerPadding = PaddingValues(
        start = 32.dp,
        end = 32.dp,
        top = Spacing.Small,
        bottom = Spacing.Tiny,
    ),
)

/**
 * The spec used by the top-level Settings screen, whose rows are hand-rolled
 * (`SettingsItem`, not `SettingsRow`) and so sit on a tighter 20dp / 12dp /
 * 20dp rhythm with captions inset to the 20dp / 16dp text column.
 */
val CompactSettingsSectionSpec = SettingsSectionSpec(
    topPadding = Spacing.Giant,
    cardCornerRadius = Radius.Large,
    cardHorizontalPadding = Spacing.Huge,
    headerPadding = PaddingValues(horizontal = Spacing.Huge, vertical = Spacing.ExtraSmall),
    footerPadding = PaddingValues(horizontal = Spacing.Giant, vertical = Spacing.ExtraSmall),
)

/**
 * A grouped section — optional small-caps header + rounded card + optional footer caption.
 * Children (SettingsRow / SettingsSwitchRow / …) appear inside the card; dividers auto-inset.
 *
 * [spec] carries the per-screen geometry; it defaults to [StandardSettingsSectionSpec],
 * which is what every screen using the shared row primitives wants. Pass
 * [CompactSettingsSectionSpec] only where the rows are not `SettingsRow`s.
 */
@Composable
fun SettingsSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    spec: SettingsSectionSpec = StandardSettingsSectionSpec,
    content: @Composable ColumnScope.() -> Unit,
) {
    // [T-android-settings-ui-md3] Section vertical rhythm normalized to the 4dp
    // grid (fix_android_settings_ui.md #13): 24dp between sections instead of the
    // off-grid 20dp. Applied as top padding so the first section under a TopAppBar
    // keeps a consistent gap too.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spec.topPadding),
    ) {
        if (header != null) {
            Text(
                text = header.uppercaseForDisplay(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                // [T-android-settings-ui-md3] #5 header→card gap = 8dp (was 6dp,
                // off-grid). Horizontal stays 32dp to align the header text with
                // the inset card's content.
                modifier = Modifier.padding(spec.headerPadding),
            )
        }
        // [T-android-settings-section-symmetry] The card carries NO vertical
        // padding of its own: rows already supply 12dp top AND bottom, so any
        // tail here makes the gap below the content read larger than the gap
        // above it. An unconditional 8dp bottom pad used to live on this
        // Column to stop a trailing divider sitting flush against the rounded
        // edge — but every section either suppresses its last divider
        // (showDivider = false, or an `index < size - 1` guard) or follows it
        // with more content, so nothing actually needed the clearance. The
        // asymmetry it cost was invisible in a tall multi-row card and glaring
        // in a single-row one ("Status / Enabled", "Voice Services", the
        // thinking-rules note).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spec.cardHorizontalPadding)
                .clip(RoundedCornerShape(spec.cardCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // [T-android-settings-ui-md3] #6 explanatory footer: 8dp below the
                // card, 4dp before the next section (the parent's 24dp top padding
                // already provides separation, so keep the footer's own bottom
                // tight at 4dp).
                modifier = Modifier.padding(spec.footerPadding),
                lineHeight = 16.sp,
            )
        }
    }
}

// ─── Row primitives ────────────────────────────────────────────────────────────

/**
 * Hairline separator between two rows inside a [SettingsSection] card.
 *
 * Rows carry their own leading icon (or none), so the line has to start past
 * it — pass [startInset] as 58dp when the row above has an icon and 14dp when
 * it does not. The trailing inset is fixed at 14dp.
 *
 * Distinct from `ui.components.SettingsRowDivider` (16dp symmetric inset, a
 * different colour) and from `ui.components.SectionDivider` (a full-bleed
 * section rule). Deliberately not merged with either: all three draw different
 * lines.
 */
@Composable
fun SettingsCardRowDivider(startInset: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startInset, end = Spacing.ExtraLarge)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/**
 * Generic row: left icon (optional colored circle) + title/subtitle + trailing slot + optional chevron.
 * Pass `showDivider = false` on the last row of a section.
 */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    showDivider: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
    // [T-android-settings-ui-md3] #8 single-line List Item is 56dp; a caller with
    // two-line content (e.g. the model list: name + id) passes 72dp for the MD3
    // double-line height. Default keeps every other row at the single-line 56dp.
    minHeight: Dp = 56.dp,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // [T-android-settings-ui-md3] #1/#8 fixed List Item height so every
                // toggle/value/nav row in a section is uniform (was content-driven
                // → 24/54/72dp mix). heightIn(min) not height() so an unexpectedly
                // tall row can still grow; the symmetric 12dp vertical padding (was
                // effectively asymmetric once the 0.5dp divider was added/removed)
                // is what made a no-subtitle last row read ~50px shorter.
                .heightIn(min = minHeight)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Large),
            // #10 keep the trailing control (Switch/value) vertically centered
            // against the title — already centered, kept explicit.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(iconColor, RoundedCornerShape(Radius.Small)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.ExtraLarge))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailing != null) {
                Spacer(Modifier.width(Spacing.Small))
                trailing()
            }

            if (showChevron) {
                Spacer(Modifier.width(Spacing.Tiny))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (showDivider) {
            val insetStart = if (icon != null) 58.dp else 14.dp
            SettingsCardRowDivider(startInset = insetStart)
        }
    }
}

/** Title + Switch row. */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconColor = iconColor,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        showChevron = false,
        showDivider = showDivider,
        trailing = {
            SettingsSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(),
            )
        },
    )
}

/** Title + right-aligned value text (tap opens picker/detail). */
@Composable
fun SettingsValueRow(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconColor = iconColor,
        onClick = onClick,
        showChevron = onClick != null,
        showDivider = showDivider,
        trailing = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** Single-choice row (tap to select; shows check on selected). Used for radio-style lists. */
@Composable
fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // [T-android-settings-ui-md3] #1 match SettingsRow's 56dp min so
                // choice/radio rows line up with toggle/value rows in mixed lists.
                .heightIn(min = 56.dp)
                .clickable(onClick = onSelect)
                .padding(horizontal = Spacing.Huge, vertical = Spacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Spacing.Large))
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.common_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showDivider) {
            SettingsCardRowDivider(startInset = Spacing.Huge)
        }
    }
}

/**
 * Container for non-row content (sliders, segmented pickers, custom composables)
 * that still wants the grouped-card background.
 */
@Composable
fun SettingsCardBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = Spacing.Huge, vertical = Spacing.Large)
            .fillMaxWidth(),
        content = content,
    )
}
