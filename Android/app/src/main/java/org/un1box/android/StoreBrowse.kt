package org.un1box.android

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The sections the store can be browsed by. The website's Featured, Charts, Categories and Collections, after the device's own list. */
internal enum class StoreTab(val title: String) {
    DEVICE("For this device"), INSTALLED("Installed"), FEATURED("Featured"), CHARTS("Charts"), CATEGORIES("Categories"), COLLECTIONS("Collections");

    /** What the segmented control can show: iOS 6's segments hold a word, not a sentence. */
    val short: String get() = when (this) {
        DEVICE -> "Device"; CATEGORIES -> "Genres"; COLLECTIONS -> "Sets"; else -> title
    }
}

/** A list of apps that loads in pages (or all at once), for one section of the store. */
internal class Listing {
    var rows by mutableStateOf<List<JSONObject>>(emptyList())
    var next by mutableStateOf<String?>(null)
    var total by mutableStateOf(0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
    var generation = 0
    /** Kept here, not in the screen, so a list stays where it was while an app's details are open. */
    val listState = LazyListState()
}

/**
 * What the store's browsing tabs show. The device tab (the compatibility-checked list and search) stays in
 * [StoreModel]; these sections come from the catalog's plain listings, which say nothing about the device.
 * Work runs on worker threads and posts back, like [StoreModel].
 */
internal class StoreBrowser(private val target: () -> StoreTarget?) {
    private val handler = Handler(Looper.getMainLooper())
    var tab by mutableStateOf(StoreTab.DEVICE)

    val featured = Listing()
    val chartList = LazyListState()
    val genreList = LazyListState()
    val collectionList = LazyListState()

    var chartType by mutableStateOf("top-free")
    private var chartDeviceChoice by mutableStateOf<String?>(null)
    var chartGenre by mutableStateOf<StoreGenre?>(null)
    var chart by mutableStateOf<StoreChart?>(null)
    var chartLoading by mutableStateOf(false)
    var chartError by mutableStateOf<String?>(null)
    private var chartDate: String? = null
    private var chartGeneration = 0
    /** iPad targets see the iPad charts unless the user picks otherwise. */
    var chartDevice: String
        get() = chartDeviceChoice ?: if (target()?.family == "2") "ipad" else "iphone"
        set(value) { chartDeviceChoice = value }

    var genres by mutableStateOf<List<StoreGenre>?>(null)
    var genresError by mutableStateOf<String?>(null)
    var category by mutableStateOf<StoreGenre?>(null)
    var categorySort by mutableStateOf("versions")
    val categoryApps = Listing()

    var collections by mutableStateOf<List<StoreCollection>?>(null)
    var collectionsError by mutableStateOf<String?>(null)
    var collection by mutableStateOf<StoreCollection?>(null)
    val collectionApps = Listing()

    private fun background(name: String, work: () -> Unit) = Thread(work, name).start()
    private fun post(work: () -> Unit) = handler.post(work)

    val canGoBack get() = (tab == StoreTab.CATEGORIES && category != null) || (tab == StoreTab.COLLECTIONS && collection != null)
    fun back() { if (tab == StoreTab.CATEGORIES) category = null else collection = null }

    /** Another device record: what was loaded for the last one (its era's charts, its apps) is not this one's. */
    fun reset() {
        for (listing in listOf(featured, categoryApps, collectionApps)) { listing.generation++; listing.rows = emptyList(); listing.next = null; listing.loaded = false; listing.loading = false; listing.error = null }
        chartGeneration++; chart = null; chartDate = null; chartError = null; chartLoading = false; chartGenre = null; chartDeviceChoice = null
        category = null; collection = null
    }

    /** Switch to a tab and load what it shows, once. */
    fun show(next: StoreTab) {
        tab = next
        when (next) {
            StoreTab.DEVICE, StoreTab.INSTALLED -> {}
            StoreTab.FEATURED -> if (!featured.loaded && !featured.loading) loadFeatured()
            StoreTab.CHARTS -> { loadGenres(); if (chart == null && !chartLoading && chartError == null) loadChart(chartDate ?: eraDate()) }
            StoreTab.CATEGORIES -> loadGenres()
            StoreTab.COLLECTIONS -> if (collections == null) loadCollections()
        }
    }

    private fun load(listing: Listing, reset: Boolean, fetch: () -> StorePage) {
        if (listing.loading && !reset) return
        val generation = ++listing.generation
        if (reset) { listing.rows = emptyList(); listing.next = null; listing.total = 0; listing.loaded = false }
        listing.loading = true; listing.error = null
        background("store-browse") {
            val outcome = runCatching(fetch)
            post {
                if (generation != listing.generation) return@post
                listing.loading = false
                outcome.onSuccess {
                    listing.rows = listing.rows + it.rows; listing.next = it.next; listing.total = it.total; listing.loaded = true
                    if (reset) listing.listState.requestScrollToItem(0)
                }
                    .onFailure { listing.error = it.message ?: "Couldn't load" }
            }
        }
    }

    /** The next page of a listing that has one. */
    fun more(listing: Listing) { listing.next?.let { address -> if (!listing.loading) load(listing, false) { LegacyStore.page(address) } } }

    fun loadFeatured() = load(featured, true) { LegacyStore.collection("featured") }

    /** Roughly when the device's iOS was in use: a year after its release, so the charts are of apps it can run. */
    private fun eraDate(): String? = target()?.ios?.substringBefore('.')?.toIntOrNull()?.let { "${(2007 + it).coerceAtMost(2025)}-07-01" }

    fun loadChart(date: String? = chartDate) {
        chartDate = date
        val generation = ++chartGeneration
        chartLoading = true; chartError = null
        val type = chartType; val device = chartDevice; val genre = chartGenre?.id
        background("store-chart") {
            val outcome = runCatching { LegacyStore.chart(type, device, genre, date) }
            post {
                if (generation != chartGeneration) return@post
                chartLoading = false
                outcome.onSuccess { chart = it; chartList.requestScrollToItem(0) }.onFailure { chart = null; chartError = it.message ?: "Couldn't load the chart" }
            }
        }
    }

    fun pickChartType(slug: String) { chartType = slug; loadChart() }
    fun pickChartDevice(device: String) { chartDevice = device; loadChart() }
    fun pickChartGenre(genre: StoreGenre?) { chartGenre = genre; loadChart() }
    fun pickChartYear(year: String) = loadChart("$year-07-01")
    /** The previous (-1) or next (+1) archived snapshot of this chart. */
    fun shiftChart(delta: Int) {
        val current = chart ?: return
        val index = current.dates.indexOf(current.date)
        current.dates.getOrNull(index + delta)?.let { loadChart(it) }
    }

    fun loadGenres() {
        if (genres != null) return
        genresError = null
        background("store-genres") {
            val outcome = runCatching { LegacyStore.genres() }
            post { outcome.onSuccess { genres = it }.onFailure { genresError = it.message ?: "Couldn't load the categories" } }
        }
    }

    fun openCategory(genre: StoreGenre) { category = genre; loadCategory() }
    fun pickCategorySort(sort: String) { categorySort = sort; loadCategory() }
    private fun loadCategory() { val genre = category ?: return; val sort = categorySort; load(categoryApps, true) { LegacyStore.genreApps(genre.id, sort) } }

    private fun loadCollections() {
        collectionsError = null
        background("store-collections") {
            val outcome = runCatching { LegacyStore.collections() }
            post { outcome.onSuccess { collections = it }.onFailure { collectionsError = it.message ?: "Couldn't load the collections" } }
        }
    }
    fun openCollection(item: StoreCollection) { collection = item; load(collectionApps, true) { LegacyStore.collection(item.slug) } }
    fun retryCollections() = loadCollections()
}

@Composable
internal fun BrowseTabs(browser: StoreBrowser) {
    // iOS 6 segmented control under the bar: the six sections of the store, the chosen one blue.
    Ios6Segmented(StoreTab.entries.map { it.short }, browser.tab.ordinal, { browser.show(StoreTab.entries[it]) },
        Modifier.testTag("store_tabs").padding(horizontal = 8.dp, vertical = 6.dp), compact = true,
        tags = StoreTab.entries.mapIndexed { index, tab -> index to "store_tab_" + tab.name.lowercase() }.toMap())
}

/** An app list that pages in as it is scrolled to the end. */
@Composable
internal fun ColumnScope.AppListing(listing: Listing, browser: StoreBrowser, model: StoreModel, onRetry: () -> Unit, empty: String = "No apps found.") {
    val state = listing.listState
    val atEnd by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.let { it.index >= state.layoutInfo.totalItemsCount - 5 } == true } }
    LaunchedEffect(atEnd, listing.rows.size, listing.next) { if (atEnd && listing.next != null) browser.more(listing) }
    if (listing.loading && listing.rows.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
    listing.error?.let { ErrorLine(it, onRetry) }
    if (listing.loaded && listing.rows.isEmpty() && listing.error == null) Text(empty, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyColumn(Modifier.weight(1f).testTag("store_browse_results"), state = state, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        items(listing.rows) { app -> ResultRow(app, model) }
        if (listing.loading && listing.rows.isNotEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
    }
}

@Composable
private fun ErrorLine(message: String, onRetry: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
internal fun ColumnScope.FeaturedTab(model: StoreModel) {
    val browser = model.browser
    Text("Hand-picked by the archive. Whether a version runs on this device is checked when you open an app.", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    AppListing(browser.featured, browser, model, browser::loadFeatured)
}

@Composable
internal fun ColumnScope.ChartsTab(model: StoreModel) {
    val browser = model.browser
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((slug, title) in CHART_TYPES) FilterChip(browser.chartType == slug, { browser.pickChartType(slug) }, label = { Text(title) })
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((device, title) in listOf("iphone" to "iPhone", "ipad" to "iPad")) FilterChip(browser.chartDevice == device, { browser.pickChartDevice(device) }, label = { Text(title) })
        var menu by remember { mutableStateOf(false) }
        Box {
            AssistChip({ menu = true }, label = { Text(browser.chartGenre?.name ?: "All apps") }, trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) })
            DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 360.dp)) {
                DropdownMenuItem(text = { Text("All apps") }, onClick = { menu = false; browser.pickChartGenre(null) })
                for (genre in browser.genres.orEmpty()) DropdownMenuItem(text = { Text(genre.name) }, onClick = { menu = false; browser.pickChartGenre(genre) })
            }
        }
    }
    val chart = browser.chart
    if (chart != null) SnapshotBar(chart, browser)
    if (browser.chartLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
    browser.chartError?.let { ErrorLine(it) { browser.loadChart() } }
    LazyColumn(Modifier.weight(1f).testTag("store_chart"), state = browser.chartList, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        items(chart?.rows.orEmpty(), key = { it.position }) { row ->
            if (row.app != null) ResultRow(row.app, model, rank = row.position) else GhostRow(row)
        }
        if (chart != null && chart.rows.any { it.app == null }) item {
            Text("Titles in grey haven't been preserved in the archive yet.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Which snapshot is showing, with the archived ones before and after it. */
@Composable
private fun SnapshotBar(chart: StoreChart, browser: StoreBrowser) {
    val index = chart.dates.indexOf(chart.date)
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { browser.shiftChart(-1) }, enabled = index > 0 && !browser.chartLoading) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier snapshot") }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextButton(onClick = { menu = true }) {
                Text(snapshotDate(chart.date)); Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 360.dp)) {
                for (year in chart.dates.map { it.take(4) }.distinct()) DropdownMenuItem(text = { Text(year) }, onClick = { menu = false; browser.pickChartYear(year) })
            }
        }
        IconButton(onClick = { browser.shiftChart(1) }, enabled = index in 0 until chart.dates.size - 1 && !browser.chartLoading) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later snapshot") }
    }
}

private fun snapshotDate(date: String): String =
    runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(date)

@Composable
private fun GhostRow(row: ChartRow) {
    Row(Modifier.fillMaxWidth().alpha(0.5f).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(row.position.toString(), Modifier.width(32.dp), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text("Not in the archive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ColumnScope.CategoriesTab(model: StoreModel) {
    val browser = model.browser
    val category = browser.category
    if (category == null) {
        val genres = browser.genres
        if (genres == null && browser.genresError == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        browser.genresError?.let { ErrorLine(it) { browser.loadGenres() } }
        LazyColumn(Modifier.weight(1f).testTag("store_categories"), state = browser.genreList, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            items(genres.orEmpty(), key = { it.id }) { genre ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                    .ios6Lacquer(RoundedCornerShape(9.dp), Color.White, Color(0xFFF4F4F7), Color(0xFF8E8E93))
                    .clickable { browser.openCategory(genre) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(genre.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(genre.count.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Ios6Chevron()
                }
            }
        }
    } else {
        SectionHeader(category.name, browser.categoryApps.total.takeIf { it > 0 }?.let { "$it apps" })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((sort, title) in CATEGORY_SORTS) FilterChip(browser.categorySort == sort, { browser.pickCategorySort(sort) }, label = { Text(title) })
        }
        AppListing(browser.categoryApps, browser, model, { browser.pickCategorySort(browser.categorySort) })
    }
}

@Composable
internal fun ColumnScope.CollectionsTab(model: StoreModel) {
    val browser = model.browser
    val collection = browser.collection
    if (collection == null) {
        val list = browser.collections
        if (list == null && browser.collectionsError == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        browser.collectionsError?.let { ErrorLine(it, browser::retryCollections) }
        LazyColumn(Modifier.weight(1f).testTag("store_collections"), state = browser.collectionList, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            items(list.orEmpty(), key = { it.slug }) { item ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                    .ios6Lacquer(RoundedCornerShape(9.dp), Color.White, Color(0xFFF4F4F7), Color(0xFF8E8E93))
                    .clickable { browser.openCollection(item) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Text(item.count.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Ios6Chevron()
                }
            }
        }
    } else {
        SectionHeader(collection.title, collection.subtitle)
        AppListing(browser.collectionApps, browser, model, { browser.openCollection(collection) })
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    // Back is the top bar's arrow, which steps out of the category or collection first.
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
