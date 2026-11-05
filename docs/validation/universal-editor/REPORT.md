# Issue #15 — universal editor validation

Development branch: `feature/15-universal-mapping-editor`, directly based on
consolidated `dev` 54b18e1. This is an incremental draft, not release acceptance.
Physical S10e/G8/MLBB were not used or modified. Issue #20 remains deferred.

Latest production/UI checkpoint: **3f249ff**, including guarded malformed-profile
routing and retained-field synchronization; additive lifecycle fixture **197c5e1**. **348 Android tests passed**. Actual blank/create/save/reopen, CRUD,
DocumentsUI import/export and eight simulated scenarios passed (seven historical,
one universal lifecycle), with zero final pointers. See the final sections below
for current evidence; earlier sections retain their original checkpoint counts.
The physical acceptance and controlled performance gates are still pending.

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
python3 tools/virtual_g8.py ux universal-crud
python3 tools/virtual_g8.py ux universal-io
python3 tools/virtual_g8.py smoke
python3 tools/virtual_g8.py ux home
python3 tools/virtual_g8.py ux bubble
```

New UI suite runs against the real editor with an isolated preferences namespace,
uses arbitrary **synthetic** coordinates in the canonical reference plane and deletes
only profiles it created. It emits machine-readable saved-profile/replay/result JSON
and screenshots under app-private `files/universal-editor/`; the CLI copies evidence
into `app/build/virtual-g8/universal-*`.

## Remaining acceptance work at the initial checkpoint

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

### Historical continuation checkpoint (f52944c)

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

## Export-safety checkpoint: 3be26f9

All **345 Android tests** passed, including the new export regression. An invalid,
missing or future-schema snapshot cannot invoke the destination-opening callback;
a validated snapshot survives Bundle parceling. The Activity retains the pending
export snapshot across recreation. The JSON picker interaction itself remains a
separate UI acceptance case.

Debug build, lint, release source compilation and release manifest checks passed.
The actual compiled release class tree contains the production editor and no
Virtual G8 classes; no debug validation activities appear in the release manifest.
See [release/unit evidence](evidence/release-and-unit-checks.json).

The complete local editor suite passed again with the hardened export APK. Both
fresh hosted workflows passed on 3be26f9:
[host](https://github.com/rickallauigan/mythicmapper/actions/runs/41937425678),
[AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41937425695).
The evidence/build identity now refers to this checkpoint. The remaining interaction
and fault-injection matrix continues on the same feature branch and draft PR.

## Expanded user-operation and fault checkpoint: 8122510

The new code checkpoint also includes the fault-injection tests in 7e7f965.
Full Android unit tests: **348 passed, zero failures/errors/skips**, including 18
universal tests. Debug assembly, lint and release Kotlin/Java/manifest compilation
passed. Production signing remains unavailable; no release signing change was made.

| Acceptance case | Executed evidence |
|---|---|
| Actual AVD CRUD | PASS: unmeasured starter, new UUID on duplicate/clone, remove/undo/redo, rename/save, Home edit route, keep-editing/discard, delete cancel/confirm, favorite cleanup |
| Real Android document picker | PASS: JSON export across stopped-parent recreation, exact saved snapshot round-trip, import as a new disabled/uncalibrated UUID, source/favorites preserved |
| Invalid import | PASS: future schema99 rejected visibly; existing mapping/library unchanged |
| Editor safe preview | PASS: UI-triggered A DOWN/UP with ownership and recorded touch traces; STOP; no privileged backend |
| Disposable document cleanup | PASS: DocumentsContract removed the test-created export and its scoped URI grant was released |
| Failed writes | PASS: mapping commit and metadata rename failures restore prior mapping/favorite ownership; unrecoverable rollback is reported explicitly |
| Legacy data | PASS: native and structured-v1 profiles remain readable with no automatic conversion |
| Injection rejection | PASS in host simulation: actual InputService/monitor stop repeat after backend rejection; a fresh healthy runtime is required to recover |

The UI harness now waits for app focus and cancels only its own touchscreen gesture
after picker return/recreation or an interrupted test gesture. This addresses the
observed InputDispatcher invalid-DOWN failure without changing gameplay pointer
semantics or removing assertions. Import/save failures now use a dismissible dialog,
so an error cannot be hidden above the current editor scroll position.

The picker suite used the actual AOSP DocumentsUI, not a stub provider or direct
profile writes. It only mutated a UUID-named disposable export created by this
suite; original preferences and favorite snapshots matched after cleanup.

Fresh hosted validation was requested on 8122510:
[host](https://github.com/rickallauigan/mythicmapper/actions/runs/42016825826),
[AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/42016825852).
The earlier documentation-head AVD run
[42008751697](https://github.com/rickallauigan/mythicmapper/actions/runs/42008751697)
failed before app tests because its SystemUI startup ANR did not dismiss within the
bounded recovery window. This is retained as failed evidence; previous green runs
do not substitute for the new checks.

Remaining acceptance: larger-font and user-selected HUD-image interactions, detailed
editor allocation/recomposition profiling, and optional physical root/HUD/gameplay
validation. Two-store power-loss atomicity is not claimed. Native left-stick
pass-through and legacy hero compatibility adapters remain; issue20 is deferred.
The personal S10e installation, Bennett/Kimmy data and physical controller were not
accessed. PR25 stays draft and issue15 stays open for review.

### Final local replay and rendering observations

The expanded core editor and CRUD suites passed again on the installed APK whose
SHA-256 is in [build.json](evidence/build.json). Home, bubble, smoke, packaged JNI
and all seven original Virtual G8 scenarios also passed again.

The additional [universal lifecycle scenario](../../../tools/scenarios/universal-lifecycle.json)
passed 42 ordered logical steps using disposable version2 documents: screen-off,
locked/unlocked conditions, MLBB foreground loss/restoration, switching the active
custom document while held, SYN_DROPPED, diagnostic ownership and disconnect.
It recorded **14 touch events, six blocked inputs, zero final pointers and zero
platform gameplay submissions**. This exercises the actual universal resolver and
InputService recording backend; it does not reproduce Android keyguard broadcasts,
physical USB behavior or MLBB's response to cancellation. Issue20 stays deferred.
The seven historical scenarios remain unchanged; this is one additional scenario.

[Debug AVD observation](evidence/debug-avd-observation.json): a partial process-wide
sample during the core accessibility workflow reported 6,394 rendered frames,
35.71% janky, p50/p95/p99 21/57/73 ms, PSS 127,910 KB and RSS 260,820 KB.
These are **debug SwiftShader emulator observations**, not a matched before/after
benchmark, input-processing latency or physical S10e result. Instrumentation ends
the process, so the attempted post-suite gfxinfo/memory collection had no process
to inspect. No performance improvement is claimed. CPU, allocations and Compose
recomposition counts still need a dedicated controlled profiling run.

### Hosted picker synchronization follow-up: b23c963

The first expanded hosted run on 8122510 passed core editor/CRUD, but failed in
picker Close navigation after a successful import. The saved mapping and favorite
assertions had already passed; original-data and temporary-document cleanup also
passed. [Failing run](https://github.com/rickallauigan/mythicmapper/actions/runs/42016825852).
This failure is not counted as a hosted pass.

The harness now awaits the editor's actual focus after every picker result, or the
specifically recognized app-owned import error dialog. Activity focus alone is too
strict when that modal dialog is visible. Scroll coordinates are intersected with
the active app window; a rejected gesture reports its exact action/bounds instead
of an uninformative assertion. No input or data-preservation assertion was removed.

**Two consecutive real DocumentsUI runs passed** after the correction, including
normal return and modal rejection. Both deleted their own temporary export and
preserved original data. See [repeat evidence](evidence/picker-repeats.json).
This follow-up changes debug instrumentation only; production gameplay is unchanged.
Fresh current-head CI is required; consult PR25's checks rather than inferring green
from an earlier host run. Run `ux universal` and `ux universal-crud` with this bounded helper as separate
required checks. The committed screenshots/core trace are from the same production
UI/runtime at 8122510; APK identity includes the b23c963 debug harness follow-up.

### Saved-profile safety and retained-input follow-up: 3f249ff

Source review identified an unguarded parse in Home's Edit callback: a malformed
saved universal envelope could throw before reaching the editor. Home now uses the
format marker only for routing; the existing guarded editor loader performs full
validation and shows an error. The new AVD CRUD regression opens a disposable
schema99 profile through Home, verifies the error/Activity remain usable, verifies
its invalid bytes are preserved, then deletes only that test profile with the normal
confirmation flow. This test passed. Debug build/lint, release compilation and all
**348 unit tests** passed again with the production routing correction.

The subsequent hosted run
[42019907179](https://github.com/rickallauigan/mythicmapper/actions/runs/42019907179)
failed on a retained numeric-field assertion after a recovered SystemUI startup ANR.
Its captured accessibility tree contained the numeric field value, but the helper
only checked the value when ACTION_SET_TEXT acknowledged success. The helper now
independently verifies the retained value before/after the action, waits for the
selected binding before typing, and reports expected/actual values and the action
acknowledgement on failure. Exact-value requirements are retained. This is not a
claim that the failed run passed; current-head hosted validation is still required.

The complete core editor suite passed locally again on 3f249ff, including exact
field values, marker drag/single undo, save/reopen, both orientations, favorite
ownership and shared-runtime replay (19 touch events, max two pointers, final zero).
The current screenshots and APK hash have been refreshed to this checkpoint.
