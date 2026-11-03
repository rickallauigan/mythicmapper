# Virtual G8 / AVD validation — issue #21

Executed 2026-11-03 on the separate `feature/21-virtual-g8-avd` branch, based on
PR #14 head 7efcf35. The #12 → #13 → #14 dependency chain is preserved. No physical
S10e command, app install/reset, profile edit or G8 input was used for this work.
The emulator's Android input inventory contained no GameSir-G8 device. This proves
independence from physical input; it does not assert the user's phone was physically
unplugged. No production main source or historical profile changed from the base.

## Host and executed environment

EndeavourOS Linux x86_64, eight logical CPU threads (Intel i5-1135G7/VT-x), usable
KVM permissions, 31 GiB RAM (~7 GiB available at initial probe), ~81 GiB free storage.
Existing Android SDK command-line tools, JDK 21 Gradle toolchain, Gradle wrapper
9.5.1 and NDK 28.0.13004108 were reused. API 36 AOSP default x86_64 revision 2 was
available and installed through sdkmanager. Existing emulator 37.1.11.0 was retained.
No host service, KVM permissions, SELinux/root policy or global phone setting changed.

Owned AVD: `MythicMapper_API36_G8`, `emulator-5580`, API 36, x86_64, 4 GiB/four cores,
2280×1080, density 420, headless with KVM and graphics `auto`. Emulator log reported
boot completion in 32,771 ms; this is one boot observation, not a performance budget.
APK packaging includes all ten observed x86_64 native libraries, including Rust JNI.
The non-injecting harness runs as an ordinary app UID without requiring su or adb root.

Actual AVD UI capture: [Virtual G8](virtual-controller-avd.png).

## Results

| ID | Component / expected | Observed | Status | Evidence / follow-up |
|---|---|---|---|---|
| ENV-01 | Owned API36 x86_64 AVD boot | KVM boot; API/ABI/qemu verified | PASS | evidence.json; generated emulator.log |
| ENV-02 | Build/install debug APK | Installed with -r; x86_64 libraries packaged | PASS | evidence.json APK SHA256; generated install.json |
| A | Button identity/grid, edges, simultaneous holds, axes | 36 recording touch events, zero final pointers; each button checked at its distinct grid X | PASS | basic-input.json |
| B | Bennett repeat, latest direction, LT cancellation | 10 recording events; ≥3 DOWN phases; post-release checkpoint quiet; final zero | PASS | beatrix-bennett.json; disposable180/400 fixture |
| C | Kimmy continuous aim, RT handoff, neutral, B/L3 shared ownership | 12 recording events; delayed resume observed; last owner releases; final zero | PASS | kimmy-skill-resume.json |
| D | Logical screen/foreground pause/resume | Six recording events; locked/other-app frames blocked; neutral rearm; final zero | PASS (simulation) | lock-unlock.json; issue #20 game effects UNVERIFIED/deferred |
| E | Disconnect/reconnect held stick; SYN_DROPPED | Nine recording events; reset release and fresh neutral gating; final zero | PASS | disconnect-reconnect.json; not USB hotplug proof |
| F | Switch disposable hero while held | Eight recording events; old ownership released; final zero | PASS | profile-switch.json |
| G | Virtual diagnostics excludes mapping | Six recording events; frames blocked while owned; final zero | PASS (recording gate) | diagnostics-exclusive.json; real root Binder gate separate |
| H | UI smoke | Home, catalog/five-slot model, real tester, editor and Virtual G8 Activities open; virtual orientation survives | PASS | evidence.json; presence/render smoke, not complete interaction/gameplay acceptance |
| HOST-01 | All inherited and new Android tests | 322 tests, zero failures/errors/skips | PASS | full testDebugUnitTest run; new12 debug-variant source tests |
| HOST-02 | Lint/build/dependency inventory/release manifest | All tasks passed; zero VirtualG8 release manifest entries | PASS | Gradle task logs; evidence.json |
| HOST-03 | Rust/native/JNI parity | Rust5, native3, JNI242406 vectors and actual Kotlin missing-library handling pass | PASS | existing scripts rerun |
| HOST-04 | Fixtures/profiles/quality tooling | 12/12 fixture hashes; 12 generator tests; eleven Python quality tests | PASS | existing scripts + new workflow tests |
| SAFE-01 | Production allowlist/profile preservation | No app/src/main or historical profile diff from base | PASS | git diff against7efcf35 |
| HW-01 | Physical G8/Lineage/MLBB compatibility | Not exercised by this harness | PENDING PHYSICAL | Preserve previous reports; no new certification |
| ROOT-01 | RootService / Android injected touch delivery on AVD | Recording sink only | NOT TESTED | Optional rooted integration, not required for daily workflow |
| UINPUT-01 | System-visible virtual hardware | Internal adapter only | NOT IMPLEMENTED | Optional feasibility phase |

Representative CLI output:

```json
{"id":"kimmy-skill-resume","status":"PASS","events":12,"final_pointers":0,"report":"app/build/virtual-g8/kimmy-skill-resume.json"}
```

The committed cases are reviewed synthetic event/touch traces, not private device
logs. Each has nominal steps and actual monotonic observations. Software scheduler
intervals are not physical controller-to-display latency. Automated replay suppresses
per-event UI publication to keep rendering work out of the timing sequence; the
manual virtual UI uses event-driven coalesced publication and the UI smoke exercises
that path. Host tests use exact virtual time; AVD uses minimum inter-step gaps.

## Failures found and corrected

The first Android replay exposed two harness defects that host time hid: exceptions
inside Instrumentation.runOnMainSync crashed the app instead of producing reports,
and elapsed absolute timestamps compressed late steps. Exceptions are now captured
on the lifecycle thread and reported after cleanup; minimum deltas preserve input
spacing under emulator load. A further teardown race showed Activity.finish/idle
was insufficient to prove onStop released the UI lease. Explicit idempotent disable
now releases the lease before finish. The corrected suite passed on the AVD.
No failing test was disabled. A premature screenshot captured the splash screen;
CLI UI startup now waits for Activity launch and visual inspection uses the rendered
screen rather than claiming that splash was the feature.

## Remaining limits and next work

Issue #20's active-Kimmy-shot lock behavior is unchanged/deferred. Root Binder
exclusivity and Android MotionEvent delivery are not proven by recording-mode
passes. Physical movement remains MLBB native and has no synthesized joystick in
this engine. UI smoke checks Activity rendering/model availability/orientation;
full accessibility-driven navigation, editor save on unrooted AVD and real overlay
switching are not certified. Optional uinput/root/backend fault integration and
sanitized physical-frame import remain future work. The production allowlist must
remain closed to simulation.

A bounded GitHub Actions API36/x86_64 job has been added alongside the unchanged host
workflow. Its observed remote result belongs in the PR/checks; local success is not
a claim that hosted CI passed. No merge or production release is authorized.

Daily commands, failure reproduction and owned-emulator cleanup:
[AVD setup](../../development/AVD-SETUP.md), [virtual source](../../development/VIRTUAL-G8.md),
[test matrix](../../development/TEST-MATRIX.md).

## Cold-restart, JNI and CI follow-up

Owned stop/cold restart and Bennett/Kimmy reruns passed. Later local cold boots in
both graphics configurations encountered a focused **SystemUI isn't responding**
dialog; events recorded SystemUI failing to complete startup. Exact host/platform
cause is unproven. Tests correctly refused that foreground rather than ignoring
the guard. An explicit `recover` command records the condition and presses Wait
only on that verified owned-AVD SystemUI dialog; other app ANRs are refused. After
recovery, the entire JNI/UI/seven-case suite passed again. These are recovered runs,
not claims of clean boot reliability. No phone/keyguard/security setting was touched.

The emulator JNI suite executed 61,206 vectors with zero max error, missing-library
handling and a real x86_64/64-bit report. It exposed an inherited hardcoded ARM64
report label, now corrected in debug instrumentation. Root-free smoke automatically
checks the reported ABI. A bounded initial-focus wait also corrects a startup race;
actual unexpected focus loss still aborts and cleans up.

Hosted run41625577976 failed because sdkmanager was not on PATH; the workflow now
uses its SDK path. Run41626628456 reached installation but doctor's emulator-version
probe exited127; captured stderr was not previously surfaced. Doctor now reports
stderr and uses headless `-no-window -version`, avoiding unnecessary GUI dependencies.
Those failed runs remain recorded; subsequent passing runs are listed below. The
workflow's conditional cleanup only avoids stopping an emulator never created; it
does not bypass any failed test. PR: https://github.com/rickallauigan/mythicmapper/pull/22.

## Hosted acceptance

Source **3d985e3** passed both [host validation](https://github.com/rickallauigan/mythicmapper/actions/runs/41627465766)
and [API36 AVD validation](https://github.com/rickallauigan/mythicmapper/actions/runs/41627465742).
Downloaded artifacts were inspected: emulator37.2.13.0/KVM, x86_64 JNI61,206 vectors
with zero error, real UI smoke and all seven cases PASS with zero final pointers.
The hosted AVD needed no SystemUI recovery. This establishes execution on an
independent hosted VM without the S10e/G8, not physical compatibility. Local
emulator37.1.11 startup caveats remain documented; remote success does not erase them.
The local owned emulator was stopped successfully after validation; guest data and
APK remain available for the next boot. No physical device was stopped or changed.

Final delivery review added a fresh instrumentation-output requirement for JNI,
UI smoke and scenario results, so a crashed runner cannot reuse a saved PASS.
Eleven Python tests cover stale/ambiguous rejection and validate actual captured
hosted output. Virtual Activity intent activation now reports refusal instead of
crashing when a real mapping service is started; an Android regression covers the
case without acquiring a lease or changing the mapper. Final local321 Android tests,
lint/build and full AVD JNI/UI/seven-case validation passed with the new APK.
The later local boot required the recorded SystemUI Wait recovery again. A separate
hosted rerun encountered a Robolectric MavenArtifactFetcher HTTP dependency failure;
no tests were disabled. Subsequent executable-head checks are authoritative in PR22.

Final lifecycle fault coverage reproduced `Cleanup leaked contacts` when duplicate
resume notifications replaced a held Bennett runtime. Startup is now idempotent,
and a locked screen-on also disposes the prior runtime. Host regression and the
AVD Bennett replay cover duplicate notifications without changing production
Bennett or Kimmy code. All322 Android tests and11 Python tests pass. Scenario JSON
files are declared Gradle test inputs, preventing stale UP-TO-DATE results after
fixture edits. Debug harness tests live in src/testDebug, not release/common tests.
Release Kotlin/Java compilation passed and compiled release classes contain no
simulator. This project has no compileReleaseUnitTest tasks; none were silently
claimed executed or disabled.

The transient hosted failure was HTTP429 for Robolectric's Android15 SDK jar from
repo1.maven.org. Tests now use the canonical repo.maven.apache.org Maven Central
endpoint (HEAD200 verified), and the host workflow caches only public Robolectric
SDK artifacts. Existing checks stay enabled. Configuration follows
[Robolectric's documented runtime-repository option](https://robolectric.org/configuring/).
Final APK installation and the complete fresh-result JNI/UI/seven-case AVD suite
passed again; the later executable-head CI results are recorded in PR22.
