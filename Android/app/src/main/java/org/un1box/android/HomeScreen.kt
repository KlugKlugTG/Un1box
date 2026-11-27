package org.un1box.android

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The default view: the selected device lit from behind, a top bar for switching devices and
 * seeing the session at a glance, the session controls in a glass dock, and the app's three
 * destinations along the bottom.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun HomeScreen(host: GuestHost, guestSurface: @Composable (Modifier) -> Unit) {
    var confirmStop by remember { mutableStateOf(false) }
    var dockHeight by remember { mutableStateOf(0) }
    Ios6WallpaperPage(Modifier.semantics { testTagsAsResourceId = true }) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            HomeTopBar(host)
            // The device fits this room, but its drawing runs on under the dock and off the screen's sides and
            // bottom; only the top stays clear, cut where the top bar ends.
            Box(Modifier.weight(1f).fillMaxWidth().drawWithContent {
                val big = 10_000f
                clipRect(left = -big, top = -8.dp.toPx(), right = size.width + big, bottom = size.height + big) { this@drawWithContent.drawContent() }
            }.padding(horizontal = 24.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                DeviceStage(host) { modifier -> GuestScreen(host, guestSurface, modifier, overlayScale = 0.85f, inDeviceView = true) }
            }
            SessionStats(host)
            val docked = host.active && !host.settings.dockHidden
            AnimatedVisibility(docked, modifier = Modifier.zIndex(1f), enter = expandVertically() + slideInHorizontally { it } + fadeIn(),
                exit = shrinkVertically() + fadeOut()) {
                SessionDock(host, onHeight = { dockHeight = it }, onHide = { host.settings.dockHidden = true }) { confirmStop = true }
            }
            LtNavBar(Screen.HOME, host::navigate, Modifier.padding(top = if (docked) 0.dp else 14.dp))
        }
        AnimatedVisibility(host.active && host.settings.dockHidden, modifier = Modifier.align(Alignment.BottomEnd).zIndex(2f)
            .windowInsetsPadding(WindowInsets.safeDrawing),
            enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
            val middle = 64.dp + with(LocalDensity.current) { if (dockHeight > 0) (dockHeight / 2).toDp() else 34.dp }
            DockTab(middle) { host.settings.dockHidden = false }
        }
        // Floats over the device under the top bar, so a message never resizes the guest screen.
        ErrorBanner(host.error, host::dismissError, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 16.dp, end = 16.dp, top = 68.dp))
        // For automation (tools/perf): the raw session state as text.
        Text(host.status, Modifier.align(Alignment.TopStart).size(1.dp).testTag("session_status"), color = Color.Transparent, fontSize = 1.sp)
    }
    if (confirmStop) StopDialog(host) { confirmStop = false }
}

/** The iOS 6 navigation bar: the selected device on the left, its session state on the right. */
@Composable
private fun HomeTopBar(host: GuestHost) {
    val device = host.selected
    Ios6NavBar(
        title = device?.name ?: "No device",
        subtitle = device?.subtitle ?: statusLabel(host.status),
        start = {
            Ios6BarButton(icon = LtIcons.Phone, label = "Devices", onClick = { host.openDevices() }, tag = "device_selector")
        },
        end = { StatusPill(host.status) })
}

/**
 * The device, life-size-ish, standing in a still pool of light, with a soft shadow under it so it
 * reads as an object rather than a picture. The light does not pulse: this is a paused or running
 * machine on a desk, and a breathing glow is an effect, not a state.
 */
@Composable
private fun DeviceStage(host: GuestHost, screen: @Composable (Modifier) -> Unit) {
    val glow = when (host.status) {
        "running" -> Ios6.Blue
        "starting", "stopping" -> LtColors.Warning
        "failed" -> LtColors.Danger
        else -> Ios6.InkSecondary
    }
    // One static pool of light: a device standing on a desk, not a screensaver. The old pulsing
    // bloom asked for frames on an idle screen and made the home view read as a poster.
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.09f), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.52f), radius = size.minDimension * 0.95f))
        // The radius matches the oval's half-width so the shadow reaches zero at its edge.
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.94f), radius = size.width * 0.34f),
            topLeft = Offset(size.width * 0.16f, size.height * 0.88f), size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.10f))
    }, contentAlignment = Alignment.Center) {
        DeviceFrame(DeviceSkin.forBoard(host.selected?.board), enabled = host.running, button = host::button,
            screen = screen, modifier = Modifier.fillMaxSize(), haptics = host.settings.haptics,
            pixels = screenPixels(host.selected?.board).takeIf { host.settings.scaling == ScreenScaling.PIXEL_PERFECT },
            multiple = host.settings.integerScale, realPxPerMm = host.settings.realPxPerMm(host.selected?.board))
    }
}

/** The live read-out under the device: how long it has been up and how fast it is going. */
@Composable
private fun SessionStats(host: GuestHost) {
    val clock = rememberElapsed(host.running)
    if (!host.running) return
    ChipRow(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState())) {
        if (clock.isNotEmpty()) StatChip("Session", clock, Ios6.Blue)
        if (host.perf.fps > 0) StatChip("FPS", "%.0f".format(host.perf.fps), LtColors.OverlayFps)
        // The worker reports guest CPU only when it could read it, so this is a chip that comes and goes.
        val cpu = host.perf.vcpuPercent
        if (cpu != null && cpu > 0f) StatChip("CPU", "%.0f%%".format(cpu), LtColors.OverlayCpu)
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

/** Pause, screenshot, full screen, inspect, stop: one blue toolbar over the bottom bar. */
@Composable
private fun SessionDock(host: GuestHost, onHeight: (Int) -> Unit, onHide: () -> Unit, onStop: () -> Unit) {
    // Dragged right it follows the finger; let go past a sixth of its width, or flick it, (or tap its arrow) and it
    // slides the rest of the way off before it's hidden, so it never jumps back first. Short of that it springs back.
    val slide = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableStateOf(1) }
    val tuck: () -> Unit = {
        scope.launch {
            slide.animateTo(width * 1.1f, tween(160))
            onHide()
        }
    }
    val dark = Ios6.isDark()
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
        .onSizeChanged { width = it.width.coerceAtLeast(1); onHeight(it.height) }
        .graphicsLayer { translationX = slide.value; alpha = 1f - (slide.value / width).coerceIn(0f, 1f) * 0.6f }
        .pointerInput(Unit) {
            val tracker = VelocityTracker()
            var travel = 0f
            detectHorizontalDragGestures(
                onDragStart = { tracker.resetTracking(); travel = 0f },
                onDragEnd = {
                    val flick = tracker.calculateVelocity().x > 400.dp.toPx()
                    if (flick || slide.value > width / 6f) tuck() else scope.launch { slide.animateTo(0f) }
                },
                onDragCancel = { scope.launch { slide.animateTo(0f) } }) { change, dx ->
                change.consume()
                travel += dx
                tracker.addPosition(change.uptimeMillis, Offset(travel, 0f))
                scope.launch { slide.snapTo((slide.value + dx).coerceAtLeast(0f)) }
            }
        }
        .testTag("dock")
        .ios6Lacquer(RoundedCornerShape(9.dp),
            if (dark) Ios6.NavTopDark else Ios6.NavTop,
            if (dark) Ios6.NavBottomDark else Ios6.NavBottom,
            if (dark) Color(0xFF0A0A0B) else Color(0xFF173F6B))) {
        CompositionLocalProvider(LocalIosOnChrome provides true) {
            Row(Modifier.padding(start = 4.dp, end = 0.dp, top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                val canControl = host.status in listOf("running", "paused")
                DockButton(if (host.paused) LtIcons.Play else LtIcons.Pause, if (host.paused) "Resume" else "Pause",
                    { host.setPaused(!host.paused) }, Modifier.weight(1f), enabled = canControl, tag = "pause_guest", primary = true)
                DockButton(LtIcons.Fullscreen, "Full screen", { host.navigate(Screen.FULLSCREEN) }, Modifier.weight(1f), tag = "fullscreen_button")
                if (host.settings.screenshotButton) DockButton(LtIcons.Camera, "Screenshot", host::screenshot, Modifier.weight(1f), enabled = canControl, tag = "screenshot_button")
                if (host.settings.hitchButton) DockButton(LtIcons.Timeline, "Inspect", host::openHitch, Modifier.weight(1f), enabled = canControl, tag = "hitch_button")
                if (host.carrierButton) CarrierDockButton(host, Modifier.weight(1f))
                DockButton(LtIcons.Stop, if (host.status == "stopping") "Force stop" else "Stop", onStop, Modifier.weight(1f),
                    enabled = host.sessionOwned, tint = LtColors.Danger, tag = "stop_guest")
                Box(Modifier.width(30.dp).height(52.dp).clip(RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)).clickable(onClick = tuck)
                    .testTag("dock_hide"), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Hide the controls", tint = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

/**
 * The tucked-away dock: a small tab at the right edge, its middle [middle] above the bottom (level with the dock's),
 * where a tap, or a drag left, brings the dock back. A taller band around it is kept out of the system's edge-swipe
 * Back gesture (which would otherwise take the drag) and takes the same tap and drag, so it's forgiving to hit.
 */
@Composable
private fun DockTab(middle: Dp, onShow: () -> Unit) {
    val dark = Ios6.isDark()
    Box(Modifier.systemGestureExclusion().pointerInput(Unit) {
        var pulled = 0f
        detectHorizontalDragGestures(onDragEnd = { if (pulled < -16f) onShow(); pulled = 0f }, onDragCancel = { pulled = 0f }) { change, dx ->
            change.consume(); pulled += dx
        }
    }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onShow)
        .padding(start = 24.dp, top = 80.dp, bottom = (middle - 32.dp).coerceAtLeast(0.dp)).testTag("dock_show"), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.size(width = 44.dp, height = 64.dp)
            .ios6Lacquer(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                if (dark) Ios6.NavTopDark else Ios6.NavTop,
                if (dark) Ios6.NavBottomDark else Ios6.NavBottom,
                if (dark) Color(0xFF0A0A0B) else Color(0xFF173F6B))
            .clickable(onClick = onShow), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Show the controls", tint = Color.White)
        }
    }
}

/** Shutting down is slow and closes every app, so it is confirmed; Save & Stop keeps the session. */
@Composable
internal fun StopDialog(host: GuestHost, onDismiss: () -> Unit) {
    if (host.status == "stopping" || host.storeInstalling) return ForceStopDialog(host, onDismiss)
    val shutDown: (@Composable () -> Unit)? = if (host.canSaveState) {
        { TextButton(onClick = { onDismiss(); host.stop() }, modifier = Modifier.testTag("confirm_stop")) { Text("Shut down") } }
    } else null
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
        // Three actions stack vertically in iOS 6, Cancel last.
        neutralButton = shutDown,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** Stop has been asked for and iOS hasn't finished, or an app install would hold the shutdown up: end it now. */
@Composable
private fun ForceStopDialog(host: GuestHost, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, modifier = DialogTags,
        icon = { Icon(LtIcons.Stop, null) },
        title = { Text("Force stop ${host.selected?.name ?: "device"}?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) },
        text = { Text(if (host.status != "stopping" && host.storeInstalling)
                "An app is being installed, so iOS can't shut down until that finishes. Force stop ends it now, like pulling the battery: the app being installed is lost, along with anything else iOS hadn't saved, and the next start checks the device's storage first."
                else "iOS hasn't finished shutting down. Force stop ends it now, like pulling the battery: anything it hadn't saved is lost, and the next start checks the device's storage first.",
            style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { Button(onClick = { onDismiss(); host.forceStop() }, modifier = Modifier.testTag("confirm_force_stop"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Force stop") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (host.status != "stopping" && host.storeInstalling) "Keep installing" else "Keep waiting") } })
}

internal fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
