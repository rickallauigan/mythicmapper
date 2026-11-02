# Beatrix and Kimmy training MVP

Stack: dev -> draft PR #12 (`test/s10e-hardware-validation`) -> draft PR #13
(`feature/g8-controller-tester`, validated 1ee6e51) -> this feature branch
`feature/beatrix-kimmy-gameplay`. Draft PR: https://github.com/rickallauigan/mythicmapper/pull/14.
Implementation commit: 0ab8303; device harness commit: 8b7b30e.
No parent PR is merged by this work. GPLv3 and
inherited notices remain. No new dependency or copied game/vendor artwork.

## Implemented control contract

| Physical input | Action |
|---|---|
| X | Recall at the historical measured S10e Recall anchor |
| A | Regen at the historical measured S10e Regen anchor |
| B or L3 | One equipped Battle Spell touch; first owner down/last owner up |
| RB | Skill 1 with right-stick aim while held |
| RT | Skill 2 with right-stick aim while held |
| LB | Ultimate with right-stick aim while held |
| Left stick | Existing native movement; no new synthetic movement joystick |
| Beatrix LT | Attack held/released or explicitly configured aimed pulses while physically held |
| Beatrix right stick | Aims the held Basic Attack or latest held directional skill/spell |
| Kimmy right stick | Outside dead zone: shooting; center: release; LT is unused |

B/L3 never allocate two spell pointers. Releasing one while the other remains held
does not release or retrigger the spell. Rear L4 aliases L3 on the observed firmware;
no independent rear identity is invented. Indistinguishable rear/stick-click codes
cannot provide independent ownership beyond the controller's reported codes.

Kimmy releases shooting before a held directional skill/spell starts. The latest
held skill owns aim; shooting resumes only after all such owners release and the
stick remains displaced. Recall latches shooting off until neutral is observed.
After startup/reconnection a displaced stick must return through neutral before
Kimmy shooting arms. Disconnect, SYN_DROPPED, pause and profile reload stop the
old producers and release pointers. No game memory, targeting decisions or match
navigation is used.

## Beatrix weapons and native-game limits

Weapon selection is manual: Nibiru, Bennett, Wesker or Renner. It records the user's
calibration context; the mapper does not inspect or infer in-game weapon state.
Each training profile stores an explicit touch policy:

- Native hold/release: one aimed contact for the duration of LT, leaving native
  weapon fire/charge/reload/release semantics to the game.
- Optional aimed pulses: the existing RepeatKeyHandler supplies cancelable phases,
  while the existing ControllerAimHandler supplies coordinates/pointer ownership.
  Pulses require physical LT held, pause for skills/spell, and cancel on release,
  lifecycle teardown, failure or device loss. Cadence is bounded 60..60000 ms.

150 ms is historical **Dyrroth** LT tuning, not a verified Beatrix weapon cadence.
The existing scheduler's press duration is retained; no performance budget or
weapon timing is invented. A selected weapon does not silently switch policies
when RB changes the in-game weapon. Stop and configure/choose a matching profile
when changing a weapon requiring another gesture. Correct native behavior for each
weapon remains a physical training gate, especially release-fired aimed attacks.
Never claim Nibiru/Wesker honor a directional drag until the installed game does.

## Calibration and installation

Two immutable generated templates are offered through Home's catalog. Cloning
creates a new UUID personal draft. The templates ship **disabled and uncalibrated**;
adding ENABLED alone cannot bypass the calibration gate. JSON/YAML and Binder
parcels retain the hero policy. Generic profiles retain existing behavior.

The reused 2280 x 1080 anchors come from the physically documented Dyrroth /
Revitalize HUD in [the original S10e fixture](../../../profiles/mlbb/samsung-s10e/README.md).
Recall (1172.29,970.84), Regen (1341.5,955.02), attack (1981.08,946.58), spell
(1477.88,982.97), skills (1650.44,965.04), (1775.13,753.31), (1986.09,648.63)
are references, **not verified Beatrix/Kimmy coordinates**. Proposed 80 px radii
come from old stick-skill tuning and require hero-specific calibration. Full display
origin is zero; do not guess a cutout offset. Existing visual editor / target capture
remain available. Coordinate edits revoke readiness and disable the hero profile;
training setup explicitly reconfirms the edited geometry and touch policy.

Hero training setup holds an exclusive root diagnostic lease, pauses mapping and
hides overlays. It injects no touches and never launches the game. Saving requires
that lease and an explicit coordinate/training confirmation. The user can opt into
recommended favorite 1 (Beatrix) or 2 (Kimmy) only if empty; occupied slots remain
unchanged. Existing Home Assign provides a separate explicit replacement flow.
There is no automatic replacement of a personal mapping or favorite.

Installed game observed through package metadata: MLBB 2.2.16.12322
(version code 211812322). Historical hero mechanics are not assumed to match this
build; Kimmy's current directional Basic Attack must be observed in training.

## Implementation boundary

REUSE: native G8 discovery/capability caching/getevent, native movement, absolute
stick normalization/dead zone/vector fitting, held aiming, repeat scheduler,
InputService's pointer ledger and backend failure barrier, foreground/runtime gates,
root injection, UUID storage/five favorites, editor/calibration and diagnostic token.

EXTEND: ControllerHeroHandler coordinates the existing handlers on their same
runtime looper; ControllerAimHandler has optional explicit focus for hero priority.
Ordinary profiles keep latest-held semantics. A validated HERO_GAMEPLAY policy is
part of KeymapProfile's Binder parcel and structured action schema. Scoped activation
requires confirmed, enabled hero data. Blocked structured hero definitions remain
editable while activation stays blocked. Rust remains reference-only; no JNI call
was added to the live event path. No native/backend rewrite or new evdev reader.

## Validation matrix

| ID | Component | Expected | Observed | Status / evidence |
|---|---|---|---|---|
| HERO-HOST-01 | Concurrent touch lifecycle | Simultaneous attack/skills/utility and shared B/L3 have balanced pointers | Real Java mapping + MotionEvent replay through fake injection sink | PASS: 294 Android tests, zero failures/errors/skips; host-validation.json |
| HERO-HOST-02 | Kimmy priority | Release shooting before skill; resume after final skill; neutral/disconnect safety | Replay includes multiple skills, recall, held disconnect/reconnect, SYN_DROPPED | Focused tests PASS |
| HERO-HOST-03 | Beatrix pulses/reload | Axis traffic does not starve pulses; release/profile reload cancels pending work | Existing pulse scheduler and actual InputService.reloadKeymap exercised | Focused tests PASS |
| HERO-HOST-04 | Persistence/gates | Preserve policy/coordinates, reject unsafe policy, block unconfirmed data, preserve occupied favorites | Parcel + JSON/YAML + store/editor tests | Focused tests PASS |
| HERO-DEVICE-01 | Root/Binder guard | Both heroes rejected uncalibrated; neither starts through tester ownership | Both uncalibrated policies rejected across Binder; diagnostic start/resume blocked, zero injection submissions; orientation retained and root exited on normal tester exit | PASS: device-heroes.json |
| HERO-DEVICE-02 | Template preparation | Only new disabled drafts; preserve every original mapping/library record and all favorites | Two disabled drafts created, original library retained and favorites unchanged; no activation requested | PASS: device-heroes_prepare.json |
| HERO-DEVICE-03 | Guarded root replay | Correct event ordering, reload/pulse cleanup, zero final contacts | 43 real injected events observed only on blue test surface; maximum 7 pointers, zero sequence/injection errors and zero final pointers | PASS: device-heroes_touch.json |
| HERO-DEVICE-04 | JNI | Packaged ARM64 reference math matches Java | 61,206 parity vectors, maximum absolute error 0; missing-library handling passed | PASS: device-jni.json |
| HERO-DEVICE-05 | Setup UI | Safe configuration screen renders and acquires lease | Beatrix setup visibly rendered on S10e; exclusive calibration status confirmed in view hierarchy; no profile enabled | PASS: private UI capture (not committed) |
| HERO-TRAIN-01 | Beatrix | LT/aim/weapon-specific native behavior correct | Requires manual Hero Training | BLOCKED on physical observations |
| HERO-TRAIN-02 | Kimmy | Stick-driven directional shots and skill-return correct on installed version | Requires manual Hero Training | BLOCKED on physical observations |

Final local gates passed: 294 Android tests (0 failures/errors/skips), debug lint,
dependency inventory, debug assembly and release manifest processing. Four tooling
tests and the tracked-source secret guard passed (not a complete security audit).
Rust 5 tests, native 3 suites, host JNI 242,406 vectors and actual compiled Kotlin
JNI checks passed. APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA256 is in
`device-environment.json`. New source GPLv3 headers and inherited notices retained.

Host tests and dedicated-surface/Binder checks do not prove gameplay compatibility.
Controller Tester rendering performance remains a separate open workstream.

The guarded replay submission sample (43 events) measured p50 0.555 ms, p95
1.187 ms, p99 1.649 ms in a debug build. This small synthetic sample is not a
controller workload, gameplay benchmark or controller-to-display latency. No Rust
production migration or performance claim follows from it. Tester historical
checklist entries in device-heroes.json are retained prior observations, not new
physical hero input validation.

The APK was installed with `adb install -r`; no application data was cleared.
The pre-feature APK, preferences and validation data were backed up privately.
Preparation found zero original mapping-store entries and retained all existing
library metadata and favorites. Original XtMapper data was not opened or modified.
All native fixture hashes remain unchanged (12/12). GPLv3 headers were added to
new sources; inherited attributions remain and no dependencies/assets were added.

## Manual Hero Training checklist

1. Stop mapping / open Controller Tester. Confirm legacy XtMapper variants stay
   stopped. Manually enter **Hero Training**, never Ranked/Classic/multiplayer.
2. Start with Beatrix and report the equipped weapon. Compare every HUD anchor and
   attack drag radius with the actual landscape screen; use visual calibration or
   Hero training setup. Confirm the unverified training policy explicitly. Assign
   favorite 1 only through an opt-in action; do not replace an occupied favorite.
3. Explicitly Use the calibrated copy. Test X Recall, A Regen and B/L3 independently,
   then B+L3 together and release in both orders. Check for unintended native-button
   duplicates as well as visible touches/actions.
4. With Beatrix: right stick alone must not attack. LT down/hold/right-stick aim/up;
   native left-stick movement + attack; each skill/ultimate while attacking; all
   releases. Repeat per equipped weapon with the correct manually selected touch
   policy. Record whether a shot fires on down, repeated hold, or release. Do not
   assume the same firing/charge cadence across weapons.
5. Stop mapping before changing hero. Prepare Kimmy in Hero Training, calibrate its
   actual attack joystick and skill HUD, opt into favorite 2 only if desired.
6. Kimmy: center means no firing; displacement starts directional shots without LT;
   movement + shooting; held RB/RT/LB/spell temporarily yields shooting and resumes
   after final release. Test centered and displaced skill release. Recall must not
   resume firing until the stick returns to neutral and is displaced again.
7. Release everything; inspect root trace/diagnostics for zero active pointers.
   Switch favorites while controls are held, leave/return to game, open Controller
   Tester, and physically disconnect/reconnect only when ready. All synthetic
   contacts must clear; no firing resumes from stale held state.
8. Record actual observations per action/weapon. If input sticks or a backend error
   appears, stop mapping immediately and recover before another attempt.

## Rollback

Stop mapping or open Controller Tester first (release barrier). Select the previous
personal profile explicitly. Disable/delete only the newly created training UUIDs
if desired; keep all pre-existing mappings/favorites. Recommended favorites were
never changed unless the user opted in. A private preparation journal identifies
original vs newly created IDs; never delete by a guessed name.

The pre-feature installed debug APK is saved privately before deployment. If an
APK rollback is needed, stop mapping and the root reader, then install that same
signed APK with `adb install -r`; never uninstall or clear data. Disable new hero
profiles and choose a previous profile before downgrading because older builds do
not implement HERO_GAMEPLAY. No original XtMapper data restoration is needed: it
was not modified. Do not restore an entire old preferences file over newer personal
work; recover individual known test entries only.
