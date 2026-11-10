#!/usr/bin/env python3
"""Check the guest's GL bridge where it can be checked without a device.

  engine stack   the patches in ci/engine-patches apply to a clean checkout
                 and leave the bridge itself alone -- the guest itpacks are
                 built from the same engine ref the APK is, so a patch that
                 touched the bridge would put the two apart
  wiring         the contract around the patch, checked as text
  engine stack   the patches in ci/engine-patches apply to a clean checkout

The stack check needs a checkout: --engine, $LT_ENGINE_SOURCE, or a qemu-ios/ directory
beside this repository. The bridge's own records and its platform are the engine's
now, and its tests/slice and its build cover them; what stays here is ours. An unpatched checkout is copied out of git into
a temporary directory and the patches are applied there, so nothing in either
tree is modified. Without one, the engine checks are skipped and the wiring
checks still run against the patch.

  python3 tests/gles/run.py [--engine PATH] [--sanitize] [-v]
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "tests" / "gles"
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "tests"))       # the slicer, shared with tests/mic

import wiring  # noqa: E402

PATCHES = ROOT / "ci" / "engine-patches"
BRIDGE = "hw/arm/gles-host.c"
NAME_TABLE = "include/hw/arm/guest-services/gles-names.h"
# The bridge is the engine branch's own now (no patch touches it), so the marker that says
# "the stack is already here" has to come from a file the series does edit: the microphone
# consult in the capture port, which patch 0001 adds.
MARKER_FILE = "hw/arm/s5l8930_i2s.c"
MARKER = "host_mic_read_frame"

ENGINE = None
SANITIZE = False
STACK_NOTE = ""


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


def engine_tree(path, scratch):
    """A copy of the engine at HEAD with ci/engine-patches applied: what CI builds."""
    global STACK_NOTE
    archive = subprocess.run(["git", "-C", str(path), "archive", "HEAD"], capture_output=True)
    if archive.returncode:
        raise Failure(f"git archive failed in {path}: {archive.stderr.decode(errors='replace').strip()}")
    target = scratch / "engine"
    target.mkdir()
    unpack = subprocess.run(["tar", "-x", "-C", str(target)], input=archive.stdout, capture_output=True)
    if unpack.returncode:
        raise Failure(f"could not unpack the engine: {unpack.stderr.decode(errors='replace').strip()}")
    bridge = target / BRIDGE
    if not bridge.is_file():
        raise Failure(f"{path} has no {BRIDGE}: not an engine checkout")
    marker = target / MARKER_FILE
    # CI commits the patches into the engine before these checks run, so a tree
    # either has all of them or none: one marker for the whole stack, not a
    # patch-by-patch question, because a later patch edits lines an earlier added.
    if MARKER in marker.read_text(errors="ignore"):
        STACK_NOTE = f"{path} already carries the stack"
        return target
    patches = sorted(PATCHES.glob("*.patch"))
    for patch in patches:
        applied = sh(["git", "apply", str(patch)], cwd=target)
        if applied.returncode:
            raise Failure(f"{patch.name} does not apply to {path} at HEAD: "
                          f"{applied.stderr.strip()[:400]}")
    if MARKER not in marker.read_text(errors="ignore"):
        raise Failure(f"{MARKER_FILE} has no {MARKER}: the series did not land")
    STACK_NOTE = f"{len(patches)} patches applied clean to {path} at HEAD"
    return target


def resolve_engine(explicit, scratch):
    global STACK_NOTE
    candidate = explicit or os.environ.get("LT_ENGINE_SOURCE")
    path = Path(candidate).expanduser().resolve() if candidate else ROOT.parent / "qemu-ios"
    if path.is_dir() and (path / BRIDGE).is_file():
        if (path / MARKER_FILE).is_file() and MARKER in (path / MARKER_FILE).read_text(errors="ignore"):
            STACK_NOTE = f"{path} already carries the stack"
            return path, f"engine tree {path} (already patched)"
        if sh(["git", "-C", str(path), "rev-parse", "--git-dir"]).returncode == 0:
            return engine_tree(path, scratch), f"engine tree {path} at HEAD + ci/engine-patches"
        raise Failure(f"{path} is an engine tree without our patches and no way to make one")
    if candidate:
        raise Failure(f"{path} is not an engine checkout (no {BRIDGE})")
    return None, "no engine checkout: the engine checks are skipped"


def main():
    global ENGINE, SANITIZE
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--engine", help="engine checkout (default: $LT_ENGINE_SOURCE or ../qemu-ios)")
    parser.add_argument("--sanitize", action="store_true",
                        help="build the slice test with -fsanitize=address,undefined")
    parser.add_argument("--keep", action="store_true", help="keep the generated sources")
    parser.add_argument("-v", "--verbose", action="store_true")
    options = parser.parse_args()
    SANITIZE = options.sanitize
    report = Report(options.verbose)
    scratch = Path(tempfile.mkdtemp(prefix="lt-gles-"))

    try:
        try:
            ENGINE, note = resolve_engine(options.engine, scratch)
        except Failure as failure:
            ENGINE, note = None, f"unusable: {failure}"
            report.add("engine checkout", False, str(failure))
        print(f"engine: {note}")

        print("the stack against the engine")
        if ENGINE is None:
            report.add("engine stack", True, "skipped: no engine checkout")
        else:
            report.run("engine stack: the patches apply to a clean checkout", lambda: STACK_NOTE)

        print("the contract around the patch")
        for name, check in wiring.CHECKS:
            report.run(name, check)
    finally:
        if options.keep:
            print(f"generated sources kept in {scratch}")
        else:
            shutil.rmtree(scratch, ignore_errors=True)

    failed = report.failed
    print()
    if failed:
        if os.environ.get("GITHUB_ACTIONS"):
            for name, _, detail in failed:
                print(f"::error title={name}::{detail}")
        print(f"{len(failed)} of {len(report.rows)} checks failed:")
        for name, _, detail in failed:
            print(f"  {name}: {detail.splitlines()[0] if detail else ''}")
        return 1
    print(f"all {len(report.rows)} GL bridge checks pass")
    return 0


if __name__ == "__main__":
    sys.exit(main())
