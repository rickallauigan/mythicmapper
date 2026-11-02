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
