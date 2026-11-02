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
| Same-APK optimized debug trial | Pending physical exercise | — | — | — | — | — |
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
| ACCEPT-02 | Physical benchmark pair | Same workload before/after | Fresh baseline saved with incomplete trigger/overlap evidence; optimized trial armed | BLOCKED | Complete optimized exercise; repeat missing phase evidence as needed |
| ACCEPT-03 | Held unplug/replug | Clear held states, preserve exclusive ownership | Guided recorder and reducer regression added | NOT TESTED on hardware | Physical held-control disconnect/reconnect |
| ACCEPT-04 | Rear aliases | Retain actual codes without separate identity | Previous actual code evidence preserved; guided sequence added | PARTIAL | Complete new four-step capture |
| ACCEPT-05 | Entry-order crash | Direct tester then Home opens safely | Reproduced libsu already-created-shell crash; shell configuration moved to Application | FIX IMPLEMENTED | Final APK runtime retest |
| ACCEPT-06 | Lifecycle/no injection | Exit, rotation, idle process death, reader cleanup | Prior suite retained; new acceptance repeat pending | NOT TESTED for final APK | Run after physical comparison |
| ACCEPT-07 | Host gates | Debug build/tests/lint, native, Rust, JNI, historical fixtures | 282 Android tests pass; debug APK and lint pass; Rust five, native three, JNI parity and compiled Kotlin bridge pass | PASS | [host-validation.json](host-validation.json); device behavior remains separate |
| ACCEPT-08 | Existing profiles/favorites | Preserve four preference files | Read-only hashes unchanged from before acceptance work | PASS | [profile-integrity.json](profile-integrity.json); no user data restored or cleared |

Reset Test archives the previous tester report and clears tester progress only.
Profiles, favorites, calibration and XtMapper data are never reset by these controls.
Private raw logs and network targets remain ignored under app/build/s10e-private.

Final host gates passed: 282 Android tests with zero failures/errors/skips, debug
APK build/lint, and non-debuggable benchmark APK build. Neither final APK has been
installed over the comparison APK yet. See host-validation.json for artifact hashes.
