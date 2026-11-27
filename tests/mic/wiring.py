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

MIC_CALLS = (
    "if (host_mic_read_frame(s->rx_frame))",
    "if (s->host_mic && host_mic_read_frame(s->rx_frame))",
    "} else if (s->host_mic && host_mic_read_frame(stereo))",
)
API_DEFINE = re.compile(r"#define QEMU_IOS_API_VERSION \(\(2u << 16\) \| (\d+)u\)")

ENGINE_SYMBOLS = ("qemu_ios_microphone_write", "qemu_ios_microphone_flush", "qemu_ios_microphone_state")
PORTABLE_SYMBOLS = ("lt_runtime_mic_write", "lt_runtime_mic_info", "lt_runtime_mic_flush")

ENGINE_TREE: Path | None = None


def set_engine(path):
    global ENGINE_TREE
    ENGINE_TREE = Path(path) if path is not None else None


def patch_with(marker):
    """The patch carrying a feature, or None once upstreamed into the pinned engine."""
    for candidate in sorted(PATCHES.glob("*.patch")):
        if marker in candidate.read_text(errors="ignore"):
            return candidate
    return None


def read(relative):
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"{relative} is missing")
    return path.read_text(errors="ignore")


def read_engine(relative):
    if ENGINE_TREE is None:
        return None
    path = ENGINE_TREE / relative
    if not path.is_file():
        raise AssertionError(f"engine/{relative} is missing")
    return path.read_text(errors="ignore")


def added_lines(patch):
    """The lines a patch adds, as one body of text."""
    return "\n".join(line[1:] for line in patch.read_text(errors="ignore").splitlines() if line.startswith("+"))


def file_body(patch, relative):
    """A patch's view of one file: the context and added lines, in order."""
    lines, inside = [], False
    for line in patch.read_text(errors="ignore").splitlines():
        if line.startswith("diff --git "):
            inside = line.endswith(" b/" + relative)
            continue
        if inside and line[:1] in (" ", "+") and not line.startswith("+++"):
            lines.append(line[1:])
    return "\n".join(lines)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def mic_call_offset(body):
    """Where the capture port asks the frontend's microphone, or -1."""
    found = [index for index in (body.find(call) for call in MIC_CALLS) if index >= 0]
    return min(found) if found else -1


# --- the engine side, from the pinned engine or a patch that ships it -----

def engine_adds_the_source():
    patch = patch_with("uint32_t host_mic_push(")
    if patch is not None:
        body = added_lines(patch)
        meson = body
    elif ENGINE_TREE is not None:
        body = read_engine("hw/arm/host-mic.c")
        meson = read_engine("hw/arm/meson.build")
    else:
        return "skipped: no engine checkout"
    require("uint32_t host_mic_push(" in body, "the engine does not define host_mic_push() in hw/arm/host-mic.c")
    require('#include "qemu/bswap.h"' in body,
            "host-mic.c must include qemu/bswap.h: stw_le_p() is not in osdep.h, and clang makes an "
            "implicit declaration an error")
    require("'host-mic.c'" in meson, "hw/arm/meson.build does not build host-mic.c")


def engine_asks_the_microphone_first():
    """The ordering that made the first device build record silence."""
    patch = patch_with("uint32_t host_mic_push(")
    if patch is not None:
        body = file_body(patch, "hw/arm/s5l8930_i2s.c")
    elif ENGINE_TREE is not None:
        body = read_engine("hw/arm/s5l8930_i2s.c")
    else:
        return "skipped: no engine checkout"
    fn = body.split("static void i2s_next_frame", 1)[-1]
    mic = mic_call_offset(fn)
    voice = fn.find("if (s->in_level >= 4)")
    require(mic >= 0, "i2s_next_frame() no longer consults the frontend's microphone")
    require(voice >= 0, "i2s_next_frame() no longer reads the port's own voice ring")
    require(mic < voice,
            "the frontend's microphone must be asked BEFORE the audio backend's input voice: "
            "driver=none opens a silent one, so a fallback behind it never runs")


def engine_exports_the_api():
    patch = patch_with("uint32_t host_mic_push(")
    if patch is not None:
        src = added_lines(patch)
        hdr = src
    elif ENGINE_TREE is not None:
        src = read_engine("contrib/macos-app/qemu-macos-extras.c")
        hdr = read_engine("contrib/macos-app/qemu-macos-extras.h")
    else:
        return "skipped: no engine checkout"
    for symbol in ENGINE_SYMBOLS:
        require(re.search(r"\b" + symbol + r"\s*\(", src), f"the engine does not define {symbol}()")
    versions = [int(m) for m in API_DEFINE.findall(hdr)]
    require(versions and max(versions) >= 4,
            "the microphone arrived in API 2.4, and an engine below it exports no "
            "qemu_ios_microphone_* entry points")


def engine_flushes_on_stop():
    patch = patch_with("uint32_t host_mic_push(")
    if patch is not None:
        body = added_lines(patch)
    elif ENGINE_TREE is not None:
        body = read_engine("hw/arm/s5l8930_i2s.c")
    else:
        return "skipped: no engine checkout"
    require("host_mic_flush();" in body, "a stopped capture does not flush the ring")


def engine_gives_the_microphone_to_every_board_that_has_one():
    """The A4 boards ship the property; the S5L8920 gets it with the FIFO window that feeds it."""
    patch = patch_with("uint32_t host_mic_push(")
    s5l8920_patch = patch_with("The phone's microphone reaches this port")
    if patch is not None:
        i2s = added_lines(patch)
        board = file_body(patch, "hw/arm/ipad1.c")
        s5l8920 = added_lines(s5l8920_patch) if s5l8920_patch else i2s
        fifo = s5l8920
    elif ENGINE_TREE is not None:
        i2s = read_engine("hw/arm/s5l8930_i2s.c")
        board = read_engine("hw/arm/ipad1.c")
        s5l8920 = read_engine("hw/arm/s5l8920.c")
        fifo = i2s
    else:
        return "skipped: no engine checkout"

    require('DEFINE_PROP_BOOL("host-mic"' in i2s,
            "the port has no host-mic property, so every board sharing the model asks the frontend's "
            "ring -- including the iPhone 3GS and the iPod touch 2G, which record silence")
    require("if (s->host_mic && host_mic_read_frame(" in i2s,
            "i2s_next_frame() reads the ring on a board that has no microphone")
    require("if (!s->tone_hz && s->host_mic)" in i2s,
            "i2s_set_in_rate() resamples for a board that has no microphone")
    require("if (s->host_mic) {" in i2s, "a stopped capture flushes the ring on every board")
    require('qdev_prop_set_bit(dev, "host-mic", i == 0);' in board,
            "the A4 board file does not set host-mic, so the iPad, the iPhone 4 and the iPod touch 4G "
            "would record silence too")
    require('qdev_prop_set_bit(dev, "host-mic", true);' in s5l8920,
            "the S5L8920 board does not set host-mic, so the iPhone 3GS and the iPod touch 3G would "
            "still record silence")
    require("if (offset == I2S_RXFIFO)" in fifo,
            "the I2S FIFO window still answers 0 for the RX FIFO: the capture chain would copy "
            "silence out of it")
    require('"s5l8930.i2s-fifo", 0x400' in fifo,
            "the FIFO window is four bytes wide, so the RX FIFO's +0x38 is not mapped")
    require('qdev_prop_set_uint8(dev, "port", 1);' in s5l8920,
            "the 3GS has no i2s1/Voice port, and iOS builds its input route from both ports: Voice "
            "Memos says \"Connect a microphone\"")
    require('"paced-ports", 2' in s5l8920,
            "the CDMA paces one I2S window, so a chain on the Voice port is not paced")
    require("S5L8920_I2S0_BASE + 0x1000" in s5l8920 and "S5L8920_I2S0_FIFO + 0x1000" in s5l8920,
            "the Voice port is not mapped at the 3GS's second port window")


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
    patch = patch_with("uint32_t host_mic_push(")
    if patch is not None:
        body = added_lines(patch)
    elif ENGINE_TREE is not None:
        body = read_engine("hw/arm/s5l8930_i2s.c")
    else:
        return "skipped: no engine checkout"
    require(mic_call_offset(body) >= 0,
            "the capture port no longer reads the microphone into its frame")
    calls = []
    for line in body.splitlines():
        if "host_mic_read_frame(" not in line:
            continue
        text = line.strip()
        if text.startswith(("*", "//")) or any(text.startswith(c) for c in MIC_CALLS):
            continue
        if text.startswith(("bool host_mic_read_frame", "void host_mic_read_frame")):
            continue
        calls.append(text)
    require(not calls, f"host_mic_read_frame() has unexpected call sites: {calls}")
    site = [text for text in body.splitlines()
            if any(text.strip().startswith(c) for c in MIC_CALLS)]
    require(len(site) == 1,
            f"the capture port is the ring's one reader, and the engine has {len(site)}: "
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
    shell = read("Android/app/src/main/java/org/un1box/android/Shell.kt")
    require("host.useMicrophone(" in screen, "the microphone row is not wired to the host")
    require("microphone_status" in screen and "host.microphoneStatus" in screen,
            "Settings shows no microphone status line, so a silent guest cannot say whether the "
            "recorder ran or the guest never opened capture")
    require("microphoneStatus" in activity and "microphoneStatus" in service and "microphoneStatus" in shell,
            "the worker's microphone state does not reach the screen")


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
        already = (tree / "hw/arm/gles-host.c").is_file() and "glGenVertexArraysEXT" in (tree / "hw/arm/gles-host.c").read_text(errors="ignore")
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
        shutil.rmtree(scratch, ignore_errors=True)
    return f"from {revision}: {len(list(Path(patch_dir).glob('*.patch')))} patches, clean at {stamp}"


CHECKS = [
    ("engine: the device code, its header and its build entry", engine_adds_the_source),
    ("engine: the microphone is asked before the silent voice", engine_asks_the_microphone_first),
    ("engine: the three entry points and the API bump", engine_exports_the_api),
    ("engine: a stopped capture flushes the ring", engine_flushes_on_stop),
    ("engine: every board with a microphone sets host-mic",
     engine_gives_the_microphone_to_every_board_that_has_one),
    ("portable: the API is declared and typed", portable_declares_the_api),
    ("portable: the engine's entry points are resolved, and optional", portable_resolves_exactly_the_exported_names),
    ("portable: one caller reads the ring, the capture port", portable_pushes_from_the_capture_port),
    ("android: the manifest asks for the microphone", android_asks_for_the_permission),
    ("android: the recorder's format is the API's", android_recorder_matches_the_api),
    ("android: the switch reaches the worker", android_switch_reaches_the_worker),
    ("android: Kotlin externals all have JNI bodies", android_jni_names_match),
]
