#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/../../../.." && pwd)
binary=$(mktemp /tmp/mythic-reader-death.XXXXXX)
trap 'rm -f "$binary"' EXIT
cc -std=c11 -Wall -Wextra "$repo_root/app/src/test/native/reader_parent_death_test.c" -o "$binary"
"$binary"
