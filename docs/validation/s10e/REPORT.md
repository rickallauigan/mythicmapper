# S10e hardware validation — 2026-11-02

Development validation only. This is not gameplay certification or release approval.
The device was an SM-G970F running rooted LineageOS 23.2 / Android 16, ARM64,
with SELinux Enforcing. Network ADB was used; identifiers, addresses, raw logs,
screenshots and user profile contents are excluded. See [environment.json](environment.json).

## Results

| Test ID | Component | Expected behavior | Observed behavior | Status | Evidence | Required follow-up |
|---|---|---|---|---|---|---|
| ENV-01 | Device/root/permissions | Correct target, app-specific root consent, overlay and notification grants | Verified; Magisk consent approved by user; SELinux Enforcing | PASS | environment.json | No security settings changed |
| SAFE-01 | XtMapper isolation/data | No concurrent injecting mapper; preserve old data | Old variants stopped and disabled; six preference files unchanged; a reappearing legacy reader was identified and stopped | PASS | xtmapper-integrity.json | Re-enable old packages only after stopping MythicMapper |
| UI-01 | Compose startup | Main screen renders, root Binder connects | Actual main Activity rendered; root UID 0 and foreground callbacks observed | PASS | startup.json | Human visual/editor usability review |
| JNI-01 | Rust/JNI ARM64 | Load, resolve, return correct reference math repeatedly | 61,206 parity vectors, zero maximum error; missing-library exception caught; invalid input neutral | PASS | jni.json | Rust remains reference-only |
| G8-01 | Capability/interface discovery | Select gamepad rather than touchpad/sensor | Exactly one accepted controller interface; right axes 0–255, observed centers 128 | PASS | injection.json | Other G8 modes remain outside certification |
| G8-02 | Physical input | Record complete button/stick exercise | A six paired presses, RX 0–255, RY 102–255, D-pad up/release observed; no duplicate downs or held buttons at end | NOT TESTED | g8-events.json | Remaining buttons, triggers, left stick, full RY range require further physical capture |
| G8-03 | Idle sleep/wake | Rediscover same controller | Absent then reappeared with stable descriptor; user reports idle sleep without unplug | PASS | controller-wake.json | Physical USB unplug/replug and active-gesture disconnect not tested |
| TOUCH-01 | Root injection | Correct one/two/three-pointer sequences and cleanup | Actual test surface observed three pointers, zero sequence errors, zero active at finish | PASS | injection.json | No injection into MLBB or arbitrary apps |
| TOUCH-02 | Lifecycle/failure | Release on reload, stop, loss and rejection | Deterministic mapping replay covers reload/SYN_DROPPED; simulated backend rejection and fresh-runtime recovery pass | PASS | injection.json | Physical disconnect with held input and real backend rejection remain untested |
| SAFE-02 | Injection focus guard | Refuse unsafe foreground | Injection refused when controlled surface was not focused; no injection attempted | PASS | injection-guard-blocked.json | Keep guard enabled in debug suites |
| PERF-01 | Scroll workers | No unused workers after repeated construction | Before fix 0→3; after fix 0→0 | PASS | injection-before-scroll-fix.json, injection.json | Scroll-specific worker lifecycle remains inherited |
| LIFE-01 | Production root lifecycle | Start/pause/resume/reload/replace/stop | Real Binder lifecycle and root exit pass with empty mapping | PASS | runtime.json | Does not exercise gameplay bindings |
| LIFE-02 | Root process death | Idle reader exits with root parent | Binder death observed; runner confirms no live reader after scoped root termination | PASS | reader_hold.json, tools/validate_s10e.py | Held-touch SIGKILL cleanup is not guaranteed or tested |
| PROF-01 | Profiles/favorites | Five slots, clone/rename/duplicate/reorder and restart persistence | Disposable fixtures persisted between instrumentation processes, then removed; prior library restored | PASS | profiles_prepare.json, profiles_verify.json | Visual CRUD gestures not covered |
| PROF-02 | Schema/preset safety | Reject corrupt/future schema; immutable starter | Direct codec rejection and bundled starter comparison pass without corrupting actual user store | PASS | profiles_prepare.json | Device upgrade migration scenarios require additional fixtures |
| OVR-01 | Floating selector | Attach, five slots, drag/collapse, editor hide, notification | Real overlay operations, immediate FGS notification and Activity rotation reclamp pass | PASS | overlay.json | Human touch usability and MLBB overlay behavior unverified |
| OVR-02 | Screen off/on | Remove selector and release runtime | Initial 300 ms assertion was premature; after normal user unlock, bounded-wait retest observed overlay detach and screen wake with keyguard intact | PASS | screen-initial-failure.json, screen.json | Idle mapping only; screen-off with physical held gestures remains untested |
| FG-01 | Foreground transitions | Avoid five-second reaction delay where supported | Root TaskStackListener registered; measured callback transitions below old five-second polling delay | PASS | foreground-before.json, startup.json | Hidden API compatibility tested only on this ROM; fallback retained |
| GAME-01 | MLBB gameplay | Physical movement/attack/skills/aiming and safe interruption | Game installed; never launched or played by this validation | NOT TESTED | environment.json | Complete manual training checklist below |
| LIC-01 | Distribution notices | Preserve GPL and bundled notices | License texts bundled; Guava parent metadata and scrcpy reference matches recorded | BLOCKED | license-evidence.json, scrcpy-comparison.json, ../../architecture/LICENSING.md | Inherited raster provenance and full artifact notice review remain release gates |
| REL-01 | Debug-only harness boundary | No validation Activity/instrumentation in release manifest | Generated release manifest excludes both | PASS | release-boundary.json | Release APK/signing/reproducibility not certified |

## Changes and architecture

Preserve XtMapper's evdev parser, controller capability cache, Java mapping/aiming,
InputService multitouch ownership and InputManager root injection. Kotlin continues
to own Android/UI/profile lifecycle. Rust/JNI remains a packed, allocation-free
reference math call, outside the production event path; no hot-path migration was
justified by these measurements.

The production changes are deliberately narrow:

- Construct the smooth-scroll worker only when scrolling is requested.
- Own the direct getevent child Process, stop it during service shutdown, and use
  Linux parent-death signaling in its existing preload to avoid orphan readers.
- Subscribe to root task-stack changes, coalesce refreshes and deduplicate package
  callbacks; retain the five-second polling fallback when subscription fails.
- Request immediate foreground-service notification display.
- Explain the controller's idle sleep possibility in the undetected status text.

The debug-only harness uses the real root backend on a blue, action-free Activity.
Every new DOWN/MOVE verifies that this Activity is foreground and ready. Cleanup
UP events remain permitted. It never launches MLBB. Controlled process termination
requires mapping inactive, zero logical pointers, a live owned reader and one
unambiguous MythicMapper root process. Root death with held touches is not claimed.

## Performance interpretation

See [BENCHMARKS.md](BENCHMARKS.md) for measured values. Input replay, Binder focus
checks, instrumentation and ART warmup affect these development measurements.
Mapping processing includes synchronous injection. Submission measures the API call,
not display observation. Replay dispatch delay is software Handler queue time, not
USB latency. No physical controller-to-display latency was measured and no numeric
release budget is set from this single device session.

## Automated host checks

[host-results.json](host-results.json) records 256 Android tests (zero failures,
errors or skips), five Rust tests, three native suites, 242,406 host JNI parity
vectors, 12 fixture hash matches, 12 profile Python tests and four tool tests.
Actual compiled Kotlin/JNI roundtrip and missing-library handling passed on host;
ARM64 JNI results are separate device evidence. Debug build/lint and release
manifest generation passed. Dependency inventory regenerated successfully; OSV
queried 104 Maven components and returned no advisory records at check time.
This is not a complete security or licensing audit.

## Reproduction

Use JDK 21, SDK 37, the repository NDK and pinned Rust toolchain; see repository build
instructions. Run host validation before installing the debug APK:

```sh
bash ./gradlew testDebugUnitTest lintDebug assembleDebug processReleaseMainManifest
bash app/src/test/native/run-controller-probe-tests.sh
bash app/src/test/native/run-mouse-reader-tests.sh
bash app/src/test/native/run-reader-parent-death-tests.sh
cargo test --locked --offline --manifest-path engine/Cargo.toml
python3 tools/check_mythic_fixtures.py
adb -s "$S10E_ADB_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
python3 tools/validate_s10e.py --serial "$S10E_ADB_SERIAL" \
  --suites jni profiles_prepare profiles_verify startup injection overlay runtime reader_hold startup screen
```

Stop all other mapper processes first. Unlock normally and approve app-specific
Magisk root if requested. Run `screen` last because it may leave keyguard showing.
The runner stores sanitized JSON here and raw/private evidence under ignored
`app/build/s10e-private/`. Run profiles_prepare and profiles_verify together, in
order; never clear app data to recover fixtures. `reader_hold` deliberately kills
only the verified idle MythicMapper root process. It does not kill held gestures.

## Required human/device work

1. Wake G8 and verify every button, both triggers, all D-pad directions, both stick
   clicks, full stick ranges and centers in a fresh read-only capture. The blue
   screen now displays physical key/axis feedback, but this feedback is not yet
   human-verified. The earlier partial capture cannot certify a full exercise.
2. In MLBB's permitted solo training workflow, manually verify movement, attack,
   Skill 1/2/Ultimate and right-stick aiming, then movement+attack and
   movement+aim+attack. Check camera release. Do not use an online match as a test.
3. Verify all five favorites, switching with held controls, overlay collapse/drag,
   editor/calibration and interruption recovery. Confirm no stuck touches.
4. Physically unplug/replug during an active controlled test, then check released
   pointers and reconnection. Idle wake is a separate scenario.
5. Repeat sustained-input and long-running stability measurements before selecting
   performance budgets or approving a release.

No CPU/GPU, thermal, SELinux, MLBB-account or gameplay settings were changed.
After these tests, the user explicitly requested keeping the display awake during
network debugging. The screen timeout was changed from five minutes to the maximum
value; see [DEBUG-POWER.md](DEBUG-POWER.md) for scope, restoration and the separate
G8 firmware standby limitation. No telemetry, purchases, online matches or production
release.
