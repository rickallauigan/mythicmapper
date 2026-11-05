# Universal editor calibration

1. Home → Create mapping → name and optional hero → blank or unmeasured starter.
2. Select a controller input, choose Tap/Hold/Held repeat/Reset, then place a target
   on the reference canvas or enter measured X/Y. No hero HUD anchors are invented.
3. Optionally select your own landscape HUD image using Android's document picker.
   The app does not silently capture MLBB. Image aspect must match the saved plane;
   large images are bounded and subsampled. No game/vendor images are bundled.
4. Set right-stick aim, radius and dead zone only where needed. Pinch/pan changes
   the viewport, not stored coordinates. A whole marker drag is one undo operation.
5. Save the disabled draft before using the existing capture tool; saved measured
   targets can be reused only when geometry/game match.
6. Confirm measured HUD targets and enable explicitly. Save. Assign a free favorite
   or explicitly confirm replacing an occupied slot. Activate from Home with the
   real allowed G8 and MLBB foreground gates.

Safe preview uses a bounded recording sink and simulated buttons/stick sliders;
STOP releases everything. Foreground Android events from an allowed physical G8
can also drive preview. There is no privileged injector in this editor preview.
Actual MLBB skill semantics and HUD placement still require Hero Training acceptance.

Legacy Beatrix/Kimmy profiles keep their compatibility editor and policies.
Cloning them does not silently convert their gestures into generic actions.
Bennett's personal 180 px/400 ms configuration is not modified. Issue #20 remains deferred.

Export saves schema-2 JSON through Android's document picker. Import validates and
creates a new disabled personal clone. Issue #17's folder synchronization is not
implemented here. Before downgrading, export personal universal profiles; older APKs
cannot interpret the v2 envelope. Never clear data or overwrite the old APK backup.
