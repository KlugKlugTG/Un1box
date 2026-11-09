"""The jailbreak build's five layers, and that they meet, checked as text.

The behaviour is tests/jailbreak/run.py's: it builds the tar and the shim and runs them. What it cannot
see is whether the APK would *use* them, which is four names and one flag spread over four files:

  gradle     -Pjailbreak becomes BuildConfig.JAILBREAK, and stages build-support/jb-tar.c's output as
             libjbTar.so -- and requires it, so a jailbreak build without the tool fails rather than
             preparing a device that cannot install Cydia
  service    the worker passes --jailbreak to firmwarekit when that flag is set, and names the staged
             tar in the child's environment (FIRMWAREKIT_TAR), the way it names iBoot32Patcher
  shim       /usr/bin/tar is redirected, and the tar it points at is run without the shim preloaded
  workflows  the build the APK comes from compiles that source with the NDK and passes -Pjailbreak; the
             jailbreak workflow asks for the jailbreak build and builds nothing else

Each check raises with one sentence when it fails, and returns a short note when it passes.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GRADLE = ROOT / "Android" / "app" / "build.gradle.kts"
SERVICE = ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android" / "FirmwareService.kt"
SHIM = ROOT / "Packages" / "FirmwareKit" / "build-support" / "spawn-shim.c"
CYDA = ROOT / "Packages" / "FirmwareKit" / "Sources" / "FirmwareKit" / "SystemEdits" / "Cydia.swift"
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
            "the jailbreak build shares the normal applicationId: a phone could not hold both, it would "
            "have to replace one with the other")
    require(re.search(r'resValue\("string", "app_name", if \(jailbreak\) "([^"]+)" else "([^"]+)"\)', gradle)
            is not None,
            "the jailbreak build has no name of its own: two Un1box icons would sit side by side")
    label = re.search(r'resValue\("string", "app_name", if \(jailbreak\) "([^"]+)" else "([^"]+)"\)', gradle)
    require(label.group(2) == "Un1box",
            f'the normal build is named "{label.group(2)}", not the product name')
    require(re.search(r'\(if \(jailbreak\) listOf\("jb-tar"\) else emptyList\(\)\)', gradle) is not None,
            "firmwareKitDir does not require jb-tar for a jailbreak build: Gradle would succeed with a "
            "tar-less kit and the phone would fail instead")
    stage = gradle.split('tasks.register<Copy>("prepareFirmwareKit")', 1)
    require(len(stage) > 1, "the firmwarekit staging task is gone")
    require(re.search(r'if \(jailbreak\) from\(kit\) \{ include\("jb-tar"\); rename \{ "libjbTar.so" \} \}',
                      stage[1].split("\n    }", 1)[0]) is not None,
            "the tar is not staged as libjbTar.so for the jailbreak build")
    require('include("jb-tar")' not in gradle.split('tasks.register<Copy>("prepareFirmwareKit")', 1)[0],
            "the tar is staged outside that task's jailbreak branch, so the normal APK would carry it too")
    return "the flag reaches BuildConfig, gives it its own app id and name, and stages libjbTar.so"


def service_passes_the_flag_and_names_the_tar():
    service = read(SERVICE)
    require('listOf("--jailbreak")' in service and "BuildConfig.JAILBREAK" in service,
            "the worker never passes --jailbreak: a jailbreak build would prepare stock systems")
    create = service.split('run(libs, listOf("create"', 1)[1][:1200]
    require("--jailbreak" in create,
            "the flag is not in the create command's arguments, so firmwarekit never hears it")
    require('put("FIRMWAREKIT_TAR"' in service and "libjbTar.so" in service,
            "the worker does not name the staged tar: the shim would still find the copy beside itself, "
            "but the override is how one build points at one file")
    require('put("LD_PRELOAD"' in service and "libspawnshim.so" in service,
            "the shim is not preloaded into firmwarekit, so neither the patcher nor the tar is redirected")
    return "--jailbreak on create, FIRMWAREKIT_TAR named, shim preloaded"


def shim_redirects_tar():
    shim = read(SHIM)
    require('#define TAR_PATH "/usr/bin/tar"' in shim and '#define TAR_REAL "libjbTar.so"' in shim,
            "the shim does not know the tar's paths, so a spawn of /usr/bin/tar finds nothing")
    redirect = shim.split("static const char *redirected(", 1)[1].split("\n}", 1)[0]
    require("TAR_PATH" in redirect and "TAR_ENV" in redirect,
            "redirected() has no branch for the tar: the recipe's \"/usr/bin/tar\" stays as it is")
    require("shim_quiet" in shim and "/proc/self/exe" in shim,
            "the shim does not recognise the tar it spawns, so the extracted members' own accesses would "
            "fill the log a preparation failure is read from")
    require("without_preload" in shim and "(preload dropped)" in shim,
            "the tar inherits LD_PRELOAD, so the shim interposes inside it as well")
    for wrapper in ("posix_spawn", "posix_spawnp", "execve"):
        body = shim.split(f"int {wrapper}(", 1)[1].split("\n}", 1)[0]
        require("without_preload" in body,
                f"{wrapper}() does not drop LD_PRELOAD for the tar: which spawn firmwarekit uses would "
                "decide whether the shim is in front of it")
    return "/usr/bin/tar redirected, the tar spawned without the shim"


def workflows_build_the_jailbreak_apk_only():
    workflow = read(WORKFLOW)
    jailbreak = read(JAILBREAK_WORKFLOW)
    require("build-support/jb-tar.c" in workflow,
            "the APK workflow never builds the tar, so the staged libjbTar.so would not exist")
    require("-Pjailbreak=\"$DECLARED_JAILBREAK\"" in workflow,
            "the APK workflow does not pass the flag to Gradle")
    require("workflow_call:" in workflow and "jailbreak:" in workflow.split("workflow_call:", 1)[1][:2000],
            "the APK workflow cannot be called with a jailbreak input")
    require("tests/jailbreak/run.py" in workflow,
            "the APK workflow does not run the jailbreak self-check")
    require("uses: ./.github/workflows/android-perf-apk.yml" in jailbreak,
            "the jailbreak workflow does not call the APK workflow")
    require("jailbreak: \"true\"" in jailbreak,
            "the jailbreak workflow does not ask for the jailbreak build")
    require("assemble" not in jailbreak and "gradlew" not in jailbreak,
            "the jailbreak workflow builds the APK itself as well as calling the build: there would be two "
            "definitions of the same APK")
    return "one build, called with jailbreak true, self-checked"


def the_archives_are_the_pinned_ones():
    """The self-check can only compare the real bytes if the build fetches exactly what the recipe pins."""
    cyda = read(CYDA)
    workflow = read(WORKFLOW)
    revision = re.search(r"Legacy-iOS-Kit/([0-9a-f]{40})/resources/jailbreak/", cyda)
    require(revision is not None, "Cydia.swift no longer pins the Legacy-iOS-Kit revision the archives come from")
    require(revision.group(1) in workflow,
            "the workflow fetches the jailbreak archives from a different revision than the recipe pins, so "
            "the self-check would compare against bytes no preparation ever extracts")
    pinned = re.findall(r'\(name: "([^"]+)", sha1: "([0-9a-f]{40})"\)', cyda)
    require(len(pinned) >= 3, f"Cydia.swift names {len(pinned)} archives; the bootstrap, Substrate and HTTPatch are three")
    for name, sha1 in pinned:
        require(name in workflow and sha1 in workflow,
                f"the workflow does not fetch and verify {name} ({sha1[:12]}), so a failed download would "
                "go unnoticed")
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
            "the APK check compares the jailbreak APK against the normal key, so a correctly signed "
            "jailbreak build would be rejected")
    keystore = workflow.split("Cache the signing key", 1)[1].split("- name:", 1)[0]
    jailbreak_cache = workflow.split("Cache the jailbreak signing key", 1)
    require(len(jailbreak_cache) > 1,
            "the jailbreak keystore has no cache entry of its own: adding it to the debug key's entry "
            "would rewrite that entry without debug.keystore, and every normal build after would sign "
            "with a new key")
    require("ci/jailbreak.keystore" in jailbreak_cache[1].split("- name:", 1)[0],
            "the jailbreak cache entry does not cache the jailbreak keystore")
    require("ci/jailbreak.keystore" not in keystore,
            "the jailbreak keystore is cached in the debug key's entry, which would drop debug.keystore "
            "from that cache when this run saves it")
    return "its own keystore, cached in its own entry, and the check expects its digest"


def the_tab_is_a_destination_of_its_own():
    """The button the user asked for: a fourth place in the bar, and a screen behind it."""
    require("JAILBREAK" in read(SHELL_KT),
            "Shell.kt has no JAILBREAK destination: the tab has nowhere to go")
    nav = read(UIKIT_KT)
    require('Triple(Screen.JAILBREAK' in nav and '"Jailbreak"' in nav,
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
            "the sources are not probed at Cydia's own layout, so a repository root would never resolve")
    require("private/var/mobile/Media/AutoInstall" in store,
            "the packages are not staged where the guest's own Cydia installs from at boot")
    require("stagedPath" in store and "replace" not in store.split("fun stagedPath", 1)[1][:400].replace("it in", ""),
            "the staged name is not sanitised: a file name from an index could leave the folder")
    require("parseIndex" in store and "lastKey" in store,
            "the control file's folded fields are not unfolded, so descriptions and versions would be wrong")
    require("Packages.bz2" in store or "bz2" not in store,
            "the tab claims to read bz2, which Android cannot: a source would fail instead of saying so")
    return "index and package fetched by the phone, staged in AutoInstall"


def the_helper_can_put_a_file_into_the_guest():
    """The one thing the guest cannot do for itself: receive the file."""
    helper = read(HELPER_C)
    require("static int action_push" in helper, "the app-service helper has no push: nothing can stage a .deb")
    parser = helper.split("int main(", 1)[1]
    require('"--local"' in parser and '"--remote"' in parser,
            "the helper takes no --local/--remote, so push cannot be called")
    push = helper.split("static int action_push", 1)[1].split("\nstatic ", 1)[0]
    require("remote[0] != '/'" in push, "push accepts a relative guest path")
    require("if (n == 2 && at[0] == '.' && at[1] == '.')" in push,
            "push accepts a path that steps above its directory")
    require("afc_file_write" in push and "afc_file_open" in push,
            "push does not write over AFC")
    require("com.apple.afc2" in push,
            "push does not try afc2 first: a jailbroken device's whole file system is behind it")
    require("fun push(" in read(GUESTAPPS_KT) and '"push"' in read(GUESTAPPS_KT),
            "GuestApps has no push, so the screen cannot reach the helper")
    installer = read(STORE_KT)
    require("apps.push(" in installer and "GuestApps(context)" in installer,
            "the installer does not use the helper: a download would never reach the guest")
    return "push over AFC (afc2 first), path-checked, wired to the screen"


def the_catalogues_cover_the_tab():
    """The app draws English sources and translates them; a missing entry is an English word on a
    Chinese or Russian screen."""
    for catalogue in ("I18nRu.kt", "I18nZh.kt"):
        text = read(ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android" / catalogue)
        for string in ("Jailbreak", "Sources", "Install", "Search", "Restart the device"):
            require(f'put("{string}"' in text, f"{catalogue} has no translation for \"{string}\"")
    screen = read(SCREEN_KT)
    for english in ("Jailbreak", "Sources", "Install", "Search", "Restart the device"):
        require(f'"{english}"' in screen, f"the screen does not use the string \"{english}\" the catalogues translate")
    return "both catalogues carry the tab's strings"


CHECKS = [
    ("gradle: -Pjailbreak reaches BuildConfig and stages the tar", gradle_carries_the_flag),
    ("service: the worker passes --jailbreak and names the tar", service_passes_the_flag_and_names_the_tar),
    ("shim: /usr/bin/tar is redirected and the tar is spawned clean", shim_redirects_tar),
    ("workflows: the jailbreak APK comes from the one build, self-checked",
     workflows_build_the_jailbreak_apk_only),
    ("workflows: the jailbreak application has its own signing key",
     the_jailbreak_apk_is_signed_with_its_own_key),
    ("workflows: the self-check gets the archives the recipe pins", the_archives_are_the_pinned_ones),
    ("jailbreak tab: a destination of its own", the_tab_is_a_destination_of_its_own),
    ("jailbreak tab: the browsing and the download happen on the phone", the_store_browses_on_the_phone),
    ("jailbreak tab: the helper stages the file in the guest", the_helper_can_put_a_file_into_the_guest),
    ("jailbreak tab: the catalogues carry its strings", the_catalogues_cover_the_tab),
]
