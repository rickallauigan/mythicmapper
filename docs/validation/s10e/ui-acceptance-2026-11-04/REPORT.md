# Physical S10e UI acceptance — 2026-11-04

SM-G970F, rooted LineageOS23.2 / Android16, arm64, original USB GameSir G8.
Commands used the explicitly verified network ADB target. Raw backups, serials,
network addresses, personal screenshots and logs remain outside the repository.
No security, battery, font-scale or performance settings changed.

| Test | Observed result | Status | Evidence |
|---|---|---|---|
| Bubble tap/collapse/drag | User confirmed circular movable bubble | PASS | Physical user observation |
| Unlock restoration | Initially absent; UI-only restoration fix; user confirmed return at moved position | PASS after fix | ui-16 report,15f82e6 |
| Rotation/restart anchor | Initially rendered clamped coordinate; restore-before-clamp fix passed repeat | PASS after fix | geometry-fixed-result.json,fcb6150 |
| Overlay safe lifecycle | Editor guard, transparent return, restart, stop cleanup | PASS | geometry-fixed-result.json |
| Gameplay submissions on safe surface | Delta0; mapping remained paused | PASS | geometry-fixed-result.json |
| Home/G8/five favorites | Actual connection and all five slots observed | PASS | home-physical-result.json |
| Search/editor/calibrate | Read-only navigation; editor finished without save | PASS | home-physical-result.json |
| Rotation/large text | Portrait/landscape; per-Activity1.5× configuration, no global change | PASS | home-physical-result.json |
| Kimmy active-firing lock | Separate issue20, deliberately untouched | NOT TESTED | Deferred |
| MLBB touch pass-through/ergonomics | Safe test surface cannot certify gameplay | NOT TESTED | Physical training follow-up |

## Data preservation

The installed APK and complete app data were backed up privately before upgrades.
Same package/signing identity; install-r only, no uninstall/clear. Three personal
profiles, five favorite slots and calibration match the original canonical digests:

- Profiles:50fd07c12e1a9b1e5056b20e20d10b0bab197b13ee05617934d2f61f554d0c69
- Library/favorites:a8ee08cf163b7af3bc0263d5bfc76d54808d6fffc109d2c50e87f4a0e15e57e4

Working Bennett180px/400ms and Kimmy configuration remain unchanged. XtMapper
was not uninstalled or its data modified. No competing mapper was running.

## Physical Home scroll samples

Three trials per APK: four warm-up swipes, then twelve alternating250ms swipes,
same phone, portrait, data and gesture geometry. Frame metrics are software render
measurements, not controller-to-display latency. Baseline installed source is
unknown; identify it by APK SHA256
9b2f27389779cea66f23c36a1a07b7482857ad9483ab91b638787ab43564f98c.
The newer initial measurement used bad4a9f debug, before correctness fixes.

| Debug APK | Janky frames per trial | p99 ms per trial | PSS KB per trial |
|---|---|---|---|
| Installed baseline |8/420;6/420;1/421|31;21;17|170539;175125;175683|
| UI head bad4a9f |13/428;13/460;11/416|27;48;20|168212;182721;171762|

No speedup is demonstrated. The second newer trial had a longer command window;
network-driven gestures and debug overhead limit comparison. Prior SwiftShader
26.7%/36.5% jank values are separate emulator measurements. No hot-path or rendering
optimization was introduced without bottleneck evidence. See raw sanitized metrics
for p50/p95 and workload durations.

Host final checks:330 Android tests, zero failures/errors/skips; debug build/lint
passed. Other final verification is recorded as executed in the follow-up below.

Screenshots: home-overview-redacted.png (cropped above personal names),
home-large-text-redacted.png (status bar masked), bubble-landscape-cropped.png
(cropped above the transient root toast). These are real device captures;
no UI was generated or reconstructed. Private full originals remain retained.
