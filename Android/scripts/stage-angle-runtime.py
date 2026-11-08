#!/usr/bin/env python3
"""Validate/stage closed ABI-specific debug providers and checked offline notices."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
from urllib.parse import urlsplit

NAMES = {'libEGL_lighttouch_angle.so', 'libGLESv1_CM_lighttouch_angle.so', 'libGLESv2_lighttouch_angle.so'}
ABIS = {'arm64-v8a': 183, 'x86_64': 62}

def asset_paths(abi):
    if abi not in ABIS:
        raise ValueError('Unsupported fixed provider ABI')
    return ('angle' if abi == 'x86_64' else 'angle-arm64-v8a',
            'angle-runtime.json' if abi == 'x86_64' else 'angle-runtime-arm64-v8a.json')


# libandroid.so is the NDK's platform library: the ARM64 libGLESv2 links it for
# the AssetManager its shader cache loads through, and it is part of Bionic's
# public API surface, not a second provider.
PLATFORM = {'libc.so', 'libdl.so', 'libm.so', 'liblog.so', 'libnativewindow.so', 'libandroid.so'}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checked_file(root, name):
    current = root
    for part in name.split('/'):
        if part in ('', '.', '..') or not re.fullmatch(r'[A-Za-z0-9._-]+', part):
            raise ValueError('Invalid provider notice path')
        current /= part
        if current.is_symlink():
            raise ValueError('Provider bundle symlinks are not accepted')
    if not current.is_file():
        raise ValueError(f'Missing provider input: {name}')
    return current


def inspect_library(path, readelf, abi='x86_64'):
    asset_paths(abi)
    data = path.read_bytes()
    if (len(data) < 64 or data[:7] != b'\x7fELF\x02\x01\x01'
            or struct.unpack_from('<HH', data, 16) != (3, ABIS[abi])
            or struct.unpack_from('<I', data, 20)[0] != 1
            or struct.unpack_from('<H', data, 52)[0] != 64):
        raise ValueError(f'Provider must be an ELF64 little-endian {abi} shared object')
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    if size != 56 or not 1 <= count <= 4096 or offset < 64 or offset + size * count > len(data):
        raise ValueError('Malformed provider ELF program headers')
    alignments = []
    for index in range(count):
        start = offset + index * size
        if struct.unpack_from('<I', data, start)[0] == 1:
            file_offset, virtual = struct.unpack_from('<QQ', data, start + 8)
            file_size, memory_size, alignment = struct.unpack_from('<QQQ', data, start + 32)
            if (alignment < 16384 or alignment & (alignment - 1)
                    or (virtual - file_offset) % alignment or file_offset + file_size > len(data)
                    or memory_size < file_size):
                raise ValueError(f'Provider ELF is not valid/16KiB aligned: {path.name}')
            alignments.append(alignment)
    if not alignments:
        raise ValueError('Provider has no load segments')
    dynamic = subprocess.check_output([str(readelf), '--dynamic', '--wide', str(path)], text=True)
    sonames = re.findall(r'\(SONAME\).*\[([^\]]+)\]', dynamic)
    needed = re.findall(r'\(NEEDED\).*\[([^\]]+)\]', dynamic)
    if sonames != [path.name] or not set(needed) <= NAMES | PLATFORM:
        raise ValueError(f'Unexpected provider SONAME/dependency closure: {path.name}')
    return {'sha256': sha(path), 'soname': path.name, 'needed': needed, 'load_alignments': alignments}


def validate_bundle(abi, libraries, manifest_path, notices, readelf, manifest_sha, notices_sha):
    asset_paths(abi)
    if manifest_path.is_symlink() or not manifest_path.is_file():
        raise ValueError('Invalid provider closure manifest')
    if not re.fullmatch(r'[0-9a-f]{64}', manifest_sha) or sha(manifest_path) != manifest_sha:
        raise ValueError('Provider closure manifest pin changed')
    closure = json.loads(manifest_path.read_text(encoding='utf-8'))
    if closure.get('abi', 'x86_64') != abi or set(closure['objects']) != NAMES:
        raise ValueError('Provider closure must contain exactly the three fixed libraries')
    observed = {}
    for name in sorted(NAMES):
        path = checked_file(libraries, name)
        result = inspect_library(path, readelf, abi)
        expected = closure['objects'][name]
        if result != {key: expected[key] for key in result}:
            raise ValueError(f'Provider closure identity changed: {name}')
        observed[name] = result
    notice_path = checked_file(notices, 'manifest.json')
    if not re.fullmatch(r'[0-9a-f]{64}', notices_sha) or sha(notice_path) != notices_sha:
        raise ValueError('Provider notice manifest pin changed')
    notice_manifest = json.loads(notice_path.read_text(encoding='utf-8'))
    if notice_manifest.get('abi', abi) != abi or notice_manifest['schema'] != 1 or notice_manifest['libraries'] != {name: value['sha256'] for name, value in observed.items()}:
        raise ValueError('Provider notices do not match the staged runtime')
    reference = urlsplit(notice_manifest['source_reference'])
    if reference.scheme != 'https' or not reference.hostname or reference.username or reference.password or not re.fullmatch(r'[0-9a-f]{40}', notice_manifest['source_revision']):
        raise ValueError('Invalid provider source reference/revision')
    files = [('manifest.json', notice_path)]
    names = {'manifest.json'}
    for row in notice_manifest['files']:
        path = checked_file(notices, row['path'])
        if row['path'] in names or not re.fullmatch(r'[0-9a-f]{64}', row['sha256']) or sha(path) != row['sha256']:
            raise ValueError('Provider notice integrity failure')
        names.add(row['path']); files.append((row['path'], path))
    group, metadata = asset_paths(abi)
    return {'abi': abi, 'libraries_root': libraries, 'observed': observed,
            'notice_files': files, 'notice_manifest': notice_manifest,
            'closure_manifest_sha256': manifest_sha, 'notice_manifest_sha256': notices_sha,
            'group': group, 'metadata': metadata}


def stage_bundles(destination, bundles, readelf, validate_only=False):
    seen = set()
    validated = []
    for abi, libraries, manifest, notices, manifest_sha, notices_sha in bundles:
        if abi in seen:
            raise ValueError('Duplicate provider ABI')
        seen.add(abi)
        libraries, manifest, notices = map(Path, (libraries, manifest, notices))
        # Generated cleanup must never remove an authenticated input tree.
        output = destination.resolve()
        for path in (libraries, manifest, notices):
            source = path.resolve(strict=True)
            if source == output or output in source.parents or source in output.parents:
                raise ValueError('Provider inputs and generated destination overlap')
        validated.append(validate_bundle(abi, libraries, manifest, notices, readelf, manifest_sha, notices_sha))
    # Every selected ABI/native/notice check precedes ANY generated mutation.
    if validate_only:
        return validated
    clear(destination)
    for bundle in validated:
        abi = bundle['abi']
        for name in sorted(NAMES):
            output = destination / 'jniLibs' / abi / name
            output.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(bundle['libraries_root'] / name, output)
            if sha(output) != bundle['observed'][name]['sha256']:
                raise ValueError('Provider input changed while copying')
        for name, path in bundle['notice_files']:
            output = destination / 'assets/licenses' / bundle['group'] / name
            output.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, output)
            expected = bundle['notice_manifest_sha256'] if name == 'manifest.json' else next(
                row['sha256'] for row in bundle['notice_manifest']['files'] if row['path'] == name)
            if sha(output) != expected:
                raise ValueError('Provider notice changed while copying')
        receipt = {'schema': 1, 'abi': abi, 'libraries': bundle['observed'],
                   'notice_group': bundle['group'],
                   'closure_manifest_sha256': bundle['closure_manifest_sha256'],
                   'notice_manifest_sha256': bundle['notice_manifest_sha256'],
                   'source_reference': bundle['notice_manifest']['source_reference'],
                   'source_revision': bundle['notice_manifest']['source_revision'],
                   'license_compatibility': bundle['notice_manifest'].get('license_compatibility', 'unresolved')}
        output = destination / 'assets/providers' / bundle['metadata']
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(receipt, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return validated


def stage(destination, libraries, manifest_path, notices, readelf, manifest_sha, notices_sha, abi='x86_64'):
    # Existing Python callers and legacy CLI inputs retain their x86 default.
    return stage_bundles(destination, [(abi, libraries, manifest_path, notices, manifest_sha, notices_sha)], readelf)


def clear(destination):
    if destination.is_symlink():
        raise ValueError('Unexpected generated provider destination')
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('destination', type=Path)
    parser.add_argument('--abi', choices=ABIS)
    parser.add_argument('--bundle', nargs=6, action='append', default=[],
                        metavar=('ABI', 'LIBRARIES', 'MANIFEST', 'NOTICES', 'MANIFEST_SHA', 'NOTICES_SHA'))
    parser.add_argument('--validate-only', action='store_true')
    parser.add_argument('--libraries', type=Path)
    parser.add_argument('--manifest', type=Path)
    parser.add_argument('--notices', type=Path)
    parser.add_argument('--readelf', type=Path)
    parser.add_argument('--manifest-sha256')
    parser.add_argument('--notices-sha256')
    args = parser.parse_args()
    selected = [args.libraries, args.manifest, args.notices, args.manifest_sha256, args.notices_sha256]
    bundles = list(args.bundle)
    if args.abi is not None and not any(selected):
        parser.error('An ABI selector requires all legacy provider inputs')
    if any(selected):
        if not all(selected):
            parser.error('Provide all fixed runtime, closure, notice inputs and pins')
        bundles.append((args.abi or 'x86_64', *selected))
    if bundles and not args.readelf:
        parser.error('Provider validation requires readelf')
    if not bundles and args.readelf:
        parser.error('readelf supplied without a fixed provider bundle')
    stage_bundles(args.destination, bundles, args.readelf, args.validate_only)
    print('Validated closed ABI provider inputs' if args.validate_only else
          'Staged fixed ABI ANGLE runtime/notices; app-UID/guest acceptance remains separate')
