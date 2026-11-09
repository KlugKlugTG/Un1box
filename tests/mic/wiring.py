"""The connections between the microphone's five layers, checked as text.

The slices prove what each layer does. These checks prove the layers are wired
to each other and to the platform: the same three engine entry points are
exported, resolved and declared; the switch writes the key the worker reads; the
manifest asks for the permission the recorder tests for; and the capture port
asks the frontend before it asks the audio backend's own voice, which is the
ordering that made the first device build record silence.

Each check raises with one sentence when it fails, and returns a short note when
it passes.
"""
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCHES = ROOT / "ci" / "engine-patches"

def patch_with(marker):
    """The patch that carries a feature: the engine rework is applied in more than one file now."""
    for candidate in sorted(PATCHES.glob("*.patch")):
        if marker in candidate.read_text(errors="ignore"):
            return candidate
    raise AssertionError("no patch in ci/engine-patches carries " + marker)

MIC_PATCH = patch_with("host-mic")
BOARDS_PATCH = patch_with("host-mic")

# The capture port's read of the ring has been shipped in two shapes: the plain call, while the
# microphone was a patch of its own, and -- since the port grew the A4-only property -- that same
# call behind the property. The series is one patch now, so both checks below look for the call site
# itself rather than for one spelling of it.
MIC_CALL = "if (host_mic_read_frame(s->rx_frame))"
GATED_MIC_CALL = "if (s->host_mic && host_mic_read_frame(s->rx_frame))"
API_DEFINE = re.compile(r"^([+-])#define QEMU_IOS_API_VERSION \(\(2u << 16\) \| (\d+)u\)", re.M)

ENGINE_SYMBOLS = ("qemu_ios_microphone_write", "qemu_ios_microphone_flush", "qemu_ios_microphone_state")
PORTABLE_SYMBOLS = ("lt_runtime_mic_write", "lt_runtime_mic_info", "lt_runtime_mic_flush")


def read(relative):
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"{relative} is missing")
    return path.read_text(errors="ignore")


def added_lines(patch):
    """The lines a patch adds, as one body of text."""
    return "\n".join(line[1:] for line in read(patch).splitlines() if line.startswith("+"))


def require(condition, message):
    if not condition:
        raise AssertionError(message)


# --- the engine side, from the patch that ships it -------------------------

def engine_patch_adds_the_source():
    body = added_lines(MIC_PATCH)
    require("uint32_t host_mic_push(" in body, "the patch no longer adds hw/arm/host-mic.c")
    require("#include \"qemu/bswap.h\"" in body,
            "host-mic.c must include qemu/bswap.h: stw_le_p() is not in osdep.h, and clang makes an "
            "implicit declaration an error")
    require("'host-mic.c'" in body, "hw/arm/meson.build does not build host-mic.c")


def file_body(patch, relative):
    """A patch's view of one file: the context and added lines, in order."""
    lines, inside = [], False
    for line in read(patch).splitlines():
        if line.startswith("diff --git "):
            inside = line.endswith(" b/" + relative)
            continue
        if inside and line[:1] in (" ", "+") and not line.startswith("+++"):
            lines.append(line[1:])
    return "\n".join(lines)


def mic_call_offset(body):
    """Where the capture port asks the frontend's microphone, in either shape, or -1."""
    found = [index for index in (body.find(MIC_CALL), body.find(GATED_MIC_CALL)) if index >= 0]
    return min(found) if found else -1


def engine_patch_asks_the_microphone_first():
    """The ordering that made the first device build record silence."""
    body = file_body(MIC_PATCH, "hw/arm/s5l8930_i2s.c")
    mic = mic_call_offset(body)
    voice = body.find("if (s->in_level >= 4)")
    require(mic >= 0, "i2s_next_frame() no longer consults the frontend's microphone")
    require(voice >= 0, "i2s_next_frame() no longer reads the port's own voice ring")
    require(mic < voice,
            "the frontend's microphone must be asked BEFORE the audio backend's input voice: "
            "driver=none opens a silent one, so a fallback behind it never runs")


def engine_patch_exports_the_api():
    body = added_lines(MIC_PATCH)
    for symbol in ENGINE_SYMBOLS:
        require(re.search(r"\b" + symbol + r"\s*\(", body), f"the patch does not define {symbol}()")
    bumps = API_DEFINE.findall(read(MIC_PATCH))
    raised = [int(minor) for sign, minor in bumps if sign == "+"]
    lowered = [int(minor) for sign, minor in bumps if sign == "-"]
    require(raised and lowered,
            "the patch does not touch QEMU_IOS_API_VERSION, so an app cannot tell this engine "
            "from one without the microphone")
    require(max(raised) > min(lowered),
            f"QEMU_IOS_API_VERSION is not raised: {min(lowered)} -> {max(raised)}")
    require(max(raised) >= 4,
            "the microphone arrived in API 2.4, and an engine below it exports no "
            "qemu_ios_microphone_* entry points")


def engine_patch_flushes_on_stop():
    body = added_lines(MIC_PATCH)
    require("host_mic_flush();" in body, "a stopped capture does not flush the ring")


def engine_patch_gives_the_microphone_to_the_a4_boards_only():
    """The S5L8920 boards share the port model and record silence."""
    body = added_lines(BOARDS_PATCH)
    require('DEFINE_PROP_BOOL("host-mic"' in body,
            "the port has no host-mic property, so every board sharing the model asks the frontend's "
            "ring -- including the iPhone 3GS and the iPod touch 2G, which record silence")
    require("if (s->host_mic && host_mic_read_frame(s->rx_frame))" in body,
            "i2s_next_frame() reads the ring on a board that has no microphone")
    require("if (!s->tone_hz && s->host_mic)" in body,
            "i2s_set_in_rate() resamples for a board that has no microphone")
    require("if (s->host_mic) {" in body, "a stopped capture flushes the ring on every board")
    board = file_body(BOARDS_PATCH, "hw/arm/ipad1.c")
    require('qdev_prop_set_bit(dev, "host-mic", i == 0);' in board,
            "the A4 board file does not set host-mic, so the iPad, the iPhone 4 and the iPod touch 4G "
            "would record silence too")


# --- portable ---------------------------------------------------------------

def portable_declares_the_api():
    header = read("Portable/include/lighttouch.h")
    for symbol in PORTABLE_SYMBOLS:
        require(re.search(r"\b" + symbol + r"\s*\(", header), f"lighttouch.h does not declare {symbol}()")
    require("#define LT_MIC_RATE 44100" in header, "lighttouch.h does not define LT_MIC_RATE")
    fields = re.search(r"typedef struct \{[^}]*\} lt_mic_info;", header, re.S)
    require(fields, "lighttouch.h has no lt_mic_info")
    order = [name for name in ("sample_rate", "queued_frames", "capturing", "pushed_frames", "dropped_frames")
             if name in fields.group(0)]
    require(len(order) == 5, f"lt_mic_info is missing counters: has {order}")


def portable_resolves_exactly_the_exported_names():
    runtime = read("Portable/src/Runtime.cpp")
    for symbol in ENGINE_SYMBOLS:
        require(f'dlsym(library, "{symbol}")' in runtime, f"Runtime.cpp never resolves {symbol}")
    require("engine has no microphone input" in runtime,
            "the optional microphone symbols are not treated as optional")


def portable_pushes_from_the_capture_port():
    """One caller reads the ring, and it is the capture port."""
    body = added_lines(MIC_PATCH)
    require(mic_call_offset(body) >= 0,
            "the capture port no longer reads the microphone into its frame")
    calls = []
    for line in body.splitlines():
        if "host_mic_read_frame(" not in line:
            continue
        text = line.strip()
        if text.startswith(("*", "//", MIC_CALL, GATED_MIC_CALL)):
            continue
        if text.startswith(("bool host_mic_read_frame", "void host_mic_read_frame")):
            continue  # its own definition or declaration
        calls.append(text)
    require(not calls, f"host_mic_read_frame() has unexpected call sites: {calls}")
    site = [text for text in body.splitlines()
            if text.strip().startswith((MIC_CALL, GATED_MIC_CALL))]
    require(len(site) == 1,
            f"the capture port is the ring's one reader, and the patch has {len(site)}: "
            f"{[text.strip() for text in site]}")


# --- android ----------------------------------------------------------------

def android_asks_for_the_permission():
    manifest = read("Android/app/src/main/AndroidManifest.xml")
    require("android.permission.RECORD_AUDIO" in manifest,
            "AndroidManifest.xml does not declare RECORD_AUDIO")


def android_recorder_matches_the_api():
    service = read("Android/app/src/main/java/org/un1box/android/EngineService.kt")
    require("Manifest.permission.RECORD_AUDIO" in service, "the worker does not check the permission")
    require("MediaRecorder.AudioSource.MIC" in service, "the recorder does not use the microphone source")
    require("CHANNEL_IN_MONO" in service, "the recorder does not record mono")
    require("ENCODING_PCM_16BIT" in service, "the recorder does not record 16-bit")
    require("val rate = 44100" in service, f"the recorder does not use {44100} Hz")
    require("Native.microphone(samples, read)" in service, "the recorder never pushes a block")
    require("Native.microphoneFlush()" in service, "stopping the recorder does not flush the ring")
    require('Thread({' in service and '"microphone"' in service, "the recorder has no thread of its own")


def android_switch_reaches_the_worker():
    settings = read("Android/app/src/main/java/org/un1box/android/AppSettings.kt")
    require('getSharedPreferences("ui"' in settings, "AppSettings no longer uses the ui preferences")
    require('putBoolean("microphone"' in settings, "the switch no longer persists its value")
    service = read("Android/app/src/main/java/org/un1box/android/EngineService.kt")
    require('getSharedPreferences("ui", MODE_PRIVATE).getBoolean("microphone"' in service,
            "the worker reads the switch from a different store than the settings write it to")
    activity = read("Android/app/src/main/java/org/un1box/android/MainActivity.kt")
    require("RequestPermission()" in activity and "RECORD_AUDIO" in activity,
            "turning the switch on does not ask Android for the permission")
    screen = read("Android/app/src/main/java/org/un1box/android/SettingsScreen.kt")
    require("host.useMicrophone(" in screen, "the microphone row is not wired to the host")


def android_jni_names_match():
    native = read("Android/app/src/main/java/org/un1box/android/Native.kt")
    jni = read("Android/app/src/main/cpp/un1box_jni.cpp")
    declared = re.findall(r"external fun (\w+)\(", native)
    missing = [name for name in declared
               if not re.search(r"\bJava_org_un1box_android_Native_" + name + r"\s*\(", jni)]
    require(not missing, f"Native.kt declares externals the JNI never defines: {missing}")
    for name in ("microphone", "microphoneFlush", "microphoneInfo"):
        require(name in declared, f"Native.kt does not declare {name}()")


# --- the patch stack --------------------------------------------------------

def patch_stack(patch_dir, engine):
    """Apply ci/engine-patches to the engine and commit them as CI does."""
    if engine is None:
        return "skipped: no engine checkout"
    engine = Path(engine)
    inside = subprocess.run(["git", "-C", str(engine), "rev-parse", "--git-dir"],
                            capture_output=True, text=True)
    if inside.returncode:
        return "skipped: the engine checkout is not a git repository"
    revision = subprocess.run(["git", "-C", str(engine), "rev-parse", "--short", "HEAD"],
                              capture_output=True, text=True).stdout.strip()
    scratch = Path(tempfile.mkdtemp(prefix="lt-mic-stack-"))
    try:
        tree = scratch / "engine"
        tree.mkdir()
        archive = subprocess.run(["git", "-C", str(engine), "archive", "HEAD"], capture_output=True)
        if archive.returncode:
            raise AssertionError(f"git archive failed in {engine}")
        subprocess.run(["tar", "-x", "-C", str(tree)], input=archive.stdout, check=True)
        already = "host_mic_read_frame" in (tree / "hw/arm/s5l8930_i2s.c").read_text(errors="ignore")
        if not already:
            for patch in sorted(Path(patch_dir).glob("*.patch")):
                applied = subprocess.run(["git", "apply", str(patch)], cwd=tree, capture_output=True, text=True)
                if applied.returncode:
                    raise AssertionError(f"{patch.name} does not apply to {revision}: {applied.stderr.strip()}")
        for command in (["git", "init", "-q"], ["git", "add", "-A"]):
            subprocess.run(command, cwd=tree, check=True, capture_output=True)
        subprocess.run(["git", "-c", "user.name=mic-selftest", "-c", "user.email=mic@selftest",
                        "commit", "-q", "-m", "engine patches"],
                       cwd=tree, check=True, capture_output=True,
                       env={**os.environ, "GIT_AUTHOR_DATE": "2001-01-01T00:00:00Z",
                            "GIT_COMMITTER_DATE": "2001-01-01T00:00:00Z"})
        dirty = subprocess.run(["git", "status", "--porcelain"], cwd=tree, capture_output=True, text=True)
        if dirty.stdout.strip():
            raise AssertionError(f"the patched engine tree is dirty after the commit: {dirty.stdout.strip()[:400]}")
        stamp = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=tree,
                               capture_output=True, text=True).stdout.strip()
    finally:
        shutil.rmtree(scratch, ignore_errors=True)  # a .git tree does not always delete cleanly
    return f"from {revision}: {len(list(Path(patch_dir).glob('*.patch')))} patches, clean at {stamp}"


CHECKS = [
    ("engine patch: the device code, its header and its build entry", engine_patch_adds_the_source),
    ("engine patch: the microphone is asked before the silent voice", engine_patch_asks_the_microphone_first),
    ("engine patch: the three entry points and the API bump", engine_patch_exports_the_api),
    ("engine patch: a stopped capture flushes the ring", engine_patch_flushes_on_stop),
    ("engine patch: the A4 boards have the microphone, the S5L8920 ones do not",
     engine_patch_gives_the_microphone_to_the_a4_boards_only),
    ("portable: the API is declared and typed", portable_declares_the_api),
    ("portable: the engine's entry points are resolved, and optional", portable_resolves_exactly_the_exported_names),
    ("portable: one caller reads the ring, the capture port", portable_pushes_from_the_capture_port),
    ("android: the manifest asks for the microphone", android_asks_for_the_permission),
    ("android: the recorder's format is the API's", android_recorder_matches_the_api),
    ("android: the switch reaches the worker", android_switch_reaches_the_worker),
    ("android: Kotlin externals all have JNI bodies", android_jni_names_match),
]
