"""The vertex array object contract, checked as text.

tests/gles/vao.c drives the state machine itself. These checks hold the pieces
around it in place: the patch carries the four entry points in both spellings
and touches nothing else, it asks the host for no VAO of its own -- which is the
design that keeps it working on an ES 1.1 host context and on every backend --
it reads only the arguments the name table says the guest sends, it releases the
records with the context, and the record layout the slice test restates is the
engine's own.

Each check raises with one sentence when it fails, and returns a short note when
it passes.
"""
import re
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCHES = ROOT / "ci" / "engine-patches"
# The bridge and its records come from the engine branch: the slice test restates what the engine's
# own header declares, and the engine's tests/slice/ipod-gles-* cover its internals.
ENGINE_GLES_HEADER = "include/hw/arm/guest-services/gles.h"

def patch_with(marker):
    """The patch that carries a feature: the engine rework is applied in more than one file now."""
    for candidate in sorted(PATCHES.glob("*.patch")):
        if marker in candidate.read_text(errors="ignore"):
            return candidate
    raise AssertionError("no patch in ci/engine-patches carries " + marker)

TEST = ROOT / "tests" / "gles" / "vao.c"

BRIDGE = "hw/arm/gles-host.c"

def read(relative):
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"{relative} is missing")
    return path.read_text(errors="ignore")


def patch_added_lines(patch):
    """The lines a patch adds, as one body of text (its commit message is not in it)."""
    lines = read(patch).splitlines()
    return "\n".join(line[1:] for line in lines
                     if line.startswith("+") and not line.startswith("+++"))


def code_only(text):
    """The added text with its comments dropped: a host call in a comment is not a call."""
    kept = []
    for line in text.splitlines():
        if line.strip().startswith(("/*", "*", "//")):
            continue
        kept.append(re.sub(r"/\*.*?\*/", "", line))
    return "\n".join(kept)


def patch_files(patch):
    return sorted({match.group(1) for match in
                   re.finditer(r"^diff --git a/(\S+) b/\S+$", read(patch), re.M)})


def files_touched_by_the_patch():
    """The bridge is the engine branch's own now, and the guest itpacks are built from that same
    branch: no patch may touch it, because a patch that did would put the engine and the guest shim
    on different footings -- they ship together, from one source."""
    touched = [candidate.name for candidate in sorted(PATCHES.glob("*.patch"))
               if BRIDGE in patch_files("ci/engine-patches/" + candidate.name)]
    if touched:
        raise AssertionError(f"{touched} touch {BRIDGE}: the GL bridge belongs to the engine branch "
                             f"now, and the guest packages are built from the engine ref the APK is "
                             f"built from")
    return "no patch touches the bridge: it and the guest shim come from the engine branch together"


CHECKS = [
    ("engine patch: no patch touches the bridge, which the guest shares", files_touched_by_the_patch),
]
