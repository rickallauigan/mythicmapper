# Attribution and licensing inventory

XtMapper remains GPLv3; original LICENSE and README copyright (C) 2022 Xtr126 are retained.
README permits version 3 or later; inherited files mostly lack SPDX headers, so do not infer
per-file authorship or mechanically relabel third-party files. MythicMapper contributions use
GPL-3.0-or-later. Preserve historical contributors through Git history and source notices.
Distribution must retain notices and provide the complete corresponding source/build scripts
for the exact binary and dependency modifications under applicable terms. Do not relicense
copied GPL code to Apache. License interpretation/complete provenance clearance is pending.

| Component | Source evidence | License / action |
|---|---|---|
| XtMapper app/native/profiles | LICENSE, README, full dev history | GPLv3; retain notices/source |
| scrcpy Point/Pointer/PointersState; Input derived pipeline | README explicit credit, com/genymobile/scrcpy paths | Apache-2.0 upstream; inherited copies lack original headers. Preserve/restore appropriate notices after pinning origin; exact copied revision/copyright still INVESTIGATE. https://github.com/Genymobile/scrcpy/blob/master/LICENSE |
| SnakeYAML 2.5 | app/build.gradle; THIRD_PARTY_NOTICES | Apache-2.0; retain notices |
| AndroidX / Compose / Material / constraint/coordinator / WebKit | app/build.gradle + resolved POM inventory | POM-declared licenses in dependency-licenses.json; typically Apache-2.0, verify every resolved artifact |
| libsu core/service 5.3.0 | app/build.gradle | Apache-2.0 in resolved pinned POMs; preserve license/notice when redistributing |
| Shizuku API/provider 13.1.5 | app/build.gradle | MIT upstream https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE; SDK is distinct from Shizuku app |
| JUnit/Robolectric and transitives | testImplementation + Gradle cache | Test-only; inventory separately before redistribution |
| AGP/Gradle/Kotlin/NDK/JDK | Gradle/settings/wrapper properties | Build tools; runtime components separately resolved. Tool licenses still apply if redistributed |
| Launcher/key/cursor images and documentation screenshots | source-inventory.json | Individual provenance unresolved; not licensed merely by filename. No new vendor/game artwork copied |

Existing :app:dependencyInventory emits runtime coordinates, CycloneDX JSON and POM license
records, not proof of compatibility or complete embedded notices. Empty licenses remain
UNRESOLVED. Verify shaded/native/transitive notices before release; no legal-compliance claim
is made from successful builds. Rust starts without third-party crate dependencies.
