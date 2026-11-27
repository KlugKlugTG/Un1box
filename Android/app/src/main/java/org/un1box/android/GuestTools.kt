package org.un1box.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.Inflater

/**
 * The guest tools shipped in the APK (assets/guest-tools/armv6.itpack and armv7.itpack, beside the helpers
 * firmwarekit bakes in). A new
 * device gets the pack with a family for its board and build, matched the way the library's
 * guest_offer matches it; firmware preparation reads the whole folder (firmwareTools).
 */
internal object GuestTools {
    private class Pack(val asset: String, val files: Map<String, ByteArray>, val families: List<JSONObject>)

    @Volatile private var cache: List<Pack>? = null

    private fun packs(context: Context): List<Pack> = cache ?: synchronized(this) {
        cache ?: (context.assets.list("guest-tools") ?: emptyArray()).filter { it.endsWith(".itpack") }.sorted().mapNotNull { name ->
            try { read("guest-tools/$name", context.assets.open("guest-tools/$name").use { it.readBytes() }) } catch (_: Exception) { null }
        }.also { cache = it }
    }

    /** ITPACK01: magic, a little-endian index length, the JSON index, then one zlib stream of every entry in order. */
    private fun read(asset: String, blob: ByteArray): Pack {
        require(blob.size > 12 && String(blob, 0, 8, Charsets.US_ASCII) == "ITPACK01") { "$asset is not a guest package" }
        val length = (blob[8].toInt() and 0xff) or ((blob[9].toInt() and 0xff) shl 8) or ((blob[10].toInt() and 0xff) shl 16) or ((blob[11].toInt() and 0xff) shl 24)
        val entries = JSONObject(String(blob, 12, length, Charsets.UTF_8)).getJSONArray("entries")
        val total = (0 until entries.length()).sumOf { entries.getJSONObject(it).getLong("size") }
        require(total in 1..(64L shl 20)) { "$asset exceeds the guest package limit" }
        val payload = ByteArray(total.toInt())
        Inflater().apply { setInput(blob, 12 + length, blob.size - 12 - length) }.run {
            var at = 0
            while (at < payload.size && !finished()) at += inflate(payload, at, payload.size - at).also { require(it > 0 || !needsInput()) { "$asset is truncated" } }
            end(); require(at == payload.size) { "$asset is truncated" }
        }
        var offset = 0
        val files = (0 until entries.length()).associate { i ->
            val entry = entries.getJSONObject(i); val size = entry.getInt("size")
            (entry.getString("name") to payload.copyOfRange(offset, offset + size)).also { offset += size }
        }
        val families = files.filterKeys { it.endsWith("/manifest.json") }.map { JSONObject(String(it.value, Charsets.UTF_8)) }
        return Pack(asset, files, families)
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private fun covers(family: JSONObject, board: String, build: String): Boolean {
        if (family.optBoolean("stub")) return false
        val requires = family.getJSONObject("requires")
        val major = build.takeWhile { it.isDigit() }
        return board in requires.getJSONArray("boards").strings() &&
            requires.getJSONArray("builds").strings().any { it == build || it == "$major*" }
    }

    /** The bundled pack for [board]-[build], copied out of the APK so the library can import it; null when none covers it. */
    fun forDevice(context: Context, board: String, build: String): File? {
        val pack = packs(context).firstOrNull { pack -> pack.families.any { covers(it, board, build) } } ?: return null
        val target = File(context.filesDir, "bundled-${pack.asset.substringAfterLast('/')}")
        val bytes = context.assets.open(pack.asset).use { it.readBytes() }
        if (!target.isFile || !target.readBytes().contentEquals(bytes)) {
            val partial = File(target.path + ".partial"); partial.writeBytes(bytes)
            check(partial.renameTo(target)) { "Could not stage the built-in guest tools" }
        }
        return target
    }

    private fun serial(pack: Pack, board: String, build: String) = pack.families.firstOrNull { covers(it, board, build) }?.optInt("serial", 0)

    /**
     * The bundled pack, staged, when it is newer than the guest tools device [recordId] carries; null otherwise. A
     * device keeps the tools it was given, so after an update of Un1box that bundles newer ones they are handed
     * over before the device starts, and iOS installs them during that boot. Never older: a newer pack the user
     * chose stays, and so does one this build cannot read.
     */
    fun newerFor(context: Context, recordId: String): File? {
        val record = File(context.filesDir, "library/records/$recordId")
        val metadata = JSONObject(File(record, "record.json").readText())
        val board = metadata.optString("board"); val build = metadata.optString("build")
        val bundled = packs(context).firstOrNull { pack -> pack.families.any { covers(it, board, build) } } ?: return null
        val installed = File(record, "assets/guest.itpack")
        val have = if (!installed.isFile) 0 else try { serial(read("guest.itpack", installed.readBytes()), board, build) ?: 0 } catch (_: Exception) { return null }
        return if ((serial(bundled, board, build) ?: 0) > have) forDevice(context, board, build) else null
    }

    /**
     * The folder firmwarekit create reads (--guest-tools): every file the APK carries under assets/guest-tools/ (the
     * helpers it bakes into a system, from qemu-ios contrib/export-guest-artifacts.sh, and both guest packages).
     * Rebuilt from the APK each time, so it always matches what this build ships.
     */
    fun firmwareTools(context: Context): File {
        val names = (context.assets.list("guest-tools") ?: emptyArray()).filter { !it.endsWith(".json") }
        check(names.any { it == "it_seal" }) { "This build of Un1box has no device preparation tools" }
        val root = File(context.filesDir, "firmware-tools")
        root.deleteRecursively()
        check(root.mkdirs()) { "Could not create the preparation tools folder" }
        for (name in names) {
            val target = File(root, name)
            context.assets.open("guest-tools/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
            // Mach-O helpers stay as the guest gets them; firmwarekit only reads them, but keeps their modes.
            if (!name.contains('.') || name.endsWith(".dylib")) target.setExecutable(true, false)
        }
        return root
    }

    /**
     * The iPhone 5's preparation tools (assets/guest-tools-a6): the A6 set the Rust firmwarekit's N42 recipe reads
     * (it_initialize, it_mobilegestalt, launchd-cache-template, it_agent, httpget-armv7, libappsync.dylib), which
     * this APK carries beside the older devices' tools. Null when this build carries none, so the caller keeps the
     * other devices' folder and the recipe says in words what it is missing.
     */
    fun firmwareToolsA6(context: Context): File? {
        if ((context.assets.list("guest-tools-a6") ?: emptyArray()).isEmpty()) return null
        val names = (context.assets.list("guest-tools-a6") ?: emptyArray()).filter { !it.endsWith(".json") }
        if (names.isEmpty() || "it_initialize" !in names) return null
        val root = File(context.filesDir, "firmware-tools-a6")
        root.deleteRecursively()
        check(root.mkdirs()) { "Could not create the iPhone 5's preparation tools folder" }
        for (name in names) {
            val target = File(root, name)
            context.assets.open("guest-tools-a6/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
            if (!name.contains('.') || name.endsWith(".dylib")) target.setExecutable(true, false)
        }
        return root
    }

}
