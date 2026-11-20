package org.un1box.android

import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class Screen { LOADING, SETUP, HOME, FULLSCREEN, SETTINGS, STORE, JAILBREAK, HITCH, DETAILS }

/** What the screens read from and ask of the activity, which owns the worker and its lifecycle. */
interface GuestHost {
    val status: String
    val sessionOwned: Boolean
    val displayAsleep: Boolean
    val busy: Boolean
    val devices: List<DeviceSummary>
    val selected: DeviceSummary?
    val error: String
    val settings: AppSettings
    val perf: PerfSnapshot
    val profileRecording: Boolean
    val profileAutomatic: Boolean
    val profileBusy: Boolean
    val profileNotice: String
    val storeInstalling: Boolean
    /** The engine can save this guest's state (Save & Stop); false until supported on this host. */
    val canSaveState: Boolean
    fun dismissError()
    fun start()
    fun recover()
    fun stop()
    fun saveAndStop()
    fun setPaused(paused: Boolean)
    fun button(id: Int, down: Boolean)
    /** Fake drives for the fullscreen menu: "" when the engine queued the write, else what to show. */
    fun setBattery(level: Int, charging: Int): String
    fun fakeCall(number: String): String
    fun endFakeCall(): String
    fun fakeSms(number: String, text: String): String
    fun pulse(id: Int)
    fun screenshot()
    fun navigate(screen: Screen)
    fun openDevices()
    fun openDetails(device: DeviceSummary)
    fun openHitch()
    fun selectDevice(id: String)
    fun renameDevice(id: String, name: String)
    fun deleteDevice(id: String)
    fun addDevice()
    fun toggleProfile()
    /** True while the engine copies a presented frame on a worker (its default). */
    val presentAsync: Boolean
    fun usePresentAsync(on: Boolean)
    /** True while the frame block models a transfer's full hardware span (its default). */
    val faithfulScaler: Boolean
    fun useFaithfulScaler(on: Boolean)
    /** True while this phone's cameras feed the emulated device; read at guest start. */
    val camera: Boolean
    fun useCamera(on: Boolean)
    /** What the guest's camera is doing, from the worker: the sensor it is on and the frames it has taken. */
    val cameraStatus: String
    /** True while this phone's microphone feeds the emulated device's capture port; read at guest start. */
    val microphone: Boolean
    fun useMicrophone(on: Boolean)
    fun pickGuestTools()
    fun openLicenses()
    fun openPreparation()
    fun applySettings()
}

internal val GuestHost.running get() = status == "running"
internal val GuestHost.paused get() = status == "paused"
internal val GuestHost.active get() = status in listOf("running", "paused", "starting", "stopping")

/**
 * A labelled control for the session dock: the glyph sits on its own tile, so the eye reads the
 * row as a set of buttons rather than a list of words. [primary] fills that tile with the app's
 * gradient, which is reserved for the one control a session is usually asking for.
 */
@Composable
internal fun DockButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                        enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurface, tag: String? = null,
                        primary: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val onChrome = LocalIosOnChrome.current
    val dark = Ios6.isDark()
    val shape = RoundedCornerShape(8.dp)
    Column(modifier.pressFeedback(interaction, enabled).clip(shape)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier).semantics(mergeDescendants = true) { contentDescription = label }
        .padding(horizontal = 2.dp, vertical = 4.dp).alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // A toolbar key: blue lacquer for the one action a session usually wants, silver for the rest.
        Box(Modifier.size(40.dp).ios6Lacquer(shape,
            if (onChrome) Ios6.BarButtonTop else if (primary) Ios6.ButtonTop else if (dark) Color(0xFF4A4A4E) else Ios6.GrayButtonTop,
            if (onChrome) Ios6.BarButtonBottom else if (primary) Ios6.ButtonBottom else if (dark) Color(0xFF26262A) else Ios6.GrayButtonBottom,
            if (onChrome) Color(0xFF163E6E) else if (primary) Ios6.ButtonEdge else Ios6.GrayButtonEdge),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (primary || onChrome) Color.White else if (dark) Color.White else Ios6.Ink)
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (primary) Color.White else if (onChrome) Color.White else if (dark) Color.White else Ios6.Ink,
            maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** A dismissible error card in iOS 6's red lacquer; long messages expand on tap. */
@Composable
internal fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(message.isNotEmpty(), modifier) {
        var expanded by remember(message) { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().testTag("session_error").clickable { expanded = !expanded }
            .ios6Lacquer(RoundedCornerShape(10.dp), Color(0xFFE06A60), Color(0xFF9E2119), Color(0xFF70140E))
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Warning, null, Modifier.size(15.dp), tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Close, "Dismiss", Modifier.size(15.dp), tint = Color.White)
            }
        }
    }
}

/** A round lacquered control drawn over the guest screen, as iOS draws its lock-screen buttons. */
@Composable
internal fun ScreenAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape)
            .drawBehind {
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.06f))))
                drawCircle(Color.White.copy(alpha = 0.45f), radius = size.minDimension * 0.46f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
                drawCircle(Color.White.copy(alpha = 0.55f), radius = size.minDimension * 0.49f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            }
            .clickable(onClick = onClick).then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(32.dp), tint = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center)
    }
}

internal fun statusLabel(status: String) = when (status) {
    "running" -> "Running"; "starting" -> "Starting"; "paused" -> "Paused"; "stopping" -> "Shutting down"
    "failed" -> "Stopped unexpectedly"; else -> "Off"
}

/** Dialogs get their own window, so they repeat the activity's opt-in to expose test tags as resource ids. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal val DialogTags = Modifier.semantics { testTagsAsResourceId = true }
