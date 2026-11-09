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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
 * The app's look: a dark room with three coloured lights in it, everything you touch made of
 * frosted glass. [AuroraBackdrop] paints the room once per screen, [GlassSurface] is the one
 * material every panel, sheet and dock is cut from, and the rest of this file is the small
 * parts (pills, chips, the bar at the bottom) so no screen has to invent its own.
 *
 * Glass is a fill plus a hairline: the top edge is brighter than the bottom one, which is
 * what makes a flat rectangle read as a pane catching the light.
 */

/** One bloom of light: a soft radial wash with no hard edge anywhere. */
@Composable
private fun Modifier.bloom(color: Color, atX: Float, atY: Float, radius: Float): Modifier =
    drawBehind {
        drawRect(Brush.radialGradient(listOf(color, Color.Transparent),
            center = Offset(size.width * atX, size.height * atY), radius = size.minDimension * radius))
    }

/** The screen's background: a vertical wash and three aurora blooms behind everything else. */
@Composable
internal fun AuroraBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val violet = LtColors.Violet.copy(alpha = if (dark) 0.17f else 0.13f)
    val teal = LtColors.Accent.copy(alpha = if (dark) 0.13f else 0.16f)
    val pink = LtColors.Pink.copy(alpha = if (dark) 0.10f else 0.09f)
    val wash = Brush.verticalGradient(listOf(scheme.background, scheme.surfaceContainerLowest))
    Box(modifier.fillMaxSize()
        .drawBehind { drawRect(wash) }
        .bloom(violet, 0.90f, 0.02f, 1.05f)
        .bloom(teal, 0.05f, 0.95f, 1.10f)
        .bloom(pink, 0.12f, 0.10f, 0.70f),
        content = content)
}

/** The hairline around a pane: bright where the light is, nearly gone at the far corner. */
@Composable
private fun glassEdge(): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return Brush.linearGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f))
        else listOf(Color.White.copy(alpha = 0.95f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)))
}

/** Frosted glass. [raised] adds a drop shadow for the few things that float (dock, sheets). */
@Composable
internal fun GlassSurface(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.large,
                          raised: Boolean = false, strong: Boolean = false,
                          content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val fill = if (strong) scheme.surfaceContainerHigh.copy(alpha = if (dark) 0.92f else 0.96f)
        else scheme.surfaceContainer.copy(alpha = if (dark) 0.68f else 0.82f)
    Box(modifier.then(if (raised) Modifier.shadow(18.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f),
        spotColor = Color.Black.copy(alpha = 0.5f)) else Modifier)
        .clip(shape).background(fill).border(1.dp, glassEdge(), shape), content = content)
}

/** A round glass button, for the top bar and anything else that sits on the backdrop. */
@Composable
internal fun GlassIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                             tag: String? = null, tint: Color = MaterialTheme.colorScheme.onSurface, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.size(44.dp).pressFeedback(interaction, enabled).clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
        .border(1.dp, glassEdge(), CircleShape)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(21.dp), tint = tint.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

/** The session state as a pill you can read at arm's length. */
@Composable
internal fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Row(modifier.clip(CircleShape).background(color.copy(alpha = 0.14f))
        .border(1.dp, color.copy(alpha = 0.35f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(statusLabel(status), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
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
    Row(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
        .border(1.dp, glassEdge(), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(tint, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** A titled row of chips. */
@Composable
internal fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

/** The label above a group of settings: quiet, spaced, never shouted in capitals (the string is
    translated as written, so it must reach [Text] unchanged). */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, top: Dp = 24.dp) {
    Text(text, modifier.padding(start = 30.dp, end = 16.dp, top = top, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

/** The primary action: filled with the app's gradient, so it is the one thing that glows. */
@Composable
internal fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                            icon: ImageVector? = null, enabled: Boolean = true, tag: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    Row(modifier.pressFeedback(interaction, enabled).clip(CircleShape)
        .background(Brush.linearGradient(LtGradients.mixed)).clickable(interactionSource = interaction, indication = null,
            enabled = enabled, onClick = onClick).then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = scheme.onPrimary); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
    }
}

/** The three destinations, as one pane of glass floating over the bottom of the screen. */
@Composable
internal fun LtNavBar(current: Screen, onNavigate: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple(Screen.HOME, LtIcons.Phone, "Device"),
        Triple(Screen.STORE, LtIcons.Bag, "Store"),
        Triple(Screen.JAILBREAK, LtIcons.Puzzle, "Jailbreak"),
        Triple(Screen.SETTINGS, Icons.Filled.Settings, "Settings"))
    val tags = mapOf(Screen.STORE to "open_store", Screen.JAILBREAK to "open_jailbreak",
        Screen.SETTINGS to "open_settings")
    // Callers already inset their page against the system bars, so this only leaves a margin.
    GlassSurface(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp), raised = true) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((screen, icon, label) in items) {
                val selected = current == screen
                val interaction = remember { MutableInteractionSource() }
                Column(Modifier.weight(1f).pressFeedback(interaction).clip(RoundedCornerShape(22.dp))
                    .then(if (selected) Modifier.background(Brush.linearGradient(LtGradients.mixed)) else Modifier)
                    .clickable(interactionSource = interaction, indication = null) { if (!selected) onNavigate(screen) }
                    .then(tags[screen]?.let { Modifier.testTag(it) } ?: Modifier)
                    .semantics { contentDescription = label; role = Role.Tab }.padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(icon, null, Modifier.size(22.dp), tint = tint)
                    Spacer(Modifier.height(3.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                }
            }
        }
    }
}

/** Shrink slightly while held: the cheapest way to make a flat panel feel like a button. */
@Composable
internal fun Modifier.pressFeedback(source: InteractionSource, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.955f else 1f, tween(110), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
