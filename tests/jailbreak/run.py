#!/usr/bin/env python3
"""The jailbreak path's tar, checked against the host's own tar -- and the shim that reaches it.

The jailbreak build's preparation passes --jailbreak, and firmwarekit then installs the Cydia
bootstrap by running the host's tar three times per archive:

    tar -tf  <archive>                                 the member names
    tar -tvf <archive>                                 the same with modes, for set-user-ID files
    tar -xkf <archive> -C <volume> --exclude <path>     the extraction, keeping what is there

Android has no /usr/bin/tar and toybox's has neither -k nor --exclude, so Packages/FirmwareKit/
build-support/jb-tar.c is built for arm64 and the spawn shim points that path at it. Both are C that
runs on the host too, so both are checked here, with no phone and no engine:

  the tar     built with the host's cc and run against the archives it is pinned to (fetch them into
              $LT_JAILBREAK_ARCHIVES or tests/jailbreak/archives to have the real ones checked too)
              and against fixtures built here, always: the listing must equal the host tar's, the -tv
              lines must be the nine fields firmwarekit's parser reads, an extraction must produce the
              tree the host tar produces, an entry the volume already has must be kept (and the run
              must still succeed -- GNU tar fails there, which is why this one exists), the excluded
              directory must be untouched, and a damaged or escaping archive must be refused.
  the shim    built as a library, preloaded into tests/jailbreak/spawn-tar.c, which does what
              firmwarekit does: spawn "/usr/bin/tar" with the recipe's arguments. The output must be
              our tar's, and the shim's log must show the redirect and the dropped LD_PRELOAD.

  python3 tests/jailbreak/run.py [-v] [--keep]
"""
import argparse
import hashlib
import io
import os
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "tests" / "jailbreak"
sys.path.insert(0, str(HERE))

import wiring  # noqa: E402  (the layer-to-layer checks, kept beside this file)
TAR_SOURCE = ROOT / "Packages" / "FirmwareKit" / "build-support" / "jb-tar.c"
SHIM_SOURCE = ROOT / "Packages" / "FirmwareKit" / "build-support" / "spawn-shim.c"
CYDA = ROOT / "Packages" / "FirmwareKit" / "Sources" / "FirmwareKit" / "SystemEdits" / "Cydia.swift"
WORKFLOW = ROOT / ".github" / "workflows" / "android-perf-apk.yml"
JAILBREAK_WORKFLOW = ROOT / ".github" / "workflows" / "jailbreak-apk.yml"


def pinned_by_the_recipe():
    """The archives and SHA-1s firmwarekit downloads, read out of the recipe that pins them."""
    if not CYDA.is_file():
        raise Failure(f"{CYDA.relative_to(ROOT)} is missing: the archives it pins cannot be read")
    found = re.findall(r'\(name: "([^"]+)", sha1: "([0-9a-f]{40})"\)', CYDA.read_text(errors="ignore"))
    if not found:
        raise Failure(f"{CYDA.relative_to(ROOT)} no longer names the jailbreak archives and their SHA-1s")
    return found


ARCHIVES = pinned_by_the_recipe()
EXCLUDES = ["--exclude", "./private/var/Keychains", "--exclude", "private/var/Keychains"]
EXCLUDED_DIR = "private/var/Keychains"
# The host tar is asked for --same-permissions in the extraction comparisons. A plain `tar -x` applies
# the process umask and drops set-user-ID, which on a phone (Android apps run with umask 0077) would
# leave the guest's Cydia at 0700 and its cydo without its bits; jb-tar restores the archive's modes the
# way -p does, and -p is also what makes the reference the same whether the test runs as root or not.
HOST_EXTRACT = ["tar", "-xpkf"]

# Same command line as the workflows': the tar strict, the shim as Android compiles it (no -Werror;
# on glibc three warnings about headers Android does not have would stop it).
TAR_FLAGS = ["-Wall", "-Wextra", "-Werror"]
SHIM_FLAGS = ["-Wall", "-Wextra", "-fPIC", "-shared", "-Wno-nonnull-compare", "-Wno-format-truncation",
              "-Wno-pointer-bool-conversion"]


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
        except Exception as error:   # a broken check is a failure, not a crash
            self.failures.append((name, repr(error)))
            print(f"  FAIL {name} -- {error!r}")


# --- building the programs -------------------------------------------------------------------------

def build(source, output, flags, libraries=()):
    built = run(["cc", "-O2", *flags, "-o", str(output), str(source), *libraries])
    require(not built.returncode,
            f"{source.name} does not compile:\n{built.stderr.strip()[-2000:]}")
    return output


# --- fixtures --------------------------------------------------------------------------------------

FIXTURE_ENTRIES = [
    (".", None, "dir", 0o755, ""),
    ("./usr", None, "dir", 0o755, ""),
    ("./usr/bin", None, "dir", 0o775, ""),
    ("./usr/bin/tool", b"tool-bytes\n", "file", 0o755, ""),
    ("./usr/bin/tool-link", None, "symlink", 0o777, "tool"),
    ("./usr/bin/cydo", b"setuid-bytes\n", "file", 0o6755, ""),
    ("./private/var/Keychains/secret.plist", b"secret\n", "file", 0o600, ""),
    ("./private/var/mobile/Media", None, "dir", 0o755, ""),
]


def fixture(scratch, name, fmt, compress=False, deep=False):
    """One small archive with the feature set the pinned ones have, in the format named."""
    path = scratch / name
    long_member = "./usr/libexec/" + "/".join(["nested" + "d" * 40] * 2) + "/payload"
    entries = list(FIXTURE_ENTRIES)
    entries.append((long_member, b"deep\n", "file", 0o644, "") if deep else
                   ("./usr/libexec/payload", b"deep\n", "file", 0o644, ""))
    with tarfile.open(path, "w:gz" if compress else "w", format=fmt) as archive:
        for member, data, kind, permissions, target in entries:
            info = tarfile.TarInfo(member)
            info.mtime = 1500000000
            info.mode = permissions
            info.uname, info.gname = "root", "wheel"
            if kind == "dir":
                info.type = tarfile.DIRTYPE
            elif kind == "symlink":
                info.type, info.linkname = tarfile.SYMTYPE, target
            else:
                info.type, info.size = tarfile.REGTYPE, len(data)
            archive.addfile(info, io.BytesIO(data) if kind == "file" else None)
    return path


def fixtures(scratch):
    return [("fixture-ustar.tar", fixture(scratch, "fixture-ustar.tar", tarfile.USTAR_FORMAT)),
            ("fixture-ustar.tar.gz", fixture(scratch, "fixture-ustar.tar.gz", tarfile.USTAR_FORMAT, compress=True)),
            ("fixture-pax.tar", fixture(scratch, "fixture-pax.tar", tarfile.PAX_FORMAT, deep=True)),
            ("fixture-gnu.tar", fixture(scratch, "fixture-gnu.tar", tarfile.GNU_FORMAT, deep=True))]


def pinned_archives():
    """The real archives, when a checkout has them: [(name, path)]."""
    for directory in (os.environ.get("LT_JAILBREAK_ARCHIVES"), HERE / "archives"):
        if not directory:
            continue
        directory = Path(directory)
        found = []
        for name, sha1 in ARCHIVES:
            path = directory / name
            if not path.is_file() or hashlib.sha1(path.read_bytes()).hexdigest() != sha1:
                break
            found.append((name, path))
        if len(found) == len(ARCHIVES):
            return found
    return []


# --- comparing an extraction with the host tar's ----------------------------------------------------

def tree(root):
    """Every entry under `root`: type, mode, size, content or link target, and the link count."""
    records = {}
    for base, directories, files in os.walk(root):
        directories.sort()
        for name in sorted(directories + files):
            path = Path(base) / name
            relative = str(path.relative_to(root))
            status = os.lstat(path)
            if stat.S_ISLNK(status.st_mode):
                records[relative] = ("link", os.readlink(path))
            elif stat.S_ISDIR(status.st_mode):
                records[relative] = ("dir", oct(status.st_mode & 0o7777))
            elif stat.S_ISREG(status.st_mode):
                records[relative] = ("file", oct(status.st_mode & 0o7777), status.st_size,
                                     hashlib.sha256(path.read_bytes()).hexdigest(), status.st_nlink)
            else:
                records[relative] = ("other", oct(status.st_mode))
    return records


def members_of(path):
    """The archive's own entry paths, as the extraction lays them out (no ./, no trailing /)."""
    with tarfile.open(path) as archive:
        return {member.name.removeprefix("./").rstrip("/") for member in archive.getmembers()}


def compare_trees(ours, theirs, what, keys=None):
    """The two trees, entry for entry -- all of them, or only those the archive names.

    `keys` is for the directories a tar invents to hold a member whose parents the archive does not
    list: the host tar makes those with the umask (0700 under a phone's 0077), jb-tar makes them 0755
    because they end up in the guest, and that difference is asserted separately rather than compared.
    """
    mine, host = tree(ours), tree(theirs)
    if keys is not None:
        mine = {name: record for name, record in mine.items() if name in keys}
        host = {name: record for name, record in host.items() if name in keys}
    require(mine.keys() == host.keys(),
            f"{what}: the trees differ in which entries exist: {sorted(mine.keys() ^ host.keys())[:4]}")
    differing = [name for name in mine if mine[name] != host[name]]
    require(not differing,
            f"{what}: {len(differing)} entries differ from the host tar's, first {differing[:2]}: "
            f"{[(mine[name], host[name]) for name in differing[:1]]}")
    return len(mine)


def invented_directories_are_readable(ours, members, what, ignore=()):
    """Every directory the extraction made, that the archive does not name, must be enterable."""
    seen = tree(ours)
    invented = [name for name, record in seen.items()
                if name not in members and name not in ignore and record[0] == "dir"]
    wrong = [name for name in invented if seen[name][1] != oct(0o755)]
    require(not wrong,
            f"{what}: {wrong[:3]} were created with a mode the guest's users may not enter; the phone's "
            "umask (0077) must not reach the volume")
    return len(invented)


# --- what firmwarekit's parser reads out of -tv -----------------------------------------------------

def special_mode(line):
    """SystemEdits.Cydia.specialMode: nine fields, a ten-character mode, a set-user-ID regular file."""
    fields = line.split(None, 8)
    if len(fields) != 9 or len(fields[0]) != 10 or fields[0][0] != "-":
        return None
    mode = 0
    for index, character in enumerate(fields[0][1:]):
        if character not in "-ST":
            mode |= 1 << (8 - index)
    if fields[0][3] in "sS":
        mode |= 0o4000
    if fields[0][6] in "sS":
        mode |= 0o2000
    return (fields[8], oct(mode)) if mode & 0o6000 else None


def verbose_contract(listing):
    lines = listing.splitlines()
    malformed = [line for line in lines if len(line.split(None, 8)) != 9 or len(line.split(None, 8)[0]) != 10]
    require(not malformed,
            f"{len(malformed)} -tv lines are not the nine fields the recipe's parser reads: {malformed[:2]}")
    return [entry for entry in (special_mode(line) for line in lines) if entry]


# --- the checks -------------------------------------------------------------------------------------

def the_listing_is_the_host_tars(tar, archives):
    for name, path in archives:
        ours, theirs = run([str(tar), "-tf", str(path)]), run(["tar", "-tf", str(path)])
        require(ours.returncode == 0, f"{name}: -tf failed: {ours.stderr.strip()[-400:]}")
        require(theirs.returncode == 0, f"the host's tar cannot read {name}")
        require(ours.stdout == theirs.stdout,
                f"{name}: the listing differs from the host tar's ({len(ours.stdout.splitlines())} against "
                f"{len(theirs.stdout.splitlines())} names)")
    return f"{len(archives)} archives, listings identical to the host's tar"


def the_verbose_lines_are_what_the_recipe_parses(tar, archives):
    lines, setuid = 0, []
    for name, path in archives:
        listing = run([str(tar), "-tvf", str(path)])
        require(listing.returncode == 0, f"{name}: -tvf failed: {listing.stderr.strip()[-400:]}")
        entries = verbose_contract(listing.stdout)
        lines += len(listing.stdout.splitlines())
        setuid += [(name, entry) for entry in entries]
    bootstrap = [entry for name, entry in setuid if name.endswith("freeze.tar.gz")]
    if bootstrap:
        require(len(bootstrap) == 4,
                "the bootstrap's four set-user-ID files (login, passwd, cydo, su) are not all found: "
                f"{[entry[0] for entry in bootstrap]}")
        require(any(entry[1] == oct(0o6755) for entry in bootstrap),
                "the set-group-ID on Cydia's cydo is not read out of the listing")
    return (f"{lines} lines, {len(setuid)} set-user-ID files found"
            f"{': ' + ', '.join(entry[0].split('/')[-1] for entry in bootstrap) if bootstrap else ''}")


def an_extraction_is_the_host_tars_tree(tmp, tar, archives):
    entries, modes = 0, 0
    for name, path in archives:
        ours, theirs = tmp / f"extract-ours-{name}", tmp / f"extract-theirs-{name}"
        for directory in (ours, theirs):
            shutil.rmtree(directory, ignore_errors=True)
            directory.mkdir(parents=True)
        mine = run([str(tar), "-xkf", str(path), "-C", str(ours), *EXCLUDES])
        host = run([*HOST_EXTRACT, str(path), "-C", str(theirs), *EXCLUDES])
        require(host.returncode == 0, f"{name}: the host's tar failed to extract: {host.stderr.strip()[-300:]}")
        require(mine.returncode == 0, f"{name}: extraction failed: {mine.stderr.strip()[-400:]}")
        require(not (ours / EXCLUDED_DIR).exists(), f"{name}: --exclude did not keep {EXCLUDED_DIR} out")
        members = members_of(path)
        entries += compare_trees(ours, theirs, name, keys=members)
        invented_directories_are_readable(ours, members, name)
        # What -tvf reports and what is on disk must agree: the recipe reads the set-user-ID modes out of
        # the listing and its volume builder reads the files, so a pair that disagreed would either make
        # Cydia's helpers unusable or set a bit the guest's file system does not have.
        listing = run([str(tar), "-tvf", str(path)])
        for entry_path, mode in verbose_contract(listing.stdout):
            landed = ours / entry_path.removeprefix("./")
            if not landed.exists():
                continue   # excluded, or it was not a member of this package's set
            got = stat.S_IMODE(os.stat(landed).st_mode)
            require(got == int(mode, 8),
                    f"{name}: {entry_path} landed as {oct(got)}, and the -tv listing the recipe parses "
                    f"says {mode}: the archive's modes are not being restored")
            modes += 1
    return (f"{entries} entries identical to the host tar's (asked for --same-permissions), the excluded "
            f"directory absent, {modes} set-user-ID modes as listed")


def an_entry_the_volume_has_is_kept(tmp, tar, archives):
    """The stock-volume case: the file is there, the archive has its own, the run must still succeed.

    On the bootstrap itself when it is cached -- that is the archive firmwarekit extracts onto a stock
    iOS root, where /usr/bin/login, /bin/su and Cydia's own cydo are already present.
    """
    targets = [archives[0]] + [entry for entry in archives if entry[0].endswith("freeze.tar.gz")]
    summaries = []
    for name, path in targets:
        with tarfile.open(path) as archive:
            members = archive.getmembers()
        regular = next(m for m in members if m.isreg() and m.size > 0 and not m.name.startswith("./" + EXCLUDED_DIR))
        link = next(m for m in members if m.issym())
        directory = next(m for m in members if m.isdir() and m.name not in (".", "./") and
                         not m.name.startswith("./" + EXCLUDED_DIR))
        stock = {"usr/bin/login": b"STOCK LOGIN\n", "bin/uncompress": b"STOCK UNCOMPRESS\n",
                 EXCLUDED_DIR + "/keep.plist": b"keychain\n"}
        ours, theirs = tmp / f"stock-ours-{name}", tmp / f"stock-theirs-{name}"
        for target in (ours, theirs):
            shutil.rmtree(target, ignore_errors=True)
            for relative, data in stock.items():
                file = target / relative
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(data)
            for member in (regular, directory):
                (target / member.name.lstrip("./")).parent.mkdir(parents=True, exist_ok=True)
            (target / regular.name.lstrip("./")).write_bytes(b"STOCK ENTRY\n")
            (target / regular.name.lstrip("./")).chmod(0o600)
            (target / link.name.lstrip("./")).symlink_to("/stock/target")
            (target / directory.name.lstrip("./")).mkdir(parents=True, exist_ok=True)
            (target / directory.name.lstrip("./")).chmod(0o700)
        existing = [regular.name.lstrip("./"), link.name.lstrip("./"), directory.name.lstrip("./")] + list(stock)
        before = set(tree(ours))   # the stock tree is the test's own; only what the extraction makes counts
        mine = run([str(tar), "-xkf", str(path), "-C", str(ours), *EXCLUDES])
        host = run([*HOST_EXTRACT, str(path), "-C", str(theirs), *EXCLUDES])
        require(host.returncode != 0,
                f"{name}: the host's tar accepted a volume whose files it would overwrite: this check no "
                "longer tests the difference it exists for")
        require(mine.returncode == 0,
                f"{name}: an entry the volume already has failed the run (exit {mine.returncode}): "
                f"{mine.stderr.strip()[-300:]}")
        require((ours / regular.name.lstrip("./")).read_bytes() == b"STOCK ENTRY\n",
                f"{name}: {regular.name} was overwritten")
        require(stat.S_IMODE(os.stat(ours / regular.name.lstrip("./")).st_mode) == 0o600,
                f"{name}: {regular.name} changed mode")
        require(os.readlink(ours / link.name.lstrip("./")) == "/stock/target",
                f"{name}: {link.name}, a symbolic link the volume had, was replaced")
        require(stat.S_IMODE(os.stat(ours / directory.name.lstrip("./")).st_mode) == 0o700,
                f"{name}: {directory.name} changed mode")
        require(all((ours / relative).read_bytes() == data for relative, data in stock.items()),
                f"{name}: extraction changed a file it was told to keep")
        entries = compare_trees(ours, theirs, f"{name} stock volume", keys=members_of(path))
        invented_directories_are_readable(ours, members_of(path), f"{name} stock volume", ignore=before)
        added = [m.name for m in members if m.isreg() and (ours / m.name.lstrip("./")).is_file()]
        require(len(added) > 1, f"{name}: the archive's own files were not extracted at all")
        summaries.append(f"{name}: {len(existing)} kept, {entries} entries")
    return "; ".join(summaries)


def a_damaged_header_and_an_escaping_member_are_refused(tmp, tar):
    damaged = fixture(tmp, "damaged.tar", tarfile.USTAR_FORMAT)
    raw = bytearray(damaged.read_bytes())
    raw[0] = ord("X")
    damaged.write_bytes(bytes(raw))
    refused = run([str(tar), "-tf", str(damaged)])
    require(refused.returncode != 0, "a header with a bad checksum was accepted")
    require("checksum" in refused.stderr, f"the refusal does not say what is wrong: {refused.stderr.strip()[:200]}")

    evil = tmp / "evil.tar"
    with tarfile.open(evil, "w") as archive:
        info = tarfile.TarInfo("../escaped")
        info.size = 4
        archive.addfile(info, io.BytesIO(b"evil"))
    destination = tmp / "evil-out"
    destination.mkdir(exist_ok=True)
    escaping = run([str(tar), "-xf", str(evil), "-C", str(destination)])
    require(escaping.returncode != 0, "a member above the destination was extracted")
    require(not (tmp / "escaped").exists(), "a member above the destination was written")
    return "a bad checksum and a ../ member both refused, with a message"


def a_spawn_of_usr_bin_tar_reaches_this_tar(tmp, tar, archives, shim, harness):
    """What firmwarekit does: spawn "/usr/bin/tar", under the shim, and read what comes back."""
    staged = tmp / "shim-dir"
    staged.mkdir(exist_ok=True)
    shutil.copy(shim, staged / "libspawnshim.so")
    shutil.copy(tar, staged / "libjbTar.so")
    log = tmp / "shim.log"
    environment = {**os.environ, "LD_PRELOAD": str(staged / "libspawnshim.so"), "SPAWN_SHIM_LOG": str(log)}
    environment.pop("FIRMWAREKIT_TAR", None)   # the shim has to find the copy beside itself
    name, path = archives[0]
    for arguments in (["-tf", str(path)], ["-tvf", str(path)]):
        if log.exists():
            log.unlink()
        mine = subprocess.run([str(harness), *arguments], capture_output=True, text=True, env=environment)
        # -tf is the host tar's listing exactly; -tv is this tar's own, which is the nine-field line the
        # recipe's parser reads rather than GNU tar's six (see the note in jb-tar.c).
        theirs = run(["tar", *arguments]) if arguments[0] == "-tf" else run([str(tar), *arguments])
        require(mine.returncode == 0,
                f"the spawn of /usr/bin/tar did not succeed: {mine.stderr.strip()[-300:]}")
        require(theirs.returncode == 0, f"{arguments[0]} failed for the reference tar")
        require(mine.stdout == theirs.stdout,
                f"the {arguments[0]} listing the shim produced is not the tar's the shim points at")
        written = log.read_text(errors="ignore") if log.exists() else ""
        require("/usr/bin/tar -> " in written and "libjbTar.so" in written,
                f"the shim did not redirect /usr/bin/tar: {written.strip()[-300:]}")
        require("(preload dropped)" in written, f"the tar inherited the shim: {written.strip()[-300:]}")

    volume, theirs = tmp / "shim-volume", tmp / "shim-theirs"
    for directory in (volume, theirs):
        shutil.rmtree(directory, ignore_errors=True)
        directory.mkdir()
    mine = subprocess.run([str(harness), "-xkf", str(path), "-C", str(volume), *EXCLUDES],
                          capture_output=True, text=True, env=environment)
    host = run([*HOST_EXTRACT, str(path), "-C", str(theirs), *EXCLUDES])
    require(mine.returncode == 0, f"the extraction through the shim failed: {mine.stderr.strip()[-300:]}")
    require(host.returncode == 0, "the host's tar failed the same extraction")
    entries = compare_trees(volume, theirs, "through the shim", keys=members_of(path))
    return f"a listing and an extraction ({entries} entries) reached the tar the shim names"


def the_workflow_builds_what_this_tests():
    """These two files are the ones the APK ships, built the way the workflow builds them."""
    workflow = WORKFLOW.read_text()
    jailbreak = JAILBREAK_WORKFLOW.read_text()
    require("build-support/jb-tar.c" in workflow and "ci/firmwarekit/android/jb-tar" in workflow,
            f"{WORKFLOW.name} does not build jb-tar from the source this test compiles")
    require("-Pjailbreak=\"$DECLARED_JAILBREAK\"" in workflow,
            f"{WORKFLOW.name} does not pass -Pjailbreak, so a jailbreak build would prepare stock systems")
    require(re.search(r'jailbreak:\s*"?true"?', jailbreak) is not None,
            f"{JAILBREAK_WORKFLOW.name} does not ask for the jailbreak build")
    require("uses: ./.github/workflows/android-perf-apk.yml" in jailbreak,
            f"{JAILBREAK_WORKFLOW.name} does not call the build that produces the APK")
    return "the workflow builds these sources and passes the flag"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-v", "--verbose", action="store_true")
    parser.add_argument("--keep", action="store_true", help="keep the built programs and fixtures")
    options = parser.parse_args()
    report = Report()
    scratch = Path(tempfile.mkdtemp(prefix="lt-jailbreak-"))
    started = time.time()
    try:
        tar = build(TAR_SOURCE, scratch / "jb-tar", TAR_FLAGS, ["-lz"])
        print(f"jb-tar built from {TAR_SOURCE.relative_to(ROOT)} with the host compiler")
        real = pinned_archives()
        archives = fixtures(scratch) + real
        print(f"archives: {len(archives)}" + (f", the {len(real)} pinned ones from {real[0][1].parent}" if real
              else " (fixtures only: put the pinned bootstrap archives in tests/jailbreak/archives or name"
                   " their directory in LT_JAILBREAK_ARCHIVES to check those too)"))
        print("the tar, against the host's own")
        report.run("the listing is the host tar's", lambda: the_listing_is_the_host_tars(tar, archives))
        report.run("the -tv lines are the nine fields the recipe parses",
                   lambda: the_verbose_lines_are_what_the_recipe_parses(tar, archives))
        report.run("an extraction is the tree the host tar makes",
                   lambda: an_extraction_is_the_host_tars_tree(scratch, tar, archives))
        report.run("an entry the volume already has is kept, and the run still succeeds",
                   lambda: an_entry_the_volume_has_is_kept(scratch, tar, archives))
        report.run("a damaged header and an escaping member are refused",
                   lambda: a_damaged_header_and_an_escaping_member_are_refused(scratch, tar))

        print("the shim that points firmwarekit at it")
        shim = build(SHIM_SOURCE, scratch / "libspawnshim.so", SHIM_FLAGS)
        harness = build(HERE / "spawn-tar.c", scratch / "spawn-tar", ["-Wall", "-Wextra", "-Werror"])
        report.run("a spawn of /usr/bin/tar runs this tar",
                   lambda: a_spawn_of_usr_bin_tar_reaches_this_tar(scratch, tar, archives, shim, harness))

        print("the build that ships them")
        report.run("the workflow builds these sources, and passes the flag",
                   lambda: the_workflow_builds_what_this_tests())

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
