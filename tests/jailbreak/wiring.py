"""The jailbreak build's layers, and that they meet, checked as text.

Each check raises with one sentence when it fails, and returns a short note when it passes.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GRADLE = ROOT / "Android" / "app" / "build.gradle.kts"
SERVICE = ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android" / "FirmwareService.kt"
CYDIA_RS = ROOT / "crates" / "lighttouch-firmware" / "src" / "cydia.rs"
CREATE_RS = ROOT / "crates" / "lighttouch-firmware" / "src" / "create.rs"
SYSTEM_EDITS_RS = ROOT / "crates" / "lighttouch-firmware" / "src" / "system_edits.rs"
N72_RECIPE_RS = ROOT / "crates" / "lighttouch-firmware" / "src" / "n72_recipe.rs"
BZ2_C = ROOT / "build-support" / "jb-bunzip2.c"
SHELL_KT = ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android" / "Shell.kt"
UIKIT_KT = ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android" / "UiKit.kt"
ACTIVITY_KT = ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android" / "MainActivity.kt"
STORE_KT = ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android" / "JailbreakStore.kt"
SCREEN_KT = ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android" / "JailbreakScreen.kt"
HELPER_C = ROOT / "Android" / "native-services" / "lt_appservice.c"
GUESTAPPS_KT = ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android" / "GuestApps.kt"
WORKFLOW = ROOT / ".github" / "workflows" / "android-perf-apk.yml"
JAILBREAK_WORKFLOW = ROOT / ".github" / "workflows" / "jailbreak-apk.yml"


def read(path):
    if not path.is_file():
        raise AssertionError(f"{path.relative_to(ROOT)} is missing")
    return path.read_text(errors="ignore")


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def gradle_carries_the_flag():
    gradle = read(GRADLE)
    require('gradleProperty("jailbreak")' in gradle,
            "build.gradle.kts reads no -Pjailbreak property, so nothing can ask for the jailbreak build")
    require(re.search(r'buildConfigField\("boolean",\s*"JAILBREAK"', gradle) is not None,
            "the flag never reaches the app: there is no BuildConfig.JAILBREAK")
    require(re.search(r'if \(jailbreak\) applicationIdSuffix = "\.jb"', gradle) is not None,
            "the jailbreak build shares the normal applicationId: a phone could not hold both")
    label = re.search(r'resValue\("string", "app_name", if \(jailbreak\) "([^"]+)" else "([^"]+)"\)', gradle)
    require(label is not None,
            "the jailbreak build has no name of its own: two Un1box icons would sit side by side")
    require(label.group(2) == "Un1box",
            f'the normal build is named "{label.group(2)}", not the product name')
    require(re.search(r'if \(jailbreak\) listOf\("jb-bunzip2"\) else emptyList\(\)', gradle) is not None,
            "firmwareKitDir does not require jb-bunzip2 for a jailbreak build")
    stage = gradle.split('tasks.register<Copy>("prepareFirmwareKit")', 1)
    require(len(stage) > 1, "the firmwarekit staging task is gone")
    task_body = stage[1].split("\n    }", 1)[0]
    require(re.search(r'if \(jailbreak\) from\(kit\) \{ include\("jb-bunzip2"\); rename \{ "libjbBz2\.so" \} \}',
                      task_body) is not None,
            "jb-bunzip2 is not staged as libjbBz2.so for the jailbreak build")
    require('include("jb-bunzip2")' not in stage[0],
            "jb-bunzip2 is staged outside the jailbreak branch, so the normal APK would carry it too")
    return "the flag reaches BuildConfig, gives it its own app id and name, and stages libjbBz2.so"


def service_passes_the_flag():
    service = read(SERVICE)
    require('listOf("--jailbreak")' in service and "BuildConfig.JAILBREAK" in service,
            "the worker never passes --jailbreak: a jailbreak build would prepare stock systems")
    create = service.split('run(libs, listOf("create"', 1)[1][:1200]
    require("--jailbreak" in create,
            "the flag is not in the create command's arguments, so firmwarekit never hears it")
    return "--jailbreak reaches firmwarekit create"


def firmwarekit_extracts_cydia_in_process():
    cydia = read(CYDIA_RS)
    create = read(CREATE_RS)
    system_edits = read(SYSTEM_EDITS_RS)
    n72 = read(N72_RECIPE_RS)
    require("pub fn install_jailbreak(" in cydia and "pub fn install_cydia(" in cydia,
            "cydia.rs does not define install_jailbreak() / install_cydia()")
    require('pub const EXCLUDED: &str = "private/var/Keychains";' in cydia,
            "cydia.rs does not exclude private/var/Keychains")
    require("pub const PATCYH:" in cydia,
            "cydia.rs does not strip patcyh on pre-8.3 systems")
    require("crate::cydia::bootstrap(" in create and 'recipe.option("jailbreak") == Some(true)' in create,
            "create.rs does not fetch the Cydia bootstrap when --jailbreak is set")
    require("cydia::install_jailbreak(" in system_edits and "root_modes.extend(jb.modes)" in system_edits,
            "system_edits.rs does not install the jailbreak and its set-ID modes on the volume")
    require("cydia::install_jailbreak(" in n72 and "modes = jb.modes" in n72,
            "n72_recipe.rs does not install the jailbreak and its set-ID modes on the volume")
    return "in-process tar extraction, Keychains excluded, bootstrap + system_edits + n72 wired"


def workflows_build_the_jailbreak_apk_only():
    workflow = read(WORKFLOW)
    jailbreak = read(JAILBREAK_WORKFLOW)
    require("crates/lighttouch-firmware" in workflow and "ci/firmwarekit/android/firmwarekit" in workflow,
            "the APK workflow does not build Rust firmwarekit")
    require("build-support/jb-bunzip2.c" in workflow and "ci/firmwarekit/android/jb-bunzip2" in workflow,
            "the APK workflow never builds jb-bunzip2, so the staged libjbBz2.so would not exist")
    require('-Pjailbreak="$DECLARED_JAILBREAK"' in workflow,
            "the APK workflow does not pass the flag to Gradle")
    require("workflow_call:" in workflow and "jailbreak:" in workflow.split("workflow_call:", 1)[1][:2000],
            "the APK workflow cannot be called with a jailbreak input")
    require("tests/jailbreak/run.py" in workflow,
            "the APK workflow does not run the jailbreak self-check")
    require("uses: ./.github/workflows/android-perf-apk.yml" in jailbreak,
            "the jailbreak workflow does not call the APK workflow")
    require('jailbreak: "true"' in jailbreak,
            "the jailbreak workflow does not ask for the jailbreak build")
    require("assemble" not in jailbreak and "gradlew" not in jailbreak,
            "the jailbreak workflow builds the APK itself as well as calling the build")
    return "one build, Rust firmwarekit + jb-bunzip2, called with jailbreak true, self-checked"


def the_archives_are_the_pinned_ones():
    """The self-check verifies the same pinned Legacy-iOS-Kit archives as cydia.rs."""
    cydia = read(CYDIA_RS)
    workflow = read(WORKFLOW)
    revision = re.search(r"Legacy-iOS-Kit/([0-9a-f]{40})/resources/jailbreak/", cydia)
    require(revision is not None, "cydia.rs no longer pins the Legacy-iOS-Kit revision")
    require(revision.group(1) in workflow,
            "the workflow fetches the jailbreak archives from a different revision than cydia.rs pins")
    pinned = re.findall(r'\("([^"]+)",\s*"([0-9a-f]{40})"\)', cydia)
    require(len(pinned) >= 3, f"cydia.rs names {len(pinned)} archives; expected at least 3")
    for name, sha1 in pinned:
        require(name in workflow and sha1 in workflow,
                f"the workflow does not fetch and verify {name} ({sha1[:12]})")
    require("tests/jailbreak/archives" in workflow,
            "the workflow does not put the archives where tests/jailbreak/run.py looks for them")
    return f"{len(pinned)} archives at {revision.group(1)[:8]}, verified by the workflow"


def the_jailbreak_apk_is_signed_with_its_own_key():
    """Its own application id means it may have its own key, and the checks must expect the right one."""
    workflow = read(WORKFLOW)
    require("ci/jailbreak.keystore" in workflow and "un1boxjailbreak" in workflow,
            "the workflow has no jailbreak keystore: the two applications would share a signing key")
    sign = workflow.split("Sign the APK with the workflow's keystore", 1)
    require(len(sign) > 1, "the signing step is gone")
    require("jailbreak.keystore" in sign[1].split("- name:", 1)[0] and "DECLARED_JAILBREAK" in sign[1][:1200],
            "the signing step does not choose the jailbreak keystore for a jailbreak build")
    check = workflow.split("Check the APK and write provenance", 1)
    require(len(check) > 1, "the APK check step is gone")
    require("LT_JB_SIGNER_SHA256" in check[1].split("- name:", 1)[0],
            "the APK check compares the jailbreak APK against the normal key")
    keystore = workflow.split("Cache the signing key", 1)[1].split("- name:", 1)[0]
    jailbreak_cache = workflow.split("Cache the jailbreak signing key", 1)
    require(len(jailbreak_cache) > 1, "the jailbreak keystore has no cache entry of its own")
    require("ci/jailbreak.keystore" in jailbreak_cache[1].split("- name:", 1)[0],
            "the jailbreak cache entry does not cache the jailbreak keystore")
    require("ci/jailbreak.keystore" not in keystore,
            "the jailbreak keystore is cached in the debug key's entry")
    return "its own keystore, cached in its own entry, and the check expects its digest"


def the_tab_is_a_destination_of_its_own():
    """A fourth destination in the bar, and a screen behind it."""
    require("JAILBREAK" in read(SHELL_KT),
            "Shell.kt has no JAILBREAK destination: the tab has nowhere to go")
    nav = read(UIKIT_KT)
    body = nav.split("internal fun LtNavBar", 1)
    require(len(body) > 1, "UiKit.kt has no LtNavBar: there is no bar to put a tab in")
    body = body[1].split("\n}", 1)[0]
    require("Screen.JAILBREAK" in body and '"Jailbreak"' in body,
            "the navigation bar does not offer the Jailbreak tab")
    require('Screen.JAILBREAK to "open_jailbreak"' in nav,
            "the tab has no test tag, so nothing can open it in a check")
    require("Screen.JAILBREAK ->" in read(ACTIVITY_KT),
            "MainActivity does not draw the Jailbreak screen for that destination")
    return "a fourth destination, its bar entry and its screen"


def the_store_browses_on_the_phone():
    """What makes the tab faster than Cydia: the index and the .deb are fetched here, not in the guest."""
    store = read(STORE_KT)
    require("GZIPInputStream" in store and "HttpURLConnection" in store,
            "the tab does not fetch an index itself: it would have to ask the guest's 2010 stack")
    require("dists/stable/main/binary-iphoneos-arm" in store and "dists/ios/main/binary-iphoneos-arm" in store,
            "the sources are not probed at Cydia's own layout")
    require("/private/var/root/Media/Cydia/AutoInstall" in store,
            "the packages are not staged in /private/var/root/Media/Cydia/AutoInstall")
    require("stagedPath" in store and "replace" not in store.split("fun stagedPath", 1)[1][:400].replace("it in", ""),
            "the staged name is not sanitised")
    require("parseIndex" in store and "lastKey" in store,
            "the control file's folded fields are not unfolded")
    require("Packages.bz2" in store and "bunzip2" in store,
            "the tab does not try a .bz2 index through the app's own decompressor")
    return "index and package fetched by the phone, staged in AutoInstall"


def the_bz2_indexes_have_a_decompressor():
    """Android has no bzip2 and BigBoss serves nothing else: the tool is built here and shipped."""
    workflow = read(WORKFLOW)
    require("build-support/jb-bunzip2.c" in workflow and "ci/firmwarekit/android/jb-bunzip2" in workflow,
            "the workflow does not build the bz2 decompressor the tab needs for BigBoss")
    gradle = read(GRADLE)
    require('include("jb-bunzip2")' in gradle and "libjbBz2.so" in gradle,
            "the decompressor is not staged into the APK")
    source = read(BZ2_C)
    require("int main(int argc, char **argv)" in source and "in.bz2" in source,
            "the decompressor has no command line of its own")
    require("run-length" in source or "RLE" in source or "repeat count" in source,
            "the decompressor does not expand the run-length pass")
    require("unRLE" not in source and "bzip2-1.0.8" not in source,
            "the decompressor vendored upstream bzip2")
    require("JailbreakBunzip2" in read(STORE_KT) and "libjbBz2.so" in read(STORE_KT),
            "the store has no way to run the tool")
    screen = read(SCREEN_KT)
    require('Text("Add Source")' in screen and "OutlinedTextField(adding" in screen,
            "the Sources list has no field to type a source into")
    require("repositoryIcons(" in screen and "firstIcon(" in read(STORE_KT),
            "a source's icon is looked for in one guessed place again")
    return "built here, checked, staged, and run by the store"


def the_helper_can_put_a_file_into_the_guest():
    """The one thing the guest cannot do for itself: receive the file."""
    helper = read(HELPER_C)
    require("static int action_push" in helper, "the app-service helper has no push")
    parser = helper.split("int main(", 1)[1]
    require('"--local"' in parser and '"--remote"' in parser,
            "the helper takes no --local/--remote")
    push = helper.split("static int action_push", 1)[1].split("\nstatic ", 1)[0]
    require("remote[0] != '/'" in push, "push accepts a relative guest path")
    require("if (n == 2 && at[0] == '.' && at[1] == '.')" in push,
            "push accepts a path that steps above its directory")
    require("afc_file_write" in push and "afc_file_open" in push,
            "push does not write over AFC")
    require("com.apple.afc2" in push,
            "push does not try afc2 first")
    require("fun push(" in read(GUESTAPPS_KT) and '"push"' in read(GUESTAPPS_KT),
            "GuestApps has no push")
    installer = read(STORE_KT)
    require("apps.push(" in installer and "GuestApps(context)" in installer,
            "the installer does not use the helper")
    return "push over AFC (afc2 first), path-checked, wired to the screen"


def the_tab_may_speak_cleartext():
    manifest = read(ROOT / "Android" / "app" / "src" / "main" / "AndroidManifest.xml")
    require('android:usesCleartextTraffic="true"' in manifest,
            "the manifest forbids cleartext, and every repository these systems have is http://")
    store = read(STORE_KT)
    require("Packages.gz" in store and '"Packages"' in store,
            "the tab does not try a plain index")
    require("notAnIndex" in store and "not an index" in store,
            "a repository that answers with a web page is not reported as such")
    return "cleartext allowed, plain index tried, a web page reported as one"


def the_apk_carries_this_build_s_helper():
    workflow = read(WORKFLOW)
    require("Android/packaging/build-mobile-android.py" in workflow,
            "the workflow does not build the app-service helper")
    require("mobile-services-built" in workflow and "push|pull" in workflow,
            "the helper built here is not checked for push/pull")
    require("ci/mobile-services/arm64-v8a" in workflow,
            "the built helper is not put where -PmobileServicesArm64 stages the APK from")
    helper = read(HELPER_C)
    for action in ('"push"', '"pull"'):
        require(f"strcmp(action, {action})" in helper, f"the helper has no {action} action")
    require("private/var/lib/dpkg/status" in read(STORE_KT),
            "the tab does not read dpkg's status")
    require("fun pull(" in read(GUESTAPPS_KT) and "fun push(" in read(GUESTAPPS_KT),
            "GuestApps has no push and pull pair")
    return "built here, checked for push/pull, staged into the APK"


def the_catalogues_cover_the_tab():
    for catalogue in ("I18nRu.kt", "I18nZh.kt"):
        text = read(ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android" / catalogue)
        for string in ("Jailbreak", "Sources", "Install", "Search", "Restart the device"):
            require(f'put("{string}"' in text, f"{catalogue} has no translation for \"{string}\"")
    screen = read(SCREEN_KT)
    for english in ("Cydia", "Sources", "Changes", "Installed", "Search", "Install", "Restart the device",
                    "Add Source", "Information"):
        require(f'"{english}"' in screen, f"the screen does not use the string \"{english}\"")
    return "both catalogues carry the tab's strings"


def a_local_deb_can_be_staged():
    screen = read(SCREEN_KT)
    store = read(STORE_KT)
    require("OpenMultipleDocuments" in screen,
            "the tab cannot pick a .deb from the phone")
    require('testTag("install_local_deb")' in screen,
            "the local-package button has no tag")
    require("copyDebToCache(" in screen and "copyTo(it)" in screen,
            "the picked document is never copied to a file the guest can be given")
    require("fun installLocal(" in store,
            "the installer has no local-file path")
    require("autoInstallDir" in store and "GuestApps(context).push(" in store,
            "a local package does not go through AutoInstall")


CHECKS = [
    ("gradle: -Pjailbreak reaches BuildConfig and stages libjbBz2.so", gradle_carries_the_flag),
    ("service: the worker passes --jailbreak to create", service_passes_the_flag),
    ("firmwarekit: cydia.rs extracts the bootstrap in-process", firmwarekit_extracts_cydia_in_process),
    ("workflows: the jailbreak APK comes from the one build, self-checked",
     workflows_build_the_jailbreak_apk_only),
    ("workflows: the jailbreak application has its own signing key",
     the_jailbreak_apk_is_signed_with_its_own_key),
    ("workflows: the self-check gets the archives the recipe pins", the_archives_are_the_pinned_ones),
    ("jailbreak tab: a destination of its own", the_tab_is_a_destination_of_its_own),
    ("jailbreak tab: the browsing and the download happen on the phone", the_store_browses_on_the_phone),
    ("jailbreak tab: the helper stages the file in the guest", the_helper_can_put_a_file_into_the_guest),
    ("jailbreak tab: it may speak cleartext, and reads a plain index", the_tab_may_speak_cleartext),
    ("jailbreak tab: .bz2 indexes have a decompressor", the_bz2_indexes_have_a_decompressor),
    ("jailbreak tab: the APK carries the helper built here", the_apk_carries_this_build_s_helper),
    ("jailbreak tab: the catalogues carry its strings", the_catalogues_cover_the_tab),
    ("jailbreak tab: a .deb from the phone can be staged", a_local_deb_can_be_staged),
]
