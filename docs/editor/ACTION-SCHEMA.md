# Universal action schema v2

The editor exports strict JSON through Android's document picker. No scripts,
macros, arbitrary event codes or game-state access are accepted. The exact schema
is implemented by `universal/UniversalMapping.kt`; unknown/missing fields, duplicate
JSON keys, future versions and invalid geometry fail closed.

Identity includes a stable personal UUID, name, optional hero, origin, creation and
update timestamps, game `com.mobile.legends`, controller `ORIGINAL_G8`, schema 2,
explicit landscape geometry, calibration and enabled flags. A clone/import always
gets a new UUID and starts disabled with calibration unconfirmed.

Coordinates are canonical pixels in the saved landscape plane (default 2280×1080),
not guessed HUD positions. Blank targets have null X and Y. The runtime requires a
matching landscape aspect (within 1%) and scales measured anchors/radii to the
actual display. A geometry or binding edit revokes calibration and enablement.

## Supported actions

| Action | Contact policy |
|---|---|
| TAP | One 40 ms bounded contact; early hardware UP does not truncate it; cancellation releases immediately. No repeat. |
| HOLD | First owner DOWN starts; final owner UP releases. Also represents press/release. |
| REPEAT | Explicit held-repeat, 60–60000 ms interval; stops on final UP or cancellation. Optional anchor acquisition 0–250 ms, less than half interval. |
| RESET | Releases owned contacts and requires neutral rearm; no coordinates. |

HOLD/REPEAT can use right-stick directional aim, adjustable radius, dead zone,
sensitivity, Y inversion and priority. Higher-priority exclusive aimed contacts
suspend lower-priority aim, resuming still-held owners on release. Equal-priority
contacts remain independently owned. A button action can explicitly cancel other
contacts first (for example Recall), requiring neutral before stick shooting resumes.

Right Stick can modify a held/repeated button target, or activate its own continuous
HOLD outside dead zone. The latter has no repeat timer. Left-stick movement remains
native pass-through. Rear L4/R4 retain their observed L3/R3 aliases.

At most eight distinct touch targets are allowed. Multiple observed button aliases
can share one action: first DOWN acquires, additional owners join, final UP releases.
One input cannot belong to two actions. Duplicate target anchors are rejected; use
aliases on one action instead. Nonfinite/out-of-bounds anchors, invalid timing,
unsupported inputs and unmeasured enabled mappings cannot activate.

The existing profile string-set stores an exclusive `UNIVERSAL_MAPPING_V2` base64
JSON envelope and validated APPLICATION/SCREENSIZE/ENABLED projection. Mixed legacy
bindings are rejected. v1 structured and historical native profiles are unchanged;
there is no automatic migration or destructive conversion. Library UUID ownership,
expected name and expected mapping content are checked under the existing save lock.
SharedPreferences commit failure uses the existing rollback helper. Library rename
failure attempts restoration of the prior mapping. Two-file writes cannot promise
power-loss atomicity; UUID identity remains authoritative and library name wins on read.
