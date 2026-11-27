#!/usr/bin/env python3
"""Check the workflow pins and, with --engine, apply the stack to the pinned tree.

The supplied engine checkout must contain the pinned commit; its worktree is not changed.
"""
import argparse
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PIN = 'e6ab90f02c7fa4d4a81696da7d1204be5c259bac'
PATCHES = sorted((ROOT / 'ci/engine-patches').glob('*.patch'))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--engine', type=Path)
    args = parser.parse_args()
    workflows = ROOT / '.github/workflows'
    for name, count in [('android-perf-apk.yml', 3), ('guest-package.yml', 2),
                        ('jailbreak-apk.yml', 1)]:
        text = (workflows / name).read_text()
        assert text.count(PIN) == count, f'{name}: inconsistent engine pin'
        assert not re.search(r"(?:default: perf\b|\|\| 'perf')", text), name
    assert '+serial 49\n' in PATCHES[0].read_text()
    print('workflow defaults and guest serial agree')
    if args.engine:
        with tempfile.TemporaryDirectory() as temp:
            archive = subprocess.Popen(['git', '-C', str(args.engine), 'archive', PIN],
                                       stdout=subprocess.PIPE)
            unpack = subprocess.run(['tar', '-x', '-C', temp], stdin=archive.stdout)
            archive.stdout.close()
            if archive.wait() or unpack.returncode:
                raise SystemExit('could not archive pinned engine commit')
            subprocess.run(['git', '-C', temp, 'apply', *map(str, PATCHES)], check=True)
            version = (Path(temp) / 'contrib/guest-package/VERSION').read_text()
            assert re.search(r'^serial 49$', version, re.MULTILINE), version
            print(f'{len(PATCHES)} patches apply to pinned engine; guest serial is 49')


if __name__ == '__main__':
    main()
