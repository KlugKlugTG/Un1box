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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val scheme = MaterialTheme.colorScheme
    Column(modifier.pressFeedback(interaction, enabled).clip(RoundedCornerShape(18.dp))
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier).semantics(mergeDescendants = true) { contentDescription = label }
        .padding(horizontal = 2.dp, vertical = 6.dp).alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
            .then(if (primary) Modifier.background(Brush.linearGradient(LtGradients.mixed))
                else Modifier.background(scheme.surfaceContainerHigh.copy(alpha = 0.8f))),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp), tint = if (primary) scheme.onPrimary else tint)
        }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (primary) scheme.primary else tint,
            maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** A dismissible error card; long messages expand on tap. */
@Composable
internal fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(message.isNotEmpty(), modifier) {
        var expanded by remember(message) { mutableStateOf(false) }
        Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f), shape = RoundedCornerShape(20.dp), shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth().testTag("session_error").clickable { expanded = !expanded }) {
            Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(Modifier.width(10.dp))
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Dismiss", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

/** A round translucent control drawn over the guest screen. */
@Composable
internal fun ScreenAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(Color.White.copy(alpha = 0.14f), CircleShape).clip(CircleShape)
            .clickable(onClick = onClick).then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(32.dp), tint = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
    }
}

internal fun statusLabel(status: String) = when (status) {
    "running" -> "Running"; "starting" -> "Starting"; "paused" -> "Paused"; "stopping" -> "Shutting down"
    "failed" -> "Stopped unexpectedly"; else -> "Off"
}

/** Dialogs get their own window, so they repeat the activity's opt-in to expose test tags as resource ids. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal val DialogTags = Modifier.semantics { testTagsAsResourceId = true }
