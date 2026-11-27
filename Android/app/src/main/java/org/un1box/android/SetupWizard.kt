package org.un1box.android

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class SetupStep { WELCOME, FILES, CREATING, DONE }
/** A chosen file or folder; [board] is the prepared system's board when its device lock could be read. */
internal data class PickedFile(val uri: Uri, val name: String, val folder: Boolean = false, val board: String = "")

/** Wizard state; the activity owns it so pickers and the import worker can update it. */
internal class SetupState {
    var step by mutableStateOf(SetupStep.WELCOME)
    var system by mutableStateOf<PickedFile?>(null)
    var rom by mutableStateOf<PickedFile?>(null)
    var name by mutableStateOf("")
    var phase by mutableStateOf("")
    /** How far the current phase is, 0 to 1; negative while its size is unknown. */
    var progress by mutableFloatStateOf(-1f)
    var error by mutableStateOf("")
    var created by mutableStateOf<String?>(null)
    /** How long each stage of a preparation took ("stage\tseconds" lines), shown folded on the finished screen. */
    var timings by mutableStateOf("")
    var scanned by mutableStateOf<String?>(null)
    fun reset(firstRun: Boolean) {
        step = if (firstRun) SetupStep.WELCOME else SetupStep.FILES
        system = null; rom = null; name = ""; phase = ""; progress = -1f; error = ""; created = null; scanned = null; timings = ""
    }
}

/**
 * First launch: a welcome that leads into preparing a device from an IPSW on the phone (PrepareScreen). Its advanced
 * path, also reached from Add a device, adds device files the user already has: an iOS system and boot file, or one
 * folder holding them. Then the device is created and offered to start. Guest tools come with Un1box.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun SetupWizard(state: SetupState, canCancel: Boolean, onPickFolder: () -> Unit, onPickSystem: () -> Unit,
                         onPickRom: () -> Unit, onCreate: () -> Unit, onPrepare: () -> Unit,
                         onFinish: (start: Boolean) -> Unit, onCancel: () -> Unit, onSaveLogs: () -> Unit = {},
                         savingLogs: Boolean = false, onImport: () -> Unit = {}) {
    BackHandler {
        when {
            state.step == SetupStep.CREATING -> {}  // consumed: the import keeps running behind the wizard
            state.step == SetupStep.FILES && !canCancel && state.system == null -> state.step = SetupStep.WELCOME
            canCancel && state.step != SetupStep.DONE -> onCancel()
            state.step == SetupStep.DONE -> onFinish(false)
        }
    }
    Ios6Page(Modifier.semantics { testTagsAsResourceId = true }.testTag("setup")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (canCancel && state.step != SetupStep.CREATING) Row(Modifier.fillMaxWidth().padding(4.dp)) {
                IconButton(onClick = { if (state.step == SetupStep.DONE) onFinish(false) else onCancel() }) { Icon(Icons.Filled.Close, "Close") }
            }
            AnimatedContent(state.step, Modifier.weight(1f), label = "setup-step") { step ->
                when (step) {
                    SetupStep.WELCOME -> Welcome(onPrepare, onImport) { state.step = SetupStep.FILES }
                    SetupStep.FILES -> Files(state, onPickFolder, onPickSystem, onPickRom, onCreate, onPrepare)
                    SetupStep.CREATING -> Creating(state.phase, state.progress)
                    SetupStep.DONE -> Done(state.created ?: "Your device", state.timings, onFinish, onSaveLogs, savingLogs)
                }
            }
        }
    }
}

@Composable
private fun Welcome(onPrepare: () -> Unit, onImport: () -> Unit, onFiles: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).ios6Lacquer(RoundedCornerShape(12.dp), Ios6.ButtonTop, Ios6.ButtonBottom, Ios6.ButtonEdge),
            contentAlignment = Alignment.Center) {
            Icon(LtIcons.Phone, null, Modifier.size(36.dp), tint = Color.White)
        }
        Spacer(Modifier.height(28.dp))
        Text("Un1box", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Classic iPod touch and iPhone software, running on your phone.", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(36.dp))
        Text("Un1box sets up a device for you, right on this phone:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Need(LtIcons.Phone, "Choose a device and iOS version", "The iPod touch 2G on iOS 3.1.3 is the easiest place to start")
        Need(LtIcons.Download, "Un1box downloads it", "iOS comes from Apple, or the Internet Archive when Apple no longer has it")
        Need(LtIcons.Layers, "And prepares it", "From a minute to half an hour, with 1.5 to 10 GB free")
        Spacer(Modifier.height(40.dp))
        Button(onClick = onPrepare, modifier = Modifier.align(Alignment.CenterHorizontally).height(44.dp).testTag("setup_start"), contentPadding = PaddingValues(horizontal = 32.dp)) {
            Text("Get started", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onImport, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).testTag("setup_import")) {
            Text("Import a device from another phone or computer")
        }
        TextButton(onClick = onFiles, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("setup_advanced")) {
            Text("I already have the device files")
        }
    }
}

@Composable
private fun Need(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuideLink(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW,
        Uri.parse("https://github.com/KlugKlugTG/Un1box/blob/main/docs/ASSETS.md"))) }, modifier = modifier) {
        Text("Where do I get these?")
    }
}

@Composable
private fun Files(state: SetupState, onPickFolder: () -> Unit, onPickSystem: () -> Unit,
                  onPickRom: () -> Unit, onCreate: () -> Unit, onPrepare: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Add device files", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("For a device prepared elsewhere, such as on a computer: its iOS system (.itbase) and, for the iPod touch 1G and 2G and the original iPhone, its boot file.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        FileRow(LtIcons.Folder, "Choose a folder", state.scanned ?: "Un1box finds the files in it", null, onPickFolder, "setup_folder", action = true)
        FileRow(LtIcons.Layers, "iOS system", "Required · .itbase", state.system, onPickSystem, "setup_system")
        // Only the iPod touch 1G and 2G and the original iPhone boot from a boot ROM; a system known to be for
        // another device needs none.
        val board = state.system?.board ?: ""
        val rom = Bootrom.forBoard(board)
        if (board.isEmpty() || rom != null) FileRow(LtIcons.Chip, "Boot file",
            rom?.let { "Required · the ${it.label} boot ROM" } ?: "Required for iPod touch 1G and 2G, and the original iPhone · bootrom.bin", state.rom, onPickRom, "setup_rom")
        if (state.system != null && state.rom == null && rom != null) Text("Without the boot file this device will not start; you can still add it now.",
            style = MaterialTheme.typography.bodySmall, color = LtColors.Warning, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(state.name, { if (fitsDeviceName(it)) state.name = it }, Modifier.fillMaxWidth().testTag("setup_name"), singleLine = true,
            label = { Text("Name (optional)") }, placeholder = { Text("Named after the model and iOS version") })
        if (state.error.isNotEmpty()) Text(state.error, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreate, enabled = state.system != null, modifier = Modifier.align(Alignment.CenterHorizontally).height(44.dp).testTag("setup_create"),
            contentPadding = PaddingValues(horizontal = 32.dp)) { Text("Create device", style = MaterialTheme.typography.titleMedium) }
        GuideLink(Modifier.align(Alignment.CenterHorizontally))
        TextButton(onClick = onPrepare, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("setup_prepare")) {
            Text("Prepare from an IPSW instead")
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** A file to choose, with a check once chosen; [action] rows (the folder scan) have no check. */
@Composable
private fun FileRow(icon: ImageVector, title: String, hint: String, picked: PickedFile?, onPick: () -> Unit, tag: String, action: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)
        .ios6CellLacquer()
        .clickable(onClick = onPick).padding(12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).ios6Lacquer(RoundedCornerShape(7.dp),
            if (picked != null) Ios6.ButtonTop else Color(0xFFDCDCE1),
            if (picked != null) Ios6.ButtonBottom else Color(0xFFB8B8BE),
            if (picked != null) Ios6.ButtonEdge else Color(0xFF8E8E93)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(picked?.name ?: hint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (picked != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (picked != null) Icon(Icons.Filled.CheckCircle, "Chosen", tint = LtColors.Success)
        else if (!action) Ios6Chevron()
    }
}

@Composable
private fun Creating(phase: String, progress: Float) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Creating your device", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(phase.ifEmpty { "Copying files…" } + if (progress >= 0) " ${(progress * 100).toInt()}%" else "",
            Modifier.testTag("setup_phase"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (progress >= 0) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text("Keep Un1box open until it finishes.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Done(name: String, timings: String, onFinish: (Boolean) -> Unit, onSaveLogs: () -> Unit, savingLogs: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.CheckCircle, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("$name is ready", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("The first start takes longer while iOS sets itself up.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        Button(onClick = { onFinish(true) }, modifier = Modifier.height(44.dp).testTag("setup_launch"), contentPadding = PaddingValues(start = 24.dp, end = 32.dp)) {
            Icon(LtIcons.Play, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Start it now", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = { onFinish(false) }, modifier = Modifier.padding(top = 8.dp)) { Text("Not now") }
        if (timings.isNotEmpty()) StageTimes(timings)
        TextButton(onClick = onSaveLogs, enabled = !savingLogs, modifier = Modifier.testTag("save_logs")) { Text("Save logs") }
    }
}

/** A preparation's stages and their seconds, folded until asked for (for debugging slow preparations). */
@Composable
private fun StageTimes(timings: String) {
    var open by remember { mutableStateOf(false) }
    val rows = timings.lines().mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
    TextButton(onClick = { open = !open }, modifier = Modifier.padding(top = 24.dp).testTag("setup_details")) {
        Text(if (open) "Hide details" else "Details")
    }
    if (open) Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        for ((stage, seconds) in rows + ("Total" to "%.1f".format(rows.sumOf { it.second.toDoubleOrNull() ?: 0.0 }))) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(stage, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$seconds s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
