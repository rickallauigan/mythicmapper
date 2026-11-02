# Delivery backlog and acceptance

PR 1: source audit, inventory, ADRs, baseline protocol; retain original history and fixtures.
PR 2: host Rust core/replay/property tests and opt-in JNI math bridge; compile Android
ABIs, compare results to original Java and record benchmark limitations. Do not activate
Rust in mapping hot path before device baseline.
PR 3: scope gates, injection status/interface and failure recovery, deterministic movement
(native pass-through)/attack/three skills; preserve LT overlap and pointer cleanup tests.
PR 4: immutable catalog, personal UUID profiles, exactly five reorderable/replaceable/removable
slots, checked atomic writes, v0 migration and corrupt/future schema tests. Upgrade never
replaces personal content. Bundle only historically evidenced templates with honest status.
PR 5: Compose home, live G8 evidence/status, searchable catalog, favorite editing, movable
collapsible in-game selector, active profile, existing visual editor/calibration routes.
Selection uses existing release/reset/publish lifecycle, handles service death/permission loss.
PR 6: diagnostics percentiles/errors/drop/jitter, baseline comparison, APK reproducibility,
licenses/artwork clearance, physical stability acceptance and release documentation.

Host gates: existing native/Python/Robolectric tests, Rust unit/property/replay, JNI build,
Android lint/debug APK, fixture hash comparison and secret checks. Record failures rather
than claiming incomplete gates. Physical gates: rooted SM-G970F LineageOS 23.2, original
G8 identity/mode, MLBB HUD at 2280x1080, both LT/skill press orders, native movement with
attack/three skills, simultaneous physical touch, rapid switches, unplug/replug, screen-off,
crash/backend rejection, overlay permission/rotation and p99 stability. No automated test
may mark a hero verified. No automatic merges or production releases.
