package org.un1box.android

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * iOS 6, drawn from scratch.
 *
 * The last Apple interface made of materials rather than pixels: brushed blue chrome with a
 * gloss line across it, pinstriped paper under grouped tables, white lacquered cells, hairline
 * separators, switches that say ON and OFF, and a spinner made of twelve spokes. Everything
 * here is a piece of that vocabulary; the screens compose them.
 */

/** True while a control is composed on coloured chrome (a toolbar), where labels are white. */
internal val LocalIosOnChrome = compositionLocalOf { false }

// ---------------------------------------------------------------- textures and lacquer

/**
 * The pinstriped page a grouped table sits on: a pale blue-grey wash, fine vertical lines,
 * a little light at the top and a little shade at the bottom. Dark is the same paper at night.
 */
internal fun Modifier.ios6Pinstripe(dark: Boolean): Modifier = drawBehind {
    val top = if (dark) Ios6.PageDarkTop else Ios6.PageTop
    val bottom = if (dark) Ios6.PageDarkBottom else Ios6.PageBottom
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    val step = 3.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(Color.White.copy(alpha = if (dark) 0.012f else 0.16f), Offset(x, 0f), Offset(x, size.height), 1f)
        drawLine(Color.Black.copy(alpha = if (dark) 0.06f else 0.022f), Offset(x + step / 2f, 0f), Offset(x + step / 2f, size.height), 1f)
        x += step
    }
    // The page catches the room light at the top and falls away at the bottom. Both are drawn
    // over the whole page: a Brush's shader is laid out over the draw scope, not over the rect
    // it fills, so a short rect would cut its gradient off mid-colour and leave a hard line.
    drawRect(Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.02f else 0.10f),
        0.30f to Color.Transparent))
    drawRect(Brush.verticalGradient(
        0f to Color.Transparent,
        0.62f to Color.Transparent,
        1f to Color.Black.copy(alpha = if (dark) 0.18f else 0.05f)))
}

/**
 * The home wallpaper: the blue water iOS 6 shipped with, drawn as a deep blue wash, two soft
 * lights and the slow rings a drop leaves in water.
 */
internal fun Modifier.ios6Wallpaper(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B3F63), Color(0xFF0B1D31), Color(0xFF050D18))))
    drawRect(Brush.radialGradient(listOf(Color(0xFF3E7FB8).copy(alpha = 0.55f), Color.Transparent),
        center = Offset(size.width * 0.22f, size.height * 0.16f), radius = size.maxDimension * 0.75f))
    drawRect(Brush.radialGradient(listOf(Color(0xFF6C4FA0).copy(alpha = 0.30f), Color.Transparent),
        center = Offset(size.width * 0.88f, size.height * 0.78f), radius = size.maxDimension * 0.6f))
    // The rings, each fainter than the one before it.
    val centre = Offset(size.width * 0.5f, size.height * 0.52f)
    for (i in 0 until 7) {
        val radius = size.minDimension * (0.18f + i * 0.11f)
        drawCircle(Color.White.copy(alpha = 0.055f - i * 0.006f), radius = radius, center = centre, style = Stroke(width = 1.4f))
    }
    // Full-height stops, for the same reason as the pinstripe: a rect shorter than the scope
    // would end the gradient in mid-colour and leave a seam across the wallpaper.
    drawRect(Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.05f),
        0.28f to Color.Transparent))
    drawRect(Brush.verticalGradient(
        0f to Color.Transparent,
        0.60f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.28f)))
}

/**
 * Lacquer: a vertical gradient with a white gloss band across the top half and a hairline
 * highlight on the very top edge. This is what makes a flat rectangle look pressable in iOS 6.
 */
internal fun Modifier.ios6Lacquer(shape: Shape, top: Color, bottom: Color, edge: Color, gloss: Float = 1f): Modifier =
    this.clip(shape).drawBehind {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        if (gloss > 0f) {
            drawRect(Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.55f * gloss),
                0.48f to Color.White.copy(alpha = 0.16f * gloss),
                0.5f to Color.White.copy(alpha = 0.02f * gloss),
                0.52f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.10f * gloss)))
        }
        drawLine(Color.White.copy(alpha = 0.55f * gloss), Offset(0f, 1f), Offset(size.width, 1f), 1.2f)
        drawLine(Color.Black.copy(alpha = 0.18f * gloss), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
    }.border(1.dp, edge, shape)

/**
 * A lacquered table cell: white paper by day, dark glass at night, and a bluer paper when the
 * row is the selected one. Callers pass only the shape, so no screen has to know the theme.
 */
@Composable
internal fun Modifier.ios6CellLacquer(shape: Shape = RoundedCornerShape(10.dp), selected: Boolean = false,
                                     gloss: Float = 0.55f): Modifier {
    val dark = Ios6.isDark()
    val top = when {
        selected && dark -> Color(0xFF2E4463)
        selected -> Color(0xFFE7F1FD)
        dark -> Ios6.CellDark
        else -> Ios6.Cell
    }
    val bottom = when {
        selected && dark -> Color(0xFF20304A)
        selected -> Color(0xFFD2E4F8)
        dark -> Color(0xFF1B1B1E)
        else -> Color(0xFFF4F4F7)
    }
    return ios6Lacquer(shape, top, bottom, if (dark) Color(0xFF3A3A3E) else Color(0xFF8E8E93), gloss = gloss)
}

// ---------------------------------------------------------------- pages and chrome

/** Full-screen pinstriped page; every page composes inside one. */
@Composable
internal fun Ios6Page(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = Ios6.isDark()
    Box(modifier.fillMaxSize().ios6Pinstripe(dark)) {
        content()
        Ios6StatusStrip()
    }
}

/** The home screen's wallpaper instead of a table page. */
@Composable
internal fun Ios6WallpaperPage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().ios6Wallpaper()) {
        content()
        Ios6StatusStrip()
    }
}

/**
 * iOS 6's status bar is a black band with white glyphs above every page, even a light one. The
 * page content is inset below it; this only paints the band, and only when the system shows one.
 */
@Composable
private fun BoxScope.Ios6StatusStrip() {
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
        .windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black))
}

/**
 * The blue navigation bar: two-tone lacquer, a centred bold title, and room for one control on
 * either side. White on blue is what makes every iOS 5/6 page recognisable at a glance.
 */
@Composable
internal fun Ios6NavBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    start: (@Composable RowScope.() -> Unit)? = null,
    end: (@Composable RowScope.() -> Unit)? = null,
    dark: Boolean = Ios6.isDark(),
) {
    val top = if (dark) Ios6.NavTopDark else Ios6.NavTop
    val mid = if (dark) Ios6.NavMidDark else Ios6.NavMid
    val bottom = if (dark) Ios6.NavBottomDark else Ios6.NavBottom
    // Everything on the bar is white: glyphs, labels and whatever the caller puts in its slots.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Row(
            modifier.fillMaxWidth().height(if (subtitle != null) 62.dp else 44.dp)
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(top, mid, bottom)))
                    drawRect(Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.42f),
                        0.46f to Color.White.copy(alpha = 0.10f),
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.16f)))
                    drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, 1f), Offset(size.width, 1f), 1.2f)
                    drawLine(Color.Black.copy(alpha = 0.55f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
                }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // The slots size themselves; the title takes what is left, so nothing ever overlaps it.
            start?.invoke(this)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.82f)),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            end?.invoke(this)
        }
    }
}

/** A bar button as iOS 6 draws one: a small lacquered chip, white glyph and label. */
@Composable
internal fun Ios6BarButton(
    icon: ImageVector? = null,
    label: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null,
    destructive: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(6.dp)
    val top = when { !enabled -> Color(0xFF8B8B90); destructive -> Ios6.DestructiveTop; else -> Ios6.BarButtonTop }
    val bottom = when { !enabled -> Color(0xFF5F5F64); destructive -> Ios6.DestructiveBottom; else -> Ios6.BarButtonBottom }
    Row(
        modifier.heightIn(min = 30.dp).ios6Lacquer(shape, top, bottom, Color(0xFF163E6E), gloss = if (pressed) 0.3f else 1f)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics { contentDescription = label ?: "Button"; role = Role.Button }
            .padding(horizontal = if (label == null) 8.dp else 9.dp, vertical = 5.dp)
            .alpha(if (enabled) 1f else 0.65f),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(if (label == null) 20.dp else 16.dp), tint = Color.White)
        }
        if (label != null) {
            if (icon != null) Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                maxLines = 1)
        }
    }
}

/** The silver section bar above a group of rows, with white bold text and a soft shadow. */
@Composable
internal fun Ios6GroupHeader(text: String, modifier: Modifier = Modifier) {
    val dark = Ios6.isDark()
    Box(modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp).height(24.dp)
        .ios6Lacquer(RoundedCornerShape(6.dp), if (dark) Ios6.HeaderTopDark else Ios6.HeaderTop,
            if (dark) Ios6.HeaderBottomDark else Ios6.HeaderBottom, Color(0xFF5E6A7A), gloss = if (dark) 0.35f else 1f)
        .padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A hairline separator, inset where iOS starts it (under the text, not under the icon). */
@Composable
internal fun Ios6Separator(modifier: Modifier = Modifier, inset: Dp = 0.dp, dark: Boolean = Ios6.isDark()) {
    Box(modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(Ios6.separator(dark)))
}

// ---------------------------------------------------------------- bars and tables

/** One item of an iOS 5/6 tab bar. */
internal data class Ios6TabItem(val label: String, val icon: ImageVector)

/** The black lacquered tab bar: an icon and a caption per tab, the selected one lit. */
@Composable
internal fun Ios6TabBar(items: List<Ios6TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
                        tags: Map<Int, String> = emptyMap()) {
    Row(modifier.fillMaxWidth().height(50.dp)
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(Ios6.TabTop, Ios6.TabBottom)))
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.35f to Color.White.copy(alpha = 0.06f),
                0.36f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.25f)))
            drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, 1f), Offset(size.width, 1f), 1f)
            drawLine(Color.Black, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
        },
        verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selected
            val shape = RoundedCornerShape(7.dp)
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 3.dp, vertical = 4.dp)
                    .then(if (isSelected) Modifier.ios6Lacquer(shape, Ios6.TabSelectedTop, Ios6.TabSelectedBottom,
                        Color(0xFF0A0A0B), gloss = 0.6f) else Modifier)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!isSelected) onSelect(index) }
                    .then(tags[index]?.let { Modifier.testTag(it) } ?: Modifier)
                    .semantics { contentDescription = item.label; role = Role.Tab },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(item.icon, null, Modifier.size(21.dp), tint = if (isSelected) Color.White else Color(0xFFB4B4B8))
                Spacer(Modifier.height(2.dp))
                Text(item.label, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) Color.White else Color(0xFFB4B4B8)),
                    maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

// ---------------------------------------------------------------- controls

/** iOS 6 segmented control: a silver track with one blue lacquered selection sliding in it. */
@Composable
internal fun Ios6Segmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
                           compact: Boolean = false, tags: Map<Int, String> = emptyMap()) {
    val dark = Ios6.isDark()
    Row(modifier.fillMaxWidth().height(31.dp).ios6Lacquer(RoundedCornerShape(7.dp),
        if (dark) Color(0xFF3A3A3E) else Color(0xFFB9BCC2),
        if (dark) Color(0xFF232326) else Color(0xFF8E9299), Color(0xFF5F646C), gloss = 0.5f).padding(2.dp)) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(Modifier.weight(1f).fillMaxHeight()
                .then(if (isSelected) Modifier.ios6Lacquer(RoundedCornerShape(5.dp), Ios6.ButtonTop,
                    Ios6.ButtonBottom, Ios6.ButtonEdge, gloss = 1f) else Modifier)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(index) }
                .then(tags[index]?.let { Modifier.testTag(it) } ?: Modifier)
                .padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    style = (if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
                        .copy(color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold))
            }
        }
    }
}

/**
 * The iOS 6 switch: a lozenge with ON or OFF printed inside it and a silver knob that slides.
 */
@Composable
internal fun Ios6Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
                        enabled: Boolean = true, tag: String? = null) {
    val dark = Ios6.isDark()
    val offset by animateFloatAsState(if (checked) 20f else 0f, tween(160), label = "switch")
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.size(51.dp, 31.dp).clip(RoundedCornerShape(16.dp))
        .then(if (checked)
            Modifier.background(Brush.verticalGradient(listOf(Ios6.ButtonTop, Ios6.SwitchTrackOn, Ios6.ButtonBottom)))
        else Modifier.background(Brush.verticalGradient(listOf(if (dark) Color(0xFF3A3A3E) else Color(0xFFE8E8ED), Ios6.SwitchTrackOff))))
        .border(1.dp, if (checked) Color(0xFF15437D) else (if (dark) Color(0xFF0B0B0C) else Color(0xFF8E8E93)), RoundedCornerShape(16.dp))
        .toggleable(value = checked, interactionSource = interaction, indication = null,
            enabled = enabled && onCheckedChange != null, role = Role.Switch) { onCheckedChange?.invoke(it) }
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .alpha(if (enabled) 1f else 0.5f)) {
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.35f), 0.45f to Color.White.copy(alpha = 0.05f), 0.46f to Color.Transparent))
            drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, 1.5f), Offset(size.width, 1.5f), 1f)
        })
        Text(if (checked) "ON" else "OFF", Modifier.align(if (checked) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(start = if (checked) 7.dp else 0.dp, end = if (checked) 0.dp else 7.dp),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (checked) Color.White else (if (dark) Color(0xFF8E8E93) else Color(0xFF6E6E73))))
        Box(Modifier.offset(x = (2f + offset).dp, y = 2.dp).size(27.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Ios6.SwitchKnobTop, Ios6.SwitchKnobBottom)))
            .border(1.dp, Color(0xFF7C7C82), CircleShape)
            .drawBehind {
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), 0f, size.height * 0.6f))
                drawLine(Color.White.copy(alpha = 0.8f), Offset(size.width * 0.18f, 1f), Offset(size.width * 0.82f, 1f), 1f)
            })
    }
}

/** The round blue radio mark iOS 6 uses for a picked option in a list. */
@Composable
internal fun Ios6Radio(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true, tag: String? = null) {
    val dark = Ios6.isDark()
    Box(modifier.size(28.dp).selectable(selected = selected, interactionSource = remember { MutableInteractionSource() }, indication = null,
        enabled = enabled && onClick != null, role = Role.RadioButton) { onClick?.invoke() }.then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(if (dark) Color(0xFF3A3A3E) else Color(0xFFFDFDFE), if (dark) Color(0xFF1D1D20) else Color(0xFFD8D8DD))))
            .border(1.dp, if (dark) Color(0xFF0B0B0C) else Color(0xFF9A9AA0), CircleShape)) {
            if (selected) Box(Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Ios6.ButtonTop, Ios6.ButtonBottom)))
                .border(1.dp, Ios6.ButtonEdge, CircleShape)
                .drawBehind { drawCircle(Color.White.copy(alpha = 0.55f), radius = size.minDimension * 0.30f, center = Offset(size.width * 0.35f, size.height * 0.28f)) })
        }
    }
}

/**
 * The iOS 6 slider: a recessed track, a blue fill with a bright top edge, and a chrome thumb.
 * Drawn in one canvas so the thumb can travel the full width, with drag and tap on top.
 */
@Composable
internal fun Ios6Slider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                        valueRange: ClosedFloatingPointRange<Float> = 0f..1f, onValueChangeFinished: (() -> Unit)? = null) {
    val dark = Ios6.isDark()
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it != 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(32.dp).alpha(if (enabled) 1f else 0.5f)
        .pointerInput(enabled, valueRange) {
            if (!enabled) return@pointerInput
            fun pick(x: Float) {
                val f = (x / size.width).coerceIn(0f, 1f)
                onValueChange(valueRange.start + f * span)
            }
            detectTapGestures { pick(it.x); onValueChangeFinished?.invoke() }
        }
        .pointerInput(enabled, valueRange) {
            if (!enabled) return@pointerInput
            fun pick(x: Float) {
                val f = (x / size.width).coerceIn(0f, 1f)
                onValueChange(valueRange.start + f * span)
            }
            detectHorizontalDragGestures(
                onDragStart = { pick(it.x) },
                onDragEnd = { onValueChangeFinished?.invoke() },
                onDragCancel = { onValueChangeFinished?.invoke() }) { change, _ -> pick(change.position.x) }
        }) {
        val track = 9.dp.toPx()
        val cy = size.height / 2f
        val thumbRadius = 13.dp.toPx()
        val travel = (size.width - thumbRadius * 2f).coerceAtLeast(1f)
        val thumbX = thumbRadius + travel * fraction
        val trackRadius = CornerRadius(track / 2f)
        drawRoundRect(Brush.verticalGradient(listOf(if (dark) Color(0xFF101013) else Color(0xFFB5B8BE),
            if (dark) Color(0xFF2A2A2E) else Color(0xFFDDE0E5))),
            topLeft = Offset(0f, cy - track / 2f), size = Size(size.width, track), cornerRadius = trackRadius)
        drawRoundRect(Brush.verticalGradient(listOf(Ios6.ButtonTop, Ios6.ButtonMid, Ios6.ButtonBottom)),
            topLeft = Offset(0f, cy - track / 2f), size = Size((thumbX).coerceAtLeast(track), track), cornerRadius = trackRadius)
        drawLine(Color.White.copy(alpha = 0.55f), Offset(3f, cy - track / 2f + 1.5f), Offset((thumbX - 3f).coerceAtLeast(4f), cy - track / 2f + 1.5f), 1.2f)
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD9D9DE))), radius = thumbRadius, center = Offset(thumbX, cy))
        drawCircle(Color(0xFF6E6E73), radius = thumbRadius, center = Offset(thumbX, cy), style = Stroke(width = 1.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.75f), radius = thumbRadius * 0.55f, center = Offset(thumbX - thumbRadius * 0.18f, cy - thumbRadius * 0.22f))
    }
}

/**
 * The iOS 6 progress bar: a recessed track with a glossy blue fill that grows from the left.
 * A null [progress] draws moving candy stripes; a determinate one is a plain bar.
 */
@Composable
internal fun Ios6ProgressBar(progress: Float?, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val dark = Ios6.isDark()
    val transition = rememberInfiniteTransition(label = "progress")
    val shift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "shift")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2))
        .background(Brush.verticalGradient(listOf(if (dark) Color(0xFF101013) else Color(0xFFA8ACB3), if (dark) Color(0xFF2A2A2E) else Color(0xFFD7DAE0))))
        .border(1.dp, if (dark) Color(0xFF050506) else Color(0xFF7F838A), RoundedCornerShape(height / 2))
        .padding(1.dp)) {
        val fraction = progress?.coerceIn(0f, 1f)
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction ?: 1f).clip(RoundedCornerShape(height / 2))
            .background(Brush.verticalGradient(listOf(Ios6.ButtonTop, Ios6.ButtonMid, Ios6.ButtonBottom)))
            .drawBehind {
                drawLine(Color.White.copy(alpha = 0.65f), Offset(1f, 1.5f), Offset(size.width - 1f, 1.5f), 1.4f)
                if (fraction == null) {
                    // The candy stripes only move while the length is unknown.
                    val stripe = 12.dp.toPx()
                    var x = -stripe + shift * stripe * 2
                    while (x < size.width) {
                        drawLine(Color.White.copy(alpha = 0.18f), Offset(x, size.height), Offset(x + stripe, 0f), stripe / 2.2f)
                        x += stripe * 2
                    }
                }
            })
    }
}

/** The twelve-spoke iOS spinner: each spoke a little brighter than the one behind it. */
@Composable
internal fun Ios6Spinner(modifier: Modifier = Modifier, color: Color = Color.White, spokes: Int = 12) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val turn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val inner = radius * 0.30f
        val outer = radius * 0.92f
        for (i in 0 until spokes) {
            val angle = ((i.toFloat() / spokes) * 360f + turn) * PI.toFloat() / 180f
            val alpha = (0.14f + 0.86f * (i.toFloat() / spokes)).coerceAtMost(1f)
            drawLine(color.copy(alpha = alpha),
                Offset(center.x + inner * cos(angle), center.y + inner * sin(angle)),
                Offset(center.x + outer * cos(angle), center.y + outer * sin(angle)),
                strokeWidth = radius * 0.19f, cap = StrokeCap.Round)
        }
    }
}

/** The grey chevron that ends a tappable iOS table row. */
@Composable
internal fun Ios6Chevron(dark: Boolean = Ios6.isDark(), modifier: Modifier = Modifier) {
    Canvas(modifier.size(10.dp, 16.dp)) {
        val colour = if (dark) Color(0xFF5A5A60) else Color(0xFF9A9AA0)
        drawLine(colour, Offset(size.width * 0.2f, size.height * 0.08f), Offset(size.width * 0.78f, size.height * 0.5f), 2.2f, cap = StrokeCap.Round)
        drawLine(colour, Offset(size.width * 0.78f, size.height * 0.5f), Offset(size.width * 0.2f, size.height * 0.92f), 2.2f, cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- fields

/** A label in the small bold grey iOS puts above a field. */
@Composable
internal fun Ios6FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 2.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold))
}

/**
 * A text field as iOS 6 draws one: a white inset box with a hairline border and a soft inner
 * shade, the text in Helvetica's size, and the placeholder in grey.
 */
@Composable
internal fun Ios6TextFieldCore(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val dark = Ios6.isDark()
    val shape = RoundedCornerShape(9.dp)
    Row(modifier.fillMaxWidth().ios6Lacquer(shape,
        if (dark) Color(0xFF121214) else Color(0xFFFFFFFF),
        if (dark) Color(0xFF0B0B0C) else Color(0xFFF4F4F7),
        if (dark) Color(0xFF3A3A3E) else Color(0xFF8E9299), gloss = 0f)
        .padding(horizontal = 10.dp, vertical = 8.dp).alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.Top) {
        leadingIcon?.let { Box(Modifier.padding(end = 8.dp, top = 2.dp), contentAlignment = Alignment.Center) { it() } }
        Box(Modifier.weight(1f)) {
            if (value.text.isEmpty() && placeholder != null) {
                Box(Modifier.padding(top = 1.dp)) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { placeholder() }
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                minLines = minLines,
                cursorBrush = SolidColor(Ios6.Blue))
        }
        trailingIcon?.let { Box(Modifier.padding(start = 8.dp, top = 2.dp), contentAlignment = Alignment.Center) { it() } }
    }
}

/** The same inset field, holding a plain String (Compose keeps the caret for us). */
@Composable
internal fun Ios6TextFieldCore(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val dark = Ios6.isDark()
    val shape = RoundedCornerShape(9.dp)
    Row(modifier.fillMaxWidth().ios6Lacquer(shape,
        if (dark) Color(0xFF121214) else Color(0xFFFFFFFF),
        if (dark) Color(0xFF0B0B0C) else Color(0xFFF4F4F7),
        if (dark) Color(0xFF3A3A3E) else Color(0xFF8E9299), gloss = 0f)
        .padding(horizontal = 10.dp, vertical = 8.dp).alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.Top) {
        leadingIcon?.let { Box(Modifier.padding(end = 8.dp, top = 2.dp), contentAlignment = Alignment.Center) { it() } }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder != null) {
                Box(Modifier.padding(top = 1.dp)) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { placeholder() }
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                minLines = minLines,
                cursorBrush = SolidColor(Ios6.Blue))
        }
        trailingIcon?.let { Box(Modifier.padding(start = 8.dp, top = 2.dp), contentAlignment = Alignment.Center) { it() } }
    }
}

/** The store's search field, which takes the title's place in the bar. */
@Composable
internal fun Ios6SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSearch: (() -> Unit)? = null,
) {
    val dark = Ios6.isDark()
    val shape = RoundedCornerShape(9.dp)
    Row(modifier.heightIn(min = 34.dp).ios6Lacquer(shape,
        if (dark) Color(0xFF121214) else Color(0xFFFFFFFF),
        if (dark) Color(0xFF0B0B0C) else Color(0xFFF4F4F7),
        if (dark) Color(0xFF3A3A3E) else Color(0xFF8E9299), gloss = 0f)
        .padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Search, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                singleLine = true,
                cursorBrush = SolidColor(Ios6.Blue))
        }
        if (value.isNotEmpty()) Icon(Icons.Filled.Clear, "Clear", Modifier.size(16.dp).clickable { onValueChange("") },
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
