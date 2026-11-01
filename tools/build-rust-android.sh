#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
cd "$(dirname "$0")/../engine"
for target in aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android; do
    cargo build --locked --offline --release --target "$target"
done
