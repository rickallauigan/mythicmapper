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

- Full real-AVD coverage of clone starter, duplicate, remove, discard, import/
  export; model-only coverage is not a substitute.
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

## Expanded local regression checkpoint

- Full Android suite: **344 tests, zero failures** (330 existing + 14 universal).
  Added short/rapid taps, Recall-like neutral rearm, gesture undo/saved-state and
  stale/wrong/externally-renamed ownership checks.
- Debug build, release Kotlin/Java compilation, lintDebug and dependency inventory:
  **PASS** after fixing a restricted ComponentActivity override by reusing Tester's
  Window.Callback pattern, and guarding API34 accessibility cache access.
- All seven existing Virtual G8 scenarios: **PASS**, every final pointer count zero.
- AVD smoke and packaged x86_64 JNI: **PASS**, 61206 parity vectors, max error zero.
- Home and bubble AVD accessibility suites: **PASS**, including favorites,
  editor/Tester/Virtual navigation, overlay ownership, rotation and teardown.
- OSV dependency inventory: 104 Maven components queried, zero advisory records;
  no dependency changed in this feature.
- An extended editor run confirmed UI favorite assignment but exposed harness
  scrolling/dialog-ID problems before undo/recreation assertions could run.
  Bidirectional scrolling and dialog accessibility identifiers were corrected.
  Do not count the extended run as passed until its fresh report says PASS.

Evidence: [existing AVD regressions](evidence/existing-avd-regressions.json).
The production release-signing keystore remains unavailable; compilation passed,
while signed release assembly remains blocked. No signing workaround was introduced.

## Extended editor acceptance — observed PASS

The fresh `ui-result.json` records real AVD accessibility operations through Home's
Create action, six targets / seven logical controls (right stick modifies LT aim),
favorite assignment, save/reopen, marker drag with one undo, undo/redo, unsaved draft
Activity recreation and revert. Replay uses the saved custom document, with no new
hero class. It recorded **19 touch events**, **2 maximum simultaneous pointers**,
**0 final pointers**, **0 platform touch submissions**, and one blocked input while
diagnostic ownership was active. Disconnect released held contacts. Original mapping
and library snapshots matched after cleanup.

- [UI result](evidence/ui-result.json)
- [Synthetic profile, not gameplay coordinates](evidence/synthetic-profile.json)
- [Observed controller-to-touch replay](evidence/replay.json)
- [Saved editor screenshot](evidence/editor-saved.png)
- [Canvas screenshot](evidence/editor-canvas.png)

The direct drag exposed a test synchronization race during animated scrolling.
The harness now waits for accessibility/layout stability and checks the resulting
numeric coordinates, retaining the single-undo and saved-state assertions. Landscape
canvas height is bounded to 240 dp; transforms still use the explicit reference plane.
Initial AVD boot encountered the known SystemUI startup ANR, acknowledged once with
the owned-emulator recovery command. No application ANR was bypassed.

Hosted host validation passed on 939cc6c:
[run 41930967616](https://github.com/rickallauigan/mythicmapper/actions/runs/41930967616).
Later canvas/harness changes require fresh hosted checks; inspect PR25's current SHA.
This checkpoint does not imply the complete issue acceptance matrix has passed.

## Final local harness checkpoint: f52944c

Fresh `python3 tools/virtual_g8.py ux universal` **PASS** with all assertions retained:
verified text entry, exact drag coordinates, one-step drag undo, Home entry,
favorite assignment, save/reopen, redo/recreation/revert, overlap, disconnect and
diagnostic rejection. The final fix waits for dialog accessibility publication,
uses real scroll gestures in the padded margin, reacquires stale nodes and checks
entered values. No production input restriction or test assertion was removed.

Current [build identity and APK SHA-256](evidence/build.json) and screenshots are from
the actual installed AVD APK. Fresh hosted checks on f52944c were requested:
[host](https://github.com/rickallauigan/mythicmapper/actions/runs/41934439943),
[AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41934439911).
Consult those results and PR25's current head; earlier successful host runs do not
make a later AVD failure green. Report/evidence-only commits may follow this tested
code checkpoint.

### Continuation checkpoint

1. Finish the remaining user-operation AVD matrix: unmeasured starter cloning,
   duplicate/remove/discard, JSON picker import/export and larger-font
   editor checks. Existing Home tests cover shared legacy library operations;
   do not count those as complete universal editor coverage.
2. Add failure-injected universal persistence/rename recovery tests and explicit
   old-format fixture assertions. No automatic profile migration is performed.
3. Profile editor/runtime allocations with a controlled workload before optimizing.
4. Only then arrange optional S10e backup, non-destructive upgrade and disposable
   Hero Training calibration/acceptance. Do not modify Bennett or Kimmy to do so.
5. Keep PR25 draft, #15 open and #20 deferred. Issue #17 remains folder-workflow
   follow-up; no folder synchronization is claimed here.

## Final presentation follow-up: 6c9a613

The complete local editor suite passed again after the final header fix, including
explicit portrait → landscape Activity recreation and unchanged saved geometry/data.
The portrait screenshot exposed a narrow fragmented Close label; the title now takes
flexible width and Close has an 80 dp minimum width / 48 dp minimum height and an
accessible “Close editor” description. Build and lint passed; release source checks
were rerun. All six synthetic targets are visible in the committed portrait capture.

Both hosted workflows on f52944c passed before this presentation follow-up (see
[evidence](evidence/hosted-checkpoint.json)). Fresh 6c9a613 workflows are separate:
[host](https://github.com/rickallauigan/mythicmapper/actions/runs/41936089115),
[AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41936089122).
The PR remains draft; check its current SHA for later evidence-only commits.
