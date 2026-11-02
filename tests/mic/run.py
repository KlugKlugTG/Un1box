#!/usr/bin/env python3
"""Check the microphone path without a build, a device or a CI run.

Every layer is exercised where it can be, and read where it cannot:

  capture port   real hw/arm/host-mic.c + the engine's own i2s_next_frame() and
                 i2s_rx_read(), sliced out of hw/arm/s5l8930_i2s.c, driven with
                 the silence the `none` audio backend really produces
  portable API   real lt_runtime_mic_write/info/flush, sliced out of
                 Portable/src/Runtime.cpp, against a stub engine
  JNI glue       real Native.microphone/microphoneFlush/microphoneInfo, sliced
                 out of un1box_jni.cpp, against a stub JNIEnv
  wiring         the names, permissions and orderings that connect those layers
  engine patches the patch stack still applies to the engine and still commits
                 to a clean tree (needs --engine; skipped otherwise)

The engine side needs an engine checkout: --engine, $LT_ENGINE_SOURCE, or a
qemu-ios/ directory beside this repository. An unpatched checkout is copied out
of git into a temporary directory and the patches in ci/engine-patches are
applied there, so nothing in either tree is modified.

  python3 tests/mic/run.py [--engine PATH] [-v]
"""
import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "tests" / "mic"
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "tests"))       # the slicer, shared with tests/gles

import slice as slicer  # noqa: E402
import wiring  # noqa: E402

PATCHES = ROOT / "ci" / "engine-patches"


class Failure(RuntimeError):
    pass


def sh(command, cwd=None, stdin=None):
    return subprocess.run(command, cwd=cwd, capture_output=True, text=True, input=stdin)


def engine_from_git(path: Path, scratch: Path) -> Path:
    """A copy of the engine at HEAD with ci/engine-patches applied."""
    archive = subprocess.run(["git", "-C", str(path), "archive", "HEAD"], capture_output=True)
    if archive.returncode:
        raise Failure(f"git archive failed in {path}: {archive.stderr.decode(errors='replace').strip()}")
    target = scratch / "engine"
    target.mkdir()
    extract = subprocess.run(["tar", "-x", "-C", str(target)], input=archive.stdout, capture_output=True)
    if extract.returncode:
        raise Failure(f"could not unpack the engine: {extract.stderr.decode(errors='replace').strip()}")
    patches = sorted(PATCHES.glob("*.patch"))
    for patch in patches:
        applied = sh(["git", "apply", str(patch)], cwd=target)
        if applied.returncode:
            # 0009 alone is enough for the sliced device code; anything else means
            # the patch stack no longer fits this engine revision.
            single = sh(["git", "apply", str(PATCHES / "0009-engine-microphone-input.patch")], cwd=target)
            if single.returncode:
                raise Failure(
                    f"{patch.name} does not apply to {path} at HEAD, and neither does 0009 alone: "
                    f"{applied.stderr.strip()}")
    return target


def resolve_engine(explicit: str | None, scratch: Path) -> tuple[Path | None, str]:
    candidate = explicit or __import__("os").environ.get("LT_ENGINE_SOURCE")
    path = Path(candidate).expanduser().resolve() if candidate else ROOT.parent / "qemu-ios"
    if path.is_dir() and (path / "hw/arm/s5l8930_i2s.c").is_file():
        text = (path / "hw/arm/s5l8930_i2s.c").read_text(errors="ignore")
        if "host_mic_read_frame" in text:
            return path, f"engine tree {path} (already patched)"
        if (path / ".git").exists() or sh(["git", "-C", str(path), "rev-parse", "--git-dir"]).returncode == 0:
            return engine_from_git(path, scratch), f"engine tree {path} at HEAD + ci/engine-patches"
        raise Failure(f"{path} is an engine tree with our patches and no way to make one")
    if candidate:
        raise Failure(f"{path} is not an engine checkout (no hw/arm/s5l8930_i2s.c)")
    return None, "no engine checkout: device-side slices skipped"


class Report:
    def __init__(self, verbose=False):
        self.rows = []
        self.verbose = verbose

    def add(self, name, ok, detail=""):
        self.rows.append((name, ok, detail))
        mark = "ok  " if ok else "FAIL"
        print(f"  {mark} {name}" + (f" — {detail}" if detail and (not ok or self.verbose) else ""))
        return ok

    def run(self, name, action):
        try:
            detail = action() or ""
            return self.add(name, True, detail)
        except Failure as failure:
            return self.add(name, False, str(failure))
        except Exception as failure:  # a crash in a check is a failed check
            return self.add(name, False, f"{type(failure).__name__}: {failure}")

    @property
    def failed(self):
        return [row for row in self.rows if not row[1]]


def compile_and_run(report, name, test_file, compiler, flags, sources, includes, scratch):
    generated = scratch / (test_file.stem + ".gen" + test_file.suffix)
    binary = scratch / test_file.stem
    _, own_flags, own_libs = slicer.generate(test_file, ROOT, ENGINE, generated)
    command = [compiler, "-std=gnu11" if compiler.endswith("cc") else "-std=gnu++20", "-O1", "-g",
               *own_flags, *flags]
    if SANITIZE:
        command += ["-fsanitize=address,undefined", "-fno-sanitize-recover=all"]
    for include in includes:
        command += ["-I", str(include)]
    command += [str(generated), *map(str, sources), "-o", str(binary), *own_libs]
    built = sh(command)
    if built.returncode:
        raise Failure("compile failed:\n" + built.stderr.strip()[-4000:])
    ran = sh([str(binary)])
    if ran.returncode:
        raise Failure("failed:\n" + (ran.stdout + ran.stderr).strip()[-4000:])
    return ran.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--engine", help="engine checkout (default: $LT_ENGINE_SOURCE or ../qemu-ios)")
    parser.add_argument("-v", "--verbose", action="store_true")
    parser.add_argument("--keep", action="store_true", help="keep the generated sources")
    parser.add_argument("--sanitize", action="store_true",
                        help="build the slice tests with -fsanitize=address,undefined")
    options = parser.parse_args()
    report = Report(options.verbose)
    scratch = Path(tempfile.mkdtemp(prefix="lt-mic-"))

    global ENGINE, SANITIZE
    SANITIZE = options.sanitize
    try:
        ENGINE, engine_note = resolve_engine(options.engine, scratch)
        print(f"engine: {engine_note}")

        print("device capture port")
        if ENGINE is None:
            report.add("capture port", True, "skipped: no engine checkout")
        else:
            report.run("capture port: the recorded frames reach the guest", lambda: compile_and_run(
                report, "capture port", HERE / "capture-port.c", shutil.which("cc") or "cc",
                ["-Wall", "-Wextra", "-Werror", "-Wno-unused-parameter"],
                [HERE / "clock.c", ENGINE / "hw/arm/host-mic.c"],
                [HERE / "stub", ENGINE / "include", ROOT], scratch))

        print("portable and JNI glue")
        report.run("portable API: validation, LT_AGAIN, LT_UNSUPPORTED", lambda: compile_and_run(
            report, "portable api", HERE / "portable-api.cpp", shutil.which("c++") or "c++",
            ["-Wall", "-Werror"], [], [ROOT, ROOT / "Portable/include", HERE / "stub"], scratch))
        report.run("JNI glue: mono to stereo, counters, return codes", lambda: compile_and_run(
            report, "jni glue", HERE / "jni-glue.cpp", shutil.which("c++") or "c++",
            ["-Wall", "-Werror"], [], [ROOT, ROOT / "Portable/include"], scratch))

        print("wiring between the layers")
        for name, check in wiring.CHECKS:
            report.run(name, check)

        print("engine patches")
        report.run("patches: stack applies and commits to a clean tree",
                   lambda: wiring.patch_stack(PATCHES, options.engine or ENGINE))
    finally:
        if options.keep:
            print(f"generated sources kept in {scratch}")
        else:
            shutil.rmtree(scratch, ignore_errors=True)

    failed = report.failed
    print()
    if failed:
        print(f"{len(failed)} of {len(report.rows)} checks failed:")
        for name, _, detail in failed:
            print(f"  {name}: {detail.splitlines()[0] if detail else ''}")
        return 1
    print(f"all {len(report.rows)} microphone checks pass")
    return 0


ENGINE = None
SANITIZE = False

if __name__ == "__main__":
    sys.exit(main())
