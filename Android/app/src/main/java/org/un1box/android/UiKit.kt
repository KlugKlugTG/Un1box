package org.un1box.android

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's look: iOS 6.
 *
 * [AuroraBackdrop] lays the pinstriped page once per screen, [GlassSurface] is the white
 * lacquered table every panel and list is cut from, and the rest of this file is the small
 * parts — section bars, badges, the tab bar — so no screen has to invent its own.
 */

/** The screen's background: iOS 6's pinstriped grouped-table paper. */
@Composable
internal fun AuroraBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Ios6Page(modifier, content)
}

/**
 * A lacquered iOS 6 panel: white paper with a hairline border, a gloss across its top and,
 * when asked, a shadow that lifts it off the page. [strong] is for the selected row, which
 * iOS marks with a bluer paper rather than a different shape.
 */
@Composable
internal fun GlassSurface(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium,
                          raised: Boolean = false, strong: Boolean = false,
                          content: @Composable BoxScope.() -> Unit) {
    val dark = Ios6.isDark()
    val top = when {
        dark && strong -> Color(0xFF2E4463)
        dark -> Color(0xFF2A2A2E)
        strong -> Color(0xFFEAF2FC)
        else -> Color(0xFFFFFFFF)
    }
    val bottom = when {
        dark && strong -> Color(0xFF20304A)
        dark -> Color(0xFF1B1B1E)
        strong -> Color(0xFFD7E5F7)
        else -> Color(0xFFF2F2F5)
    }
    Box(modifier
        .then(if (raised) Modifier.shadow(3.dp, shape) else Modifier)
        .ios6Lacquer(shape, top, bottom, if (dark) Color(0xFF0B0B0C) else Color(0xFF8E8E93), gloss = if (dark) 0.25f else 0.55f),
        content = content)
}

/** A bar button: the small lacquered chip iOS puts in a navigation bar. */
@Composable
internal fun GlassIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                             tag: String? = null, tint: Color = Color.White, enabled: Boolean = true) {
    Ios6BarButton(icon = icon, onClick = onClick, modifier = modifier, enabled = enabled, tag = tag)
}

/** The session state as a small lacquered badge you can read at arm's length. */
@Composable
internal fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    val dark = Ios6.isDark()
    Row(modifier.heightIn(min = 24.dp)
        .ios6Lacquer(CircleShape, if (dark) Color(0xFF3A3A3E) else Color(0xFFF7F7F9), if (dark) Color(0xFF232326) else Color(0xFFD5D5DA),
            if (dark) Color(0xFF0B0B0C) else Color(0xFF9A9AA0), gloss = 0.6f)
        .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(statusLabel(status), style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold, color = if (dark) Color.White else Ios6.Ink), maxLines = 1)
    }
}

@Composable
internal fun statusColor(status: String) = when (status) {
    "running" -> LtColors.Success
    "starting", "stopping" -> LtColors.Warning
    "paused" -> MaterialTheme.colorScheme.onSurfaceVariant
    "failed" -> LtColors.Danger
    else -> MaterialTheme.colorScheme.outline
}

/** A live number with a caption, small enough to sit under the device. */
@Composable
internal fun StatChip(caption: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val dark = Ios6.isDark()
    Row(modifier.ios6Lacquer(CircleShape, if (dark) Color(0xCC2A2A2E) else Color(0xE6FFFFFF),
        if (dark) Color(0xCC1B1B1E) else Color(0xE6E9E9EE), if (dark) Color(0xFF0B0B0C) else Color(0xFF9A9AA0), gloss = 0.5f)
        .padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(tint, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
    }
}

/** A titled row of chips. */
@Composable
internal fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

/** The silver bar above a group of settings, as iOS 6 headings groups of cells. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, top: Dp = 24.dp) {
    Ios6GroupHeader(text, modifier.padding(start = 2.dp, end = 2.dp, top = top, bottom = 6.dp))
}

/** The primary action: the blue iOS 6 button with its gloss. */
@Composable
internal fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                            icon: ImageVector? = null, enabled: Boolean = true, tag: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.then(if (enabled) Modifier.shadow(2.dp, shape) else Modifier)
        .ios6Lacquer(shape, if (enabled) Ios6.ButtonTop else Color(0xFFCFCFD4), if (enabled) Ios6.ButtonBottom else Color(0xFF9A9AA0),
            if (enabled) Ios6.ButtonEdge else Color(0xFF8E8E93), gloss = if (pressed) 0.3f else 1f)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = Color.White); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
    }
}

/** The four destinations, as iOS 6's black lacquered tab bar. */
@Composable
internal fun LtNavBar(current: Screen, onNavigate: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Screen.HOME to Ios6TabItem("Device", LtIcons.Phone),
        Screen.STORE to Ios6TabItem("Store", LtIcons.Bag),
        Screen.JAILBREAK to Ios6TabItem("Jailbreak", LtIcons.Puzzle),
        Screen.SETTINGS to Ios6TabItem("Settings", Icons.Filled.Settings))
    val tags = mapOf(Screen.STORE to "open_store", Screen.JAILBREAK to "open_jailbreak",
        Screen.SETTINGS to "open_settings")
    val selected = items.indexOfFirst { it.first == current }.coerceAtLeast(0)
    Ios6TabBar(items.map { it.second }, selected, { index -> onNavigate(items[index].first) },
        modifier, tags.mapKeys { (screen, _) -> items.indexOfFirst { it.first == screen } })
}

/** Dims slightly while held: iOS 6 highlights a control rather than shrinking it. */
@Composable
internal fun Modifier.pressFeedback(source: InteractionSource, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    val dim by animateFloatAsState(if (pressed && enabled) 0.72f else 1f, tween(90), label = "press")
    return graphicsLayer { alpha = dim }
}
