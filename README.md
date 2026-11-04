# MythicMapper

An open-source, root-first MLBB controller mapper for the **original GameSir Galileo G8
USB-C**, initially targeting **Samsung Galaxy S10e SM-G970F / rooted LineageOS 23.2**.
Based on [XtMapper](https://github.com/rickallauigan/XtMapper), preserving its Git history,
working controller runtime, multitouch backend, editor and calibration tools.

**Development preview, not gameplay-verified.** The original repository is untouched.
Work is in stacked draft PRs; `dev` retains the XtMapper foundation until human review.
No production release or automatic merge has been performed.

## Implemented in the development branch

- Compose home with controller status, personal mappings and searchable offline starter catalog.
- Five persistent, configurable favorite slots referencing stable UUID profile identities.
- Movable floating selector, profile switching through the existing runtime cleanup gate,
  and access to the existing visual controller editor and coordinate capture.
- Reused attack, three-skill/right-stick aiming, camera and multitouch handling. Left-stick
  movement remains native to MLBB. No replacement joystick emulation was added.
- A separately identified **unverified** starter adapted from historical tuned v0.3;
  cloning never changes bundled assets or personal edits. Original S10e fixtures are protected.
- Root MotionEvent injection behind a replaceable touch interface, rejection diagnostics
  and fail-closed input handling with cleanup attempts. No SELinux/performance-setting changes.
- Rust pure reference math, primitive JNI bridge and four-ABI packaging. Java mapping stays
  active until device benchmarks and parity acceptance justify a migration.
- Controller event/status diagnostics, bounded software timing percentiles, receipt jitter,
  SYN_DROPPED and injection-error counters. Software timing is not controller-to-display latency.

The current allowlist accepts only the historically observed G8 USB mode with exact name
`Sony Interactive Entertainment GameSir-G8` and VID/PID `054c:0ce6`; native probing also checks
USB bus and gamepad capabilities. Other G8 modes require evidence before adding support.
No other games/controllers, macros, automatic targeting, memory modification, anti-cheat
bypass, cloud accounts or analytics are part of the MythicMapper MVP.

## Build and validation

Use JDK 21, Android SDK API 37, NDK `28.0.13004108`, and Rust via rustup. At repository root:

```sh
rustup show
cargo test --locked --offline --manifest-path engine/Cargo.toml
bash tools/run-rust-jni-host.sh
bash app/src/test/native/run-controller-probe-tests.sh
bash app/src/test/native/run-mouse-reader-tests.sh
python3 tools/check_mythic_fixtures.py
bash ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Initial Gradle/rustup setup needs network access; the Rust crate has no third-party crates.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, application ID
`io.github.rickallauigan.mythicmapper.debug`. Java namespace remains `xtr.keymapper` for
compatibility. **Never run two injecting mappers.** No device installation was performed
by this development session. Back up profiles before any later controlled device test.

## Evidence and remaining work

[Source audit and component decisions](docs/architecture/AUDIT.md) ·
[Exact baseline file inventory](docs/architecture/source-inventory.json) ·
[Architecture decisions](docs/architecture/DECISIONS.md) ·
[Benchmarks](docs/architecture/BENCHMARKS.md) ·
[Rust/JNI evidence](docs/architecture/RUST.md) ·
[Profile persistence](docs/architecture/HERO-LIBRARY.md) ·
[Current checkpoint and limitations](docs/architecture/CHECKPOINT.md) ·
[Historical branding preparation](docs/branding-preparation.md).

Physical S10e/root/G8 testing, new hero-specific verification, device p99/CPU/allocation
measurements, overlay interruption/reconnection acceptance and full dependency/asset notice
clearance remain release gates. The starter's removal of historical LT repeating behavior
also requires gameplay acceptance. Automated tests never certify MLBB compatibility.

## License and attribution

GPLv3, retaining **Copyright (C) 2022 Xtr126** and all applicable XtMapper/contributor
notices and history. New contributions are GPL-3.0-or-later. scrcpy-derived components retain
their applicable upstream terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md),
[license inventory](docs/architecture/LICENSING.md), and [LICENSE](LICENSE).
The original XtMapper README is preserved at [docs/XTMAPPER-README.md](docs/XTMAPPER-README.md).
