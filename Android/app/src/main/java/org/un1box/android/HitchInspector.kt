package org.un1box.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

private fun frameColor(ms: Float) = when {
    ms > 50f -> LtColors.Danger
    ms > 25f -> LtColors.Warning
    else -> LtColors.Accent
}

/**
 * The guest is paused; this shows the frames that led up to the button press. Drag across the
 * overview to pick a range, tap a bar in the detail chart to read one frame, then save the
 * range as a report with the engine's records for exactly that window.
 */
@Composable
internal fun HitchInspector(data: HitchData?, loadError: String?, onResume: () -> Unit, onSave: (Long, Long) -> Unit,
                            saving: Boolean, savedNotice: String?, onShare: (() -> Unit)?) {
    BackHandler(onBack = onResume)
    Ios6Page(Modifier.then(DialogTags).testTag("hitch_inspector")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Ios6NavBar("Frame inspector",
                subtitle = listOfNotNull(data?.appName?.takeIf { it.isNotEmpty() }, "Guest paused").joinToString(" · "),
                start = { Ios6BarButton(Icons.Filled.Close, "Close", onResume, tag = "hitch_close") },
                end = { Ios6BarButton(LtIcons.Play, "Resume", onResume, tag = "hitch_resume") })
            when {
                loadError != null -> Message("Could not read the frame history", loadError)
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                data.frames.size < 2 -> Message("No frames yet", "The guest has not drawn enough frames to inspect. Resume, play for a few seconds and try again.")
                else -> InspectorBody(data, onSave, saving, savedNotice, onShare)
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InspectorBody(data: HitchData, onSave: (Long, Long) -> Unit, saving: Boolean, savedNotice: String?, onShare: (() -> Unit)?) {
    val start = data.startNs
    val end = data.frames.last().publishedNs
    // Default to the worst frame of the last ten seconds, with a second either side.
    val initial = remember(data) {
        val recent = data.frames.filter { !it.idle && end - it.publishedNs < 10_000_000_000L }.ifEmpty { data.frames.filter { !it.idle } }.ifEmpty { data.frames }
        val worst = recent.maxBy { it.intervalMs }
        (worst.publishedNs - 1_000_000_000L).coerceAtLeast(start) to (worst.publishedNs + 1_000_000_000L).coerceAtMost(end)
    }
    var range by remember(data) { mutableStateOf(initial) }
    var picked by remember(data) { mutableStateOf<HitchFrame?>(data.framesIn(initial.first, initial.second).maxByOrNull { it.intervalMs }) }
    val selected = remember(range, data) { data.framesIn(range.first, range.second) }
    val stats = remember(selected) { RangeStats.of(selected) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionLabel("Last ${String.format(Locale.ROOT, "%.0f", (end - start) / 1e9)} s · drag to select a range, tap to jump")
        Overview(data, range, Modifier.fillMaxWidth().height(72.dp).testTag("hitch_overview")) { range = it; picked = data.framesIn(it.first, it.second).maxByOrNull { f -> f.intervalMs } }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(seconds(range.first - start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${String.format(Locale.ROOT, "%.2f", (range.second - range.first) / 1e9)} s selected", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(seconds(range.second - start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Detail(data, selected, picked, Modifier.fillMaxWidth().height(180.dp).testTag("hitch_detail")) { picked = it }
        if (data.detailed) Legend()
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Avg FPS", String.format(Locale.ROOT, "%.1f", stats.avgFps), Modifier.weight(1f))
            Stat("1% low", String.format(Locale.ROOT, "%.1f", stats.lowFps), Modifier.weight(1f))
            Stat("Worst", String.format(Locale.ROOT, "%.0f ms", stats.worstMs), Modifier.weight(1f))
            Stat("Hitches", "${stats.hitches}", Modifier.weight(1f), if (stats.hitches > 0) LtColors.Danger else null)
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("Frame times · ${stats.frames} frames")
        Histogram(stats)
        picked?.let { frame -> Spacer(Modifier.height(16.dp)); FrameCard(data, frame) }
        val slow = remember(range, data) { data.eventsIn(range.first, range.second).sortedByDescending { it.ms }.take(30) }
        if (data.detailed) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("Slow engine events in range")
            if (slow.isEmpty()) Text("None over 0.5 ms.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var all by remember(range) { mutableStateOf(false) }
            for (event in if (all) slow else slow.take(8)) EventRow(event, start)
            if (slow.size > 8) TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer" else "Show all ${slow.size}") }
        } else {
            Spacer(Modifier.height(16.dp))
            GlassSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp)) {
                    Icon(LtIcons.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Turn on Detailed frame timing in Settings › Diagnostics to see where each frame's time went: guest CPU, GL and lock waits. It costs a few percent of speed while on.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onSave(range.first, range.second) }, enabled = !saving, modifier = Modifier.testTag("hitch_save")) {
                Icon(LtIcons.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (saving) "Saving…" else "Save report")
            }
            if (onShare != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onShare) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share") }
            }
        }
        savedNotice?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(32.dp))
    }
}

private fun seconds(ns: Long) = String.format(Locale.ROOT, "%.1f s", ns / 1e9)

@Composable
private fun SectionLabel(text: String) = Text(text, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: Color? = null) {
    Column(modifier.ios6Lacquer(RoundedCornerShape(8.dp), Ios6.GrayButtonTop, Ios6.GrayButtonBottom, Ios6.GrayButtonEdge)
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Every frame of the window as a thin bar; the selection is the lit band. */
@Composable
private fun Overview(data: HitchData, range: Pair<Long, Long>, modifier: Modifier, onRange: (Pair<Long, Long>) -> Unit) {
    val start = data.startNs; val end = data.frames.last().publishedNs
    val span = (end - start).coerceAtLeast(1L)
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val band = MaterialTheme.colorScheme.primary
    Canvas(modifier
        .pointerInput(data) {
            fun at(x: Float) = start + (x / size.width * span).toLong().coerceIn(0L, span)
            var anchor = 0L
            detectDragGestures(onDragStart = { anchor = at(it.x) }) { change, _ ->
                val now = at(change.position.x)
                if (kotlin.math.abs(now - anchor) > 50_000_000L) onRange(minOf(anchor, now) to maxOf(anchor, now))
            }
        }
        .pointerInput(data) {
            detectTapGestures { offset ->
                val t = start + (offset.x / size.width * span).toLong()
                onRange((t - 1_000_000_000L).coerceAtLeast(start) to (t + 1_000_000_000L).coerceAtMost(end))
            }
        }) {
        drawRoundRect(surface, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        fun x(t: Long) = (t - start).toFloat() / span * size.width
        fun h(ms: Float) = (ms.coerceAtMost(100f) / 100f) * (size.height - 8f)
        for (f in data.frames) {
            if (f.idle) continue
            val color = frameColor(f.intervalMs)
            drawLine(color.copy(alpha = if (f.publishedNs in range.first..range.second) 1f else 0.45f),
                Offset(x(f.publishedNs), size.height - 4f), Offset(x(f.publishedNs), size.height - 4f - h(f.intervalMs)), 1.5f)
        }
        drawRect(band.copy(alpha = 0.16f), Offset(x(range.first), 0f), Size(x(range.second) - x(range.first), size.height))
        drawLine(band, Offset(x(range.first), 0f), Offset(x(range.first), size.height), 2.dp.toPx())
        drawLine(band, Offset(x(range.second), 0f), Offset(x(range.second), size.height), 2.dp.toPx())
    }
}

/**
 * The selected frames as bars sized by frame time (0–66 ms, guides at 16.7/33.3/50). With
 * detailed timing each bar is split into GL, lock waits, other guest work and the rest.
 */
@Composable
private fun Detail(data: HitchData, frames: List<HitchFrame>, picked: HitchFrame?, modifier: Modifier, onPick: (HitchFrame) -> Unit) {
    val guide = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface
    val breakdown = remember(frames) { frames.map { data.breakdownFor(it) } }
    val top = maxOf(40f, (frames.maxOfOrNull { it.intervalMs } ?: 0f) * 1.1f).coerceAtMost(250f)
    Canvas(modifier.pointerInput(frames) {
        detectTapGestures { offset ->
            if (frames.isNotEmpty()) onPick(frames[(offset.x / size.width * frames.size).toInt().coerceIn(0, frames.size - 1)])
        }
    }) {
        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.dp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT
            color = android.graphics.Color.argb(230, (label.red * 255).toInt(), (label.green * 255).toInt(), (label.blue * 255).toInt())
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }
        fun y(ms: Float) = size.height - ms.coerceAtMost(top) / top * size.height
        val guides = listOf(16.7f, 33.3f, 50f, 100f, 200f).filter { it < top }
        for (g in guides) drawLine(guide, Offset(0f, y(g)), Offset(size.width, y(g)), 1f)
        val canvas = drawContext.canvas.nativeCanvas
        fun labels() { for (g in guides) canvas.drawText(String.format(Locale.ROOT, "%.0f ms", g), size.width - 4f, y(g) - 4f, labelPaint) }
        if (frames.isEmpty()) { labels(); return@Canvas }
        val w = size.width / frames.size
        val gap = if (w > 4f) 1f else 0f
        frames.forEachIndexed { i, f ->
            val left = i * w
            val b = breakdown[i]
            if (b != null) {
                // Stack from the bottom: GL, lock waits, other guest work, then everything else.
                var base = size.height
                fun seg(ms: Float, color: Color) {
                    val h = ms.coerceAtLeast(0f) / top * size.height
                    val segTop = (base - h).coerceAtLeast(y(f.intervalMs))
                    if (base > segTop) drawRect(color, Offset(left, segTop), Size(w - gap, base - segTop))
                    base = segTop
                }
                seg(b.glMs, LtColors.OverlayGpu); seg(b.bqlMs, LtColors.Warning)
                seg((b.runMs - b.glMs).coerceAtLeast(0f), LtColors.OverlayCpu)
                seg(f.intervalMs, Color(0xFF5A6366))
            } else drawRect(frameColor(f.intervalMs), Offset(left, y(f.intervalMs)), Size(w - gap, size.height - y(f.intervalMs)))
            if (f == picked) drawRect(highlight, Offset(left, y(f.intervalMs) - 3.dp.toPx()), Size((w - gap).coerceAtLeast(2f), 2.dp.toPx()))
        }
        labels()
    }
}

@Composable
private fun Legend() {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((color, name) in listOf(LtColors.OverlayGpu to "GL", LtColors.Warning to "Lock wait", LtColors.OverlayCpu to "Guest CPU", Color(0xFF5A6366) to "Other")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(4.dp))
                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Histogram(stats: RangeStats) {
    val most = (stats.buckets.maxOrNull() ?: 0).coerceAtLeast(1)
    val colors = listOf(LtColors.Accent, LtColors.Accent, LtColors.Accent, LtColors.Warning, LtColors.Warning, LtColors.Danger, LtColors.Danger)
    Column(Modifier.testTag("hitch_histogram"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RangeStats.bucketLabels.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(stats.buckets[i].toFloat() / most).background(colors[i].copy(alpha = 0.85f), RoundedCornerShape(3.dp)))
                }
                Text("${stats.buckets[i]}", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

@Composable
private fun FrameCard(data: HitchData, frame: HitchFrame) {
    val b = data.breakdownFor(frame)
    GlassSurface(Modifier.fillMaxWidth().testTag("hitch_frame")) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(frameColor(frame.intervalMs), RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(8.dp))
                Text("Frame ${frame.sequence}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(String.format(Locale.ROOT, "%.1f ms", frame.intervalMs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = frameColor(frame.intervalMs))
            }
            Text("at ${seconds(frame.publishedNs - data.startNs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            @Composable fun line(name: String, value: String) = Row {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodySmall.merge(MonoStyle))
            }
            fun ms(v: Float) = String.format(Locale.ROOT, "%.2f ms", v)
            if (frame.presentedNs > 0) {
                line("Shown on screen after", ms((frame.presentedNs - frame.publishedNs) / 1e6f))
                line("Presenter work", ms(frame.workNs / 1e6f))
            } else line("Shown on screen", "skipped (a newer frame replaced it)")
            if (b != null) {
                line("Guest code (vCPU running)", ms(b.runMs))
                line("  of which GL calls", ms(b.glMs))
                line("  of which GL present", ms(b.glPresentMs))
                line("vCPU on a host core", ms(b.vcpuCpuMs))
                line("vCPU halted or outside", ms(b.outsideMs))
                line("Lock (BQL) waits", ms(b.bqlMs))
                line("Main loop CPU", ms(b.mainCpuMs))
                val hint = when {
                    b.runMs > frame.intervalMs * 0.8f && b.vcpuCpuMs < b.runMs * 0.75f -> "The vCPU was runnable but often off-CPU: the phone scheduled other work or slowed the core."
                    b.glMs > frame.intervalMs * 0.4f -> "GL calls took most of this frame: host driver or readback cost."
                    b.bqlMs > frame.intervalMs * 0.25f -> "The vCPU spent much of this frame waiting for the engine lock."
                    b.runMs > frame.intervalMs * 0.8f -> "The guest was busy for the whole frame: more guest work than usual."
                    else -> null
                }
                hint?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
            val overlapping = data.eventsIn(frame.publishedNs - (frame.intervalMs * 1e6).toLong(), frame.publishedNs).sortedByDescending { it.ms }.take(6)
            if (overlapping.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Slow events in this frame", style = MaterialTheme.typography.labelMedium)
                for (event in overlapping) EventRow(event, data.startNs)
            }
        }
    }
}

@Composable
private fun EventRow(event: SlowEvent, start: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(String.format(Locale.ROOT, "%6.2f ms", event.ms), Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall.merge(MonoStyle),
            color = if (event.ms > 8f) LtColors.Danger else MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f)) {
            Text(event.label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text("${event.kind.label} · ${seconds(event.startNs - start)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
