# MythicMapper development checkpoint

Repository: https://github.com/rickallauigan/mythicmapper (public).
Original source: XtMapper dev 603b11dd9f919fd447dee25a2640dad03932af97; original worktree/remote
not modified, deleted or force-pushed. The destination dev branch preserves that ancestry.
All implementation is in stacked draft PRs, not merged into dev. No production release.

## Published sequence

1. foundation/source-audit — PR #2: inventory, traced architecture, reuse matrix, licenses, ADRs, backlog and baseline.
2. engine/rust-reference — PR #8, based on #2: Rust math/reference tests and four-ABI JNI packaging.
3. profiles/offline-hero-library — PR #9, based on #8: immutable starter and UUID-based five-slot metadata.
4. runtime/root-scope-diagnostics — PR #10, based on #9: G8/game scope, separate app/Binder identity,
   touch interface, submission failure handling, metrics and CI.
5. ui/mythicmapper-home — PR #11, based on #10: Compose home, favorite/personal profile controls, floating
   selector, visual/calibration routes, diagnostics, original artwork and lifecycle tests.

## Implemented and preserved

Reuses Java/native controller capture, canonical buttons/hats/digital triggers, native MLBB
left-stick movement, skill/camera/chord logic, pointer ledger/cleanup, RuntimeRequestGate,
foreground resolver, visual editor, target capture, profile parser/structured persistence and
existing regression suites. Rust remains a pure reference library and explicit JNI interface;
no active-event hot-path migration. No uinput touchscreen replacement.

Home supports name search, clone, rename, duplicate, restore starter, delete, five-slot
assignment/replacement/clearing/reordering, connection status and selected profile. Floating
selector is movable/collapsible, hides during editor leases, uses a bounded scrolling panel,
reclamps after display changes and routes switching through the existing foreground/runtime
barriers. The selected favorite survives leaving/returning to MLBB within the service session.
Screen-off pauses mapping and closes the selector. Visual editor/calibration use the existing
pause/release lease; stable UUIDs stay storage keys while the editor displays a friendly name.

Diagnostics shows Android device identity, focused Android button/axis events, root-side
controller/button/axis state, logical touches, errors, software p50/p95/p99, last interval
jitter and SYN_DROPPED count. JSON snapshots are on-demand and local. No telemetry or accounts.

## Verified locally

- Original baseline: 238 Android tests and debug APK PASS.
- Combined development implementation: 254 Android tests; zero failures/errors/skips.
- Android lint, debug APK assembly, runtime dependency inventory PASS.
- Five Rust unit/property/replay tests and clippy -D warnings PASS.
- Original Java/C JNI/Rust parity across 242406 vectors PASS.
- Actual compiled Kotlin -> JNI -> Rust -> Kotlin on Linux host PASS; absent library fallback PASS.
- Native controller identity/capability and mouse-reader suites PASS.
- Python generator 12 tests, tools 4 tests, schema and all six generator checks PASS.
- All 12 protected S10e fixture file hashes match the baseline.
- Portrait Compose render inspected; scrolling/clone/search/disconnected controls and overlay
  permission/editor-lease teardown have host tests. Preview: previews/mythic-home.png.
- APK ZIP contains Rust JNI libraries for arm64-v8a, armeabi-v7a, x86, x86_64.
- Basic tracked-source secret guard and git whitespace checks PASS; not an exhaustive security audit.

Local build logs: /tmp/mythicmapper-baseline-gradle.log, /tmp/mythicmapper-scope-reload-gradle.log.
Rust cross-toolchain installed under /tmp only; no shell startup or system configuration changes.
ADB listed no devices; it started its ordinary local development daemon. No APK installation,
phone commands, mapper/profile changes on a device or physical gameplay validation occurred.

## Explicit limitations and next work

1. No connected S10e/G8. Root LineageOS 23.2 reflection/permissions, actual Kotlin JNI loading on
   Android, native movement with held attack/skills, disconnection, switching and overlay
   interruption/rotation need physical acceptance. Do not run two injecting mappers.
2. Only one UNVERIFIED generic starter adaptation is bundled, not a verified hero library.
   Historical tuned v0.3 is preserved verbatim as evidence; the new starter removes repeating
   LT attack and has NOT been gameplay validated. Capture actual hero/HUD data before new presets.
3. Native movement remains native; a separate configurable synthetic movement joystick is not
   implemented. Existing advanced editor can still offer legacy mappings that the MythicMapper
   runtime rejects; narrow those controls in a follow-up after preserving editor regressions.
4. Foreground detection retains XtMapper's five-second polling cadence. Leaving-game pause is
   not instantaneous and requires device validation/improvement before release. Preferred hero
   choice lives for the current service session; five favorite slots persist across restarts.
5. Injection failure cleanup is best effort, not proof of release after platform rejection or
   SIGKILL/native process death. Reader failure requires mapper-service restart. Runtime JNI
   usage/failure recovery on Android remains untested; host JNI success is not device evidence.
6. No target CPU/allocation measurements, software stage comparison or physical input/display
   latency numbers. Desktop math samples only validate the harness. No device performance
   budgets or speedup claim. Follow BENCHMARKS.md before activating more Rust mapping logic.
7. Runtime license inventory has 104 components, one unresolved direct-POM record:
   com.google.guava:listenablefuture:1.0. Exact scrcpy copied revision/notices and inherited raster
   asset provenance remain release gates. GPLv3 and existing notices/history are retained;
   a build/secret scan is not a complete licensing clearance. New artwork is original.
8. Metadata/mapping storage uses ordered checked writes, not a cross-file power-loss transaction;
   failures can leave unreferenced mappings. Legacy ZIP backups omit favorite metadata and
   captured-target library. Add explicit complete backup/recovery before release.

Resume on ui/mythicmapper-home. Read this checkpoint and inspect status/PR checks before edits.
Do not merge draft PRs or publish releases without a new explicit instruction.

Development APK: `app/build/outputs/apk/debug/app-debug.apk`
SHA-256: `db4a608fee0cfc4151daf9783ad8b0c99553ad22a358c7f4fee8238c1b2782a6`. Debug-signed; not installed or gameplay-certified.

Draft UI PR: https://github.com/rickallauigan/mythicmapper/pull/11.
Archived README trailing whitespace was normalized; original attribution/content and Git history remain preserved.

Follow-up review: scoped editor reloads reject unsupported definitions before installing them.
The local XtMapper remote has its push URL set to DISABLED as an additional guard.
The runtime parent branch was integrated with a normal local Git merge; no GitHub PR was merged.


## S10e validation sprint — 2026-11-02 (supersedes device-untested notes above)

Branch `test/s10e-hardware-validation` starts from merged dev `0bc0869`.
See `docs/validation/s10e/REPORT.md` and JSON evidence for actual device results.
256 Android tests, five Rust tests, native regression suites, host JNI and device
ARM64 JNI pass. Debug APK built/installed; no release/gameplay certification.
Production fixes: lazy scroll worker; owned getevent teardown/parent-death signal;
root task-stack subscription with polling fallback; immediate FGS notification.
Debug harness validates actual root multitouch only on its dedicated safe Activity.
Overlay rotation, favorites persistence/restoration, normal root lifecycle and idle
root-process death cleanup passed. Existing XtMapper preferences remain unchanged.
Both legacy packages disabled to prevent concurrent mapper startup; enable only
after stopping MythicMapper. No data clearing/uninstall.

Screen-off bounded-wait retest passed after normal user unlock; overlay detached,
screen woke and keyguard remained locked. The initial 300ms assertion was premature.
Remaining: complete physical G8 buttons/sticks/triggers, physical disconnect with
held gestures, and manual MLBB
training. G8 idle sleep/wake is observed, not physical USB unplug/replug. Physical
capture is partial; no gameplay claims. Rust stays reference-only. Licensing
evidence resolves Guava parent metadata and scrcpy reference match but inherited
raster provenance/final artifact-specific notices remain release blockers.

Resume with status/evidence inspection. Do not bypass keyguard. Do not merge PR or
publish production releases. Private raw evidence remains ignored under
`app/build/s10e-private/`; never commit it.

Draft S10e validation PR: https://github.com/rickallauigan/mythicmapper/pull/12.


## G8 Controller Tester — 2026-11-02

Branch `feature/g8-controller-tester` is stacked on PR #12's
`test/s10e-hardware-validation` at `f0132f9`; PR #12 remains unmerged.
See `docs/validation/g8-tester/REPORT.md` for actual screenshots, sanitized hardware
data, build identity, test results and remaining gates. The original supplied
reference PNG is intentionally untracked and must not be copied into the APK.

Implemented Compose Controller/Diagnostics tabs, original Canvas/SVG drawing,
Android live key/stick/trigger feedback, capability-derived checklist, bounded log,
sanitary export and root-enforced Binder diagnostic ownership. Existing Java/native
mapping, profile storage and Rust reference-only boundary remain. The previous
Runtime metrics UI remains linked from Home.

271 Android tests, five Rust tests, native three suites, host/Kotlin JNI and debug
build/lint/release-manifest boundary passed. Device ownership/start-resume rejection,
rotation, normal exit and idle client-death cleanup passed. Physical captures include
2,996 real Android events, full stick/trigger range, no held controls at end, zero
overflows and zero mapper touch submissions. User reported other buttons working;
retained telemetry does not independently certify every button. Rear L4/R4 report
L3/R3 aliases, not independent states. Four app preference files are unchanged.

Known: remaining debug rendering jank (separate synthetic replay 40.36% / p99 53ms),
full physical button capture, disconnect with held controls and OS-reserved Home
behavior. No gameplay validation, release budget or 60-FPS claim. Private raw logs
remain ignored in `app/build/s10e-private/`. Never merge/publish automatically.


## Controller tester acceptance follow-up — 2026-11-02

Continues on feature/g8-controller-tester / draft PR #13, still stacked on PR #12.
Device was rediscovered after a network change: read ignored
`app/build/s10e-private/current-adb-target.txt` rather than assuming an address.
Actual S10e model and G8 interfaces verified; tester foreground and phone awake.

New work adds persistent bounded guided down/up evidence, rear-alias prompts,
manual held-unplug recording, a timed physical benchmark with common instrumentation,
scoped Compose state/lazy sections and cheaper display-only decimal formatting.
Root mapping pipeline and profile schemas are unchanged. A real tester-first/Home
libsu initialization crash was reproduced; shell configuration now belongs in
Application before Activity entry (commit 3fef480). Final runtime retest is pending.

See docs/validation/g8-tester/acceptance/README.md. An earlier physical debug baseline
is retained; no matched optimization improvement is claimed. Fresh controlled
baseline `before-current-debug-1` was saved (711 frames; 72.86% jank; zero touch
submissions), but trigger/overlap evidence is incomplete. Optimized paired trial
`after-current-debug-1` is now armed, waiting for physical input. Do not replace that installed APK or interrupt the
Activity before collecting the result. Its exact APK/hash is preserved privately as
acceptance-common-debug.apk / acceptance-common-build.json. Use the same APK's
`render_baseline=false` path for the paired optimized trial; changing that flag
requires a new Activity, not merely onNewIntent. Root logical pointers must be zero
and mapping inactive before any lifecycle/process termination test.

Next: collect and JSON-validate files/validation/physical-after-current-debug-1.json;
check actual trigger/overlap evidence before claiming matching workloads. Keep all old evidence. Guided rear aliases
and held-control unplug/replug require physical actions, never automated USB disconnect.
Final debug APK rebuild/test/lint passed (282 Android tests); final benchmark build
also passed but has not run on the phone. No merges/releases. Reference PNG stays untracked.
