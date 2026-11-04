#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Protect original S10e mappings and generator evidence from accidental rewrites."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
expected = json.loads((root / 'docs/architecture/fixture-sha256.json').read_text())
bad = [name for name, digest in expected.items()
       if not (root / name).is_file()
       or hashlib.sha256((root / name).read_bytes()).hexdigest() != digest]
for name in bad:
    print(f'Preserved fixture changed or missing: {name}')
print(f'Original S10e fixture hashes: {len(expected) - len(bad)}/{len(expected)} match')
raise SystemExit(bool(bad))
