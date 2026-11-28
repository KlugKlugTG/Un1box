#!/usr/bin/env python3
"""Fetch, verify, and path-align a Clang IR PGO profile for the Android QEMU engine.

Clang names file-static functions using the compiler's source path relative to the
engine build directory (e.g. ``../../../../engine/accel/tcg/cpu-exec.c:func`` in
LightTouch release builds vs ``../../qemu-ios/accel/tcg/cpu-exec.c:func`` in
``android-perf-apk.yml``).  Without aligning that prefix, static functions in the
profile do not match the build and miss PGO optimization.

Usage:
  prepare-engine-pgo.py --url URL --sha256 HEX --output OUT.profdata \
      --llvm-profdata PATH --source-dir QEMU_IOS --build-dir ENGINE_BUILD
  prepare-engine-pgo.py --input FILE --output OUT.profdata \
      --llvm-profdata PATH --source-dir QEMU_IOS --build-dir ENGINE_BUILD
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.request

SOURCE_SUFFIXES = (".c", ".cc", ".cpp", ".cxx", ".h", ".hh", ".hpp", ".S", ".s", ".inc")
TOKEN_SPLIT_RE = re.compile(r"[:;]")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def compute_target_prefix(source_dir: Path, build_dir: Path) -> str:
    rel = os.path.relpath(source_dir.resolve(), build_dir.resolve()).replace(os.sep, "/")
    if rel == ".":
        return ""
    return rel if rel.endswith("/") else rel + "/"


def download_verified(url: str, expected_sha256: str, dest: Path) -> Path:
    expected = expected_sha256.lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise SystemExit(f"invalid expected sha256: {expected_sha256!r}")
    dest.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(4):
        part = dest.with_name(dest.name + ".part")
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "un1box-ci"})
            with urllib.request.urlopen(req, timeout=120) as response, part.open("wb") as out:
                while chunk := response.read(1 << 20):
                    out.write(chunk)
            part.replace(dest)
            break
        except OSError as error:
            if part.exists():
                part.unlink()
            print(f"{url}: {error}; retrying", file=sys.stderr)
            time.sleep(3 * (attempt + 1))
    else:
        raise SystemExit(f"could not download {url}")

    actual = sha256_file(dest)
    if actual != expected:
        dest.unlink()
        raise SystemExit(f"{url}: SHA-256 {actual}, expected {expected}")
    return dest


def safe_extract_tar_gz(archive: Path, dest: Path) -> None:
    dest_resolved = dest.resolve()
    dest.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive, "r:gz") as tar:
        for member in tar.getmembers():
            member_path = Path(member.name)
            if member_path.is_absolute() or ".." in member_path.parts:
                raise SystemExit(f"unsafe archive path in {archive}: {member.name!r}")
            if member.issym() or member.islnk() or member.isdev():
                raise SystemExit(f"unsupported archive member type in {archive}: {member.name!r}")
            target = (dest_resolved / member_path).resolve()
            if target != dest_resolved and dest_resolved not in target.parents:
                raise SystemExit(f"archive path escapes extraction root: {member.name!r}")
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                target.parent.mkdir(parents=True, exist_ok=True)
                extracted = tar.extractfile(member)
                if extracted is None:
                    raise SystemExit(f"could not read archive member: {member.name!r}")
                with extracted, target.open("wb") as out:
                    shutil.copyfileobj(extracted, out)


def find_profdata_in_archive(extract_dir: Path) -> Path:
    preferred = extract_dir / "builds/backend/pgo/engine-arm64.profdata"
    if preferred.is_file():
        return preferred
    candidates = sorted(p for p in extract_dir.rglob("*.profdata") if p.is_file())
    if not candidates:
        raise SystemExit(f"no .profdata file found inside extracted archive {extract_dir}")
    named = [p for p in candidates if p.name == "engine-arm64.profdata"]
    if len(named) == 1:
        return named[0]
    if len(candidates) == 1:
        return candidates[0]
    raise SystemExit(f"ambiguous .profdata files in archive: {', '.join(str(p) for p in candidates)}")


def is_gzip_archive(path: Path) -> bool:
    if path.name.endswith((".tar.gz", ".tgz")):
        return True
    with path.open("rb") as handle:
        return handle.read(2) == b"\x1f\x8b"


def detect_source_prefixes(text: str, source_dir: Path) -> tuple[set[str], set[str]]:
    """Return (non_empty_prefixes, matched_rel_files) present in LLVM text profdata."""
    prefixes: set[str] = set()
    matched_files: set[str] = set()
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", ":")):
            continue
        for token in line.split():
            # Split at ':' or ';' (Clang IR PGO static names look like path/file.c:func or path/file.c;func)
            parts = TOKEN_SPLIT_RE.split(token)
            for part in parts:
                if not part.endswith(SOURCE_SUFFIXES):
                    continue
                idx = part.find("/")
                while idx != -1:
                    prefix = part[: idx + 1]
                    rel = part[idx + 1 :]
                    rel_path = Path(rel) if rel else None
                    if (
                        rel_path is not None
                        and not rel_path.is_absolute()
                        and ".." not in rel_path.parts
                        and (source_dir / rel_path).is_file()
                    ):
                        prefixes.add(prefix)
                        matched_files.add(rel)
                        break
                    idx = part.find("/", idx + 1)
                else:
                    part_path = Path(part)
                    if (
                        not part_path.is_absolute()
                        and ".." not in part_path.parts
                        and (source_dir / part_path).is_file()
                    ):
                        matched_files.add(part)
    return prefixes, matched_files


def rewrite_profdata_text(text: str, source_prefixes: set[str], target_prefix: str) -> str:
    remap = sorted((p for p in source_prefixes if p != target_prefix), key=len, reverse=True)
    if not remap:
        return text
    pattern = re.compile(
        r"(^|[\s:;])(?:" + "|".join(re.escape(p) for p in remap) + r")"
    )
    out_lines = []
    for line in text.splitlines(keepends=True):
        stripped = line.lstrip()
        if stripped.startswith(("#", ":")):
            out_lines.append(line)
        else:
            out_lines.append(pattern.sub(lambda m: m.group(1) + target_prefix, line))
    return "".join(out_lines)


def inspect_profdata_matches(
    llvm_profdata: str, profdata: Path, source_dir: Path, target_prefix: str
) -> dict[str, int]:
    proc = subprocess.run(
        [llvm_profdata, "show", "--all-functions", str(profdata)],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=True,
    )
    total_functions = 0
    static_functions = 0
    matched_static_functions = 0
    matched_files: set[str] = set()
    in_counters = False
    for line in proc.stdout.splitlines():
        m_total = re.match(r"^Total functions:\s+(\d+)\s*$", line)
        if m_total:
            total_functions = int(m_total.group(1))
            continue
        if line == "Counters:":
            in_counters = True
            continue
        if in_counters:
            if line and not line.startswith(" "):
                in_counters = False
                continue
            stripped = line.strip()
            if line.startswith(" ") and stripped.endswith(":") and stripped != "Counters:":
                func_name = stripped[:-1]
                pieces = TOKEN_SPLIT_RE.split(func_name, maxsplit=1)
                if len(pieces) == 2 and pieces[0].endswith(SOURCE_SUFFIXES):
                    static_functions += 1
                    path_part = pieces[0]
                    rel = None
                    if target_prefix and path_part.startswith(target_prefix):
                        rel = path_part[len(target_prefix) :]
                    elif not target_prefix:
                        rel = path_part
                    if rel:
                        rel_path = Path(rel)
                        if (
                            not rel_path.is_absolute()
                            and ".." not in rel_path.parts
                            and (source_dir / rel_path).is_file()
                        ):
                            matched_static_functions += 1
                            matched_files.add(rel)
    return {
        "total_functions": total_functions,
        "static_functions": static_functions,
        "matched_static_functions": matched_static_functions,
        "matched_source_files": len(matched_files),
    }


def format_summary(meta: dict) -> str:
    return (
        f"enabled (input {meta['input_sha256'][:12]}, profile {meta['aligned_sha256'][:12]}, "
        f"{meta['matched_static_functions']}/{meta['static_functions']} static functions "
        f"across {meta['matched_source_files']} source files -> {meta['target_prefix'] or './'})"
    )


def prepare(args: argparse.Namespace) -> dict:
    source_dir = Path(args.source_dir).resolve()
    if not source_dir.is_dir():
        raise SystemExit(f"missing source directory: {source_dir}")
    build_dir = Path(args.build_dir).resolve()
    output = Path(args.output).resolve()
    metadata_path = Path(args.metadata).resolve() if args.metadata else output.with_suffix(".json")
    summary_path = Path(args.summary_file).resolve() if args.summary_file else None
    target_prefix = compute_target_prefix(source_dir, build_dir)
    expected_sha = args.sha256.lower() if args.sha256 else None
    local_input_path = None
    cache_key_sha = expected_sha
    if args.input:
        local_input_path = Path(args.input).resolve()
        if not local_input_path.is_file():
            raise SystemExit(f"missing PGO input: {local_input_path}")
        local_sha = sha256_file(local_input_path)
        if expected_sha and local_sha != expected_sha:
            raise SystemExit(f"{local_input_path}: SHA-256 {local_sha}, expected {expected_sha}")
        cache_key_sha = local_sha

    if metadata_path.is_file() and output.is_file() and cache_key_sha:
        try:
            cached = json.loads(metadata_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            cached = None
        if (
            isinstance(cached, dict)
            and cached.get("input_sha256") == cache_key_sha
            and cached.get("target_prefix") == target_prefix
            and cached.get("aligned_sha256") == sha256_file(output)
            and (args.allow_unmatched or cached.get("matched_source_files", 0) > 0)
        ):
            summary = format_summary(cached)
            if summary_path:
                summary_path.parent.mkdir(parents=True, exist_ok=True)
                summary_path.write_text(summary + "\n", encoding="utf-8")
            print(f"PGO cache hit: {summary}")
            return cached

    with tempfile.TemporaryDirectory(prefix="un1box-pgo-") as tmp:
        tmp_dir = Path(tmp)
        if args.url:
            if not expected_sha:
                raise SystemExit("--sha256 is required with --url")
            downloaded = download_verified(args.url, expected_sha, tmp_dir / "bundle")
            input_path = downloaded
            input_sha = expected_sha
        else:
            assert local_input_path is not None and cache_key_sha is not None
            input_path = local_input_path
            input_sha = cache_key_sha

        if is_gzip_archive(input_path):
            extract_dir = tmp_dir / "extract"
            safe_extract_tar_gz(input_path, extract_dir)
            raw_profdata = find_profdata_in_archive(extract_dir)
        else:
            raw_profdata = input_path

        raw_txt = tmp_dir / "raw.proftext"
        subprocess.run(
            [args.llvm_profdata, "merge", "--text", "-o", str(raw_txt), str(raw_profdata)],
            check=True,
        )
        raw_text = raw_txt.read_text(encoding="utf-8", errors="surrogateescape")
        detected_prefixes, matched_rel_files = detect_source_prefixes(raw_text, source_dir)
        if not matched_rel_files and not args.allow_unmatched:
            raise SystemExit(
                f"no static-function source paths in {raw_profdata} matched files under {source_dir}"
            )

        output.parent.mkdir(parents=True, exist_ok=True)
        staged_profdata = tmp_dir / "aligned.profdata"
        remapped_from = sorted(p for p in detected_prefixes if p != target_prefix)
        if remapped_from:
            aligned_txt = tmp_dir / "aligned.proftext"
            aligned_text = rewrite_profdata_text(raw_text, detected_prefixes, target_prefix)
            aligned_txt.write_text(aligned_text, encoding="utf-8", errors="surrogateescape")
            subprocess.run(
                [args.llvm_profdata, "merge", "-o", str(staged_profdata), str(aligned_txt)],
                check=True,
            )
        else:
            shutil.copy2(raw_profdata, staged_profdata)

        stats = inspect_profdata_matches(args.llvm_profdata, staged_profdata, source_dir, target_prefix)
        if not args.allow_unmatched and stats["matched_static_functions"] == 0:
            raise SystemExit(
                f"aligned profile {staged_profdata} matched 0 static functions under {source_dir} "
                f"(target prefix {target_prefix!r})"
            )

        tmp_out = output.with_name(output.name + ".tmp")
        shutil.copy2(staged_profdata, tmp_out)
        tmp_out.replace(output)

    aligned_sha = sha256_file(output)
    meta = {
        "schema": 1,
        "input_sha256": input_sha,
        "aligned_sha256": aligned_sha,
        "target_prefix": target_prefix,
        "remapped_from": remapped_from,
        **stats,
    }
    metadata_path.parent.mkdir(parents=True, exist_ok=True)
    tmp_meta = metadata_path.with_name(metadata_path.name + ".tmp")
    tmp_meta.write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    tmp_meta.replace(metadata_path)
    summary = format_summary(meta)
    if summary_path:
        summary_path.parent.mkdir(parents=True, exist_ok=True)
        summary_path.write_text(summary + "\n", encoding="utf-8")
    print(f"Prepared PGO profile: {summary}")
    return meta


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    src = parser.add_mutually_exclusive_group(required=True)
    src.add_argument("--url", help="URL of a .tar.gz or .profdata PGO profile")
    src.add_argument("--input", help="Local .tar.gz or .profdata PGO profile")
    parser.add_argument("--sha256", help="Expected SHA-256 digest of the downloaded or local input")
    parser.add_argument("--output", required=True, help="Output aligned .profdata path")
    parser.add_argument("--metadata", help="Optional metadata JSON path (defaults to <output>.json)")
    parser.add_argument("--summary-file", help="Optional one-line summary output path")
    parser.add_argument("--llvm-profdata", required=True, help="Path to llvm-profdata binary")
    parser.add_argument("--source-dir", required=True, help="QEMU engine source directory")
    parser.add_argument("--build-dir", required=True, help="QEMU engine build directory")
    parser.add_argument(
        "--allow-unmatched",
        action="store_true",
        help="Do not fail if zero static functions match files in --source-dir",
    )
    return parser.parse_args(argv)


if __name__ == "__main__":
    prepare(parse_args())
