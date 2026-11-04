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

Continue feature/g8-controller-tester / draft PR #13, stacked on unmerged PR #12.
Read the ignored app/build/s10e-private/current-adb-target.txt for the device's
current network target. Do not commit addresses, serials or raw private logs.
Historical Beatrix/Kimmy pause is superseded by the user-authorized gameplay feature below. Do not merge PRs or publish releases.

All four fresh physical reports are retained under docs/validation/g8-tester/acceptance:
before-current-debug-1 / after-current-debug-1 and repeat -2 JSON files. Both
original baselines are preserved. The exact comparison APK/hash is archived privately
as acceptance-common-debug.apk / acceptance-common-build.json. Reports describe the
pre-navigation-fix debug APK, not release performance or final-build display latency.

The repeat observed jank 70.39% -> 60.47%, frame p99 41.28 -> 47.62 ms, app CPU
54.45% -> 45.25% of one core. Workloads still differ (11259 vs 8627 events; trigger
pairs 24 each vs 13 each). Baseline ended with A+B held; later actual releases were
observed. Both root snapshots remained zero pointers/submissions, mapping inactive.
No stable p99 improvement or matched-workload performance acceptance is claimed.

CI run 41455233159 failed ActivityObserverLifecycleTest line 33. Its artifact and
a deterministic local thread-join repro show HandlerThread.getLooper() becoming
null after termination. e05532f keeps a live Handler/Looper before stopping, joins
and verifies termination/rejected posts/late refresh/idempotent stop. The enabled
282-test suite passed locally and CI run 41461355489 is green; no skipped tests.

The debug APK was installed with -r, preserving data. On-device JNI passed 61206
vectors with zero error. Root ownership/start-resume rejection, rotation, normal
exit and controlled zero-pointer process death passed. Cold Tester -> Home in the
same process passed without the previous libsu initialization crash. One existing
root-owned getevent reader runs during diagnostics; diagnosticOwner rejects mapping
frames. It stops on exit/death. App-only CPU excludes that root process/reader.

Final screenshots exposed a missing Diagnostics icon/label on Guided despite a
retained semantic target. Stable Compose keys for tab identities fix it on S10e;
regression navigation now exercises Guided -> Diagnostics -> Guided -> Controller.
Final build/test/lint passed after that delta (282 tests, no skips). The exact
rebuilt APK was installed and its JNI/ownership/rotation/teardown and zero-pointer
process-death checks passed again; four preference hashes remain unchanged. See
PR Checks for latest-head CI and acceptance reports for exact artifact hashes.

No profile/favorite reset, XtMapper data operation, gameplay touch injection,
firmware/security/thermal changes or online MLBB matches. All test artifacts are
scoped; the source reference PNG stays untracked. Remaining acceptance gates include
physical held-control unplug/replug, exhaustive supported-button evidence, reliable
matched/release-like performance, manual MLBB training and inherited license/assets.


## Beatrix / Kimmy gameplay feature — 2026-11-02

User explicitly authorized this feature after the tester checkpoint. Branch
feature/beatrix-kimmy-gameplay starts at complete validated PR #13 head 1ee6e51,
stacked over PR #12 f0132f9 and dev. See docs/gameplay/beatrix-kimmy/REPORT.md.
Draft PR #14 is open against feature/g8-controller-tester. Implementation 0ab8303;
guarded device harness 8b7b30e. Final local lint/build pass, remote CI pending.
No parent PR merged; no tester work, personal profile or favorite overwritten.
Supplied mockup PNG remains untracked.

Implemented shared controls, B/L3 ownership, Beatrix manual weapon/hold-or-pulse
policy and Kimmy stick shooting/skill priority/neutral arming. Reuses held aim,
RepeatKeyHandler, pointer ledger, native movement and diagnostic ownership. Rust
remains reference-only. Both templates disabled until explicit calibration; edits
revoke readiness. Recommended favorite slots require opt-in and cannot overwrite
occupied slots. Hero setup acquired its exclusive root lease and rendered on S10e.

294 Android tests passed; Rust 5, native 3, host JNI 242406 vectors, compiled Kotlin
JNI/missing-library and 12/12 fixture hash checks passed. Device heroes guard,
heroes_prepare, heroes_touch and jni suites passed; sanitized JSON is in the report
directory. Guarded blue-surface replay observed 43 events / 7 pointers, zero sequence
errors and zero final contacts. Tester ownership allowed zero gameplay submissions.
Two new disabled training drafts exist; original favorites unchanged. APK installed
with -r and private pre-feature APK/preferences/evidence backups retained. Current
ADB endpoint lives only in ignored app/build/s10e-private/current-adb-target.txt.
No physical Beatrix/Kimmy gameplay or hero-specific HUD calibration verified.

Next: manually enter Beatrix Hero Training with mapping stopped and report equipped
weapon. Inspect real HUD privately before enabling a calibrated copy. Then validate
physical LT/aim, native movement, skills and shared spell, followed by Kimmy. Keep
raw game screenshots/account data private. Never claim weapon behaviors verified
from host or safe-surface replay. No automatic matches, merges or releases.


### Bennett repeat safety follow-up

User confirms180 radius and LT repeat work but reports an adjacent skill actually
casting. Explicit Home Stop then no-mapper physical LT/stick exercise produced no
action. Source now adds anchor acquisition for Beatrix PULSE only: 40 ms anchor /
40 ms aimed / 70 ms released at150ms cadence. No claim of game compatibility yet.
Commit5e7999b adds timing/cancellation regression coverage. Commit28c605b stops the
bound/started mapper before normal Home hero-setup entry (prevents exit autoResume).
297 host Android tests, lint and debug assembly passed. Updated APK installed (-r),
SHA092669eff3dd92dc7cea8c5628793e030538bb6f8cd87346fb80bf8fddbd2d93.
Guarded real-device touch replay:43/44ms acquisition,43 events/7 max pointers,0 final
pointers/errors. Diagnostic submission delta0. Normal setup exit root/readerabsent.
Setup lease availability still transiently fails handoff; Save blocked safely. Do
not claim it fixed. Profiles/library/favorites backup comparison unchanged. Read
new bennett-acquisition-validation.json. Physical neighbor-cast retest pending.
Current actual device/training state is in ignored s10e-private/hero-continuation.json;
verify before resuming; no arbitrary game injection. No parent PR merge/release.


### Kimmy calibration and resumption follow-up

Source fix ee09d5d is committed locally on the existing PR #14 branch. Actual
Bennett personal profile is PULSE 400 ms / radius 180 px and is unchanged; preserve
its private working APK backup. Neighbor-cast regression stays open. Kimmy HUD
centers were measured and saved only to its personal copy, radius 80 initial trial.
Four basic controls are explicitly human-confirmed. Skill resumption then failed;
real trace requests Attack DOWN 0-2 ms after skill UP. Kimmy-only one-shot 40 ms
release gap plus 40 ms anchor acquisition added, preserving continuous shooting.
302 tests, lint/build and native/Rust/JNI/fixture checks pass. Guarded device replay
measured 42/43 ms and zero final contacts/errors. Current installed APK source is
 ee09d5d, SHA256 0fc739ae80ba2c676de47542c9e25d83e8736b0bea76fbf3a33c9548b9ba8771.
Kimmy is active again after the user unlocked normally. RT/LB physical resumption
retest is pending. Read kimmy-handoff-validation.json and the report. Current runtime
state is in ignored app/build/s10e-private/hero-continuation.json. No raw HUD images,
profile IDs or network information may be committed. Keep PR #14 draft; push updated
source/docs and check CI. No merges or production releases.

### Kimmy screen-off safety follow-up

User confirmed post-skill resumption, Recall neutral guard, Regen and B/L3. Physical
disconnect stopped firing, root trace zero; held-stick reconnect did not immediately
fire, exact neutral step not independently established. SCREEN OFF FAILED: game
kept firing after unlock although root logged UP/zero and root/reader exited. Manual
Attack touch stopped it; controller with mapping off did not shoot. Mapping stopped.
Uncommitted candidate uses Kimmy-only backend-owned ACTION_CANCEL at stop, preserving
Bennett UP behavior. 303 tests and lint/build pass, safe blue replay passes. Candidate
installed with identical profiles/library. Debug kimmy_screen_cleanup probe sleeps
only controlled blue surface, awaits manual unlock, requires same surface evidence.
Read current private continuation and instrumentation report before any activation.
Real Training screen-off retest remains mandatory; do not claim fixed from logical
zero. No global touch cancellation, automatic game recovery tap or keyguard bypass.

### Retain foreground service across lock/unlock

User now prioritizes keeping MM running rather than restarting via Use after unlock.
TouchPointer SCREEN_OFF no longer calls stopPointer/stopSelf. It invalidates queued
activation, pauses runtime, hides overlay and retains session/profile/foreground
service. Normal unlock re-registers existing root task observer for a fresh package;
keyguard/off-screen/editor/diagnostic gates remain. Overlay show is idempotent.
308 Android tests, lint, debug build and release-manifest pass. Actual S10e safe
screen suite verified service still started+foreground while locked/root paused;
heroes_touch and heroes passed. All profiles/favorites/settings compared unchanged.
Current installed APK hash is in lock-session-validation.json. Candidate 5e0f17a
DID NOT fix gameplay stuck firing; user reported same behavior, separate known bug.
Device is normally awake but keyguard may be locked after safe screen test; async
unlock request pending. Mapping is stopped. After manual unlock activate only
existing Kimmy copy; test lock with sticks CENTERED first, then unlock without Use
and check right-stick shooting/skills. Do not inject automated touches into MLBB.
Preserve Bennett400/180, open neighboring-skill regression, draft PR14 dependency.

### Unlock settling candidate

f1360fe user test FAILED after locking while firing: icon absent/controller dead,
game still firing. ADB proved MM foreground service/root/reader still alive, so
retention worked but session resume failed. Manual Attack recovery performed, then
fresh exclusive zero-contact acknowledgement and own-app stop verified root exit.
Added bounded keyguard settling, display callback fallback and three observer
refreshes over 2.5s (same observer/reader); ordinary callbacks still deduplicate.
Added standard service dump: screen_suspended, last_screen_action, session_running,
overlay_attached, root_alive, mapping_paused. 310 tests/lint/build pass. Actual new
screen_resume suite PASSED real manual unlock and fresh foreground observation,
remaining paused outside MLBB. Profiles/favorites/settings identical after tests.
Current APK hash: see unlock-settling-validation.json. Kimmy now ACTIVE again;
awaiting user centered-sticks lock/unlock test (no firing at lock) with M icon,
right-stick and RT checks without Use. If failed, first collect dumpsys activity
service PACKAGE/xtr.keymapper.TouchPointer while leaving game foreground; do not
restart instrumentation with possible active game contacts. Existing Bennett and
favorites untouched. Firing-during-lock cleanup remains a separate open failure.

User confirmed "yes it works" to centered-sticks lock/unlock, M return, right-stick
and RT without Use on installed source51209f0. Physical session restoration PASS.
ADB confirms screen_suspendedfalse/unlocked/sessionrunning/overlayattached/mapping
active after SCREEN_ON. Existing gameplay firing-during-lock bug remains OPEN.
Keep current Kimmy session; user asked to center after test. No more physical test
needed for the scoped restoration request. Draft14 updated; no merge/release.

### Issue #21: AVD + Virtual G8 development

Branch `feature/21-virtual-g8-avd`, draft PR22, base7efcf35 on
#14 → #13 → #12 → dev. Main/controller/hero source and profiles are unchanged.
No physical S10e commands were issued. Bennett180/400, Kimmy and screen-unlock
restoration remain preserved; gameplay issue20 remains deferred.

Debug-only VirtualG8Session/UI/Instrumentation use the existing monitor,
InputService/Hero/Aim/Bindings/ledger and a recording TouchBackend. No privileged
reader, root requirement, USB identity spoofing or game touch submission. The
root-free debug Home adapter reuses Home and changes only the root-warning UI.
Simulation is explicit, labeled, default off, bounded and unavailable in release.

Actual owned AOSP API36 default x86_64 revision2 AVD runs headless with KVM,
2280×1080/4GB/four cores, explicit emulator-5580. Local37.1.11 and hosted37.2.13
executed. Local322 Android and12 tools tests, lint/debug build/release compilation,
Rust5/native3/hostJNI242406/Kotlin bridge/generator12 and12 protected hashes pass.
AVD JNI61206 zero error, UI/orientation smoke and seven replays pass with final
zero recording pointers. This does not establish physical or MLBB compatibility.

CLI verifies owned process/serial/name; reports require fresh stdout, replay nonce
and scenario hashes. Local cold boots and one later hosted run encountered a
SystemUI startup ANR. Bounded target-specific Wait recovery retains evidence;
non-SystemUI ANRs and mapper assertion failures are never dismissed/retried.
Source4c5b6da CI follow-up is recorded in PR22; prior source3d985e3/a68dd5a had
both hosted checks green. Do not infer final-head status from historical runs.

See docs/development/{AVD-SETUP,VIRTUAL-G8,TEST-MATRIX}.md and
 docs/validation/virtual-g8/{REPORT.md,evidence.json}. Generated disks/reports are
ignored. Migration MIGRATION.md and docs/MYTHICMAPPER-AVD.md were updated locally
in the separate dirty migration repository; no commit/push there. Keep PR22 draft,
preserve unrelated design PNG and all stack branches. Never merge or touch phone
without a separate physical request.
