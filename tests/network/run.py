#!/usr/bin/env python3
"""The guest's Wi-Fi link, checked against the driver's own behaviour -- the join that gets lost.

An association in these models is four events (AUTH, ASSOC, SET_SSID, LINK), and the model used to
consider that the end of it. iOS 5 is where it is not: its first wifid scan lands about when the
auto-join timer does, the driver drops a join that arrives in the middle of it, and nothing ever
repeats it -- the model believes itself associated, the guest has no link and no lease, the Wi-Fi
pane lists the network a scan found, and Cydia sits on "Loading". It is why an iPad 1 on 5.x had no
network while an iPod touch 4G on 6.x and an iPhone 4 on 7.1.2 were fine on the same engine.

  the slice   tests/network/link-watchdog.c compiles the real sdio_send_assoc_events() and
              sdio_link_check() (out of the patched engine, like tests/mic and tests/gles do) and
              drives them with a fake clock: a silent guest gets the events again, a guest that has
              sent a frame is left alone, a scan in flight defers the retry, the retries are bounded
              and stop, and a join the driver asked for gets a fresh budget.
  the wiring  the patch itself: the watchdog armed by every association, the guest's frames counted
              where they leave the dongle, the state reset and saved.

  python3 tests/network/run.py [--engine PATH] [-v]
"""
import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "tests" / "network"
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "tests"))       # the slicer, shared with tests/mic and tests/gles

import slice as slicer  # noqa: E402
import wiring  # noqa: E402

PATCHES = ROOT / "ci" / "engine-patches"


class Failure(RuntimeError):
    pass


def sh(command, cwd=None):
    return subprocess.run(command, cwd=cwd, capture_output=True, text=True)


def engine_from_git(path: Path, scratch: Path) -> Path:
    """A copy of the engine at HEAD with ci/engine-patches applied, as the APK build does."""
    # No text=True here: a tar stream is not text.
    archive = subprocess.run(["git", "-C", str(path), "archive", "HEAD"], capture_output=True)
    if archive.returncode:
        raise Failure(f"git archive failed in {path}: "
                      f"{archive.stderr.decode(errors='ignore').strip()[:300]}")
    tree = scratch / "engine"
    tree.mkdir()
    unpack = subprocess.run(["tar", "-x", "-C", str(tree)], input=archive.stdout,
                            capture_output=True)
    if unpack.returncode:
        raise Failure(f"tar failed: {unpack.stderr.decode(errors='ignore')[-400:]}")
    for patch in sorted(PATCHES.glob("*.patch")):
        applied = sh(["git", "apply", "--whitespace=nowarn", str(patch)], cwd=tree)
        if applied.returncode:
            raise Failure(f"{patch.name} does not apply to {path}: {applied.stderr.strip()[:400]}")
    return tree


class Report:
    def __init__(self, verbose=False):
        self.verbose = verbose
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


def run_slice(scratch, engine):
    generated = scratch / "link-watchdog.gen.c"
    binary = scratch / "link-watchdog"
    _, flags, libs = slicer.generate(HERE / "link-watchdog.c", ROOT, engine, generated)
    built = sh(["cc", "-std=gnu11", "-O1", "-g", *flags, str(generated), "-o", str(binary), *libs],
               cwd=scratch)
    if built.returncode:
        raise Failure("the slice does not compile:\n" + built.stderr.strip()[-2000:])
    ran = sh([str(binary)])
    if ran.returncode:
        raise Failure("the slice failed:\n" + (ran.stdout + ran.stderr).strip()[-2000:])
    return " ".join(line.strip() for line in ran.stdout.strip().splitlines()
                    if line.strip().startswith("ok"))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--engine", help="engine checkout (default: $LT_ENGINE_SOURCE or ../qemu-ios)")
    parser.add_argument("-v", "--verbose", action="store_true")
    parser.add_argument("--keep", action="store_true", help="keep the generated slice")
    options = parser.parse_args()
    report = Report(options.verbose)
    scratch = Path(tempfile.mkdtemp(prefix="lt-network-"))
    try:
        engine = Path(options.engine or ROOT / "qemu-ios")
        if not (engine / "hw" / "arm" / "ipod_touch_sdio.c").is_file():
            print(f"engine: no checkout at {engine}: the link-watchdog slice is skipped")
            patched = None
        else:
            patched = engine_from_git(engine, scratch)
            print(f"engine: {engine} at HEAD + {len(list(PATCHES.glob('*.patch')))} patches")

        print("the link watchdog a lost join needs")
        if patched is None:
            report.run("the slice compiles and passes", lambda: (_ for _ in ()).throw(
                Failure("skipped: no engine checkout")))
        else:
            report.run("the slice compiles and passes", lambda: run_slice(scratch, patched))

        print("the wiring around it")
        for name, check in wiring.CHECKS:
            report.run(name, check)
    finally:
        if options.keep:
            print(f"kept {scratch}")
        else:
            shutil.rmtree(scratch, ignore_errors=True)
    if report.failures:
        print(f"\n{len(report.failures)} of {report.count} network checks failed:")
        for name, why in report.failures:
            print(f"  {name}: {why}")
        return 1
    print(f"\nall {report.count} network checks pass")
    return 0


if __name__ == "__main__":
    sys.exit(main())
