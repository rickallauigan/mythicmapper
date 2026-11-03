# Virtual G8 — debug only

`VirtualG8Activity` and `VirtualG8Instrumentation` exist only in `src/debug` and the
debug manifest. The launcher entry says **Virtual G8 — DEV ONLY**. Opening it does
not enable simulation; press Start, or explicitly use `virtual_g8.py ui`. STOP,
leaving the Activity or the 60-second watchdog releases all recording contacts.
Manual buttons are hold/release controls, sticks are drag-and-center, and trigger
sliders report simulated analog values separately from their digital buttons.
Original Controller Tester Canvas artwork is reused. Render publication is
coalesced to at most one pending 16 ms update; input delivery is not coalesced away.

## Reused engine and isolated destination

```
versioned JSON / virtual controls
 → VirtualG8Session (SIMULATED:Virtual-G8:0)
 → ControllerDeviceMonitor.event (existing injectable discovery constructor)
 → ControllerBindings / ControllerHeroHandler / ControllerAimHandler
 → InputService.injectEvent (existing ownership ledger)
 → recording TouchBackend (never InputManager injection)
```

`InputService`'s existing Wayland/test constructor suppresses native controller,
mouse and touchpad startup. The debug session supplies a non-injecting backend and
its own logical discovery map; no `/dev/input/event*` node, native capability probe,
USB identity spoof, root process or additional privileged reader is created.
Production `MythicScope`, profiles and both real-device capture paths are unchanged.
There is no need for a new production ControllerInputSource abstraction.

The engine still constructs its existing Input object, but every supported virtual
mapping submission goes through the recording backend; this harness exposes no
mouse/hover commands and no injectable destination option. Android UI interaction
with the developer screen is ordinary app input, not mapper-generated gameplay
injection. Trace actions are logical Android DOWN/MOVE/UP/CANCEL constants and
logical pointer IDs, not proof of platform MotionEvent delivery.

The only fixtures are disposable in-memory copies of `HeroTrainingProfiles.draft`
and a BASIC grid. Bennett uses radius 180 / repeat 400 ms in the disposable copy.
No personal profile, favorite or calibration is read or changed by replay. Fixture
calibration means **test geometry**, never a verified hero HUD. Analog raw ranges
are the explicit simulated model, 0…255 with stick center 128; they are not observed
hardware capabilities. Left ABS_X/Y intentionally remain native/pass-through,
matching production. No movement touch is fabricated. Trigger ABS_Z/RZ are recorded
but do not duplicate digital trigger actions. L4/R4 are not invented identities.

## Ownership and lifecycle

The UI refuses activation while this app has a started real mapping service. It
uses the existing EditorSessionGuard while active; an Activity exit releases it.
Virtual runtime generations use the existing RuntimeRequestGate. Disconnect,
profile change, diagnostics and lifecycle transitions dispose the old monitor and
InputService before any replacement. Current raw stick values are used on restart
so Kimmy's existing neutral guard, not a new aiming engine, handles held reconnect.

The scenario diagnostics flag blocks virtual frames and disposes the runtime. This
is an isolated recording-mode gate; it does not pretend an unrooted AVD acquired a
libsu Binder lease. Existing root-ownership tests are retained and physical/root
integration remains a separate gate. Logical lock/unlock scenarios test the adapter
and production hero cleanup, not the full Android lock screen. Actual TouchPointer
unlock behavior is covered by inherited lifecycle tests and the recorded S10e
acceptance; active-shot lock issue #20 remains deferred and game effects unverified.

## Scenario schema 1

```json
{
  "schema": 1,
  "id": "example",
  "source": "synthetic",
  "steps": [
    {"at_ms": 0, "action": "axis", "code": "ABS_RX", "value": 255,
     "expect": {"active_pointers": 1}},
    {"at_ms": 100, "action": "axis", "code": "ABS_RX", "value": 128,
     "expect": {"active_pointers": 0}}
  ]
}
```

Times are monotonic nominal offsets; on AVD each delta is a minimum gap after the
previous step completes, avoiding compressed catch-up events on a loaded emulator.
Host tests advance the production Handler scheduler by the exact delta. Assertions
run on the lifecycle looper. Failures are caught there and rethrown to the runner
for cleanup/reporting, rather than crashing Android's main thread.

Actions: `button` (canonical controller code/down), `axis` (ABS_X/Y/RX/RY/Z/RZ),
`profile` (BASIC/BENNETT/KIMMY), `disconnect`, `connect` (optional right_x/right_y
held initial state), `syn_dropped`, `reset`, `release_all`, `screen_off`, `screen_on`
(unlocked boolean), `foreground` (game boolean), `diagnostics_on/off`, `checkpoint`.
Inputs are synthetic logical packets, clearly labelled even when their spelling
resembles getevent output. No raw Linux code is claimed observed.

Expect fields include snapshot values such as active_pointers/paused/connected,
min_touches, min_downs, min_max_pointers, min_blocked, last_x_min/max and
`touches_since_checkpoint`. The Bennett case verifies no late callbacks after LT UP
using a checkpoint. Each case closes in finally and requires zero final contacts
and no touch-trace overflow. Limits: 48 KB transport, 2,000 steps, 60 seconds,
2,000 input-history entries, 4,096 touch entries. Overflow counters are explicit.

JSON reports include nominal steps, actual observed timestamps, source, positions,
pointer ownership and cleanup. JUnit XML and original instrumentation output are
written alongside them. A per-request token prevents reuse of stale reports.
Reports remain generated under app/build; commit only reviewed sanitized evidence.
Host tests replay the same JSON files with a virtual clock. Reproduction uses
`python3 tools/virtual_g8.py run tools/scenarios/<case>.json`.

Optional future work: sanitized observed-frame import, backend-rejection scenarios,
system-visible uinput feasibility, root-enabled AVD touch delivery, richer UI
navigation assertions. These are not silently claimed by the recording passes.

The emulator CLI may navigate ordinary AVD UI and acknowledge the specifically
observed SystemUI boot ANR using Wait. Those host-side ADB UI actions are separate
from mapper touch submissions, which remain a non-injecting recording stream.
`smoke` also invokes the existing root-free JNI suite and requires an actual x86_64
ABI report. The inherited debug report's hardcoded ARM64 label was corrected to
read Android's supported ABI and process bitness; no Rust hot-path change was made.

All suite readers require exactly one fresh result in the current instrumentation
stdout. JNI/smoke cannot reuse a saved PASS after a process failure; replay also
checks its per-request token. Intent-based activation while a real mapping service
is started reports the refusal in the UI, preserving the running app rather than
crashing it. These boundaries have dedicated regressions.
