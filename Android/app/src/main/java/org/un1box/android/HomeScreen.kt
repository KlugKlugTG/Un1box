package org.un1box.android

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The default view: the selected device lit from behind, a top bar for switching devices and
 * seeing the session at a glance, the session controls in a glass dock, and the app's three
 * destinations along the bottom.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun HomeScreen(host: GuestHost, guestSurface: @Composable (Modifier) -> Unit) {
    var confirmStop by remember { mutableStateOf(false) }
    AuroraBackdrop(Modifier.semantics { testTagsAsResourceId = true }) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            HomeTopBar(host)
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                DeviceStage(host) { modifier -> GuestScreen(host, guestSurface, modifier, overlayScale = 0.85f, inDeviceView = true) }
            }
            SessionStats(host)
            AnimatedVisibility(host.active, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                SessionDock(host) { confirmStop = true }
            }
            LtNavBar(Screen.HOME, host::navigate, Modifier.padding(top = if (host.active) 0.dp else 14.dp))
        }
        // Floats over the device under the top bar, so a message never resizes the guest screen.
        ErrorBanner(host.error, host::dismissError, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 16.dp, end = 16.dp, top = 68.dp))
        // For automation (tools/perf): the raw session state as text.
        Text(host.status, Modifier.align(Alignment.TopStart).size(1.dp).testTag("session_status"), color = Color.Transparent, fontSize = 1.sp)
    }
    if (confirmStop) StopDialog(host) { confirmStop = false }
}

/** Device on the left, session state on the right, both made of the same glass. */
@Composable
private fun HomeTopBar(host: GuestHost) {
    Row(Modifier.fillMaxWidth().height(62.dp).padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val device = host.selected
        GlassSurface(Modifier.weight(1f, fill = false).clip(MaterialTheme.shapes.large).clickable { host.openDevices() }
            .testTag("device_selector"), shape = MaterialTheme.shapes.large) {
            Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(host.status)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(device?.name ?: "No device", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(device?.subtitle ?: statusLabel(host.status), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Filled.KeyboardArrowDown, "Devices", Modifier.padding(start = 6.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        StatusPill(host.status)
    }
}

/**
 * The device, life-size-ish, standing in its own pool of light: the bloom behind it pulses while
 * iOS runs, and a faint reflection sits under it so it reads as an object rather than a picture.
 */
@Composable
private fun DeviceStage(host: GuestHost, screen: @Composable (Modifier) -> Unit) {
    val glow = when (host.status) {
        "running" -> LtColors.Accent
        "starting", "stopping" -> LtColors.Warning
        "failed" -> LtColors.Danger
        else -> LtColors.Violet
    }
    // The pulse exists only while a session does: an idle home screen should not ask for frames, and
    // the value is read inside the draw lambda so a beat redraws the light instead of recomposing the
    // device (which holds the guest's surface).
    val beat: State<Float>? = if (host.active) {
        val pulse = rememberInfiniteTransition(label = "stage")
        pulse.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "beat")
    } else null
    Box(Modifier.fillMaxSize().drawBehind {
        val lit = beat?.value ?: 0.45f
        // A pool of light on the floor, and the bloom the device is standing in.
        drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.20f * lit), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.52f), radius = size.minDimension * 0.95f))
        drawOval(Brush.radialGradient(listOf(glow.copy(alpha = 0.13f * lit), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.94f), radius = size.width * 0.42f),
            topLeft = Offset(size.width * 0.16f, size.height * 0.88f), size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.10f))
    }, contentAlignment = Alignment.Center) {
        DeviceFrame(DeviceSkin.forBoard(host.selected?.board), enabled = host.running, button = host::button,
            screen = screen, modifier = Modifier.fillMaxSize(), haptics = host.settings.haptics)
    }
}

/** The live read-out under the device: how long it has been up and how fast it is going. */
@Composable
private fun SessionStats(host: GuestHost) {
    val clock = rememberElapsed(host.running)
    if (!host.running) return
    ChipRow(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState())) {
        if (clock.isNotEmpty()) StatChip("Session", clock, LtColors.Violet)
        if (host.perf.fps > 0) StatChip("FPS", "%.0f".format(host.perf.fps), LtColors.OverlayFps)
        if (host.perf.vcpuPercent > 0) StatChip("CPU", "%.0f%%".format(host.perf.vcpuPercent), LtColors.OverlayCpu)
    }
}

/**
 * Minutes and seconds since the guest started. Kept here rather than in the worker: it is a
 * wall-clock nicety, and a paused guest keeps its clock, exactly like a real one.
 */
@Composable
private fun rememberElapsed(active: Boolean): String {
    var started by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        started = if (active) System.currentTimeMillis() else 0L
        now = started
        while (active) { delay(1000); now = System.currentTimeMillis() }
    }
    if (!active || started <= 0L) return ""
    val seconds = ((now - started) / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** The device's power light: a solid core inside a soft halo, so it reads at a glance on glass. */
@Composable
internal fun StatusDot(status: String) {
    val color = statusColor(status)
    Box(Modifier.size(15.dp).drawBehind {
        drawCircle(color.copy(alpha = 0.22f), radius = size.minDimension / 2f)
        drawCircle(color, radius = size.minDimension * 0.29f)
    })
}

/**
 * The guest surface plus whatever belongs on top of it: the performance overlay and, when the
 * guest is not simply running, a state card (start, starting, paused, asleep, failed).
 */
@Composable
internal fun GuestScreen(host: GuestHost, guestSurface: @Composable (Modifier) -> Unit, modifier: Modifier,
                         overlayScale: Float, inDeviceView: Boolean) {
    Box(modifier.background(Color.Black).testTag("guest_screen")) {
        guestSurface(Modifier.fillMaxSize())
        val s = host.settings
        if (host.running && (!inDeviceView || s.overlayInDeviceView))
            PerformanceOverlay(host.perf, s.overlay, s.overlayGraph, s.overlayOpacity, s.detailedTiming,
                Modifier.align(Alignment.of(s.overlayCorner)).padding(6.dp), scale = overlayScale)
        val state = when {
            host.status == "running" && host.displayAsleep -> "asleep"
            host.status == "running" -> null
            else -> host.status
        }
        AnimatedContent(state, Modifier.matchParentSize(), label = "guest-state") { shown ->
            if (shown != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (shown == "asleep") 0.35f else 0.7f)),
                contentAlignment = Alignment.Center) {
                when (shown) {
                    "starting" -> Busy("Starting iOS…")
                    "stopping" -> Busy("Shutting down…")
                    "paused" -> ScreenAction(LtIcons.Play, "Paused · tap to resume", { host.setPaused(false) }, tag = "resume_overlay")
                    "asleep" -> ScreenAction(LtIcons.Power, "Screen off · tap to wake", { host.pulse(GuestButton.HOME) }, tag = "guest_screen_asleep")
                    "failed" -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = LtColors.Warning, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("iOS stopped unexpectedly", color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        if (host.sessionOwned) TextButton(onClick = host::stop) { Text("Close session") }
                        else ScreenAction(LtIcons.History, "Recover", host::recover, tag = "recover_guest")
                    }
                    else -> if (host.selected == null) ScreenAction(LtIcons.Phone, "Add a device", host::addDevice, tag = "add_device")
                        else if (host.selected?.dirty == true) ScreenAction(LtIcons.History, "Recover and start", { if (!host.busy) host.recover() }, tag = "recover_guest")
                        else ScreenAction(LtIcons.Play, "Start", { if (!host.busy) host.start() }, tag = "start_guest")
                }
            }
        }
    }
}

@Composable
private fun Busy(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(14.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
    }
}

/** Pause, screenshot, full screen, inspect, stop: one pane of glass over the bottom bar. */
@Composable
private fun SessionDock(host: GuestHost, onStop: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("dock"), raised = true) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            val canControl = host.status in listOf("running", "paused")
            DockButton(if (host.paused) LtIcons.Play else LtIcons.Pause, if (host.paused) "Resume" else "Pause",
                { host.setPaused(!host.paused) }, Modifier.weight(1f), enabled = canControl, tag = "pause_guest", primary = true)
            DockButton(LtIcons.Fullscreen, "Full screen", { host.navigate(Screen.FULLSCREEN) }, Modifier.weight(1f), tag = "fullscreen_button")
            DockButton(LtIcons.Camera, "Screenshot", host::screenshot, Modifier.weight(1f), enabled = canControl, tag = "screenshot_button")
            if (host.settings.hitchButton) DockButton(LtIcons.Timeline, "Inspect", host::openHitch, Modifier.weight(1f), enabled = canControl, tag = "hitch_button")
            DockButton(LtIcons.Stop, "Stop", onStop, Modifier.weight(1f),
                enabled = host.sessionOwned && host.status != "stopping" && !host.storeInstalling, tint = LtColors.Danger, tag = "stop_guest")
        }
    }
}

/** Shutting down is slow and closes every app, so it is confirmed; Save & Stop keeps the session. */
@Composable
internal fun StopDialog(host: GuestHost, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, modifier = DialogTags,
        icon = { Icon(LtIcons.Power, null) },
        title = { Text("Stop ${host.selected?.name ?: "device"}?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) },
        text = {
            Column {
                Text(if (host.canSaveState) "Save & Stop keeps everything as it is, open apps included, and picks up right here next time."
                    else "iOS shuts down like holding the power button: apps close and the device turns off. Your apps and data are kept.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            if (host.canSaveState) Button(onClick = { onDismiss(); host.saveAndStop() }, modifier = Modifier.testTag("save_and_stop")) { Text("Save & Stop") }
            else Button(onClick = { onDismiss(); host.stop() }, modifier = Modifier.testTag("confirm_stop"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Shut down") }
        },
        dismissButton = {
            Row {
                if (host.canSaveState) TextButton(onClick = { onDismiss(); host.stop() }, modifier = Modifier.testTag("confirm_stop")) { Text("Shut down") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        })
}

internal fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
