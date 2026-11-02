#!/usr/bin/env python3
"""Check the guest's GL bridge where it can be checked without a device.

  vertex arrays  the engine's own vertex array object state -- the record a bind
                 swaps, and the entry points that move it -- sliced out of
                 hw/arm/gles-host.c and driven: names, the array state that
                 travels, the buffer references that travel with it, and the host
                 mirror, the fetch scratch and GL_ARRAY_BUFFER, which must not
  name table     the ids the guest calls are the ones the bridge dispatches, with
                 the argument counts and the batching the table promises
  wiring         the contract around the patch, checked as text
  engine stack   the patches in ci/engine-patches apply to a clean checkout

The engine side needs a checkout: --engine, $LT_ENGINE_SOURCE, or a qemu-ios/
directory beside this repository. An unpatched checkout is copied out of git into
a temporary directory and the patches are applied there, so nothing in either
tree is modified. Without one, the engine checks are skipped and the wiring
checks still run against the patch.

  python3 tests/gles/run.py [--engine PATH] [--sanitize] [-v]
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "tests" / "gles"
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "tests"))       # the slicer, shared with tests/mic

import slice as slicer  # noqa: E402
import wiring  # noqa: E402

PATCHES = ROOT / "ci" / "engine-patches"
BRIDGE = "hw/arm/gles-host.c"
NAME_TABLE = "include/hw/arm/guest-services/gles-names.h"
MARKER = "gles_vao_bind"                      # in the bridge once the stack is applied

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
    # CI commits the patches into the engine before these checks run, so a tree
    # either has all of them or none: one marker for the whole stack, not a
    # patch-by-patch question, because a later patch edits lines an earlier added.
    if MARKER in bridge.read_text(errors="ignore"):
        STACK_NOTE = f"{path} already carries the stack"
        return target
    patches = sorted(PATCHES.glob("*.patch"))
    for patch in patches:
        applied = sh(["git", "apply", str(patch)], cwd=target)
        if applied.returncode:
            raise Failure(f"{patch.name} does not apply to {path} at HEAD: "
                          f"{applied.stderr.strip()[:400]}")
    if MARKER not in bridge.read_text(errors="ignore"):
        raise Failure(f"the {len(patches)} patches applied but {BRIDGE} has no {MARKER}: "
                      f"{wiring.VAO_PATCH.name} does not carry the vertex array objects")
    STACK_NOTE = f"{len(patches)} patches applied clean to {path} at HEAD"
    return target


def resolve_engine(explicit, scratch):
    global STACK_NOTE
    candidate = explicit or os.environ.get("LT_ENGINE_SOURCE")
    path = Path(candidate).expanduser().resolve() if candidate else ROOT.parent / "qemu-ios"
    if path.is_dir() and (path / BRIDGE).is_file():
        if MARKER in (path / BRIDGE).read_text(errors="ignore"):
            STACK_NOTE = f"{path} already carries the stack"
            return path, f"engine tree {path} (already patched)"
        if sh(["git", "-C", str(path), "rev-parse", "--git-dir"]).returncode == 0:
            return engine_tree(path, scratch), f"engine tree {path} at HEAD + ci/engine-patches"
        raise Failure(f"{path} is an engine tree without our patches and no way to make one")
    if candidate:
        raise Failure(f"{path} is not an engine checkout (no {BRIDGE})")
    return None, "no engine checkout: the engine checks are skipped"


def compile_and_run(test_file, flags, scratch):
    """Slice the test against the engine, build it and run it."""
    generated = scratch / (test_file.stem + ".gen" + test_file.suffix)
    binary = scratch / test_file.stem
    _, own_flags, own_libs = slicer.generate(test_file, ROOT, ENGINE, generated)
    command = [shutil.which("cc") or "cc", "-std=gnu11", "-O1", "-g", *own_flags, *flags]
    if SANITIZE:
        command += ["-fsanitize=address,undefined", "-fno-sanitize-recover=all"]
    command += [str(generated), "-o", str(binary), *own_libs]
    built = sh(command)
    if built.returncode:
        raise Failure("compile failed:\n" + built.stderr.strip()[-4000:])
    ran = sh([str(binary)])
    if ran.returncode:
        raise Failure("failed:\n" + (ran.stdout + ran.stderr).strip()[-4000:])
    return ran.stdout.strip()


def name_table(engine):
    """The ids the guest calls, as the engine's own table promises them."""
    text = (engine / NAME_TABLE).read_text(errors="ignore")
    rows = {}
    for match in re.finditer(r"GLES_FN\(\s*(\w+)\s*,\s*\w+\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([^)]*)\)", text):
        rows[int(match.group(2))] = (match.group(1), int(match.group(3)), match.group(4).strip())
    problems = []
    for slot, _, name, argc in wiring.ENTRY_POINTS:
        row = rows.get(slot)
        if row is None:
            problems.append(f"{name} ({slot}) is not in the name table")
            continue
        if row[0] != name:
            problems.append(f"id {slot} is {row[0]}, but the bridge dispatches it as {name}")
        if row[1] != argc:
            problems.append(f"{name} sends {row[1]} argument(s), the bridge reads {argc}")
        # A bind only sets state, so the front end may queue it. Generate writes a
        # list the guest reads at once, delete reads one, and is returns the answer:
        # none of those may be queued behind the frame.
        batched = "GLES_F_BATCH" in row[2]
        if batched != name.startswith("glBindVertexArray"):
            problems.append(f"{name} is {'batched' if batched else 'not batched'}, and "
                            + ("only a bind may be queued" if not batched else "the guest reads it at once"))
    if problems:
        raise AssertionError("; ".join(problems))
    return f"{len(wiring.ENTRY_POINTS)} ids: the names, the argument counts and the batching the bridge expects"


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

        print("the engine's vertex array state")
        if ENGINE is None:
            report.add("vertex arrays", True, "skipped: no engine checkout")
            report.add("name table", True, "skipped: no engine checkout")
            report.add("engine stack", True, "skipped: no engine checkout")
        else:
            report.run("vertex arrays: names, binds, deletes, and what stays with the context",
                       lambda: compile_and_run(HERE / "vao.c",
                                               ["-Wall", "-Wextra", "-Werror", "-Wno-unused-parameter"],
                                               scratch))
            report.run("name table: the ids, their argument counts and their batching",
                       lambda: name_table(ENGINE))
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
