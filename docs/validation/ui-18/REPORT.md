# Home modernization — issue18

Stack: #12 → #13 → #14 → #22 → #23 (issue16) → this feature.
Two focused feature branches; no PR merge, rebase, force push or release.
Normal parent-branch integrations carry the editor-backdrop fix into Home without rewriting history.

## Design and working behavior

MythicTheme shares the actual Tester Ink/Surface/Violet/Line/Muted/Green tokens;
Tester colors remain numerically identical. Home uses static rounded bordered
cards and original monograms instead of copyrighted hero/vendor artwork. The
reference board is a design reference only and is not bundled into the APK.

Header reports physical G8 detection from the unchanged allowlist/device listener,
selected UUID/name and actual service state. Root authorization is not inferred;
its check remains in the existing startup path. Overlay permission has its existing
settings route. Debug Virtual G8 action is explicitly SIMULATED and unavailable
in release; opening it does not imply a physical device or activate simulation.

Create Mapping opens a starter picker and the existing explicit personal-clone
workflow; it does not pretend to implement issue15. Personal/source profiles remain
separate. Five horizontally scrollable favorite cards retain stable UUIDs and
Use/Assign/Clear/Move/editor actions. Occupied replacement and deletion require
confirmation. Search and All/Personal/Starters filters use actual local data.
Rename/duplicate/calibration/setup/restore retain existing persistence/validation.
Issue17 folders are plainly marked planned, not an inert synchronization button.

Touch targets use Material minimums; no fixed screen pixel geometry. Main content,
favorite strip, filters and compact action rows scroll independently as needed.
The brand is single-line with ellipsis at large fonts; semantic text retains its
full name. At narrow widths adjacent favorite cards are intentionally partially
visible as horizontal-scroll affordance. Root-free preview uses the same dark
window theme, fixing a pale status-bar strip observed in screenshot inspection.

## Lifecycle and performance boundaries

The inherited750ms service/selection refresh now runs only while STARTED. Library
metadata refreshes on resume or explicit mutations, rather than parsing every tick.
Per-profile calibration inspection runs on Dispatchers.IO and is cancelled/replaced
when library state changes. No input event is routed through Compose, no gameplay
or JNI handler changed, and no new telemetry, rendering loop or runtime dependency.

The repeatable exploratory debug AVD workload uses12 alternating250ms Home swipes
in landscape with an empty regular library, same API36/x86_64/SwiftShader AVD.
Initial baseline:277 frames,74 janky (26.71%), p50/95/99 32/73/117ms, PSS111922KB.
Initial redesign:218 frames,92 janky (42.20%), p50/95/99 42/101/150ms, PSS115813KB.
This initial sample regressed; it is preserved rather than marketed as a speedup.
Counters show more slow UI/draw/deadline events; both GPU histograms include the
emulator's4950ms bucket. Host load/JIT, changed scroll extents and debug/software
rendering limit causal inference. CPU dumps are sampled, not isolated utilization.
No release-like, physical-display or guaranteed-FPS claim is made. Final repeat
results are recorded with the report; profiling on accelerated/release-like targets
remains a follow-up if debug jank persists.

## Actual automated acceptance

`python3 tools/virtual_g8.py ux home` uses real accessibility actions, targeted
small swipes and isolated `ux_home_*` preferences. It verifies physical/simulated
labels; all five slots; replacement confirmation; clear; search; rename; duplicate
UUID; delete cancel/confirm; actual editor/Tester/Virtual navigation; final lease
cleanup; portrait/landscape. A disposable UUID mapping is mirrored only for the
editor navigation check and removed afterward. Regular application profiles and
favorites are never rewritten. Root-free test toast is suppressed only under the
explicit isolated test intent. Backend permissions remain unchanged.

Initial runner failures exposed stale active/IME windows and partially visible
fields after page scrolling. The runner now restricts controls to app windows,
clears accessibility cache on supported APIs, waits for settled events and uses
smaller real swipes. Assertions were retained. Lint caught an API34 cache method
initially guarded at33; the guard was corrected, not suppressed.

Actual screenshots: home-overview.png, home-favorites.png, home-library.png,
home-landscape.png. Host tests also cover disconnected actions, explicit cloning,
search, replacement/delete confirmation and320dp/1.6× font reachability. Existing
seven replay/root/native/fixture gates stay separate from UI observations.

S10e/MLBB physical acceptance, hardware overlay ergonomics and issue20 gameplay
remain PENDING PHYSICAL/deferred. No physical S10e commands were issued. GPLv3,
XtMapper attribution, Bennett180/400, Kimmy/unlock restoration and fixtures remain
unchanged. This report does not certify release or gameplay compatibility.

Final executed local results:327 Android tests,0 failures/errors/skips;12 tools tests; lint, dependency inventory, debug APK, release manifest and release Kotlin/Java compilation PASS. Rust5/native3/hostJNI242406/Kotlin bridge/profile generators12 and12/12 protected hashes PASS. Packaged AVD JNI61206/maxerror0, all seven scenarios and both UX suites PASS; each scenario final0 pointers. Release manifest has no simulator or UX debug entry.

Final repeat:222 frames,81 janky(36.49%), p50/95/99 36/101/150ms; PSS112471KB. This remains worse than baseline; no performance acceptance or speedup claim. APK SHA256 970dd08af2ddbe43bfb6a670056de080657771b30b6c4f9fed78e5ee1d9bca6b. Hosted final-head checks are linked in the draft PR; they are authoritative over historical runs.

Final review follow-up: the overlay Editor route now uses the common collapse helper, preventing a leftover rounded panel backdrop behind the M after returning. A regression and actual AVD overlay-editor tap verify transparent collapse and eventual Activity/lease teardown. Instrumentation waits up to5s for Android destruction rather than assuming300ms. No lease is forcibly released to make the assertion pass. Final integrated suite has328 Android tests; hosted final-head results are recorded in both drafts.

## Physical S10e follow-up — 2026-11-04

See [physical acceptance report](../s10e/ui-acceptance-2026-11-04/REPORT.md)
for real-device measurements and screenshots. This supersedes the earlier
emulator-only physical-pending statement. The actual G8 status, five slots,
search, personal editor/calibration navigation, rotation and per-Activity1.5×
large text passed without modifying personal data. An IME covering the searched
card initially broke the test; dismissing the IME corrected the test navigation.

The read-only selected-profile presentation now follows the stored preferred
profile while the runtime is correctly paused outside the game. It does not
activate mappings. Physical debug scroll samples do not demonstrate a speedup;
no rendering optimization is claimed. Final host suite:330 tests, no failures,
errors or skips. Issue20 remains deferred; gameplay was not exercised.

## Consolidation: hosted navigation regression

Run41754552456 attempt2 failed on the immediate Virtual G8 warning-label assertion.
The retained screenshot actually displays that exact warning. Logcat records
VirtualG8Activity launched/displayed during a rotation transition; the label check
ran before the destination accessibility tree settled. No application crash is
shown before instrumentation completion (its process teardown follows the failure).
The original artifact did not retain ux-failure.txt, so a contemporaneous hierarchy
cannot be reconstructed. The CLI now preserves that file on any future UX failure.

Navigation now installs an Activity monitor before clicking, awaits the exact
visible simulated-mode label with a10-second deadline, verifies the destination
is alive, then requires Back to destroy it and restore the Home action. The same
route runs in portrait and landscape, preserving the original assertion and adding
lifecycle/teardown coverage. This addresses synchronization rather than hiding an
emulator failure or retrying the complete test until it passes. Only the owned AVD
is used; no physical S10e commands are part of consolidation.

The added landscape repeat initially failed because the action was below the fold;
its actual screenshot showed normal Home content and the first action row at the
viewport edge. The regression now scrolls the real Home surface until the Virtual
G8 action is visible before clicking. Destination/lifecycle assertions remain strict.

A later hosted run41759552690 failed before application tests because the existing
single SystemUI-startup-ANR Wait tap was checked after only1second. Its retained
post-failure screenshot shows the dialog already gone. Recovery now waits for
actual window disappearance for at most10seconds after that same single tap;
no extra taps, app-ANR recovery, policy changes or skipped suites are introduced.
Three tooling regressions verify delayed disappearance, deadline failure and
refusal to hide a new application ANR. The complete tooling suite now has15 tests.

Run41760183986 reached Home's new landscape navigation assertion. Its screenshot
shows rotation restored the focused search field into view, placing the viewport
below the actions rather than above them. Forward-only searching therefore moved
away from the destination. Navigation now searches backward and forward, with
bounded steps, before the same strict Activity/label/Back assertions. Failure
screenshots and app accessibility nodes are captured before Home teardown; the
previous catch ran afterward and only saw the launcher. This is test navigation
state restoration, not evidence of a broken Virtual G8 Activity.

Pre-teardown capture also reproduced a search-navigation failure: renaming the
only filtered profile can shrink the list and clamp the viewport to its top,
while the old search helper assumed the search field was always above it. The
shared bounded Home reveal helper now searches in both directions for tags or
text; search and Virtual navigation reuse it. CRUD, persisted UUID, exact label,
orientation and teardown assertions are unchanged.

The same immediate-label race reproduced on the Controller Tester route; its
pre-teardown screenshot contains the correct destination title. Both destinations
now share the Activity/visible-label/Back lifecycle helper. The landscape repeat
explicitly visits Search before reopening Virtual G8, making below-actions viewport
restoration deterministic. The complete owned-AVD Home suite passes locally with
all CRUD, five-slot, UUID, navigation, orientation and cleanup assertions retained.

Run41840154922 completed the mapping and bubble suites but exceeded the original
120-second whole-Home subprocess budget after reaching Virtual G8. The expanded
suite now includes a second orientation/navigation cycle. Home reveal first uses
ACTION_SHOW_ON_SCREEN when available and stops a search direction at the reported
scroll boundary, avoiding repeated no-op swipes. The whole-Home budget is180seconds;
bubble stays120, destination assertions stay10, and every assertion/case remains.
This is a bounded allowance for additional regression work, not a timeout bypass
for a failed assertion. The workflow's25-minute job bound remains unchanged.
