#!/usr/bin/env python3
"""The jailbreak path's bunzip2 decompressor, pinned Cydia archives, and Android wiring.

crates/lighttouch-firmware extracts the pinned Legacy-iOS-Kit bootstrap (freeze.tar.gz,
cydiasubstrate.tar, cydiahttpatch.tar) in-process without /usr/bin/tar, while
build-support/jb-bunzip2.c is built for Android as libjbBz2.so so the Jailbreak tab can
decompress BigBoss's Packages.bz2 index.

  python3 tests/jailbreak/run.py [-v] [--keep]
"""
import argparse
import bz2 as bz2_module
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "tests" / "jailbreak"
sys.path.insert(0, str(HERE))

import wiring  # noqa: E402

BZ2_SOURCE = ROOT / "build-support" / "jb-bunzip2.c"
CYDIA_RS = ROOT / "crates" / "lighttouch-firmware" / "src" / "cydia.rs"
WORKFLOW = ROOT / ".github" / "workflows" / "android-perf-apk.yml"
JAILBREAK_WORKFLOW = ROOT / ".github" / "workflows" / "jailbreak-apk.yml"
BZ2_FLAGS = ["-Wall", "-Wextra", "-Werror"]


class Failure(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise Failure(message)


def run(command, **kwargs):
    return subprocess.run(command, capture_output=True, text=True, **kwargs)


class Report:
    def __init__(self):
        self.count = 0
        self.failures = []

    def run(self, name, action):
        self.count += 1
        try:
            note = action()
            print(f"  ok   {name}" + (f" -- {note}" if note else ""))
        except Failure as error:
            self.failures.append((name, str(error)))
            print(f"  FAIL {name} -- {error}")
        except Exception as error:
            self.failures.append((name, repr(error)))
            print(f"  FAIL {name} -- {error!r}")


def pinned_by_the_recipe():
    """The archives and SHA-1s crates/lighttouch-firmware pins."""
    require(CYDIA_RS.is_file(), f"{CYDIA_RS.relative_to(ROOT)} is missing")
    text = CYDIA_RS.read_text(errors="ignore")
    found = re.findall(r'\("([^"]+)",\s*"([0-9a-f]{40})"\)', text)
    require(len(found) >= 3, f"{CYDIA_RS.relative_to(ROOT)} no longer pins the three archives")
    return found


ARCHIVES = pinned_by_the_recipe()


def build(source, output, flags):
    built = run(["cc", "-O2", *flags, "-o", str(output), str(source)])
    require(not built.returncode, f"{source.name} does not compile:\n{built.stderr.strip()[-2000:]}")
    return output


def pinned_archives():
    directory = Path(os.environ.get("LT_JAILBREAK_ARCHIVES") or HERE / "archives")
    if not directory.is_dir():
        return []
    found = []
    for name, sha1 in ARCHIVES:
        path = directory / name
        if path.is_file():
            got = hashlib.sha1(path.read_bytes()).hexdigest()
            require(got == sha1, f"{path}: SHA-1 is {got}, expected {sha1}")
            found.append((name, path))
    return found


def check_pinned_archives(real):
    if not real:
        return "skipped: no pinned archives cached (checked against cydia.rs pins)"
    entries = 0
    for name, path in real:
        mode = "r:gz" if name.endswith(".gz") else "r:"
        with tarfile.open(path, mode) as archive:
            members = archive.getmembers()
            require(len(members) > 0, f"{name} contains no entries")
            entries += len(members)
            if name == "freeze.tar.gz":
                names = {m.name.lstrip("./") for m in members}
                require("Applications/Cydia.app/Cydia" in names or
                        "Applications/Cydia.app/Cydia".lower() in {n.lower() for n in names},
                        "freeze.tar.gz missing Cydia.app")
    return f"{len(real)} pinned archives SHA-1 and tar streams valid ({entries} entries)"


def bz2_indexes_decompress(tmp, bunzip2):
    """The decompressor BigBoss needs: Android has no bzip2, and its index is served only as .bz2."""
    cases = {
        "small": b"Package: a\nVersion: 1\n\n",
        "runs": b"A" * 100000 + b"\x00" * 50000 + b"B" * 259 + b"CD",
        "incompressible": bytes(range(256)) * 4096,
        "empty": b"",
        "packages": b"".join(b"Package: x%d\nVersion: 1.%d\nSize: %d\n\n" % (i, i, i * 7) for i in range(4000)),
    }
    checked = 0
    for name, data in cases.items():
        packed = tmp / (name + ".bz2")
        packed.write_bytes(bz2_module.compress(data))
        plain = tmp / (name + ".out")
        ran = run([str(bunzip2), str(packed), str(plain)])
        require(ran.returncode == 0, f"{name}: the decompressor failed: {ran.stderr.strip()[-200:]}")
        require(plain.read_bytes() == data,
                f"{name}: {plain.stat().st_size} bytes out against {len(data)} in")
        checked += 1

    real = os.environ.get("LT_JAILBREAK_BIGBOSS")
    real = Path(real) if real else HERE / "archives" / "bigboss-Packages.bz2"
    if real.is_file():
        plain = tmp / "bigboss.out"
        ran = run([str(bunzip2), str(real), str(plain)])
        require(ran.returncode == 0, f"{real.name}: the decompressor failed: {ran.stderr.strip()[-200:]}")
        text = plain.read_text(errors="ignore")
        require(text.startswith("Package: "), f"{real.name} did not decompress to a control file")
        checked += 1
    return f"{checked} archives, byte for byte"


def rust_cydia_tests():
    cargo = shutil.which("cargo")
    if not cargo:
        return "skipped: cargo not installed"
    ran = run([cargo, "test", "-p", "lighttouch-firmware", "cydia"], cwd=ROOT)
    require(ran.returncode == 0,
            f"cargo test -p lighttouch-firmware cydia failed:\n{(ran.stdout + ran.stderr).strip()[-2000:]}")
    return "cargo test -p lighttouch-firmware cydia passed"


def the_workflow_builds_what_this_tests():
    workflow = WORKFLOW.read_text()
    jailbreak = JAILBREAK_WORKFLOW.read_text()
    require("build-support/jb-bunzip2.c" in workflow and "ci/firmwarekit/android/jb-bunzip2" in workflow,
            f"{WORKFLOW.name} does not build jb-bunzip2 from the source this test compiles")
    require("crates/lighttouch-firmware" in workflow,
            f"{WORKFLOW.name} does not build crates/lighttouch-firmware")
    require('-Pjailbreak="$DECLARED_JAILBREAK"' in workflow,
            f"{WORKFLOW.name} does not pass -Pjailbreak")
    require(re.search(r'jailbreak:\s*"?true"?', jailbreak) is not None,
            f"{JAILBREAK_WORKFLOW.name} does not ask for the jailbreak build")
    require("uses: ./.github/workflows/android-perf-apk.yml" in jailbreak,
            f"{JAILBREAK_WORKFLOW.name} does not call android-perf-apk.yml")
    return "the workflow builds Rust firmwarekit + jb-bunzip2 and passes the flag"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-v", "--verbose", action="store_true")
    parser.add_argument("--keep", action="store_true", help="keep the built programs and fixtures")
    options = parser.parse_args()
    report = Report()
    scratch = Path(tempfile.mkdtemp(prefix="lt-jailbreak-"))
    started = time.time()
    try:
        bunzip2 = build(BZ2_SOURCE, scratch / "jb-bunzip2", BZ2_FLAGS)
        print(f"jb-bunzip2 built from {BZ2_SOURCE.relative_to(ROOT)} with the host compiler")
        real = pinned_archives()
        report.run("a .bz2 package index decompresses", lambda: bz2_indexes_decompress(scratch, bunzip2))
        report.run("pinned Cydia bootstrap archives match cydia.rs", lambda: check_pinned_archives(real))
        report.run("Rust cydia unit tests pass", rust_cydia_tests)
        report.run("the workflow builds these sources, and passes the flag",
                   the_workflow_builds_what_this_tests)

        print("the wiring between the layers")
        for name, check in wiring.CHECKS:
            report.run(name, check)
    finally:
        if options.keep:
            print(f"kept {scratch}")
        else:
            shutil.rmtree(scratch, ignore_errors=True)
    if report.failures:
        print(f"\n{len(report.failures)} of {report.count} jailbreak checks failed ({time.time() - started:.1f}s):")
        for name, why in report.failures:
            print(f"  {name}: {why}")
        return 1
    print(f"\nall {report.count} jailbreak checks pass ({time.time() - started:.1f}s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
