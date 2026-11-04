# GameSir G8 Controller Tester

Implemented on `feature/g8-controller-tester`, stacked on the unmerged S10e
validation branch / PR #12. Development feature, not MLBB gameplay certification.
The user-provided design PNG remains an untracked local reference; it is not a
licensed product photograph or a bundled app asset. The implementation uses
original Compose vector geometry, with an editable 2000×1200 SVG companion in
`docs/artwork/g8-controller-original.svg` (GPL-3.0-or-later).

## Usage and interpretation

Home → Controller Tester. Grant app-specific root if needed. The Controller tab
shows current controls, two XY plots and trigger capabilities; scroll for the
metrics and trigger cards. Diagnostics contains the checklist, bounded event log
and current hardware information. Descriptor display is hidden until explicitly
revealed locally. Normal Android Back exits.

- Values come from the selected actual Android InputDevice. The existing strict
  supported-G8 identity policy selects its gamepad interface; other controllers
  are labeled Unsupported. Device IDs/descriptors are not stable profile keys. After exclusive acquisition,
  a read-only root sysfs query reports USB only when exactly one current USB device
  matches the observed numeric VID/PID. It runs once per connection, never per frame;
  ambiguity or failure leaves transport Unknown. Mode is not inferred from name.
- Raw means the value exposed by Android MotionEvent/KeyEvent, not evdev ADC counts.
  Android key action is 0=DOWN, 1=UP; its key code is not a Linux EV_KEY code.
  Linux codes and SYN_DROPPED cannot be observed in this Android stream and are
  explicitly unavailable. No raw Linux code is fabricated.
- Stick normalization uses each actual MotionRange's midpoint and extent, with Y
  positive down. Z/RZ is selected for the right stick when both exist, otherwise
  RX/RY. LT/RT use LTRIGGER/RTRIGGER or BRAKE/GAS, without reusing stick axes.
  Missing analog pressure remains unavailable. Ranges/flat values are shown.
- Normalized values are before dead-zone processing; the configurable dead-zone
  circle is a diagnostic boundary, not a mapping/calibration write. No value is
  reported as measured before receiving a sample. Measure center offset samples
  the last observed position only; release both sticks before invoking it.
- Default direction threshold: 0.65 on each of ±X/±Y. Full range: at least 0.90
  travel across all four extents and eight outer angular sectors. Trigger travel:
  observed rest ≤0.10 and travel ≥0.90. Thresholds can be changed locally in the
  tester; doing so resets its checklist. These are diagnostic defaults, not
  invented device calibration or performance budgets.
- Buttons require observed press and release. The checklist denominator includes
  actual supported keys/hat directions, complete stick pairs and available trigger
  axes. Analog-only triggers can complete observed pressure/release tests without
  inventing a digital key. Reset Test changes only test progress.
- L4/R4 are programmable copies of other controls. The user observed their current
  rear buttons lighting L3/R3; Android codes 106/107 were observed in the follow-up
  capture. Identical reports cannot reveal which physical actuator was pressed.
  No independent rear-button state or checklist pass is fabricated. Firmware
  programming was not changed. [Official rear-button instructions](https://doc.xiaoji.com/en/g8/detail/1224.html).

## Safety and data flow

`TesterActivity` acquires the existing editor UI lease (overlay hidden / activation
suppressed), then a Binder-token lease in `RemoteService`. Root acknowledges only
after invalidating pending runtime requests and stopping the owned InputService
through existing teardown. Starts, resumes and input-frame handling are blocked
while the token owns diagnostics. Another token cannot acquire or release it.
Binder client death stops the root process/readers; normal exit ends the lease,
stops the now-unused root service and unbinds this Activity's connection. Mapping
is not automatically resumed; restart from Home. Root failure disables capture.

The tester reuses the existing Android event path and existing root ownership/
reader. It never constructs an injection backend, opens evdev, starts another reader,
or calls JNI from rendering. Window.Callback consumes selected controller keys
before Compose can turn A/D-pad into UI navigation. Other Android Back navigation
is preserved. InputDeviceListener handles hotplug, clearing held/raw state on loss.
Orientation/size/keyboard/navigation configuration changes retain the tester session.

Packets enter a bounded 256-entry queue. A serial HandlerThread decodes/reduces them;
overflow increments a counter and clears uncertain held state. Rendering snapshots
are coalesced at a 16 ms scheduling interval, not a guaranteed frame rate. A
500-event ring retains diagnostics; only the newest 60 rows enter render snapshots.
Pause freezes visible log rows while capture and checklist updates continue. Export
contains only fixed control labels, Android codes/values and session-relative times;
no descriptor, VID/PID, device name, network address or profile contents.

## Debug evidence capture

The debug APK accepts an explicit `capture_diagnostics=true` extra on its own
non-exported Activity. Root ADB can launch this for persistent physical capture;
there is no short actuation deadline. It writes sanitized state/root counters at
1 Hz off the UI thread. This opt-in instrumentation adds disk, PSS and Binder
sampling overhead; production use has no such polling. PixelCopy saves actual app
window pixels, excluding other apps' status-bar overlays. It does not generate
mockups or simulated controller events. No controller input is synthesized.

```sh
adb -s "$S10E_ADB_SERIAL" shell su -c \
  'am start -n io.github.rickallauigan.mythicmapper.debug/xtr.keymapper.tester.TesterActivity --ez capture_diagnostics true'
adb -s "$S10E_ADB_SERIAL" exec-out run-as io.github.rickallauigan.mythicmapper.debug \
  cat files/validation/tester-live.json
```

The debug-only `tester_render` suite labels its synthetic UI replay and never feeds
controller events into the reducer or injection backend. Its measurements do not
prove physical input compatibility or a guaranteed frame rate.

The debug-only `tester` instrumentation suite separately checks the exclusive root
lease and attempts production start/resume while it is held. A PASS for that suite
is a safety/lifecycle result, not proof of a physical exercise. Human observations
and recorded event data are reported separately in REPORT.md.
