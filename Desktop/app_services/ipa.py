"""Bounded IPA preflight and executable-mode repair; never extracts an archive."""
import copy
import os
from pathlib import Path, PurePosixPath
import plistlib
import stat
import struct
import zipfile

from .catalog import Target, _version


class InstallError(ValueError):
    pass


MAX_IPA = 2 * 1024**3
MAX_UNPACKED = 8 * 1024**3


def binary_slices(data):
    """Return supported ARM32 subtypes after bounded Mach-O validation."""
    return [subtype for subtype, minimum in _arm_slices(data)]


def _arm_slices(data):
    """A fat executable contains flat slices, never recursively nested headers."""
    if len(data) < 28:
        raise InstallError("The app executable is truncated.")
    if data[:4] == b"\xca\xfe\xba\xbe":
        count = struct.unpack_from(">I", data, 4)[0]
        if not 0 < count <= 32 or len(data) < 8 + count * 20:
            raise InstallError("Invalid universal executable.")
        result, regions = [], []
        header_end = 8 + count * 20
        for i in range(count):
            cpu, subtype, offset, size, alignment = struct.unpack_from(">IIIII", data, 8 + i * 20)
            if (offset < header_end or size < 28 or offset + size > len(data) or
                    alignment > 31 or offset % (1 << alignment) or
                    any(offset < end and offset + size > start for start, end in regions)):
                raise InstallError("Invalid or overlapping universal executable slices.")
            regions.append((offset, offset + size))
            # Other architectures do not prevent installing the compatible ARM32
            # slice. Still validate every table range before ignoring its bytes.
            if cpu == 12:
                parsed = _arm_slice(data[offset:offset + size])
                if parsed[0] != subtype & 0xffffff:
                    raise InstallError("Universal executable architecture metadata disagrees.")
                result.append(parsed)
        if not result:
            raise InstallError("This IPA does not contain a supported ARM executable.")
        return result
    return [_arm_slice(data)]


def _arm_slice(data, total_length=None):
    if total_length is None:
        total_length = len(data)
    if len(data) < 28 or total_length < 28:
        raise InstallError("The app executable is truncated.")
    if data[:4] != b"\xce\xfa\xed\xfe":
        raise InstallError("This IPA needs an unsupported executable format.")
    _, cpu, subtype, filetype, commands, size, _ = struct.unpack_from("<7I", data)
    if cpu != 12 or filetype != 2 or commands > 65536 or 28 + size > total_length or 28 + size > len(data):
        raise InstallError("This IPA does not contain a supported ARM executable.")
    cursor, minimum = 28, None
    for _ in range(commands):
        if cursor + 8 > 28 + size:
            raise InstallError("Invalid executable load commands.")
        command, length = struct.unpack_from("<II", data, cursor)
        if length < 8 or cursor + length > 28 + size:
            raise InstallError("Invalid executable load commands.")
        if command == 0x21:
            if length < 20 or struct.unpack_from("<I", data, cursor + 16)[0] != 0:
                raise InstallError("This app is encrypted; select a decrypted IPA.")
        if command == 0x25:  # LC_VERSION_MIN_IPHONEOS
            if length < 16:
                raise InstallError("Invalid executable minimum iOS version.")
            packed = struct.unpack_from("<I", data, cursor + 8)[0]
            version = (packed >> 16, (packed >> 8) & 255, packed & 255)
            minimum = max(minimum or version, version)
        if command == 0x32:  # LC_BUILD_VERSION, platform IOS = 2
            if length < 24 or struct.unpack_from("<I", data, cursor + 8)[0] != 2:
                raise InstallError("The app executable targets an unsupported platform.")
            packed = struct.unpack_from("<I", data, cursor + 12)[0]
            version = (packed >> 16, (packed >> 8) & 255, packed & 255)
            minimum = max(minimum or version, version)
        cursor += length
    if cursor != 28 + size:
        raise InstallError("Invalid executable load-command size.")
    return subtype & 0xffffff, minimum


def _arm_slices_from_entry(archive, entry):
    """Read only Mach-O headers/load commands for thin binaries instead of inflating up to 256 MiB."""
    with archive.open(entry) as stream:
        head = stream.read(28)
        if len(head) < 28:
            raise InstallError("The app executable is truncated.")
        if head[:4] == b"\xce\xfa\xed\xfe":
            _, _, _, _, commands, size, _ = struct.unpack_from("<7I", head)
            if commands > 65536 or 28 + size > entry.file_size or size > 4 * 1024**2:
                raise InstallError("This IPA does not contain a supported ARM executable.")
            body = stream.read(size)
            return [_arm_slice(head + body, total_length=entry.file_size)]
    return _arm_slices(archive.read(entry))


def inspect(path, target=None):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= MAX_IPA:
        raise InstallError("Select a regular IPA smaller than 2 GiB.")
    try:
        with zipfile.ZipFile(path) as archive:
            entries = archive.infolist()
            if len(entries) > 100000 or sum(e.file_size for e in entries) > MAX_UNPACKED:
                raise InstallError("The unpacked IPA exceeds the supported size.")
            names = set()
            for entry in entries:
                name = entry.filename
                parts = PurePosixPath(name).parts
                if (name in names or name.startswith("/") or ".." in parts or "\\" in name or
                        name.rstrip("/") != str(PurePosixPath(name)) or entry.flag_bits & 1):
                    raise InstallError("The IPA contains unsafe, duplicate or encrypted ZIP members.")
                names.add(name)
                if stat.S_ISLNK(entry.external_attr >> 16):
                    if entry.file_size > 4096:
                        raise InstallError("The IPA contains an invalid symbolic link.")
                    link = archive.read(entry).decode("utf-8")
                    resolved = list(PurePosixPath(name).parent.parts)
                    if (not link or link.startswith("/") or "\\" in link or "\0" in link or
                            len(parts) < 3 or parts[0] != "Payload" or not parts[1].endswith(".app")):
                        raise InstallError("The IPA contains an unsafe symbolic link.")
                    for component in PurePosixPath(link).parts:
                        if component == "..":
                            if len(resolved) <= 2:
                                raise InstallError("The IPA contains a symbolic link outside its app.")
                            resolved.pop()
                        elif component != ".":
                            resolved.append(component)
            candidates = [e for e in entries if len(PurePosixPath(e.filename).parts) == 3 and
                          e.filename.startswith("Payload/") and e.filename.endswith(".app/Info.plist")]
            if len(candidates) != 1 or candidates[0].file_size > 1024**2:
                raise InstallError("Select an IPA containing one Payload app and Info.plist.")
            info = plistlib.loads(archive.read(candidates[0]))
            bundle = info.get("CFBundleIdentifier")
            executable = info.get("CFBundleExecutable")
            if (not isinstance(bundle, str) or not bundle or not isinstance(executable, str) or
                    not executable or "/" in executable or "\\" in executable or executable in (".", "..")):
                raise InstallError("The app has invalid bundle or executable metadata.")
            member = candidates[0].filename.rsplit("/", 1)[0] + "/" + executable
            entry = archive.getinfo(member)
            if entry.file_size > 256 * 1024**2 or stat.S_ISLNK(entry.external_attr >> 16):
                raise InstallError("The app executable is unsupported or too large.")
            slices = _arm_slices_from_entry(archive, entry)
            if target:
                supported = (0, 6) if target.arch == "armv6" else (0, 6, 9)
                matching = [(subtype, minimum) for subtype, minimum in slices if subtype in supported]
                if not matching:
                    raise InstallError("This app needs a newer processor than the guest.")
                # iOS selects the most specific available CPU slice. A lower
                # minimum on an unused armv6 slice cannot qualify newer armv7.
                best = max(subtype for subtype, minimum in matching)
                minima = [minimum for subtype, minimum in matching if subtype == best and minimum]
                if minima and max(minima) > _version(target.ios):
                    raise InstallError("The app executable requires iOS {}.".format(".".join(map(str, max(minima)))))
                minimum = info.get("MinimumOSVersion")
                if minimum and _version(minimum) > _version(target.ios):
                    raise InstallError("This app requires iOS {}; the guest runs {}.".format(minimum, target.ios))
                families = info.get("UIDeviceFamily")
                if families and int(target.family) not in families:
                    raise InstallError("This app is not made for the guest device family.")
            return {"bundle_id": bundle, "name": info.get("CFBundleDisplayName", bundle),
                    "executable": member, "repair": not bool((entry.external_attr >> 16) & 0o111),
                    "minimum_os": info.get("MinimumOSVersion"), "bytes": path.stat().st_size}
    except (OSError, UnicodeError, zipfile.BadZipFile, KeyError, plistlib.InvalidFileException, struct.error, TypeError) as error:
        raise InstallError("The IPA is incomplete or has invalid app metadata: " + str(error)) from error


def prepare(source, destination, target, cancelled=lambda: False):
    """Always snapshot into private scratch; repair only the main binary mode."""
    metadata = inspect(source, target)
    destination = Path(destination)
    try:
        if not metadata["repair"]:
            # Fast path for already-executable IPAs: stream raw bytes directly into
            # the private 0600 snapshot without inflating/re-deflating every member.
            with Path(source).open("rb") as src_raw, destination.open("xb") as dst_raw:
                os.chmod(destination, 0o600)
                copied = 0
                while True:
                    if cancelled():
                        raise InstallError("Install preparation cancelled.")
                    chunk = src_raw.read(1048576)
                    if not chunk:
                        break
                    copied += len(chunk)
                    if copied > MAX_IPA:
                        raise InstallError("Select a regular IPA smaller than 2 GiB.")
                    dst_raw.write(chunk)
                dst_raw.flush()
                os.fsync(dst_raw.fileno())
        else:
            with zipfile.ZipFile(source) as archive, destination.open("xb") as raw:
                os.chmod(destination, 0o600)
                with zipfile.ZipFile(raw, "w", allowZip64=True) as output:
                    for entry in archive.infolist():
                        fixed = copy.copy(entry)
                        if entry.filename == metadata["executable"]:
                            fixed.create_system = 3
                            fixed.external_attr = ((stat.S_IFREG | 0o755) << 16) | (entry.external_attr & 0xffff)
                        with archive.open(entry) as reader, output.open(fixed, "w") as writer:
                            while True:
                                if cancelled():
                                    raise InstallError("Install preparation cancelled.")
                                chunk = reader.read(1048576)
                                if not chunk:
                                    break
                                writer.write(chunk)
                raw.flush()
                os.fsync(raw.fileno())
        checked = inspect(destination, target)
        if checked["bundle_id"] != metadata["bundle_id"] or checked["executable"] != metadata["executable"]:
            raise InstallError("The source IPA changed during preparation.")
        return checked
    except BaseException:
        destination.unlink(missing_ok=True)
        raise
