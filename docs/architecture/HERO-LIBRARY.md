# Offline library and favorites

HeroLibrary uses exactly five ordered nullable UUID references; assigning an already-favorite
profile moves its reference rather than duplicating it. Swaps include empty slots. Rename
changes the name only; deletion clears references first. Profile count is not restricted to
five; metadata has a practical 4 MiB bound. Personal mappings use their UUID as the existing
KeymapProfiles storage key, so legacy visual/calibration editor saves retain identity.

Version 1 metadata is one checked SharedPreferences JSON transaction under the existing
profile save lock. Optimistic writes reject stale snapshots; failed commits restore prior
memory/disk state or explicitly report rollback failure. Corrupt/future metadata is rejected
without overwriting. Development version 0 migrates compact favorite arrays by padding to
five. There is no silent adoption of old XtMapper app data or identifier rename.

Clone writes the mapping first and then metadata; deletion clears references before mapping
cleanup. Interrupted operations can leave unreferenced mapping data; automatic destructive
orphan recovery is deliberately absent. Back up both profiles and mythic_hero_library data;
legacy profile ZIP does not include favorite metadata. Cross-file power-loss atomicity and
failed disk/rollback device behavior still need validation.

The single bundled template is immutable app asset `mythic/s10e-g8-starter.txt`, with a stable
catalog ID/revision. It is an explicitly UNVERIFIED adaptation of the historically tested
tuned v0.3: LT repeat removed for direct-touch MVP scope. The original fixture is unchanged.
It is not a newly verified hero mapping. Users name a personal clone and calibrate its HUD.
No guessed hero-specific settings or copyrighted GameSir/MLBB images are bundled.

Five targeted Robolectric tests PASS: identity/slots/reorder/rename, persistence/clone
immutability/delete, development migration/future/corrupt rejection, stale writes, and
scope rejection of other-game/repeating mappings. Full integration/build gates follow
in the UI/runtime PR. Physical gameplay acceptance remains pending.
