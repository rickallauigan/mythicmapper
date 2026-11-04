# Architecture decisions

## ADR 001 — Preserve proven runtime
Accepted for incremental development. Keep Java ControllerDeviceMonitor, native probes,
RuntimeRequestGate, InputService and reflective root MotionEvent injection. Kotlin owns
Android lifecycle, Compose, permissions, profiles, calibration and diagnostics presentation.
Rust first implements a pure deterministic reference engine and coarse JNI calls, outside
the active mapping hot path. No default Rust activation until baseline, differential replay,
JNI failure tests and physical acceptance. Native left-stick movement remains native.
No uinput touch backend without a supported-device capability check and measured benefit.

## ADR 002 — Ownership and JNI
One mapping runtime owns state; UI never owns synthetic pointers. A profile switch first
invalidates queued work, stops producers, releases all pointers, resets state, then publishes
the replacement through the existing runtime gate. Backend errors must be observable and
must stop mapping; process-death cleanup needs platform evidence. Rust cannot call Android
or retain JNIEnv/local references. JNI takes primitive values or preallocated bounded buffers;
no per-event strings/objects. Validate sizes/finite values and schema/ABI version before use.
No unwinding across FFI. Missing library/ABI errors disable experimental Rust, not injection.
Java pointer ownership remains authoritative until full replay/device parity is established.

## ADR 003 — Profiles and provenance
Keep XtMapper text/structured projection as runtime compatibility format. New immutable
bundled presets have stable IDs and revision plus explicit historical/unverified status.
Clone to personal UUID IDs; five ordered nullable favorite slots reference UUIDs, never
names. Rename changes display metadata only. Local atomic persistence uses schema version
and rejects future/corrupt data; migrations preserve personal content. Calibration remains
separate from hero behavior. No invented hero-specific settings. Starter mappings may be
historical S10e templates; hero verification requires human evidence on the target setup.

## ADR 004 — Scope and identity
MythicMapper is original G8 USB + MLBB only. Existing generic engine remains for reuse;
MVP activation must gate game package, verified device evidence, and disallow macro/repeat
sequences. Keep Java namespace for JNI, use a separate applicationId and Binder service name
before testing alongside XtMapper. Never run both injecting runtimes. Remove shell security
setting changes. No analytics/accounts/cloud, targeting, memory edits or anti-cheat bypass.
