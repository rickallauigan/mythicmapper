# Third-party notices

This fork retains upstream Xtr126/XtMapper attribution and GPLv3 licensing.

Structured YAML profiles use **SnakeYAML 2.5**, Apache License 2.0.
Source and license: https://github.com/snakeyaml/snakeyaml
The dependency is pinned in app/build.gradle. SafeConstructor and explicit
parser limits are used; no Java-object YAML construction is allowed.

This is not a complete dependency-license audit. Engineering inventory work
tracks all resolved dependencies and unresolved license records separately.

## MythicMapper foundation

MythicMapper retains XtMapper's GPLv3 license, Xtr126 copyright, contributor history
and upstream README attribution. New MythicMapper code is GPL-3.0-or-later.

The inherited `com/genymobile/scrcpy/{Point,Pointer,PointersState}.java` classes
and parts of the MotionEvent pipeline derive from scrcpy by Genymobile and
contributors (Apache License 2.0): https://github.com/Genymobile/scrcpy.
Exact copied revision and missing historical per-file notices remain under review;
see docs/architecture/LICENSING.md. New code does not claim their authorship.

Resolved libsu core/service 5.3.0 POMs declare Apache License 2.0. Shizuku API
13.1.5 components declare MIT; this does not describe the separate Shizuku app.
The resolved runtime coordinate/license inventory is committed at
`docs/architecture/dependency-licenses.json`; unresolved entries require review.

The MythicMapper launcher/notification vectors and code-drawn controller schematic
are original project artwork under GPL-3.0-or-later. They are not GameSir product
photos, MLBB artwork, or vendor endorsements. Inherited raster assets remain in
history/source and require provenance review before a production release.
The Rust reference crate has no third-party crate dependencies.

## S10e validation licensing follow-up

The embedded Maven POM in `com.google.guava:listenablefuture:1.0` identifies
`guava-parent:26.0-android`. The published parent POM declares Apache License 2.0.
Artifact/POM hashes and the exact parent source are recorded in
`docs/validation/s10e/license-evidence.json`; the cache-only inventory may still
show this entry unresolved when no loose POM is cached.

`Pointer` and `PointersState` match scrcpy v1.24 when whitespace is ignored.
`Point` is adapted, including floating-point coordinates. This identifies a
verified reference, not proof that v1.24 was the exact original copied revision.
Comparison hashes/source links: `docs/validation/s10e/scrcpy-comparison.json`.
The scrcpy v1.24 license is bundled under `assets/licenses/`.

The statically linked Rust 1.99.0 standard library includes additional components.
Its complete distributed `COPYRIGHT-library.html` is bundled under
`assets/licenses/rust-1.99.0-COPYRIGHT-library.html`. The crate has no external
Cargo dependencies; that does not mean the binary contains no third-party runtime.
GPLv3 and these project notices are also bundled as development APK assets.

Inherited raster asset provenance and a final artifact-specific notice review
remain release gates. These additions do not assert complete legal clearance.
