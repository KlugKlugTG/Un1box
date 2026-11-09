#!/usr/bin/env python3
"""Add iBoot32Patcher to the notices firmwarekit's licence screen reads.

    stage-iboot32patcher-notice.py LICENSES_DIR IBOOT32PATCHER_LICENSE

The Android APK ships the patcher as libiBoot32Patcher.so (FirmwareService points firmwarekit at it
with FIRMWAREKIT_IBOOT_PATCHER), so its terms belong in the same group as firmwarekit's: this copies
the tool's GPL-3.0 text and build-support/notices/iBoot32Patcher-NOTICE.txt into LICENSES_DIR and adds
both to its manifest.json ({"schema": 1, "source_reference", "files": [{"path", "sha256"}]}), which the
app verifies by SHA-256 before it shows a notice.

The manifest is rewritten in place, so this runs after whatever produced it (stage-notices.py on a build
host, or the APK the CI reuses firmwarekit from). Entries for the two files are replaced rather than
duplicated, which keeps a re-run idempotent.
"""
import hashlib
import json
import pathlib
import sys

LICENSE_NAME = "iBoot32Patcher-LICENSE"
NOTICE_NAME = "iBoot32Patcher-NOTICE.txt"
NOTICE_SOURCE = pathlib.Path(__file__).resolve().parent / "notices" / NOTICE_NAME


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__.strip().splitlines()[0] + "\nusage: stage-iboot32patcher-notice.py LICENSES_DIR IBOOT32PATCHER_LICENSE")
    licenses = pathlib.Path(sys.argv[1])
    license_source = pathlib.Path(sys.argv[2])
    if not licenses.is_dir():
        raise SystemExit(f"stage-iboot32patcher-notice: {licenses} is not a directory")
    if not license_source.is_file():
        raise SystemExit(f"stage-iboot32patcher-notice: missing licence file {license_source}")
    if not NOTICE_SOURCE.is_file():
        raise SystemExit(f"stage-iboot32patcher-notice: missing {NOTICE_SOURCE}")

    staged = {LICENSE_NAME: license_source.read_bytes(), NOTICE_NAME: NOTICE_SOURCE.read_bytes()}
    for name, data in staged.items():
        (licenses / name).write_bytes(data)

    manifest_path = licenses / "manifest.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise SystemExit(f"stage-iboot32patcher-notice: {manifest_path} is missing; the notice manifest must exist first")
    if manifest.get("schema") != 1 or not isinstance(manifest.get("files"), list):
        raise SystemExit(f"stage-iboot32patcher-notice: {manifest_path} is not the app's notice manifest")
    kept = [entry for entry in manifest["files"] if entry.get("path") not in staged]
    for name in sorted(staged):
        kept.append({"path": name, "sha256": hashlib.sha256(staged[name]).hexdigest()})
    manifest["files"] = kept
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"staged {', '.join(sorted(staged))} in {licenses}")


if __name__ == "__main__":
    main()
