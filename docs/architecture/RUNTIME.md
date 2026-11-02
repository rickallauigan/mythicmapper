# Scoped root runtime and diagnostics

MythicMapper keeps Java mapping and root MotionEvent injection. InputService owns the
logical touch ledger and maps through TouchBackend; Input implements the initial backend.
The injectable constructor supports replacement without moving mapping into a driver.
Legacy mouse support still constructs Input for compatibility; it is not a second touch
backend and there is no new uinput touchscreen implementation.

Root entry rejects non-MLBB, macro/swipe/repeat, keyboard/mouse and synthetic movement-pad
profiles. Controller discovery caches acceptance of the exact historically observed original
G8 USB name plus 054c:0ce6 and BUS_USB, then applies existing capability checks. Android status
uses name/VID/PID/controller sources; native bus evidence remains authoritative at runtime.
This intentionally rejects unevidenced modes and G8+. Original fixtures remain untouched.

Package ID is io.github.rickallauigan.mythicmapper (.debug for development), version 0.1.0-dev,
Java namespace remains xtr.keymapper for JNI. Legacy shell Binder lookup/registration now
uses mythicmapper. Shell startup no longer changes SELinux, grants overlay permissions or
changes low-RAM system settings, and generated scripts no longer broadly pkill the shared
Java class name. The original XtMapper app/repository has not been altered or stopped.
Root remains the UI default. No production release has been dispatched.

InputManager false return/reflection errors latch failure. InputService rejects further
mapping, posts producer cleanup and attempts UP for all logical pointers. Pause/resume
cannot clear that failed backend; a NEW runtime can recover when the backend is healthy.
These are cleanup attempts, not proof Android received a release. Reader EOF/failure stops
the owned runtime and tells diagnostics to restart the mapper service. Main-thread crash
handling attempts the existing ownership cleanup before exit; SIGKILL/native-crash cleanup
still requires physical/platform validation.

RuntimeMetrics uses three 2048-sample rings. Reader receipt-to-looper-dispatch is queue delay;
processing is inclusive of submission/callback work, NOT isolated mapping CPU time. Submission
measures reflective async mode-0 calls, NOT Android delivery/display completion. Snapshot
sorting/formatting runs on request, outside sample recording. Diagnostics exposes backend UID,
logical active touch count, raw right-stick axes/buttons, last controller event, SYN_DROPPED,
injection errors and last inter-receipt interval change. Source timestamps/USB polling jitter,
physical display latency, device allocations/CPU and per-frame dropped-work counts are not
measured by these counters. Instrumentation overhead needs device baseline comparison.

Validation: native probe now rejects name/id/bus/ioctl mismatches; existing native and Android
mapping/ownership/lifecycle tests retained. BackendFailureTest exercises rejection, stopped
input, cleanup and a fresh healthy runtime. Actual compiled Kotlin/JNI/Rust bridge and missing
library behavior pass on Linux host; Android ABI loading remains a device gate. Full combined
implementation: 253 Android tests, zero failures/errors/skips, lint/debug assembly PASS.
