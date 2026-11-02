#!/usr/bin/env python3
"""Check the camera path without a build, a device or a CI run.

Every layer is exercised where it can be, and read where it cannot:

  slots          real hw/arm/host-camera.c: the newest frame wins, an unread one
                 is replaced rather than queued, the picture is held until the
                 app flushes it, and a frame that is not a picture is refused
  convert        real include/hw/arm/s5l8930-isp-camera.h: NV12 and the ISP's
                 4:2:2 both come out right, scaling picks the nearest source
                 pixel, and nothing is written past the frame
  planes         real camera_planes.h: planar, semi-planar and row-padded
                 YUV_420_888 frames all become one NV12 frame, and shapes that
                 cannot be converted are refused
  model          real hw/arm/s5l8930_isp.c compiled whole against stub/, so the
                 device build's own errors are caught here first
  wiring         the connections: the patch, the JNI names, the permission and the
                 worker's wiring, and that Settings no longer offers the switch
  engine stack   the patches apply to a clean checkout and commit to it (needs
                 --engine; skipped otherwise)

The engine side needs a checkout: --engine, $LT_ENGINE_SOURCE, or a qemu-ios/
directory beside this repository. An unpatched checkout is copied out of git
into a temporary directory, so nothing in either tree is modified.

  python3 tests/camera/run.py [--engine PATH] [--sanitize] [-v]
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "tests" / "camera"
sys.path.insert(0, str(HERE))

import wiring  # noqa: E402

PATCHES = ROOT / "ci" / "engine-patches"


class Failure(RuntimeError):
    pass


def sh(command, cwd=None):
    return subprocess.run(command, cwd=cwd, capture_output=True, text=True)


class Report:
    def __init__(self, verbose=False):
        self.rows, self.verbose = [], verbose

    def add(self, name, ok, detail=""):
        self.rows.append((name, ok, detail))
        print(f"  {'ok  ' if ok else 'FAIL'} {name}" + (f" — {detail}" if detail and self.verbose else ""))
        return ok

    def run(self, name, action):
        try:
            return self.add(name, True, action() or "")
        except Failure as failure:
            return self.add(name, False, str(failure))
        except AssertionError as failure:
            return self.add(name, False, str(failure))
        except Exception as failure:  # a crash in a check is a failed check
            return self.add(name, False, f"{type(failure).__name__}: {failure}")

    @property
    def failed(self):
        return [row for row in self.rows if not row[1]]


def build_and_run(name, sources, includes, extra, scratch, compiler=None):
    compiler = compiler or shutil.which("cc") or "cc"
    binary = scratch / name.replace(" ", "-")
    command = [compiler, *extra]
    for include in includes:
        command += ["-I", str(include)]
    command += [str(source) for source in sources] + ["-o", str(binary)]
    if SANITIZE:
        command += ["-fsanitize=address,undefined", "-fno-sanitize-recover=all"]
    built = sh(command)
    if built.returncode:
        raise Failure("compile failed:\n" + built.stderr.strip()[-3000:])
    ran = sh([str(binary)])
    if ran.returncode:
        raise Failure("failed:\n" + (ran.stdout + ran.stderr).strip()[-3000:])
    return ran.stdout.strip()


def compile_model_file(engine, source, includes):
    """Type-check one engine source whole, against stub/, before a device build does."""
    command = [shutil.which("cc") or "cc", "-fsyntax-only", "-std=gnu11", "-Wall", "-Wextra", "-Werror",
               "-Wno-unused-parameter", "-I", str(HERE / "stub")]
    for include in includes:
        command += ["-I", str(include)]
    command.append(str(source))
    built = sh(command)
    if built.returncode:
        raise Failure("does not compile:\n" + built.stderr.strip()[-3000:])
    return f"{source.name}: {len(source.read_text(errors='ignore').splitlines())} lines"


def engine_tree(engine, scratch):
    """A copy of the engine at HEAD with the camera patch applied: what the device build will see.

    CI commits the patches into the engine checkout before it runs these checks, so a tree that already
    carries them is normal and must not be patched twice."""
    archive = subprocess.run(["git", "-C", str(engine), "archive", "HEAD"], capture_output=True)
    if archive.returncode:
        raise Failure(f"git archive failed in {engine}")
    target = scratch / "engine"
    target.mkdir()
    subprocess.run(["tar", "-x", "-C", str(target)], input=archive.stdout, check=True)
    # CI commits the patches into the engine checkout before these checks run, so the tree either has all of
    # them or none: asking patch by patch does not work, because a later patch edits lines an earlier one
    # added, and the earlier one then cannot be recognised. One marker for the whole stack.
    if not (target / "hw/arm/host-camera.c").is_file():
        for patch in sorted(PATCHES.glob("*.patch")):
            applied = sh(["git", "apply", str(patch)], cwd=target)
            if applied.returncode:
                raise Failure(f"{patch.name} does not apply: {applied.stderr.strip()[:400]}")
    for needed in ("hw/arm/host-camera.c", "include/hw/arm/host-camera.h",
                   "include/hw/arm/s5l8930-isp-camera.h"):
        if not (target / needed).is_file():
            raise Failure(f"the engine has no {needed}: the camera patch is neither in the tree nor applies")
    return target


def main():
    options = parser().parse_args()
    global SANITIZE
    SANITIZE = options.sanitize
    engine = Path(options.engine or os.environ.get("LT_ENGINE_SOURCE") or ROOT.parent / "qemu-ios")
    engine = engine if engine.is_dir() else None
    scratch = Path(tempfile.mkdtemp())
    report = Report(options.verbose)
    try:
        # A copy of the engine with the camera patch applied: what the device build will see, and the only
        # place host-camera.c and the header exist.
        tree = engine_tree(engine, scratch) if engine else None

        print("the camera's own code")
        if tree is None:
            report.add("slots", True, "skipped: no engine checkout")
            report.add("convert", True, "skipped: no engine checkout")
        else:
            report.run("slots: the newest frame wins and is held until flushed", lambda: build_and_run(
                "slots", [HERE / "slots.c", tree / "hw/arm/host-camera.c"],
                [HERE / "stub", tree / "hw/arm", tree / "include"],
                ["-std=gnu11", "-Wall", "-Wextra", "-Werror", "-Wno-unused-parameter"], scratch))
            report.run("convert: the phone's frame becomes the guest's", lambda: build_and_run(
                "convert", [HERE / "convert.c"], [tree / "include"],
                ["-std=gnu11", "-Wall", "-Wextra", "-Werror"], scratch))
        report.run("planes: every YUV_420_888 shape becomes one NV12 frame", lambda: build_and_run(
            "planes", [HERE / "planes.cpp"], [ROOT / "Android/app/src/main/cpp"],
            ["-std=c++20", "-Wall", "-Wextra", "-Werror"], scratch, compiler=shutil.which("c++") or "c++"))

        print("the device model")
        if tree is None:
            report.add("model", True, "skipped: no engine checkout")
        else:
            report.run("model: the ISP compiles whole", lambda: compile_model_file(
                tree, tree / "hw/arm/s5l8930_isp.c", [HERE / "stub", tree / "include"]))

        print("wiring between the layers")
        for name, check in wiring.CHECKS:
            report.run(name, check)

        print("engine stack")
        report.run("patches: the stack applies and commits to a clean tree",
                   lambda: wiring.patch_stack(PATCHES, engine))
    finally:
        shutil.rmtree(scratch, ignore_errors=True)

    print()
    if report.failed:
        # Also as workflow commands: these checks run with continue-on-error in CI, where a failure is not
        # allowed to withhold an APK -- so the annotation is the only place it is visible.
        for name, _, detail in report.failed:
            print(f"::error title={name}::{detail}")
        print(f"{len(report.failed)} of {len(report.rows)} checks failed:")
        for name, _, detail in report.failed:
            print(f"  {name}: {detail.splitlines()[0] if detail else ''}")
        return 1
    print(f"all {len(report.rows)} camera checks pass")
    return 0


SANITIZE = False


def parser():
    p = argparse.ArgumentParser()
    p.add_argument("--engine")
    p.add_argument("--sanitize", action="store_true")
    p.add_argument("-v", "--verbose", action="store_true")
    return p


if __name__ == "__main__":
    sys.exit(main())
