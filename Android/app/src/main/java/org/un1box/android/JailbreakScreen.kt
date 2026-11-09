package org.un1box.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Jailbreak tab: the packages a jailbroken device can have, browsed and downloaded by the phone
 * instead of by Cydia.
 *
 * Cydia in the guest is slow because everything about it is 2010 -- its TLS, its repositories, and
 * the emulated CPU under it. The packages themselves are still served, so this screen does the two
 * slow halves on the phone (fetching an index, downloading a .deb) and leaves the guest one thing it
 * is good at: its own startup job installing what is in AutoInstall at the next boot.
 *
 * Sources are kept as the user typed them (JailbreakSources); a source that answers with nothing
 * says so under its row rather than failing the screen.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)   // testTagsAsResourceId, as the other screens
@Composable
internal fun JailbreakScreen(
    running: Boolean,
    socket: String,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { JailbreakStore() }
    val scope = rememberCoroutineScope()
    val sources = remember { mutableStateListOf<String>().apply { addAll(JailbreakSources.read(context)) } }
    var chosenSource by remember { mutableStateOf(sources.firstOrNull()) }
    val packages = remember { mutableStateListOf<JailbreakPackage>() }
    var indexUrl by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var newSource by remember { mutableStateOf("") }
    var staged by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }   // the package whose details are showing
    var installed by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var filterMode by remember { mutableStateOf(0) }   // 0: all, 1: installed, 2: updates
    // What the list offers newer than the device has: Cydia's "Changes" in one line. Declared after
    // `installed`, which it reads.
    val updates = remember(packages.size, installed.size) {
        packages.filter {
            val have = installed[it.name]
            have != null && JailbreakCatalog.compareVersions(it.version, have) > 0
        }
    }

    fun load(source: String) {
        chosenSource = source
        packages.clear()
        error = ""
        note = ""
        busy = "Reading ${source.substringAfter("//").trimEnd('/')}"
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val (url, list) = store.packages(source)
                    indexUrl = url
                    Result.success(list)
                } catch (failure: Exception) {
                    Result.failure(failure)
                }
            }
            busy = ""
            outcome.onSuccess { list ->
                packages.addAll(list.sortedBy { it.name.lowercase() })
                note = "${list.size} packages"
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
    }

    LaunchedEffect(sources.toList()) { sources.firstOrNull()?.let { if (packages.isEmpty() && error.isEmpty()) load(it) } }
    // What the device already has, read from its own dpkg state (Cydia's "Installed" list).
    LaunchedEffect(running, socket) {
        if (running && socket.isNotEmpty()) {
            installed = withContext(Dispatchers.IO) { JailbreakInstaller(context) { socket }.installed() }
        }
    }

    BackHandler(enabled = true, onBack = onClose)
    Surface(Modifier.fillMaxSize().testTag("jailbreak").semantics { testTagsAsResourceId = true },
        color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Jailbreak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("Close", Modifier.clickable { onClose() }, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                when {
                    !running -> "The device is not running. Start it to install packages; browsing works without it."
                    else -> "Packages are downloaded by the phone and installed by the device's own Cydia at its next start."
                },
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (staged.isNotEmpty()) {
                GlassSurface(Modifier.padding(16.dp), strong = true) {
                    Text("Waiting for the device to restart: $staged", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Restart the device", Modifier.clickable { onRestart() }, color = MaterialTheme.colorScheme.primary)
                        Text("Dismiss", Modifier.clickable { staged = "" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("Sources", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.titleSmall)
            for (source in sources.toList()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (source == chosenSource) "•" else " ", Modifier.width(12.dp),
                        color = MaterialTheme.colorScheme.primary)
                    Text(source.substringAfter("//").trimEnd('/'), Modifier.weight(1f).clickable { load(source) },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (source == chosenSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text("×", Modifier.clickable {
                        sources.remove(source)
                        JailbreakSources.write(context, sources.toList())
                        if (chosenSource == source) { chosenSource = sources.firstOrNull(); packages.clear() }
                    }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newSource, { newSource = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Add a source") })
                Spacer(Modifier.width(8.dp))
                Text("Add", Modifier.clickable(enabled = newSource.isNotBlank()) {
                    val url = newSource.trim()
                    if (url.isNotEmpty() && url !in sources) {
                        sources.add(url)
                        JailbreakSources.write(context, sources.toList())
                        newSource = ""
                        load(url)
                    }
                }, color = MaterialTheme.colorScheme.primary)
            }

            if (busy.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Text(busy, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (error.isNotEmpty()) Text(error, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error) else if (note.isNotEmpty())
                Text(note, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(filter, { filter = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Search") })
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        when (filterMode) {
                            1 -> "All"
                            2 -> "Installed ${installed.size}"
                            else -> "Updates ${updates.size}"
                        },
                        Modifier.clickable { filterMode = (filterMode + 1) % 3 },
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }

            val shown = packages
                .filter {
                    when (filterMode) {
                        1 -> installed.containsKey(it.name)
                        2 -> updates.any { update -> update.name == it.name }
                        else -> true
                    }
                }
                .filter {
                    filter.isBlank() || it.name.contains(filter, true) ||
                        it.summary.contains(filter, true) || it.section.contains(filter, true)
                }
                .sortedWith(compareBy({ it.section.ifBlank { "Other" }.lowercase() }, { it.name.lowercase() }))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.name + it.version }) { pkg ->
                    val key = pkg.name + pkg.version
                    val expanded = open == key
                    GlassSurface {
                        Column(Modifier.padding(12.dp).clickable { open = if (expanded) null else key }) {
                            Text(pkg.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(pkg.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (pkg.summary.isNotEmpty()) Text(pkg.summary, style = MaterialTheme.typography.bodySmall,
                                maxLines = if (expanded) 8 else 2)
                            installed[pkg.name]?.let { version ->
                                Text("Installed $version", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            if (!expanded) {
                                Spacer(Modifier.height(4.dp))
                                Text("Details", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            } else {
                                Spacer(Modifier.height(6.dp))
                                Text(listOfNotNull(pkg.architecture.takeIf { it.isNotBlank() }, pkg.section.takeIf { it.isNotBlank() })
                                    .joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(pkg.source, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (pkg.wrongArchitecture) {
                                    Text("This package is built for 64-bit iOS and cannot be installed on this device.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Install", Modifier.clickable(enabled = busy.isEmpty()) {
                                error = ""; note = ""
                                busy = "Installing ${pkg.name}"
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) {
                                        try {
                                            Result.success(JailbreakInstaller(context) { socket }
                                                .install(pkg, indexUrl) { phase, percent -> busy = "$phase $percent%" })
                                        } catch (failure: Exception) { Result.failure(failure) }
                                    }
                                    busy = ""
                                    outcome.onSuccess { staged = it }
                                        .onFailure { error = it.message ?: it.javaClass.simpleName }
                                }
                            }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
