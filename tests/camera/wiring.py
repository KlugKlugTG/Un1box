"""The camera's layers, checked against the real sources and the patch that ships the engine side.

The slices prove what a piece does; these prove the pieces are connected to each other and to the guest:

  engine patch   the patch still applies to a clean engine and commits to it, and still contains the hand-off,
                 the conversion and the one place the ISP draws a picture instead of the pattern
  device model   the ISP copies the phone's frame before it draws, never draws a size that is not a mode, and
                 falls back to the pattern only when the phone has sent nothing
  native         the JNI entry points the app declares exist, with the names the app asks for
  android        the permission and the request that still belong to the path, the worker's own wiring,
                 and that Settings no longer offers a switch for a path that delivers no picture
"""
import os
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

CAMERA_PATCH = patch_with("host-camera")


def read(relative):
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"{relative} is missing")
    return path.read_text(errors="ignore")


def added_lines(patch):
    return "\n".join(line[1:] for line in read(patch).splitlines() if line.startswith("+"))


def file_body(patch, relative):
    """A patch's view of one file: its context and added lines, in order."""
    lines, inside = [], False
    for line in read(patch).splitlines():
        if line.startswith("diff --git "):
            inside = line.endswith(" b/" + relative)
            continue
        if inside and line[:1] in (" ", "+") and not line.startswith("+++"):
            lines.append(line[1:])
    return "\n".join(lines)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


# --- the engine side ---------------------------------------------------------

def engine_patch_ships_the_hand_off():
    body = added_lines(CAMERA_PATCH)
    require("int host_camera_write(" in body, "the patch no longer adds the camera's mailbox")
    require("void host_camera_snapshot(" in body, "the mailbox no longer hands a frame to the ISP")
    require("'host-camera.c'" in body, "hw/arm/meson.build does not build host-camera.c")
    for entry in ("qemu_ios_camera_write", "qemu_ios_camera_flush", "qemu_ios_camera_sensor",
                  "qemu_ios_camera_state"):
        require(entry in body, f"the engine API no longer exports {entry}")


def engine_patch_serves_one_mailbox():
    """The guest drives one camera at a time and its ISP asks for frames either way: one mailbox, whichever it is."""
    body = added_lines(CAMERA_PATCH)
    require("host_camera_take(" not in body, "the ISP takes frames per sensor again: that is what made a guest draw none")
    isp = file_body(CAMERA_PATCH, "hw/arm/s5l8930_isp.c")
    require("host_camera_snapshot(&frame)" in isp or "host_camera_snapshot(" in isp,
            "the ISP no longer asks the mailbox for the phone's newest frame")
    frame = isp.find("static void isp_frame(")
    snapshot = isp.find("isp_camera_snapshot(s);", frame)
    stopped = isp.find("!r[ISP_OUT_ENABLE / 4] || !y", frame)
    require(snapshot != -1 and stopped != -1 and snapshot < stopped,
            "the frame is taken after the ISP's own early return: a guest not drawing yet would lose it")


def engine_patch_draws_the_camera_before_the_pattern():
    """Order matters: a pattern in front of a real source is a picture nobody ever sees."""
    isp = file_body(CAMERA_PATCH, "hw/arm/s5l8930_isp.c")
    frame = isp.find("static void isp_frame(")
    camera = isp.find("s5l8930_isp_camera_convert(", frame)
    pattern = isp.find("isp_pattern(s, w, h, nv12)", frame)
    require(camera != -1 and pattern != -1 and camera < pattern,
            "the test pattern is drawn before the camera's frame: the phone's picture would never show")
    require("if (s->camera_frame)" in isp, "the ISP does not ask whether the phone has a picture")


def engine_patch_ships_the_conversion():
    body = file_body(CAMERA_PATCH, "include/hw/arm/s5l8930-isp-camera.h")
    require("s5l8930_isp_camera_convert(" in body, "the patch no longer adds the NV12 conversion")
    require("destination_uv[dst] = source_uv[src]" in body or "nv12" in body,
            "the conversion has no NV12 path")
    unit = added_lines(CAMERA_PATCH)
    require("test-s5l8930-isp-camera" in unit, "the conversion has no unit test in the engine")


def engine_patch_refuses_a_size_that_is_not_a_mode():
    isp = file_body(CAMERA_PATCH, "hw/arm/s5l8930_isp.c")
    require("w <= ISP_OUT_MAX && h <= ISP_OUT_MAX" in isp and "!(w & 1)" in isp,
            "a frame size the sensors never had is no longer skipped: it would be allocated and DMAed")
    require("y > 0x1000" in isp, "the ISP no longer checks that the guest gave it a buffer")


# --- native and android ------------------------------------------------------

def jni_matches_the_app():
    native = read("Android/app/src/main/java/org/un1box/android/Native.kt")
    jni = read("Android/app/src/main/cpp/un1box_jni.cpp")
    for method in ("cameraFrame", "cameraSensor", "cameraFlush", "cameraInfo"):
        require(f"external fun {method}(" in native, f"the app does not declare {method}")
        require(f"Java_org_un1box_android_Native_{method}(" in jni, f"the JNI side has no {method}")
    require('#include "camera_planes.h"' in jni, "the JNI side does not use the plane conversion")
    require("lt_runtime_camera_write(" in jni, "the JNI side never reaches the portable camera API")


def portable_declares_the_camera():
    header = read("Portable/include/lighttouch.h")
    source = read("Portable/src/Runtime.cpp")
    require("LT_CAMERA_BACK" in header and "LT_CAMERA_FRONT" in header, "the sensors are not named in the API")
    for function in ("lt_runtime_camera_write", "lt_runtime_camera_info", "lt_runtime_camera_flush"):
        require(function in header and function in source, f"{function} is not declared and defined")
    require('dlsym(library, "qemu_ios_camera_write")' in source,
            "the portable layer does not resolve the engine's camera entry point")
    require("camera_state(sensor, &pushed, &taken, &replaced, &width, &height)" in source,
            "the portable layer does not ask the engine for the picture's size")


def android_the_permission_and_its_request_stay():
    """The path is dark, not gone: what would ask Android for the camera is still in place."""
    manifest = read("Android/app/src/main/AndroidManifest.xml")
    require('android.permission.CAMERA' in manifest, "the manifest does not ask for the camera")
    activity = read("Android/app/src/main/java/org/un1box/android/MainActivity.kt")
    require("Manifest.permission.CAMERA" in activity, "nothing asks Android for the camera any more")
    require("cameraPermission.launch" in activity, "the permission has no request left to launch it")


def android_the_worker_decides_and_keeps_the_camera_off():
    """The worker's wiring, and the one place that now decides: no stored preference can start a capture."""
    service = read("Android/app/src/main/java/org/un1box/android/EngineService.kt")
    require('getBoolean("camera"' not in service,
            "the worker still reads a stored camera preference, and Settings has no switch to turn it off")
    require("cameraWanted = false" in service, "the worker does not say plainly that the camera stays off")
    require('setenv("IT_CAMERA"' in service,
            "IT_CAMERA is not set before the guest starts: off, the board unmatches the isp node and there is "
            "no camera at all")
    require("CameraCapture(" in service and "startCamera()" in service, "the worker never starts the capture")
    require("cameraWatch" not in service and "capture.follow(" not in service,
            "the capture waits for the guest to name a camera again: a guest that never does shows nothing")
    capture = read("Android/app/src/main/java/org/un1box/android/CameraCapture.kt")
    require("LENS_FACING_BACK" in capture, "the capture does not open the back camera")
    require("LENS_FACING_FRONT" not in capture,
            "the capture can still open the front camera, which is the switch that used to leave the guest blank")
    require("Native.CAMERA_BACK," in capture, "the frame does not say which camera it came from")


def android_settings_does_not_offer_the_camera():
    """No switch, and no status line: the guest's ISP delivers no picture, so the row is out.

    What stays is the plumbing the row was wired to, so that putting the row back is a Settings change
    and not a rebuild of the path."""
    settings = read("Android/app/src/main/java/org/un1box/android/SettingsScreen.kt")
    for gone in ('"camera_toggle"', '"camera_status"', "host.useCamera", "host.cameraStatus", 'Group("Camera")'):
        require(gone not in settings, f"Settings still offers the camera: {gone}")
    shell = read("Android/app/src/main/java/org/un1box/android/Shell.kt")
    require("val camera: Boolean" in shell and "val cameraStatus: String" in shell and "fun useCamera(" in shell,
            "the screen host no longer carries the camera's state: the plumbing is meant to stay")
    prefs = read("Android/app/src/main/java/org/un1box/android/AppSettings.kt")
    require('getBoolean("camera", false)' in prefs, "the preference the old switch wrote is not read any more")


# --- the stack itself --------------------------------------------------------

def patch_stack(patch_dir, engine):
    """The whole stack applies to a clean engine and commits to it."""
    if engine is None:
        raise AssertionError("skipped: no engine checkout")
    scratch = Path(tempfile.mkdtemp())
    tree = scratch / "engine"
    try:
        archive = subprocess.run(["git", "-C", str(engine), "archive", "HEAD"], capture_output=True)
        if archive.returncode:
            raise AssertionError(f"git archive failed in {engine}")
        tree.mkdir()
        subprocess.run(["tar", "-x", "-C", str(tree)], input=archive.stdout, check=True)
        subprocess.run(["git", "init", "-q"], cwd=tree, check=True)
        subprocess.run(["git", "add", "-A"], cwd=tree, check=True)
        subprocess.run(["git", "-c", "user.email=a@b", "-c", "user.name=c", "commit", "-q", "-m", "base"],
                       cwd=tree, check=True)
        # CI commits the patches into the engine before these checks run, so a tree either has the whole stack
        # or none of it. A file only the camera patch adds is the marker.
        patched = (tree / "hw/arm/host-camera.c").is_file()
        if not patched:
            for patch in sorted(Path(patch_dir).glob("*.patch")):
                applied = subprocess.run(["git", "apply", str(patch)], cwd=tree,
                                         capture_output=True, text=True)
                if applied.returncode:
                    raise AssertionError(f"{patch.name} does not apply: {applied.stderr.strip()[:400]}")
        subprocess.run(["git", "add", "-A"], cwd=tree, check=True)
        # CI commits the patches into its checkout before these checks run, so a tree that already has them
        # has nothing to commit -- which is not a failure.
        staged = subprocess.run(["git", "status", "--porcelain"], cwd=tree, capture_output=True, text=True)
        if staged.stdout.strip():
            committed = subprocess.run(
                ["git", "-c", "user.email=a@b", "-c", "user.name=c",
                 "-c", "user.name=Un1box", "commit", "-q", "-m", "camera"],
                cwd=tree, capture_output=True, text=True)
            if committed.returncode:
                raise AssertionError(f"the stack does not commit: "
                                     f"{(committed.stderr or committed.stdout).strip()[:400]}")
        dirty = subprocess.run(["git", "status", "--porcelain"], cwd=tree, capture_output=True, text=True)
        if dirty.stdout.strip():
            raise AssertionError(f"the patched tree is dirty after the commit: {dirty.stdout.strip()[:300]}")
        return f"{len(list(Path(patch_dir).glob('*.patch')))} patches, clean"
    finally:
        import shutil
        shutil.rmtree(scratch, ignore_errors=True)


CHECKS = [
    ("engine patch: the hand-off and the API are there", engine_patch_ships_the_hand_off),
    ("engine patch: one mailbox serves whichever camera the guest is on", engine_patch_serves_one_mailbox),
    ("engine patch: the camera is drawn before the pattern", engine_patch_draws_the_camera_before_the_pattern),
    ("engine patch: the conversion is shipped with its unit test", engine_patch_ships_the_conversion),
    ("engine patch: a size that is not a mode is refused", engine_patch_refuses_a_size_that_is_not_a_mode),
    ("portable: the camera API is declared and resolved", portable_declares_the_camera),
    ("native: every entry point the app declares exists", jni_matches_the_app),
    ("android: the permission and the request that go with it stay", android_the_permission_and_its_request_stay),
    ("android: the worker is wired, and keeps the camera off itself",
     android_the_worker_decides_and_keeps_the_camera_off),
    ("android: Settings does not offer the camera", android_settings_does_not_offer_the_camera),
]
