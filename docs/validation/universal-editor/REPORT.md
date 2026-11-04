# Issue #15 — universal editor validation

Development branch: `feature/15-universal-mapping-editor`, directly based on
consolidated `dev` 54b18e1. This is an incremental draft, not release acceptance.
Physical S10e/G8/MLBB were not used or modified. Issue #20 remains deferred.

## Executed first checkpoint

| Check | Observed result |
|---|---|
| Android full unit suite, initial editor build | PASS: 339 tests, zero failures (330 existing + 9 initial universal tests) |
| Debug APK build/install | PASS, owned API36 x86_64 AVD `MythicMapper_API36_G8`, explicitly selected emulator-5580 |
| Actual editor UI creation | PASS: accessibility entered six target definitions, selected Tap/Hold and LT right-stick aim, confirmed synthetic HUD, enabled and saved |
| Save/reopen | PASS: same UUID/document and six persisted targets reopened in actual editor |
| Saved profile replay | PASS: shared ControllerDeviceMonitor/InputService recording path emitted at least 12 touch events; final pointers 0, platform submissions 0 |
| Disposable cleanup | PASS: isolated `ux_home_` library restored to its prior value |
| Native regressions | PASS: controller probe, mouse reader, reader parent-death |
| Historical fixture integrity | PASS: 12/12 SHA-256 fixtures unchanged |
| Rust | PASS: 5 tests |
| Host Rust JNI parity | PASS: 242406 vectors + ABI/invalid-input checks |
| Actual compiled Kotlin/JNI | PASS, including missing-library handling |
| Profile Python tests | PASS: 12 |
| Tool Python tests | PASS: 15 |
| Release APK assembly | BLOCKED: production signing keystore unavailable; signing policy unchanged |

The initial first-checkpoint run did not include subsequently added favorite,
undo/redo/recreation assertions or the four additional focused unit tests. These
must pass in the final run/hosted CI before review acceptance. Lint, release source
compilation, existing seven Virtual scenarios, Home/bubble UX and packaged JNI are
separate required gates; do not infer their result from the first checkpoint.

## Reproduce

```sh
python3 tools/virtual_g8.py boot --gpu swiftshader
python3 tools/virtual_g8.py build
python3 tools/virtual_g8.py install
python3 tools/virtual_g8.py ux universal
python3 tools/virtual_g8.py smoke
python3 tools/virtual_g8.py ux home
python3 tools/virtual_g8.py ux bubble
```

New UI suite runs against the real editor with an isolated preferences namespace,
uses arbitrary **synthetic** coordinates in the canonical reference plane and deletes
only profiles it created. It emits machine-readable saved-profile/replay/result JSON
and screenshots under app-private `files/universal-editor/`; the CLI copies evidence
into `app/build/virtual-g8/universal-*`.

## Remaining acceptance work

- Full real-AVD coverage of drag, clone starter, duplicate, remove, discard, import/
  export and rotation; model undo/gesture coverage is not a substitute.
- Dedicated disk-failure injection for universal rename across metadata/mapping
  stores; existing write rollback is reused, but two-file power-loss atomicity is
  not claimed.
- Physical root-service, calibrated HUD and per-hero gameplay acceptance.
- Performance profiling under comparable input; no FPS/latency improvement claimed.
- Optional synthetic left-stick joystick and richer gesture semantics remain
  unsupported; native movement is retained. Existing hero compatibility adapters
  remain necessary for their accepted special behavior.
- Issue #17 folder synchronization remains future work; manual SAF JSON export and
  validated clone import are provided.

All artwork is Compose/native; GPLv3 attribution and inherited source notices remain.
No new third-party dependency or copyrighted hero/vendor image was added.
