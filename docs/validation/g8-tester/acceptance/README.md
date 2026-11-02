# Controller tester acceptance follow-up

Development-only validation, stacked on PR #12 through draft PR #13. This directory
adds evidence without replacing the original tester or S10e reports. No MLBB
compatibility or physical display latency is certified.

## Comparable physical protocol

`g8-physical-v1-30s`: keep orientation, display settings, build, visible Controller
page and foreground apps fixed. Warm both sticks for at least five seconds. An
observed A down starts collection: 10 seconds of both-stick circles, 10 seconds of
alternating full LT/RT, five seconds of A+B held with circles, five seconds released.
The tester saves a bounded private report automatically; disconnect, Activity stop
or rotation aborts the trial. Results with an abort reason are not completed trials.
No ADB-injected controller events substitute for the person's exercise.

The comparison APK supports a controlled eager/full-state/Formatter baseline and
scoped-state/lazy/display-formatter rendering in the **same binary**, with identical
profiling and guided-test machinery. This baseline reproduces the relevant former
rendering costs; it is not an exact build of the old commit. A fresh paired trial
is required after a long interruption/network change. The older captured trial is
retained below as a standalone observation, not a claimed before/after speedup.

Window FrameMetrics record total, layout, draw, sync, command and input duration.
On API 31+, jank means total duration exceeded that frame's reported deadline.
CPU is the app process's CPU time divided by wall time (one-core percent), not root
backend or system CPU. PSS endpoints and ART allocation/GC deltas are process-wide.
Composition counters and timed trace sections separate controller drawing,
reducer, snapshot and log work; these are instrumentation, not a sampled heap or
Perfetto investigation. Queue time is Android callback enqueue to reducer entry;
it excludes USB delivery, display scanout and most reducer execution. Frame callback
drops and input queue overflow are separate counters. Retained event logging and
checklist evidence remain independent of coalesced UI publication.

## Observations so far

| Trial | Frames | Janky | Frame p50/p95/p99 | App CPU | PSS before/after | Queue p50/p95/p99 |
|---|---:|---:|---|---:|---|---|
| Earlier physical debug baseline | 757 | 23.51% | 15.800 / 20.133 / 25.777 ms | 61.96% of one core | 176057 / 172294 KiB | 0.847 / 1.576 / 2.052 ms |
| Fresh controlled debug baseline (incomplete phase evidence) | 711 | 72.86% | 19.042 / 27.064 / 29.399 ms | 66.63% of one core | 173577 / 168114 KiB | 0.921 / 1.738 / 2.420 ms |
| Same-APK optimized debug trial (different workload) | 203 | 81.77% | 23.597 / 52.441 / 57.189 ms | 25.73% of one core | 175094 / 171603 KiB | 1.033 / 2.182 / 2.939 ms |
| Non-debuggable benchmark build | Built; pending device exercise | — | — | — | — | — |

Earlier baseline: 30.252 seconds, 14,595 changed-axis/key events, 5,623 input packets,
zero queue overflow and zero frame callback drops. ART allocated 68,820,992 bytes,
with three GC collections and 101 ms total GC time. Root diagnostics before/after
show mapping inactive, exclusive diagnostics, zero logical pointers and zero touch
submission samples. See [before-debug-1.json](before-debug-1.json). Counts describe
observed input, not proof that every supported button was exercised.

`benchmark` uses the debug-signed development package and existing data/root consent,
release Kotlin/Compose settings and R8/resource shrinking, with shell profiling
allowed. Existing broad inherited keep rules limit shrinking. It is a release-like
measurement variant, not a production build or a release performance guarantee.

Fresh baseline: saved without interruption, 16,617 observed events, 5,589 packets,
zero overflows, no held controls at end and root before/after zero submissions.
However, retained trigger extents remain 0–0 and no completed A+B overlap is
recorded. It cannot certify every prompted phase, even though the person confirmed
saving it. Treat any paired comparison as provisional until workloads are verified.

## Guided evidence and safety gates

The Guided tab retains per-control down/up/paired counts and the last eight actual
transitions per control independently of the 500-row display ring. It records
A+B and LB+RB overlap only after at least 50 ms together and actual releases;
focus loss/disconnect cannot synthesize a passing release. Stick full travel still
requires four extents and eight outer sectors, with the configured thresholds
saved with evidence. Trigger tests require observed rest and full travel.

Rear-button prompts attribute the physical actuator only by the person's requested
step. Observed Android codes remain L3=106 / R3=107 when rear L4/R4 alias them.
There are no fabricated separate rear identities. Firmware programming is unchanged.

The disconnect check must be armed, then the person physically unplugs while holding
A and deflecting a stick and reconnects with controls released. The reducer records
held controls before removal, clears all transient state, records the observed
cleared result and reconnection. Root snapshots independently verify mapping stayed
inactive and pointers/submissions stayed zero. No automatic hardware disconnect.

| ID | Component | Expected | Observed | Status | Required follow-up |
|---|---|---|---|---|---|
| ACCEPT-01 | Rediscovery | Verify device after network change | S10e identity and actual G8 interfaces detected; tester foreground and phone awake | PASS | No network address/serial committed |
| ACCEPT-02 | Physical benchmark pair | Same workload before/after | Both trials saved, but workloads differ; comparison documented | BLOCKED | Repeat matched, verified phases before performance acceptance |
| ACCEPT-03 | Held unplug/replug | Clear held states, preserve exclusive ownership | Guided recorder and reducer regression added | NOT TESTED on hardware | Physical held-control disconnect/reconnect |
| ACCEPT-04 | Rear aliases | Retain actual codes without separate identity | Previous actual code evidence preserved; guided sequence added | PARTIAL | Complete new four-step capture |
| ACCEPT-05 | Entry-order crash | Direct tester then Home opens safely | Cold tester -> Home in same process passed without fatal/libsu exception | PASS | final-device.json |
| ACCEPT-06 | Lifecycle/no injection | Exit, rotation, idle process death, reader cleanup | Final APK ownership/rotation suite and zero-pointer process-death check passed | PASS | final-tester.json, final-device.json; arbitrary active-pointer crash not certified |
| ACCEPT-07 | Host gates | Debug build/tests/lint, native, Rust, JNI, historical fixtures | 282 Android tests pass; debug APK and lint pass; Rust five, native three, JNI parity and compiled Kotlin bridge pass | PASS | [host-validation.json](host-validation.json); device behavior remains separate |
| ACCEPT-08 | Existing profiles/favorites | Preserve four preference files | Read-only hashes unchanged from before acceptance work | PASS | [profile-integrity.json](profile-integrity.json); no user data restored or cleared |

Reset Test archives the previous tester report and clears tester progress only.
Profiles, favorites, calibration and XtMapper data are never reset by these controls.
Private raw logs and network targets remain ignored under app/build/s10e-private.

Final host gates passed: 282 Android tests with zero failures/errors/skips, debug
APK build/lint, and non-debuggable benchmark APK build. The validated debug APK was subsequently installed with -r; the benchmark variant
has not been installed/measured. See host-validation.json for artifact hashes.


## Collected physical comparison

Both fresh debug reports are now retained, with the exact common APK SHA-256 and
baseline file hash in [comparison.json](comparison.json). Each has zero input queue
overflows, zero root touch submissions before/after, mapping inactive, exclusive
diagnostics and no held controls at completion. The optimized report contains
5,335 observed changed-axis/key events and 1,665 packets over 30.147 seconds, versus
16,617 events and 5,589 packets over 30.221 seconds for the baseline. The optimized
ledger retains LT/RT full travel and a completed A+B overlap; the baseline does not.
Evidence is cumulative and does not establish exact phase-by-phase timing.

**This pair is not a valid matched-workload performance proof.** CPU and allocation
are lower in the optimized trial (20,590,592 allocated bytes, one GC, 47 ms GC time),
while frame p99 and janky percentage are worse. Different input activity, one trial
per path, sequential timing and debug overhead confound attribution. Do not claim a
speedup or explain the worse p99 as a proven optimization regression. High debug
jank remains a review issue. Repeat both paths with verified equivalent input phases
and multiple trials before setting a budget or migrating the input hot path.

Instrumentation observed 709 root-screen function compositions in baseline versus
zero in the optimized trial; nested Compose restart scopes still update. Controller
compositions were 699 versus 174; stick compositions 1,291 versus 338. Controller
draw recording averaged 0.927 ms versus 1.285 ms; reducer work averaged 0.233 ms
versus 0.287 ms per packet. These counters/timers cover named functions, not every
Compose scope or a sampled allocation profile. FrameMetrics samples are submitted
app frames, not display refreshes or a continuous FPS measure. Queue percentiles
exclude mapping completion/injection and physical controller-to-display latency.
No frame/event interarrival jitter distribution or release-like physical trial was
captured. The benchmark variant compiles but is not a measured release performance
result. Normal exit of the comparison APK left no root process or getevent reader
([comparison-lifecycle.json](comparison-lifecycle.json)); final-APK checks are separate.


## CI shutdown regression

[Run 41455233159](https://github.com/rickallauigan/mythicmapper/actions/runs/41455233159)
failed one of 282 tests. Its actual JUnit artifact shows Handler construction with
a null looper at ActivityObserverLifecycleTest's old line 33. The test queried
HandlerThread.getLooper() after stop(), racing thread termination. Joining the
thread before the old assertion reproduced the same NPE locally.

The enabled test now retains its Handler/Looper before shutdown, joins and asserts
thread termination, verifies posts to that handler are rejected, then checks late
refreshes and repeated stop do not notify/restart polling. Finally always cleans up.
No runtime code or assertions were disabled to hide the failure. The focused test
and full 282-test local suite, lint, inventory, debug APK and release manifest pass.
See [ci-recovery.json](ci-recovery.json) for the rerun result when available.


## Repeat pair and final development APK

| Debug trial | Observed events / packets | Janky frames | Frame p50/p95/p99 (ms) | One-core app CPU | PSS before/after (KiB) | Queue p50/p95/p99 (ms) |
|---|---|---|---|---|---|---|
| Repeat baseline | 11259 / 3827 | 70.39% (564 frames) | 19.340 / 26.755 / 41.279 | 54.45% | 155836 / 164580 | 0.915 / 1.950 / 2.993 |
| Repeat optimized | 8627 / 2974 | 60.47% (473 frames) | 19.254 / 25.603 / 47.620 | 45.25% | 170585 / 162277 | 1.088 / 2.534 / 3.563 |

Baseline allocated 56,754,176 bytes, two GCs / 72 ms; optimized allocated
39,297,024 bytes, two GCs / 104 ms. Both have zero queue overflow, mapper touch
submissions and logical synthetic pointers. Raw reports and the exact common APK
hash are retained. The baseline held A+B at collection end; subsequent actual UP
records were observed before starting the optimized trial. Those held controller
states are not synthetic touches. The optimized trial ended fully released.

The repeat captured 24 new LT and RT pairs each in baseline versus 13 each optimized.
Different activity remains a confound: jank percentage, CPU and allocation are lower,
but frame and queue p99 are worse. **No stable p99 improvement or matched-workload
performance acceptance is established.** All reports remain available; none of the
baseline files were overwritten. These are debug software measurements, not release
or physical display latency results. A captured full-fidelity input replay or verified
phase-counted physical protocol and multiple comparable trials are recommended next.

The CI shutdown race is fixed in e05532f and [run 41461355489 passed](https://github.com/rickallauigan/mythicmapper/actions/runs/41461355489).
All 282 Android tests remained enabled, with zero skips. The validated development
APK was installed with `adb install -r`; no app data was cleared. Actual-device JNI
passed 61,206 vectors with zero max error and missing-library handling. The ownership
suite rejected gameplay start/resume, retained the lease across rotation, observed
zero injection submissions and verified root/reader teardown on normal exit.

The existing shared root reader is expected to remain alive while the tester owns
the service. RemoteService.handleInputFrame rejects mapping when diagnosticOwner
is set. No second reader was added; reader_running=true during a live diagnostic
session is not itself a safety failure. Teardown must stop it. App-only CPU figures
exclude this root process and its reader. See final-device.json, final-jni.json,
final-tester.json and final-live.json for the final APK's independent evidence.


Final-window captures: [Controller](final-controller.png), [Guided](final-guided.png).
The first Guided capture exposed an invisible Diagnostics tab label/icon despite its
semantic hit target; [navigation-before.png](navigation-before.png) preserves it.
Giving each repeated navigation item a stable Compose key restores all three on
S10e. The extended host test exercises Guided -> Diagnostics -> Guided and checks
all tab labels remain displayed. The physical performance captures predate this
navigation-only change; no new performance gain is claimed for the final APK.


The exact rebuilt navigation-fix APK also passed all 282 tests and lint locally,
was installed, and passed the real JNI and ownership/rotation/teardown suites again.
Repeated controlled termination started with zero pointers/submissions and left no
root process/reader. The four preference hashes still match after the final install.
These idle-death checks do not certify arbitrary crashes with active gameplay
pointers. The final app is left in exclusive diagnostic mode without opt-in 1 Hz
capture. Actual screenshots were fetched from fresh app-window captures, verified as
complete PNGs, rather than reusing stale files retained by Android task restoration.
The exact APK hash and source blob are in final-device.json / host-validation.json.
