# Universal mapping — source audit and implementation boundary

Base: consolidated dev54b18e1 (remote verified2026-11-04). Issue15 remains open;
this feature uses one branch/draft PR and does not deploy to the physical phone.

## Existing pipeline (before changes)

- `devices/InputDeviceDiscovery` + `mythic/MythicScope.supported`: observed physical
  G8 identity/interface gate. Retain unchanged; virtual provenance is debug-only.
- `controller/ControllerDeviceMonitor`: cached native capabilities and existing
  getevent stream; EV_KEY deduplication, hat conversion, normalized RX/RY at SYN,
  disconnect/SYN_DROPPED reset. ABS_X/Y intentionally remain native MLBB movement.
- `ControllerBindings`: observed canonical button names (including rear aliases).
- `ControllerAimHandler`: held contacts, absolute right-stick math, dead zone,
  direction-preserving edge fit and cancelable anchor acquisition. REUSE.
- `ControllerHeroHandler`/`HeroGameplayConfig`: explicit Beatrix weapon/LT pulse and
  Kimmy shooting/skill handoff. Keep compatibility path; no automatic migration.
- `server/InputService`: existing producers feed injectEvent and its ten-pointer
  ledger, submission-failure stop, pause/stop/reload cleanup. REUSE.
- `server/RemoteService` + runtime request gate: privileged backend and lifecycle
  ownership. EditorSessionGuard/diagnostic leases pause and release before editing.
- `editor/EditorActivity`/`EditorUI`/`VisualControllerEditor`: existing overlay target
  measurement, input palette, numeric tuning, saved targets and explicit Save.
- `VisualMappingSession`/`TargetPlane`: validated draft, undo/reset, optimistic save
  and geometry transform. Extend the editor family, not the native reader.
- `structured/ProfileCodec`: bounded strict JSON/YAML and v1 native projection.
  `StructuredProfiles`: single-set atomic commit/rollback and stale-save rejection.
- `mythic/HeroLibrary`: stable UUID storage keys, immutable starters, five references,
  explicit clone/delete; mapping data and library metadata have ordered writes.
- `MythicHome`: current Create action only scrolls to starter library. Extend entry
  into a real blank-or-clone editor while preserving existing library controls.
- Debug `virtual/VirtualG8Session`: real InputService/ControllerDeviceMonitor with
  recording TouchBackend; bounded input/trace history, explicit simulated identity.
  Existing seven hero/lifecycle scenarios remain independent regression gates.

## Incremental design

A version2 universal document is stored in the existing profile string-set as an
explicit validated envelope, with the same game, reference geometry and enabled
projection. It is mutually exclusive with legacy native bindings. Old documents
are never rewritten automatically. Personal UUID ownership is checked on save.

A universal resolver in the existing monitor consumes the same logical buttons
and normalized right-stick events. It delegates directional math/contact updates
to ControllerAimHandler and explicit held-repeat scheduling to RepeatKeyHandler;
all output still goes through InputService's ledger/backend. No second evdev
reader, RootService, injection backend or Rust hot-path migration is introduced.

Offline preview uses a bounded recording sink. Geometry edits revoke calibration;
activation requires explicit enabled/calibrated state and matching landscape aspect.
Left-stick movement remains native. Existing hero adapters and issue20 remain
unchanged/deferred. No physical or all-hero gameplay compatibility is implied.

Implementation/test status is recorded in the validation report; this design
section is not a claim that acceptance tests have executed.
