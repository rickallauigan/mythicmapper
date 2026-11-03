# Home modernization — issue18

Stack: #12 → #13 → #14 → #22 → #23 (issue16) → this feature.
Two focused feature branches; no merge, rebase, force push or release.

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
