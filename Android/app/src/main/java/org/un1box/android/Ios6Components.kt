package org.un1box.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/*
 * iOS 6's controls, under the names the screens already call.
 *
 * A declaration in this package outranks a star import, the same rule LocalizedText.kt uses
 * for Text. That means every `Button`, `Switch`, `Slider`, `AlertDialog`, `OutlinedTextField`,
 * menu and progress bar in the app is an iOS 6 control without a screen changing its layout —
 * the screens keep saying what they mean, and this file decides how a 2012 iPhone drew it.
 */

/** True while a control is being composed inside an iOS 6 alert sheet. */
private val LocalIosAlertButton = compositionLocalOf { false }

// ---------------------------------------------------------------- buttons

/**
 * A lacquered iOS 6 button: blue with a white gloss and a dark hairline, red when the caller
 * asked for the error colour, silver when disabled. Inside an alert it becomes the alert's
 * flat, full-width button instead.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val inAlert = LocalIosAlertButton.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    if (inAlert) {
        Box(modifier.fillMaxWidth().clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides Color.White, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { content() }
            }
        }
        return
    }
    val scheme = MaterialTheme.colorScheme
    val destructive = colors.containerColor == scheme.error
    val top = when { !enabled -> Color(0xFFCFCFD4); destructive -> Ios6.DestructiveTop; else -> Ios6.ButtonTop }
    val bottom = when { !enabled -> Color(0xFF9A9AA0); destructive -> Ios6.DestructiveBottom; else -> Ios6.ButtonBottom }
    val edge = when { !enabled -> Color(0xFF8E8E93); destructive -> Color(0xFF70140E); else -> Ios6.ButtonEdge }
    Box(modifier
        .then(if (enabled) Modifier.shadow(2.dp, shape) else Modifier)
        .ios6Lacquer(shape, top, bottom, edge, gloss = if (pressed) 0.35f else 1f)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
        .padding(contentPadding), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) colors.contentColor else colors.disabledContentColor,
            LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { content() }
        }
    }
}

/** A plain iOS 6 text button: blue bold words, no chrome, dimmed while pressed. */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val inAlert = LocalIosAlertButton.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    if (inAlert) {
        Box(modifier.fillMaxWidth().clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides Color.White, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { content() }
            }
        }
        return
    }
    Box(modifier.clip(RoundedCornerShape(8.dp))
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (pressed) 0.55f else 1f)) { content() }
        }
    }
}

/** The silver iOS 6 button, for a second action beside a blue one. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val top = if (enabled) Ios6.GrayButtonTop else Color(0xFFE6E6EA)
    val bottom = if (enabled) Ios6.GrayButtonBottom else Color(0xFFC9C9CE)
    Box(modifier.then(if (enabled) Modifier.shadow(1.5.dp, shape) else Modifier)
        .ios6Lacquer(shape, top, bottom, Ios6.GrayButtonEdge, gloss = if (pressed) 0.3f else 0.9f)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
        .padding(contentPadding), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(
            // The silver lacquer is light in both themes, so its label is always the dark ink.
            LocalContentColor provides if (enabled) Ios6.Ink else Ios6.Ink.copy(alpha = 0.5f),
            LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { content() }
        }
    }
}

/** A bare icon target, sized like a bar button's touch area. */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
        .background(if (pressed) Color.Black.copy(alpha = 0.06f) else Color.Transparent)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) colors.contentColor else colors.disabledContentColor) {
            content()
        }
    }
}

// ---------------------------------------------------------------- selection controls

/** The iOS 6 switch, under Material's name. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    Ios6Switch(checked, onCheckedChange, modifier, enabled)
}

/** The iOS 6 radio mark, under Material's name. */
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    Ios6Radio(selected, onClick, modifier, enabled)
}

/** The iOS 6 slider, under Material's name. */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    Ios6Slider(value, onValueChange, modifier, enabled, valueRange, onValueChangeFinished)
}

// ---------------------------------------------------------------- progress

/** An indeterminate iOS 6 progress bar. */
@Composable
fun LinearProgressIndicator(modifier: Modifier = Modifier) {
    Ios6ProgressBar(null, modifier)
}

/** A determinate iOS 6 progress bar. */
@Composable
fun LinearProgressIndicator(progress: () -> Float, modifier: Modifier = Modifier) {
    Ios6ProgressBar(progress(), modifier)
}

/** The iOS spinner, or a determinate blue ring, under Material's name. */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.dp,
) {
    val tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    Ios6Spinner(modifier.defaultMinSize(24.dp, 24.dp), tint)
}

/** A determinate blue ring. */
@Composable
fun CircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.dp,
) {
    val tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    val fraction = progress().coerceIn(0f, 1f)
    val dark = Ios6.isDark()
    Canvas(modifier.defaultMinSize(24.dp, 24.dp)) {
        val stroke = strokeWidth.toPx().coerceAtLeast(2f)
        val diameter = size.minDimension - stroke
        val topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        drawCircle(if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f), radius = diameter / 2f,
            center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawArc(tint, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
            topLeft = topLeft, size = androidx.compose.ui.geometry.Size(diameter, diameter),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------- alert sheets

/**
 * The iOS 6 alert: a blue-grey lacquered sheet, white title and message, and the actions as
 * full-width lacquered buttons along the bottom, split by hairlines exactly as UIAlertView did.
 * The buttons the caller passes keep their behaviour and their test tags.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    /** A third action; iOS 6 stacks three or more actions vertically. */
    neutralButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().then(modifier).background(Color.Black.copy(alpha = 0.42f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismissRequest() },
            contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(min = 260.dp, max = 300.dp)
                .ios6Lacquer(RoundedCornerShape(12.dp), Ios6.AlertTop, Ios6.AlertBottom, Color(0xFF0E141C), gloss = 0.9f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                // Long messages scroll inside the sheet; the buttons stay pinned below them.
                Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(16.dp))
                icon?.let {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) { it() }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                title?.let {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides Color.White,
                            LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)) { it() }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                text?.let {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                        CompositionLocalProvider(LocalContentColor provides Color.White.copy(alpha = 0.96f),
                            LocalTextStyle provides MaterialTheme.typography.bodySmall.copy(color = Color.White)) { it() }
                    }
                }
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.5f)))
                val dismiss = dismissButton
                val neutral = neutralButton
                if (neutral == null && dismiss != null) {
                    // Two actions sit side by side, dismiss on the left, as UIAlertView drew them.
                    // IntrinsicSize.Min keeps the hairline at the row's height: a fillMaxHeight
                    // divider inside a wrap-content Row otherwise stretches it to the whole screen.
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        AlertButtonCell(Modifier.weight(1f).fillMaxHeight()) { dismiss() }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.5f)))
                        AlertButtonCell(Modifier.weight(1f).fillMaxHeight()) { confirmButton() }
                    }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        AlertButtonCell(Modifier.fillMaxWidth()) { confirmButton() }
                        if (neutral != null) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.5f)))
                            AlertButtonCell(Modifier.fillMaxWidth()) { neutral() }
                        }
                        if (dismiss != null) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.5f)))
                            AlertButtonCell(Modifier.fillMaxWidth()) { dismiss() }
                        }
                    }
                }
            }
        }
    }
}

/** One lacquered button cell of an alert sheet. */
@Composable
private fun AlertButtonCell(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).ios6Lacquer(RoundedCornerShape(0.dp), Ios6.AlertButtonTop, Ios6.AlertButtonBottom,
        Color(0xFF16283F), gloss = 0.7f), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalIosAlertButton provides true) { content() }
    }
}

// ---------------------------------------------------------------- popup menus

/** An iOS 6 popover menu: a white lacquered list with blue-tinted glyphs and hairlines. */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    containerColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val dark = Ios6.isDark()
    val fill = if (containerColor == Color.Unspecified) Ios6.cell(dark) else containerColor
    val density = LocalDensity.current
    Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, with(density) { 46.dp.roundToPx() }),
        onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Column(modifier.widthIn(min = 220.dp, max = 330.dp)
            .verticalScroll(rememberScrollState())
            .ios6Lacquer(shape, fill, if (dark) Color(0xFF1A1A1C) else Color(0xFFF1F1F4), Ios6.separator(dark), gloss = 0.35f)
            .padding(vertical = 4.dp), content = content)
    }
}

/** One row of an iOS 6 menu. */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp)
            .alpha(if (enabled) 1f else 0.4f), verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.let {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { it() }
                }
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) { text() }
            }
            trailingIcon?.let {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { it() }
            }
        }
        Ios6Separator()
    }
}

// ---------------------------------------------------------------- chips

/** An iOS 6 filter pill: silver when off, blue lacquer when on. */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    val dark = Ios6.isDark()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(modifier.heightIn(min = 28.dp)
        .then(if (selected) Modifier.ios6Lacquer(shape, Ios6.ButtonTop, Ios6.ButtonBottom, Ios6.ButtonEdge, gloss = if (pressed) 0.3f else 1f)
        else Modifier.ios6Lacquer(shape, if (dark) Color(0xFF3A3A3E) else Color(0xFFF6F6F8),
            if (dark) Color(0xFF232326) else Color(0xFFD2D2D7), Ios6.GrayButtonEdge, gloss = 0.6f))
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            LocalTextStyle provides MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) {
            leadingIcon?.let { Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { it() }; Spacer(Modifier.width(5.dp)) }
            label()
            trailingIcon?.let { Spacer(Modifier.width(5.dp)); it() }
        }
    }
}

/** The same pill, for something that only opens a menu. */
@Composable
fun AssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    FilterChip(selected = false, onClick = onClick, label = label, modifier = modifier, enabled = enabled,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, shape = shape)
}

/** The one-pixel rule between iOS 6 rows. */
@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = Ios6.separator(Ios6.isDark())) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

// ---------------------------------------------------------------- text fields

/**
 * An iOS 6 text field: a small bold label, then a white inset box. The two overloads match
 * the two ways a screen can hold its text (a String, or a TextFieldValue with a selection).
 */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
) {
    Ios6Field(modifier, label) { field ->
        Ios6TextFieldCore(
            value = value,
            onValueChange = onValueChange,
            modifier = field,
            enabled = enabled && !readOnly,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle)
    }
}

/** The same field, holding a TextFieldValue so its selection can be set (Rename opens selected). */
@Composable
fun OutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
) {
    Ios6Field(modifier, label) { field ->
        Ios6TextFieldCore(
            value = value,
            onValueChange = onValueChange,
            modifier = field,
            enabled = enabled && !readOnly,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle)
    }
}

/** The small bold caption a field wears, and the field under it. */
@Composable
private fun Ios6Field(modifier: Modifier, label: (@Composable () -> Unit)?, field: @Composable (Modifier) -> Unit) {
    Column(modifier.fillMaxWidth()) {
        label?.let {
            Box(Modifier.padding(start = 2.dp, bottom = 4.dp)) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { it() }
            }
        }
        field(Modifier.fillMaxWidth())
    }
}
