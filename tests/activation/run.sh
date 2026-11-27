#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
sh tools/activation/test.sh
if command -v cargo >/dev/null 2>&1; then
    cargo test -p lighttouch-firmware activation
else
    echo "cargo not installed; skipping Rust lighttouch-firmware activation tests"
fi
