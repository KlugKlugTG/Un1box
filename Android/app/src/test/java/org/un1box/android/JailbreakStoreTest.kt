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
        assertFalse(candidates.any { it.contains("Packages.bz2") })   // Android cannot read bz2 itself
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
    fun `a file name that is not a name cannot leave the install folder`() {
        val pkg = JailbreakPackage("x", "1", "iphoneos-arm", 1, "debs/../../etc/passwd", "", "http://repo.example/")
        val staged = store.stagedPath(pkg)
        assertTrue(staged.startsWith("/private/var/root/Media/Cydia/AutoInstall/"))
        assertFalse(staged.contains(".."))
    }
}
