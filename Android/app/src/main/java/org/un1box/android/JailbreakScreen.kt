package org.un1box.android

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cydia's own bar: its five lists, in its order, with the icons it gives them. */
private enum class JailbreakList(val label: String, val icon: ImageVector) {
    PACKAGES("Cydia", LtIcons.Package),
    SOURCES("Sources", LtIcons.Sources),
    CHANGES("Changes", LtIcons.History),
    INSTALLED("Installed", LtIcons.Download),
    SEARCH("Search", Icons.Filled.Search),
}

/** Where the tab is: the lists, or one page over them (Cydia pushes pages the same way). */
private sealed interface JailbreakPage {
    data object Lists : JailbreakPage
    data class Source(val url: String) : JailbreakPage
    data class Section(val name: String) : JailbreakPage
    data class Package(val package_: JailbreakPackage) : JailbreakPage
}

/**
 * The Jailbreak tab, drawn as the Cydia it replaces: its five lists (its home, the sources, the
 * changes, what is installed, and a search), a page per source, section and package, and its own
 * dark cards. The work is the phone's -- indexes are fetched and packages downloaded here -- and the
 * guest's own Cydia startup job installs whatever is staged in /var/root/Media/Cydia/AutoInstall on
 * its next boot.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun JailbreakScreen(
    running: Boolean,
    socket: String,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { JailbreakStore(JailbreakBunzip2(context)::decompress) }
    val visit = remember { JailbreakCaches.current }
    val catalog = remember { visit.catalog ?: JailbreakCatalog().also { visit.catalog = it } }
    val scope = rememberCoroutineScope()
    val sources = remember { mutableStateListOf<String>().apply { addAll(JailbreakSources.read(context)) } }
    val fetchedAt = remember { mutableStateMapOf<String, Long>().apply { putAll(visit.fetchedAt) } }
    var list by remember { mutableStateOf(JailbreakList.PACKAGES) }
    var page by remember { mutableStateOf<JailbreakPage>(JailbreakPage.Lists) }
    var installed by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var newSource by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    var staged by remember { mutableStateOf("") }
    var installFile by remember { mutableStateOf("") }
    var installPhase by remember { mutableStateOf("") }
    var installPercent by remember { mutableStateOf(0) }

    fun refresh(only: String? = null) {
        scope.launch {
            error = ""
            for (one in (if (only != null) listOf(only) else sources.toList())) {
                reading = "Reading ${JailbreakSources.displayName(one)}"
                val outcome = withContext(Dispatchers.IO) {
                    try { Result.success(store.packages(one)) }
                    catch (failure: Exception) { Result.failure(failure) }
                }
                outcome.onSuccess { answer ->
                    catalog.add(one, answer.second)
                    catalog.rememberIndex(one, answer.first)
                    fetchedAt[one] = System.currentTimeMillis()
                }.onFailure { catalog.failed(one, it.message ?: it.javaClass.simpleName) }
            }
            reading = ""
            visit.sources = sources.toList()
            visit.fetchedAt.clear()
            visit.fetchedAt.putAll(fetchedAt)
            visit.refreshedAt = System.currentTimeMillis()
            revision++
        }
    }

    fun install(pkg: JailbreakPackage) {
        error = ""
        staged = ""
        installFile = pkg.filename.substringAfterLast('/')
        installPhase = "Downloading"
        installPercent = 0
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    Result.success(JailbreakInstaller(context) { socket }.install(pkg, catalog.indexOf(pkg.source)) { phase, percent ->
                        installPhase = phase
                        installPercent = percent
                    })
                } catch (failure: Exception) { Result.failure(failure) }
            }
            installFile = ""
            installPhase = ""
            outcome.onSuccess { staged = it }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
    }

    LaunchedEffect(Unit) {
        // Coming back to the tab is not a reason to ask apt.saurik.com for 364 packages again.
        if (!visit.fresh(sources.toList(), System.currentTimeMillis()) || catalog.packages().isEmpty()) refresh()
    }
    LaunchedEffect(running, socket) {
        installed = if (running && socket.isNotEmpty())
            withContext(Dispatchers.IO) { JailbreakInstaller(context) { socket }.installed() } else emptyMap()
    }
    LaunchedEffect(revision) { /* the bump above is what redraws the lists */ }

    // What the list offers newer than the device has: Cydia's Changes.
    val refreshed = revision
    val updates = remember(refreshed, installed.size) {
        catalog.updates(installed)
    }

    BackHandler(enabled = true, onBack = {
        when (page) {
            JailbreakPage.Lists -> if (list != JailbreakList.PACKAGES) list = JailbreakList.PACKAGES else onClose()
            else -> page = JailbreakPage.Lists
        }
    })

    Surface(Modifier.fillMaxSize().testTag("jailbreak").semantics { testTagsAsResourceId = true },
        color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            JailbreakHeader(
                title = when (val shown = page) {
                    JailbreakPage.Lists -> list.label
                    is JailbreakPage.Source -> JailbreakSources.displayName(shown.url)
                    is JailbreakPage.Section -> shown.name
                    is JailbreakPage.Package -> shown.package_.name
                },
                action = when (page) {
                    JailbreakPage.Lists -> if (list == JailbreakList.SOURCES) "Add Source" else "Refresh"
                    is JailbreakPage.Package -> "Install"
                    else -> null
                },
                onAction = {
                    when (val shown = page) {
                        JailbreakPage.Lists -> if (list == JailbreakList.SOURCES) adding = true else refresh()
                        is JailbreakPage.Package -> install(shown.package_)
                        else -> Unit
                    }
                },
                back = page != JailbreakPage.Lists,
                onBack = { page = JailbreakPage.Lists },
                onClose = onClose,
            )

            if (installFile.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(installFile, style = MaterialTheme.typography.bodySmall)
                    Text(installPhase, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            if (reading.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Text(reading, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (error.isNotEmpty()) Text(error, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (staged.isNotEmpty()) {
                Notice("Waiting for the device to restart", staged, "Restart the device" to onRestart,
                    "Dismiss" to { staged = "" })
            }
            for ((url, why) in catalog.failures) {
                Text("${JailbreakSources.displayName(url)}: $why",
                    Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            // The lists scroll inside a weighted box: without the weight a LazyColumn takes the whole
            // column and the tab bar below it lands off-screen, which is exactly what happened.
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val shown = page) {
                JailbreakPage.Lists -> ListsPage(list, catalog, installed, updates, sources, fetchedAt, query,
                    { query = it }, { refresh() }, { page = JailbreakPage.Source(it) },
                    { page = JailbreakPage.Section(it) }, { page = JailbreakPage.Package(it) },
                    { one -> sources.remove(one); JailbreakSources.write(context, sources.toList()); fetchedAt.remove(one) })
                is JailbreakPage.Source -> PackageList(
                    catalog.packages().filter { it.source == shown.url }, installed,
                    { JailbreakIcons.packageIcon(it, catalog.indexOf(it.source)) }) {
                    page = JailbreakPage.Package(it)
                }
                is JailbreakPage.Section -> PackageList(
                    catalog.packages().filter { it.section.ifBlank { "Other" } == shown.name }, installed,
                    { JailbreakIcons.packageIcon(it, catalog.indexOf(it.source)) }) {
                    page = JailbreakPage.Package(it)
                }
                is JailbreakPage.Package -> PackagePage(shown.package_, installed, running && socket.isNotEmpty(),
                    catalog.indexOf(shown.package_.source), ::install)
            }
            }

            JailbreakBar(list) { chosen -> list = chosen; page = JailbreakPage.Lists }
        }
    }

    if (adding) {
        Notice("Add Source", null, "Add" to {
            val url = newSource.trim()
            if (url.isNotEmpty() && sources.none { it.trimEnd('/') == url.trimEnd('/') }) {
                sources.add(url)
                JailbreakSources.write(context, sources.toList())
                refresh(url)
            }
            newSource = ""
            adding = false
        }, "Dismiss" to { adding = false })
    }
}

/** Cydia's header: a back arrow or the screen's name, and the one action the screen has. */
@Composable
private fun JailbreakHeader(
    title: String,
    action: String?,
    onAction: () -> Unit,
    back: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (back) {
            Icon(Icons.Filled.ArrowBack, null, Modifier.size(22.dp).clickable { onBack() },
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, Modifier.clickable { onAction() }, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
        }
        Text("Close", Modifier.clickable { onClose() }, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium)
    }
}

/** A line of explanation with its actions, as the tab's own notices are. */
@Composable
private fun Notice(title: String, detail: String?, vararg actions: Pair<String, () -> Unit>) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for ((label, run) in actions) {
                    Text(label, Modifier.clickable { run() }, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** The list the chosen tab shows. */
@Composable
private fun ListsPage(
    list: JailbreakList,
    catalog: JailbreakCatalog,
    installed: Map<String, String>,
    updates: List<JailbreakPackage>,
    sources: List<String>,
    fetchedAt: Map<String, Long>,
    query: String,
    onQuery: (String) -> Unit,
    onRefresh: () -> Unit,
    onSource: (String) -> Unit,
    onSection: (String) -> Unit,
    onPackage: (JailbreakPackage) -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    when (list) {
        JailbreakList.PACKAGES -> LazyColumn(contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val sections = catalog.sections()
            if (sections.isEmpty()) {
                item { Empty("Nothing here yet") }
            }
            items(sections, key = { it.first }) { entry ->
                Card(onClick = { onSection(entry.first) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.first, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("${entry.second}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Chevron()
                    }
                }
            }
        }

        JailbreakList.SOURCES -> LazyColumn(contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { GroupLabel("Sources", "Refresh", onRefresh) }
            items(sources, key = { it }) { one ->
                SourceCard(one, catalog.counts()[one] ?: 0, fetchedAt[one], { onSource(one) },
                    { onRemoveSource(one) })
            }
        }

        JailbreakList.CHANGES -> LazyColumn(contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (updates.isEmpty()) item { Empty("Nothing here yet") }
            items(updates, key = { it.name + it.version }) { pkg ->
                PackageRow(pkg, installed[pkg.name], JailbreakIcons.packageIcon(pkg, catalog.indexOf(pkg.source)), onPackage)
            }
        }

        JailbreakList.INSTALLED -> LazyColumn(contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (installed.isEmpty()) item { Empty("Nothing here yet") }
            items(installed.entries.toList(), key = { it.key }) { entry ->
                val known = catalog.packages().firstOrNull { it.name == entry.key }
                Card(onClick = { if (known != null) onPackage(known) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        PackageIcon()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.key, style = MaterialTheme.typography.bodyLarge)
                            Text(entry.value, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Chevron()
                    }
                }
            }
        }

        JailbreakList.SEARCH -> Column(Modifier.fillMaxSize()) {
            OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().padding(12.dp), singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                placeholder = { Text("Package name or description") })
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val found = catalog.search(query)
                if (query.isNotBlank() && found.isEmpty()) item { Empty("Nothing here yet") }
                items(found, key = { it.name + it.version }) { pkg ->
                    PackageRow(pkg, installed[pkg.name], JailbreakIcons.packageIcon(pkg, catalog.indexOf(pkg.source)), onPackage)
                }
            }
        }
    }
}

/** One row of a grouped list: a section's own label, or a package under it. */
private sealed interface JailbreakRow {
    data class Header(val text: String) : JailbreakRow
    data class Entry(val package_: JailbreakPackage) : JailbreakRow
}

/**
 * The packages of one source or one section, grouped the way Cydia groups them: a repository like
 * BigBoss files everything under Games, Utilities, Tweaks and the rest, and a flat list hides that.
 */
@Composable
private fun PackageList(
    packages: List<JailbreakPackage>,
    installed: Map<String, String>,
    indexOf: (JailbreakPackage) -> String,
    onOpen: (JailbreakPackage) -> Unit,
) {
    val rows = remember(packages) {
        val sorted = packages.sortedWith(compareBy({ it.section.ifBlank { "Other" }.lowercase() },
            { it.name.lowercase() }))
        buildList {
            var current = ""
            for (pkg in sorted) {
                val name = pkg.section.ifBlank { "Other" }
                if (name != current) {
                    current = name
                    add(JailbreakRow.Header(name))
                }
                add(JailbreakRow.Entry(pkg))
            }
        }
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (rows.isEmpty()) item { Empty("Nothing here yet") }
        items(rows, key = {
            when (it) {
                is JailbreakRow.Header -> "section:" + it.text
                is JailbreakRow.Entry -> it.package_.name + it.package_.version
            }
        }) { row ->
            when (row) {
                is JailbreakRow.Header -> GroupLabel(row.text, null, null)
                is JailbreakRow.Entry -> PackageRow(row.package_, installed[row.package_.name],
                    JailbreakIcons.packageIcon(row.package_, indexOf(row.package_)), onOpen)
            }
        }
    }
}

/** A source's card: its name, its URL, how many packages it offered and when it last answered. */
@Composable
private fun SourceCard(url: String, packages: Int, at: Long?, onOpen: () -> Unit, onRemove: () -> Unit) {
    Card(onClick = onOpen) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceIcon(JailbreakIcons.repositoryIcon(url))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(JailbreakSources.displayName(url), style = MaterialTheme.typography.bodyLarge)
                    Text(url, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                Text("Remove", Modifier.clickable { onRemove() },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Chevron()
            }
            Text(
                if (at == null) "$packages packages"
                else "$packages packages · " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(at)),
                Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A package's row: its icon, name, version and summary. */
@Composable
private fun PackageRow(
    pkg: JailbreakPackage,
    installedVersion: String?,
    iconUrl: String,
    onOpen: (JailbreakPackage) -> Unit,
) {
    Card(onClick = { onOpen(pkg) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PackageIcon(url = iconUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pkg.name, style = MaterialTheme.typography.bodyLarge)
                Text(pkg.version, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pkg.summary.isNotEmpty()) Text(pkg.summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (installedVersion != null) Text("Installed $installedVersion",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Chevron()
        }
    }
}

/** A package's own page: Cydia's description card and its INFORMATION group. */
@Composable
private fun PackagePage(
    pkg: JailbreakPackage,
    installed: Map<String, String>,
    ready: Boolean,
    indexUrl: String,
    onInstall: (JailbreakPackage) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PackageIcon(large = true, url = JailbreakIcons.packageIcon(pkg, indexUrl))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(pkg.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(pkg.version, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (pkg.description.isNotEmpty()) item {
            Card { Text(pkg.description, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
        }
        item { GroupLabel("Information", null, null) }
        item {
            Card {
                Column {
                    Fact("Package", pkg.name)
                    Fact("Version", pkg.version)
                    if (pkg.section.isNotEmpty()) Fact("Section", pkg.section)
                    if (pkg.author.isNotEmpty()) Fact("Author", pkg.author)
                    Fact("Architecture", pkg.architecture)
                    Fact("Source", pkg.source)
                    Fact("Size", pkg.sizeText)
                }
            }
        }
        if (pkg.wrongArchitecture) item {
            Text("This package is built for 64-bit iOS and cannot be installed on this device.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        item {
            Text(
                when {
                    installed[pkg.name] == pkg.version -> "Reinstall"
                    installed[pkg.name] != null -> "Updates"
                    else -> "Install"
                },
                Modifier.clickable(enabled = ready) { onInstall(pkg) },
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium)
            if (!ready) Text("Start the device to install: the package goes into its file system.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Text(indexUrl, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** One labelled line of the INFORMATION group. */
@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(label, Modifier.width(130.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** The tab bar at the bottom: Cydia's five lists. */
@Composable
private fun JailbreakBar(current: JailbreakList, onChoose: (JailbreakList) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly) {
        for (entry in JailbreakList.entries) {
            val selected = current == entry
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(Modifier.clickable { onChoose(entry) }.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(entry.icon, null, Modifier.size(22.dp), tint = tint)
                Spacer(Modifier.height(3.dp))
                Text(entry.label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

/** A group's small uppercase label, with the one action it may have. */
@Composable
private fun GroupLabel(text: String, action: String?, onAction: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(Locale.getDefault()), Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Text(action, Modifier.clickable { onAction() }, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** This app's card: a rounded panel the lists are made of. */
@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    GlassSurface(if (onClick != null) Modifier.fillMaxWidth().clickable { onClick() } else Modifier.fillMaxWidth(),
        strong = true) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

/** A package's icon: its own when the control file names one, else the box Cydia draws instead. */
@Composable
private fun PackageIcon(large: Boolean = false, url: String = "") {
    val size = if (large) 44.dp else 30.dp
    val bitmap by produceState<Bitmap?>(null, url) {
        value = if (url.isEmpty()) null else withContext(Dispatchers.IO) { JailbreakIcons.icon(url) }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center) {
        val picture = bitmap
        if (picture != null) {
            Image(picture.asImageBitmap(), contentDescription = null, Modifier.fillMaxSize())
        } else {
            Icon(LtIcons.Package, null, Modifier.size(if (large) 26.dp else 18.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A repository's icon, with the diamond standing in while it loads or when it has none. */
@Composable
private fun SourceIcon(url: String) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) { JailbreakIcons.icon(url) }
    }
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center) {
        val picture = bitmap
        if (picture != null) Image(picture.asImageBitmap(), contentDescription = null, Modifier.fillMaxSize())
        else Icon(LtIcons.Sources, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** The row's chevron: what a tappable row ends with in Cydia's lists. */
@Composable
private fun Chevron() {
    Icon(Icons.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** An empty list's one line. */
@Composable
private fun Empty(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(24.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
