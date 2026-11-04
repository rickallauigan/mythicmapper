# G8 Controller Tester validation — 2026-11-02

Latest follow-up: [physical comparison, CI recovery and final APK evidence](acceptance/README.md).
The tables below retain the earlier APK results; the acceptance report identifies
subsequent builds and limitations separately.

Implemented and installed on the rooted SM-G970F / Android 16 S10e. This feature
uses real Android controller data and exclusive root ownership, not a new input
injector. It is reviewable development work; no gameplay or production release
certification. Stacked on unmerged [PR #12](https://github.com/rickallauigan/mythicmapper/pull/12).

## Evidence matrix

| ID | Component | Expected | Observed | Status | Evidence / follow-up |
|---|---|---|---|---|---|
| UI-01 | Design implementation | Dark navy/violet cards, recognizable original vector, two tabs | Real S10e window captures compared with local mockup; layout tightened after first render; vertical scrolling preserves readable content | PASS | controller.png, active.png, diagnostics.png, controller-info.png, axis-info.png |
| SAFE-01 | Exclusive runtime ownership | Release owned mapping and block start/resume | Real production start/resume attempted under lease and blocked; competing token rejected; zero touch submissions | PASS | ownership.json, HardwareValidationInstrumentation.tester |
| SAFE-02 | Failed cleanup | Never acknowledge a rejected release as safe | Existing backend failure flag now participates in diagnostic acknowledgement; regression exercises rejected UP cleanup | PASS | BackendFailureTest.failedReleaseCannotAcknowledgeDiagnosticSafety |
| SAFE-03 | Normal exit | Root/reader ends, mapping remains stopped | Root Binder death observed on tester exit; runner confirms no live getevent afterward | PASS | ownership.json |
| SAFE-04 | Client process death | No idle privileged reader left behind | Force-stopped only the app with zero active pointers; root process and reader exited | PASS | client-death.json; held-touch kill not tested |
| LIFE-01 | Configuration change | Retain Activity/session and lease | Own Activity orientation changed and restored without destruction or lost lease | PASS | ownership.json |
| DATA-01 | Profile integrity | Preserve mappings/favorites | Four shared-preference files unchanged across tester use and shutdown | PASS | profile-integrity.json |
| HW-01 | Controller discovery/info | Live capabilities, identifiers and transport | Supported observed G8 identity; Android ranges and key capabilities read live; unique matching USB sysfs identity verified once per connection; mode unclassified | PASS | controller-info.png, axis-info.png; descriptor hidden in committed captures |
| HW-02 | Physical buttons | Actual down/up updates | User reported all buttons responding. Recorded follow-up observed Android L3/R3 codes 106/107; later A/B, LT/RT and R3 lifecycles completed | PASS / PARTIAL | human-observations.json, physical-input.json, physical-sticks-triggers.json; not all buttons independently retained in automated telemetry |
| HW-03 | Rear L4/R4 | Explain identical reports | User observed rear buttons lighting L3/R3; actual 106/107 reports agree. Firmware programming unchanged; no invented independent rear state | PASS | README.md, official rear-button instructions |
| HW-04 | Stick coverage | Both widgets track actual motion, require real travel | 2,996 observed Android events in combined capture; both sticks met four-direction and eight-sector/full-extents checks | PASS | physical-sticks-triggers.json |
| HW-05 | Triggers | Actual analog/digital capability, full range | LT/RT digital lifecycles and analog range passed; device also reports BRAKE/GAS aliases | PASS | physical-sticks-triggers.json; secondary alias log normalization fixed afterward and unit-tested |
| HW-06 | Simultaneous buttons | Preserve multiple held states | Model regression verifies independent A/B lifecycles; user completed A+B hold. Active screenshot shows a real B press | PASS / MANUAL | TesterModelTest, human-observations.json, active.png; screenshot alone does not prove simultaneous A+B |
| HW-07 | Disconnect/reconnect | Clear uncertain held/stick state; rediscover capabilities | Reducer disconnect/reconnect tests pass; device changes use InputDeviceListener | HOST PASS | Physical unplug during a held tester gesture remains pending |
| LOG-01 | Bounded diagnostics | Pause display, clear, export sanitized data | 500-row ring and 60-row render limit; actual capture reached 500 history with no queue overflow; host pause/clear/export tests pass | PASS | TesterModelTest, physical-sticks-triggers.json |
| TEST-01 | Host validation | Relevant tests/build/lint/JNI/native checks | 271 Android tests, zero failures/errors/skips; Rust five tests, native three suites, host JNI 242,406 vectors, actual Kotlin bridge, historical 12 fixture hashes pass | PASS | host-validation.json |
| PERF-01 | Input processing | Measure receipt-to-reducer without display-latency claims | Last 512 real-input samples: p50 0.806 ms, p95 2.103 ms, p99 2.774 ms; zero queue overflows | MEASURED | physical-sticks-triggers.json; includes debug capture overhead, not USB/display latency |
| PERF-02 | Rendering | Investigate observed jank | Initial physical debug trace: 83.28% janky, p99 85 ms. Theme/state fixes applied. Separate synthetic replay: 40.36% janky, p99 53 ms | OPEN | performance-before-ui-fix.json, render-replay.json; workloads differ, no like-for-like speedup or 60 FPS guarantee |
| REL-01 | Build boundary/licenses | Preserve licensing and debug-only validation harness | Tester in release manifest; validation instrumentation/safe surface excluded; original GPL vector, no new dependencies or copied vendor assets | PASS / LIMITED | release-boundary.json; inherited project license/raster provenance gates remain |

## Actual physical observations

The initial timed instrumentation window recorded zero controller events; it is
retained as ownership-initial.json and cannot certify the exercise. The user
subsequently reported all buttons working and identified the rear-button aliasing.
Persistent opt-in capture then recorded real L3/R3 activity. Its physical-input.json
is an intermediate sample while R3 was held, not a stuck-pointer report.

The later stick/trigger exercise recorded 2,996 Android events, a bounded 500-row
history, no held controls at completion, no queue overflows, zero logical synthetic
touches and zero mapper injection submissions. Eleven of 23 capability-derived
checks passed in that session. Other controls were not auto-completed from the
user's report or carried across distinct sessions. The committed diagnostics
screenshot reflects those actual 11/23 results.

Secondary BRAKE/GAS axes originally used signed normalization in log rows even
though the selected LT/RT primary axes and pressure cards were correct. The log
normalization now treats all Android trigger aliases as unipolar, with a regression
test. The pre-fix physical evidence is retained and explicitly annotated.

## Performance limits

The measured receipt-to-reducer interval starts after Android delivers/copies an
input callback. It excludes controller USB transport, OS delivery before that
callback and screen observation. The 512-sample window is descriptive, not a
latency budget. Rendering can still lag behind fast input reduction.

The initial mixed idle/input/navigation observation used 34,863 app CPU ms over
428,015 ms (8.15% of one core on average), with app PSS 177,719 kB at its end. This
is not sustained-input utilization and includes 1 Hz debug PSS/Binder/file capture.
The synthetic rendering replay used 5,845 app CPU ms over 4,404 ms, PSS 164,493 kB,
and produced 166 rendered frames from 240 submitted states. CPU is process-wide
across threads, so it can exceed elapsed wall time. Its UI badge says Synthetic
replay; it creates no controller events, mapping checks or touch submissions.

Remaining rendering jank is a known development limitation. Profile recomposition/
layout and warmed/release-like performance before setting budgets. Do not change
CPU/GPU/thermal/SELinux settings to disguise it. No global performance settings
were changed in this feature work.

## Screenshot provenance and navigation

PNG files are actual PixelCopy captures of the app window on the S10e, not mockups,
image-generation outputs or composited illustrations. Other apps' status-bar
notifications are excluded by window capture; the controller descriptor stays
hidden. active.png shows a real B press. Diagnostics and hardware-info views were
opened within the tester; ADB scrolling was confined to this controlled screen.
Some captures precede the nonvisual alias-normalization/theme-invalidation fixes.
The editable SVG is original companion artwork; the supplied raster reference was
not copied into the app or committed.

## Remaining validation

- Independently retain all required button/down-up sequences in one physical run;
  user observation currently covers more buttons than the stored telemetry.
- Physical disconnect with controls held, OS-reserved Home behavior and prolonged
  wake/sleep/reconnect cycles. Idle G8 firmware standby is not disabled here.
- Repeat physical-input rendering traces after optimization; investigate remaining
  debug jank and measure warmed/release-like builds before claiming smooth 60 FPS.
- Human center-offset measurement, large-font/landscape usability and recipient
  selection in Android's share sheet. Export is never sent automatically.
- Gameplay validation remains separate. The tester never launches MLBB or generates
  gameplay touches; this work does not certify MLBB mappings or gameplay latency.

Home retains the pre-existing Runtime metrics entry alongside Controller Tester.
Metadata-only USB updates preserve live state and the last observed connection
time; settings-dialog focus loss clears held buttons while retaining the last axis
sample for explicit center measurement. Both have reducer regressions.

## Acceptance and rendering follow-up

See [acceptance/README.md](acceptance/README.md) for the controlled physical protocol,
retained baseline, new guided evidence and outstanding final-device gates. Existing
results above describe the previous APK; they do not certify subsequent changes.
