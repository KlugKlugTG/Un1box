package org.un1box.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Jailbreak tab reads out of a repository: the control file's stanzas, the URLs they turn
 * into, and the name the .deb is staged under.
 *
 * The index is the one thing the tab depends on that a person cannot see it get wrong: a field that
 * is not unfolded, a `Filename` joined to the wrong root or a package staged under a name dpkg does
 * not recognise each produce a silently useless install. These are the shapes a real `Packages` file
 * has, including the folded description Cydia shows as a summary.
 */
class JailbreakStoreTest {
    private val store = JailbreakStore()

    private val index = """
        Package: com.example.first
        Version: 1.2.3
        Architecture: iphoneos-arm
        Size: 40960
        Filename: debs/first_1.2.3_iphoneos-arm.deb
        Description: The first line is the summary
         and this line is part of it, with a blank line below.

        Package: com.example.second
        Version: 2.0
        Architecture: iphoneos-arm
        Filename: ./debs/second.deb
        Description: Second
    """.trimIndent()

    @Test
    fun `every stanza becomes one package`() {
        val packages = store.parseIndex(index, "http://repo.example/")
        assertEquals(2, packages.size)
        assertEquals("com.example.first", packages[0].name)
        assertEquals("1.2.3", packages[0].version)
        assertEquals(40960L, packages[0].bytes)
        assertEquals("The first line is the summary", packages[0].summary)
        assertEquals("com.example.second", packages[1].name)
    }

    @Test
    fun `a stanza without a name or a file is not a package`() {
        val packages = store.parseIndex(
            "Package: no-file\nVersion: 1\n\nFilename: no-name.deb\nVersion: 1\n", "http://repo.example/")
        assertTrue(packages.isEmpty())
    }

    @Test
    fun `a repository root is probed where cydia's layout puts the index`() {
        val candidates = store.indexCandidates("http://repo.example/")
        assertTrue(candidates.any { it == "http://repo.example/dists/stable/main/binary-iphoneos-arm/Packages.gz" })
        assertTrue(candidates.any { it == "http://repo.example/dists/ios/main/binary-iphoneos-arm/Packages.gz" })
        assertTrue(candidates.any { it == "http://repo.example/Packages.gz" })
        // bz2 is tried, last, through the app's own decompressor: BigBoss serves nothing else.
        assertTrue(candidates.contains("http://repo.example/dists/stable/main/binary-iphoneos-arm/Packages.bz2"))
        assertTrue(candidates.indexOf("http://repo.example/Packages.bz2") >
            candidates.indexOf("http://repo.example/Packages.gz"))
    }

    @Test
    fun `an index url the user typed is used as it is`() {
        val typed = "http://repo.example/dists/ios/main/binary-iphoneos-arm/Packages.gz"
        assertEquals(listOf(typed), store.indexCandidates(typed))
    }

    @Test
    fun `a package's file is relative to the archive root, not to the index's directory`() {
        // Telesphoreo is the shape this is taken from: /dists/ios/main/binary-iphoneos-arm/Packages
        // lists Filename: debs/..., and the file is at /debs/... (the index's own directory would 404).
        val pkg = store.parseIndex(index, "http://repo.example/")[0]
        val indexUrl = "http://repo.example/dists/stable/main/binary-iphoneos-arm/Packages.gz"
        assertEquals("http://repo.example/debs/first_1.2.3_iphoneos-arm.deb", store.packageUrl(pkg, indexUrl))
        assertEquals("http://repo.example", store.archiveRoot(indexUrl))
        assertEquals("http://repo.example", store.archiveRoot("http://repo.example/Packages"))
    }

    @Test
    fun `a package is staged where cydia's own startup script installs from, under its own name`() {
        val packages = store.parseIndex(index, "http://repo.example/")
        assertEquals("/private/var/root/Media/Cydia/AutoInstall/first_1.2.3_iphoneos-arm.deb",
            store.stagedPath(packages[0]))
        assertEquals("/private/var/root/Media/Cydia/AutoInstall/second.deb", store.stagedPath(packages[1]))
    }

    @Test
    fun `a repository that answers with a web page is reported as that, not as an empty index`() {
        val page = "<!DOCTYPE html>\n<html><body>This repository is closed</body></html>".toByteArray()
        assertEquals("this repository answers with a web page, not an index",
            store.notAnIndex(page, "http://repo.example/Packages"))
        val control = "Package: com.example.first\nVersion: 1\nFilename: x.deb\n".toByteArray()
        assertEquals(null, store.notAnIndex(control, "http://repo.example/Packages"))
        assertEquals("empty index at http://repo.example/Packages",
            store.notAnIndex(ByteArray(0), "http://repo.example/Packages"))
    }

    @Test
    fun `a plain index, which is what the live repositories serve, parses like a gzipped one`() {
        val packages = store.parseIndex(index, "http://repo.example/", "http://repo.example/Packages")
        assertEquals(2, packages.size)
        assertEquals("http://repo.example/debs/first_1.2.3_iphoneos-arm.deb",
            store.packageUrl(packages[0], "http://repo.example/Packages"))   // the root itself
    }

    @Test
    fun `the installed list comes from dpkg's own status, and only for packages that are installed`() {
        val status = """
            Package: com.example.first
            Status: install ok installed
            Version: 1.2.3

            Package: com.example.second
            Status: deinstall ok config-files
            Version: 2.0

            Package: com.example.third
            Status: install ok installed
            Version: 3.1

        """.trimIndent()
        val installed = store.installedFromStatus(status)
        assertEquals(mapOf("com.example.first" to "1.2.3", "com.example.third" to "3.1"), installed)
    }

    @Test
    fun `a package knows the section it is filed under, and a 64-bit only one is flagged`() {
        val withSection = """
            Package: com.example.tweak
            Version: 1.0
            Section: Tweaks
            Architecture: iphoneos-arm
            Filename: debs/tweak.deb

        """.trimIndent()
        val tweak = store.parseIndex(withSection, "http://repo.example/")[0]
        assertEquals("Tweaks", tweak.section)
        assertEquals(false, tweak.wrongArchitecture)
        assertEquals(true, tweak.copy(architecture = "iphoneos-arm64").wrongArchitecture)
    }

    @Test
    fun `the catalog keeps the newest version of a package and remembers which source failed`() {
        val catalog = JailbreakCatalog()
        catalog.add("http://one.example/", listOf(
            JailbreakPackage("com.example.tweak", "1.2.3", "iphoneos-arm", 10, "debs/t.deb", "A tweak", "http://one.example/", "Tweaks")))
        catalog.add("http://two.example/", listOf(
            JailbreakPackage("com.example.tweak", "1.10", "iphoneos-arm", 12, "debs/t.deb", "A tweak", "http://two.example/", "Tweaks"),
            JailbreakPackage("com.example.other", "0.1", "iphoneos-arm", 5, "debs/o.deb", "Other", "http://two.example/", "Utilities")))
        catalog.failed("http://gone.example/", "this repository answers with a web page, not an index")

        assertEquals(2, catalog.packages().size)
        assertEquals("1.10", catalog.packages().first { it.name == "com.example.tweak" }.version)
        assertEquals(1, catalog.failures.size)
        assertEquals(listOf("Tweaks" to 1, "Utilities" to 1), catalog.sections())
    }

    @Test
    fun `versions compare the way an upgrade question needs`() {
        assertEquals(1, JailbreakCatalog.compareVersions("1.10", "1.2.3"))
        assertEquals(-1, JailbreakCatalog.compareVersions("1.2.3", "1.10"))
        assertEquals(0, JailbreakCatalog.compareVersions("2.0", "2.0"))
        assertEquals(1, JailbreakCatalog.compareVersions("1.0.1", "1.0"))
        assertEquals(-1, JailbreakCatalog.compareVersions("0.9", "1.0"))
        assertEquals(1, JailbreakCatalog.compareVersions("1.0-2", "1.0-1"))
    }

    @Test
    fun `the updates list is what the device has older, and installed is what it has at all`() {
        val catalog = JailbreakCatalog()
        catalog.add("http://one.example/", listOf(
            JailbreakPackage("a", "2.0", "iphoneos-arm", 1, "a.deb", "", "http://one.example/"),
            JailbreakPackage("b", "1.0", "iphoneos-arm", 1, "b.deb", "", "http://one.example/"),
            JailbreakPackage("c", "3.0", "iphoneos-arm", 1, "c.deb", "", "http://one.example/")))
        val installed = mapOf("a" to "1.0", "b" to "1.0", "z" to "9.9")
        assertEquals(listOf("a"), catalog.updates(installed).map { it.name })
        assertTrue(catalog.search("B").any { it.name == "b" })
    }

    @Test
    fun `a package keeps the icon its control file names, and its URL is resolved like its file`() {
        val withIcon = """
            Package: com.example.tweak
            Version: 1.0
            Icon: http://icons.example/tweak.png
            Filename: debs/tweak.deb

        """.trimIndent()
        val pkg = store.parseIndex(withIcon, "http://repo.example/")[0]
        assertEquals("http://icons.example/tweak.png", pkg.icon)
        assertEquals("http://icons.example/tweak.png",
            JailbreakIcons.packageIcon(pkg, "http://repo.example/Packages"))

        val relative = pkg.copy(icon = "icons/tweak.png")
        assertEquals("http://repo.example/icons/tweak.png",
            JailbreakIcons.packageIcon(relative, "http://repo.example/dists/ios/main/binary-iphoneos-arm/Packages"))
        assertEquals("", JailbreakIcons.packageIcon(pkg.copy(icon = ""), "http://repo.example/Packages"))
    }

    @Test
    fun `a repository's icon is the one cydia keeps beside its index`() {
        assertEquals("http://repo.example/CydiaIcon.png", JailbreakIcons.repositoryIcon("http://repo.example/"))
        assertEquals("http://repo.example/CydiaIcon.png",
            JailbreakIcons.repositoryIcon("http://repo.example/dists/ios/main/binary-iphoneos-arm/Packages"))
    }

    @Test
    fun `a repository's icon is looked for beside its release first, then at the root`() {
        // Telesphoreo and BigBoss both serve dists/<suite>/CydiaIcon.png; a root-level copy is the
        // fallback for the repositories that use one.
        val index = "http://apt.saurik.com/dists/ios/main/binary-iphoneos-arm/Packages"
        assertEquals(listOf("http://apt.saurik.com/dists/ios/CydiaIcon.png", "http://apt.saurik.com/CydiaIcon.png"),
            JailbreakIcons.repositoryIcons("http://apt.saurik.com/", index))
        assertEquals(listOf("http://repo.example/CydiaIcon.png"),
            JailbreakIcons.repositoryIcons("http://repo.example/", "http://repo.example/Packages"))
    }

    @Test
    fun `a package's icon from the index is a guest path, so the row falls back to the box`() {
        // BigBoss's Icon: values are file:///Library/... inside the device; the phone cannot fetch
        // those, which is why the package lists show Cydia's box.
        val withGuestIcon = """
            Package: com.example.tweak
            Version: 1.0
            Icon: file:///Library/ActionMenu/icon-large.png
            Filename: debs/tweak.deb

        """.trimIndent()
        val pkg = store.parseIndex(withGuestIcon, "http://repo.example/")[0]
        assertEquals("file:///Library/ActionMenu/icon-large.png", pkg.icon)
        assertEquals("http://repo.example/file:///Library/ActionMenu/icon-large.png",
            JailbreakIcons.packageIcon(pkg, "http://repo.example/Packages"))
    }

    @Test
    fun `a visit's lists are reused while they are fresh, and a changed source list is not`() {
        val cache = JailbreakCache()
        val now = 1_000_000_000L
        assertEquals(false, cache.fresh(listOf("http://repo.example/"), now))
        cache.catalog = JailbreakCatalog()
        cache.sources = listOf("http://repo.example/")
        cache.refreshedAt = now
        assertEquals(true, cache.fresh(listOf("http://repo.example/"), now + JailbreakCache.FRESH_MS - 1))
        assertEquals(false, cache.fresh(listOf("http://repo.example/"), now + JailbreakCache.FRESH_MS))
        assertEquals(false, cache.fresh(listOf("http://other.example/"), now + 1000))
    }

    @Test
    fun `a file name that is not a name cannot leave the install folder`() {
        val pkg = JailbreakPackage("x", "1", "iphoneos-arm", 1, "debs/../../etc/passwd", "", "http://repo.example/")
        val staged = store.stagedPath(pkg)
        assertTrue(staged.startsWith("/private/var/root/Media/Cydia/AutoInstall/"))
        assertFalse(staged.contains(".."))
    }
}
