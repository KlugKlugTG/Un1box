package org.un1box.android

import android.content.Context
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * The Jailbreak tab's store: what Cydia shows, fetched and unpacked by the phone.
 *
 * Cydia inside the guest is slow for reasons that are not the guest's: its TLS stack is from 2010,
 * its repositories are mostly gone, and every request is emulated instruction by instruction. The
 * packages themselves are still there, so this does the browsing and the downloading on the phone --
 * a modern stack over the phone's own network -- and stages the .deb in the guest's
 * /var/root/Media/Cydia/AutoInstall, which Cydia's own startup job installs at the next boot.
 *
 * The index is a Debian control file (`Packages`, usually gzipped): stanzas of `Key: value`, folded
 * continuation lines, and `Filename` relative to the source. Sources are kept as the user typed
 * them: a repository root ("http://repo.example/") is probed at the paths Cydia's own layout uses,
 * and anything ending in "Packages", "Packages.gz" or "Packages.bz2" is fetched as it is.
 */
internal object JailbreakSources {
    private const val STORE = "jailbreak"
    private const val KEY = "sources"

    /** What the jailbreak itself writes into Cydia's list (SystemEdits/Cydia.swift's sources), plus
     *  the ones that still answer for these systems. Nothing here is required: the list is the user's. */
    val DEFAULTS = listOf(
        "http://apt.saurik.com/",
        "http://apt.thebigboss.org/repofiles/cydia/",
    )

    fun read(context: Context): List<String> {
        val stored = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return DEFAULTS
        val parsed = try { JSONArray(stored) } catch (_: Exception) { return DEFAULTS }
        val list = (0 until parsed.length()).mapNotNull { parsed.optString(it).takeIf { url -> url.isNotBlank() } }
        return list.ifEmpty { DEFAULTS }
    }

    fun write(context: Context, sources: List<String>) {
        val array = JSONArray()
        sources.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { array.put(it) }
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

/** One package of a source's index, as much of it as the tab shows. */
internal data class JailbreakPackage(
    val name: String,
    val version: String,
    val architecture: String,
    val bytes: Long,
    val filename: String,
    val summary: String,
    val source: String,
    val section: String = "",
) {
    val title get() = name
    val detail get() = listOfNotNull(version.takeIf { it.isNotBlank() }, sizeText).joinToString(" · ")

    /** A package built for 64-bit iOS only cannot be installed on any device this app prepares. */
    val wrongArchitecture get() = architecture.contains("arm64", ignoreCase = true)
    val sizeText get() = when {
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576.0)
        bytes >= 1 shl 10 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

internal class JailbreakStore {
    class Failure(message: String) : Exception(message)

    /** How an index may be packed, in the order the tab tries it. */
    private val indexes = listOf("Packages.gz", "Packages")

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Un1box/" + android.os.Build.MODEL)
        connection.setRequestProperty("Accept-Encoding", "identity")
        return connection
    }

    /** The URLs an index may live at, for a source the user typed either way. */
    fun indexCandidates(source: String): List<String> {
        val base = source.trim().trimEnd('/')
        if (base.endsWith("Packages") || base.endsWith("Packages.gz") || base.endsWith("Packages.bz2"))
            return listOf(base)
        val dists = listOf("dists/stable/main/binary-iphoneos-arm", "dists/ios/main/binary-iphoneos-arm",
                           "dists/stable/main/binary-iphoneos-arm64", "")
        return dists.flatMap { dist -> indexes.map { index -> if (dist.isEmpty()) "$base/$index" else "$base/$dist/$index" } }
    }

    /**
     * The index of `source`, unpacked. gzip and plain are what the phone reads itself (Android has no
     * bz2), and a repository that answers with its own web page -- several of these are retired -- is
     * reported as that rather than as an index with nothing in it.
     */
    fun fetchIndex(source: String): Pair<String, ByteArray> {
        var last = "no index found"
        for (candidate in indexCandidates(source)) {
            val connection = try { open(candidate) } catch (error: IOException) { last = error.message ?: "?"; continue }
            try {
                val status = connection.responseCode
                if (status != 200) { last = "HTTP $status at ${candidate.substringAfter("//")}"; continue }
                val raw = connection.inputStream.use { it.readBytes() }
                val body = if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte())
                    GZIPInputStream(BufferedInputStream(raw.inputStream())).use { it.readBytes() } else raw
                val complaint = notAnIndex(body, candidate)
                if (complaint != null) { last = complaint; continue }
                return candidate to body
            } catch (error: IOException) {
                last = error.message ?: error.javaClass.simpleName
            } finally {
                connection.disconnect()
            }
        }
        throw Failure("No readable package index: $last")
    }

    /** The index's own URL and its packages: one download, whichever the caller needs. */
    /**
     * Why these bytes are not a package index, or null when they are. Several of these repositories
     * are retired and answer with their own web page -- a payment page, a "this repo is gone" notice --
     * and that is worth saying instead of showing an empty list.
     */
    fun notAnIndex(body: ByteArray, url: String): String? {
        if (body.isEmpty()) return "empty index at $url"
        // String(...), not take(64).toString(...): ByteArray.take gives a List<Byte>.
        val head = String(body, 0, minOf(64, body.size), Charsets.UTF_8).trimStart()
        if (head.startsWith("<")) return "this repository answers with a web page, not an index"
        return null
    }

    fun packages(source: String): Pair<String, List<JailbreakPackage>> {
        val (url, body) = fetchIndex(source)
        return url to parseIndex(body.toString(Charsets.UTF_8), source, url)
    }

    /**
     * The control file's stanzas. A field's value continues on the lines that start with a space; the
     * ones the tab needs are name, version, architecture, size and filename, and a description's first
     * line is the summary Cydia shows in a list.
     */
    fun parseIndex(text: String, source: String, indexUrl: String = source): List<JailbreakPackage> {
        val packages = ArrayList<JailbreakPackage>()
        var fields = HashMap<String, String>()
        fun flush() {
            val name = fields["package"]
            val filename = fields["filename"]
            if (name != null && filename != null) {
                packages += JailbreakPackage(
                    name = name,
                    version = fields["version"].orEmpty(),
                    architecture = fields["architecture"].orEmpty(),
                    bytes = fields["size"]?.trim()?.toLongOrNull() ?: 0L,
                    filename = filename,
                    summary = fields["description"].orEmpty().lineSequence().firstOrNull().orEmpty(),
                    source = source,
                    section = fields["section"].orEmpty(),
                )
            }
            fields = HashMap()
        }
        var lastKey: String? = null
        for (line in text.lineSequence()) {
            when {
                line.isBlank() -> { flush(); lastKey = null }
                line.startsWith(" ") || line.startsWith("\t") -> {
                    val key = lastKey ?: continue
                    fields[key] = (fields[key].orEmpty() + "\n" + line.trim()).trim()
                }
                else -> {
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val key = line.substring(0, colon).trim().lowercase()
                    fields[key] = line.substring(colon + 1).trim()
                    lastKey = key
                }
            }
        }
        flush()
        return packages
    }

    /**
     * The archive root a `Filename` is relative to. Debian's rule, and Telesphoreo is the check: its
     * index lives in /dists/ios/main/binary-iphoneos-arm/ and its packages in /debs/, so joining the
     * file name to the index's own directory asks for a 404.
     */
    fun archiveRoot(indexUrl: String): String {
        val dists = indexUrl.indexOf("/dists/")
        if (dists > 0) return indexUrl.substring(0, dists)
        return indexUrl.substringBeforeLast('/', "")
    }

    /** The .deb's own URL: `Filename` relative to the archive root, or absolute as it is. */
    fun packageUrl(pkg: JailbreakPackage, indexUrl: String): String {
        if (pkg.filename.startsWith("http://") || pkg.filename.startsWith("https://")) return pkg.filename
        return archiveRoot(indexUrl).trimEnd('/') + "/" + pkg.filename.trimStart('/')
    }

    /** Downloads the package beside the app's cache and returns the file. */
    fun download(pkg: JailbreakPackage, indexUrl: String, into: File, progress: (Int) -> Unit): File {
        val url = packageUrl(pkg, indexUrl)
        val connection = try { open(url) } catch (error: IOException) {
            throw Failure("Cannot reach ${url.substringAfter("//")}: ${error.message ?: "?"}")
        }
        connection.connect()
        if (connection.responseCode != 200) {
            connection.disconnect()
            throw Failure("${url.substringAfter("//")} answered HTTP ${connection.responseCode}")
        }
        val total = connection.contentLengthLong
        into.parentFile?.mkdirs()
        connection.inputStream.use { input ->
            into.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (total > 0) progress((copied * 100 / total).toInt())
                }
            }
        }
        connection.disconnect()
        if (into.length() == 0L) throw Failure("The download was empty")
        return into
    }

    /**
     * The packages a device already has, read out of dpkg's own status file (the pinned bootstrap
     * keeps it at private/var/lib/dpkg/status). Cydia shows the same list under "Installed", and
     * without it every package looks uninstalled.
     */
    fun installedFromStatus(status: String): Map<String, String> {
        val installed = HashMap<String, String>()
        var name: String? = null
        var version = ""
        var state = ""
        fun flush() {
            if (name != null && state.startsWith("install ok installed")) installed[name!!] = version
            name = null
            version = ""
            state = ""
        }
        for (line in status.lineSequence()) {
            when {
                line.isBlank() -> flush()
                line.startsWith("Package:") -> name = line.substringAfter(':').trim()
                line.startsWith("Version:") -> version = line.substringAfter(':').trim()
                line.startsWith("Status:") -> state = line.substringAfter(':').trim()
            }
        }
        flush()
        return installed
    }

    /** The guest file a device's installed packages are read from. */
    val statusPath get() = "/private/var/lib/dpkg/status"

    /**
     * Where the guest's own Cydia startup job installs from. Not a guess: the pinned bootstrap's
     * /usr/libexec/cydia/startup installs the .deb files of /var/root/Media/Cydia/AutoInstall with
     * dpkg, so that folder -- root's Media, not mobile's -- is where a package has to land. (No glob
     * in this comment on purpose: Kotlin's block comments nest, and a slash-star in one never closes.)
     */
    val autoInstallDir get() = "/private/var/root/Media/Cydia/AutoInstall"

    /** The staged name: the .deb's own, so dpkg sees a package name it recognises. */
    fun stagedPath(pkg: JailbreakPackage): String {
        val file = pkg.filename.substringAfterLast('/').ifEmpty { "${pkg.name}.deb" }
        val safe = file.map { if (it.isLetterOrDigit() || it in "._+-") it else '_' }.joinToString("")
        return "$autoInstallDir/${if (safe.endsWith(".deb")) safe else "$safe.deb"}"
    }
}

/**
 * Every source's packages as one list, the way Cydia's lists are: one row per package name, the
 * newest version of it, and the source that offered it. A source that did not answer is kept as a
 * complaint rather than dropped silently -- "Cydia's Sources list would show it too".
 */
internal class JailbreakCatalog {
    private val byName = LinkedHashMap<String, JailbreakPackage>()
    private val complaints = LinkedHashMap<String, String>()

    val failures: Map<String, String> get() = complaints

    fun clear() {
        byName.clear()
        complaints.clear()
    }

    fun add(source: String, packages: List<JailbreakPackage>) {
        complaints.remove(source)
        for (package_ in packages) {
            val known = byName[package_.name]
            if (known == null || compareVersions(package_.version, known.version) > 0) byName[package_.name] = package_
        }
    }

    fun failed(source: String, why: String) {
        complaints[source] = why
    }

    fun packages(): List<JailbreakPackage> = byName.values.sortedBy { it.name.lowercase() }

    /** What the list can be grouped by, with how many packages each has: Cydia's own section list. */
    fun sections(): List<Pair<String, Int>> = byName.values
        .groupingBy { it.section.ifBlank { "Other" } }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }

    /** Packages the device has at a different version than the list offers. */
    fun updates(installed: Map<String, String>): List<JailbreakPackage> = packages()
        .filter { installed[it.name] != null && compareVersions(it.version, installed[it.name]!!) > 0 }

    fun search(text: String): List<JailbreakPackage> {
        val needle = text.trim()
        if (needle.isEmpty()) return emptyList()
        return packages().filter {
            it.name.contains(needle, true) || it.summary.contains(needle, true) ||
                it.section.contains(needle, true)
        }
    }

    /**
     * Debian versions compared leniently: runs of digits as numbers, everything else as text. Enough
     * for "is this an upgrade", which is what the tab asks -- not dpkg's own ordering, which no list
     * of a few thousand packages needs.
     */
    companion object {
        fun compareVersions(left: String, right: String): Int {
            var a = 0
            var b = 0
            while (a < left.length || b < right.length) {
                if (a < left.length && b < right.length && left[a].isDigit() && right[b].isDigit()) {
                    var x = a
                    var y = b
                    while (x < left.length && left[x].isDigit()) x++
                    while (y < right.length && right[y].isDigit()) y++
                    val leftNumber = left.substring(a, x).trimStart('0')
                    val rightNumber = right.substring(b, y).trimStart('0')
                    if (leftNumber.length != rightNumber.length) return leftNumber.length - rightNumber.length
                    if (leftNumber != rightNumber) return leftNumber.compareTo(rightNumber)
                    a = x
                    b = y
                    continue
                }
                val leftChar = if (a < left.length) left[a] else '\u0000'
                val rightChar = if (b < right.length) right[b] else '\u0000'
                if (leftChar != rightChar) return leftChar.compareTo(rightChar)
                a++
                b++
            }
            return 0
        }
    }
}

/**
 * One package's way from a repository to the guest: downloaded by the phone, then staged in the
 * guest's AutoInstall folder over AFC, where the guest's own Cydia startup job installs it at the
 * next boot. Nothing runs in the guest: its 2010 TLS stack and its emulated CPU are what made
 * Cydia slow, and neither is on this path.
 */
internal class JailbreakInstaller(private val context: Context, private val socket: () -> String) {
    class Failure(message: String) : Exception(message)

    /** The device's installed packages (name -> version), or an empty map when it has no dpkg state. */
    fun installed(): Map<String, String> {
        val socket = socket()
        if (socket.isEmpty()) return emptyMap()
        val store = JailbreakStore()
        val file = File(context.cacheDir, "jailbreak/status")
        return try {
            GuestApps(context).pull(socket, store.statusPath, file, timeoutSeconds = 60)
            store.installedFromStatus(file.readText())
        } catch (_: Exception) {
            emptyMap()   // a device that was never jailbroken has no status file: not an error here
        } finally {
            file.delete()
        }
    }

    /** Returns the guest path the package is waiting at. */
    fun install(pkg: JailbreakPackage, indexUrl: String, progress: (String, Int) -> Unit): String {
        val socket = socket()
        if (socket.isEmpty()) throw Failure("Start the device first: the package is put into its file system.")
        val store = JailbreakStore()
        val directory = File(context.cacheDir, "jailbreak").apply { mkdirs() }
        val file = File(directory, pkg.filename.substringAfterLast('/').ifEmpty { pkg.name + ".deb" })
        progress("Downloading", 0)
        store.download(pkg, indexUrl, file) { progress("Downloading", it) }
        val staged = store.stagedPath(pkg)
        progress("Copying into the device", 0)
        val apps = GuestApps(context)
        apps.push(socket, file, staged, timeoutSeconds = 600, event = { event ->
            if (event.optString("phase") == "upload") progress("Copying into the device", event.optInt("percent"))
        })
        file.delete()
        return staged
    }
}
