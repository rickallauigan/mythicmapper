# Circular floating selector — issue16

Based on draft22 (ea94416), preserving the #14/#13/#12 stack. Original Canvas M,
44dp circular face, transparent48dp window when collapsed, accessible Button label.
Expanded dark card preserves five slots, selected UUID, editor and Collapse.
No vendor/game assets copied from the reference board. Native M artwork is original.

Tap/drag use Android touch slop. Drag never invokes selection; position persists
only after gesture completion. Window bounds include conservative system/cutout
insets; layout and display changes reclamp. The 750ms permission/display check is
inherited; there is no new controller polling or animation loop. WindowManager
updates occur on drag/size/clamp changes, not every refresh. Paint is reused.

Production constructor retains the real G8 check and existing TouchPointer switch
callback. The internal context seam allows a debug-only preview with isolated
`ux_preview_*` preferences and a recording selection callback, never mapping.
No gameplay/runtime/hero/fixture files changed. Existing service screen-off/unlock
restoration remains the owner; editor/tester leases hide the overlay immediately.
Permission loss detaches on the next bounded refresh; close/show are idempotent.

AVD interaction command: `python3 tools/virtual_g8.py ux bubble` after boot/install.
It verifies owned emulator identity and grants only this debug APK's overlay appop
on the emulator. Debug instrumentation additionally requires ranchu/goldfish hardware.
Actual touch gestures target the preview overlay; no root or game touch backend.
Checks cover48dp target, expand/collapse, drag-without-selection, favorite callback,
position retained through close/show, duplicate show, editor/tester guard hide,
rotation and detached final view. Host tests cover stale-position bounds, permission
revocation, initial lease visibility and lifecycle. Native pale button backgrounds
found in screenshot inspection were corrected to dark high-contrast surfaces.

Screenshots and exact final results are recorded alongside this report. Screenshots
are from the APK, not reference assets. Physical MLBB/S10e overlay acceptance remains
PENDING PHYSICAL; the phone was not commanded. No FPS improvement is claimed: no
comparable physical rendering benchmark was performed. Process death releases windows
through Android; this is distinct from graceful service teardown and root touch cleanup.

`tools/measure_home.py before|after` is an exploratory debug-only AVD workload:
12 alternating250ms swipes with explicit Home foreground checks. It retains raw
gfxinfo/meminfo/cpuinfo and parsed frame percentiles; it does not infer physical
latency or isolated workload CPU from sampled dumpsys. Compare only like orientation,
data, emulator graphics and host conditions; this is not a release performance gate.

Final local:324 Android tests / zero failures/errors/skips;12 Python tests; lint, debug APK and release manifest PASS. Actual AVD bubble suite PASS, JNI61,206 vectors and all seven virtual scenarios PASS / zero final pointers. A cold-boot SystemUI startup ANR was acknowledged by the existing bounded recovery; no security policy changed.
