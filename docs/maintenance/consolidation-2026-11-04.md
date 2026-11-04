# Repository consolidation — 2026-11-04

Accepted runtime/source commit: `a00386f0194809d62982f9024de033bda9f68cef`. Its complete tracked tree matches
the fully host/AVD-tested combined head `81b5b18594858d01a56b5b4db4d0ed2b22f217fc`.
This report/checkpoint is a documentation-only follow-up; no app source changes.

| PR | Result | Normal merge | Validation evidence |
|---|---|---|---|
| [#12](https://github.com/rickallauigan/mythicmapper/pull/12) | Previously merged; ancestry retained | [`53c8bfe2cac0`](https://github.com/rickallauigan/mythicmapper/commit/53c8bfe2cac0d7da7476db940bf0844585b55b2d) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291901/job/126630739339), [AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291995/job/126627091866) |
| [#13](https://github.com/rickallauigan/mythicmapper/pull/13) | Previously merged; ancestry retained | [`00450f263e90`](https://github.com/rickallauigan/mythicmapper/commit/00450f263e90ac72dd3c888dc3af37b38ebbac1d) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291901/job/126630739339), [AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291995/job/126627091866) |
| [#14](https://github.com/rickallauigan/mythicmapper/pull/14) | Previously merged; ancestry retained | [`86a3bd82d515`](https://github.com/rickallauigan/mythicmapper/commit/86a3bd82d515391c64210625cd78d6811620785c) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291901/job/126630739339), [AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291995/job/126627091866) |
| [#22](https://github.com/rickallauigan/mythicmapper/pull/22) | Previously merged; ancestry retained | [`a605ce0fe959`](https://github.com/rickallauigan/mythicmapper/commit/a605ce0fe959d290a0c5cf0f8b5275a0438bc3a8) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291901/job/126630739339), [AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291995/job/126627091866) |
| [#23](https://github.com/rickallauigan/mythicmapper/pull/23) | Previously merged; ancestry retained | [`f546f4121f03`](https://github.com/rickallauigan/mythicmapper/commit/f546f4121f03af3964a14dc12ad8986a565cd51e) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291901/job/126630739339), [AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291995/job/126627091866) |
| [#24](https://github.com/rickallauigan/mythicmapper/pull/24) | Merged after green checks | [`528c5a9939c9`](https://github.com/rickallauigan/mythicmapper/commit/528c5a9939c97b0009db233ceb0c1e0b3569b5ff) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41842142904/job/126616216710), [AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41842142579/job/126616215727) |
| [#1](https://github.com/rickallauigan/mythicmapper/pull/1) | Merged after green checks | [`9f14605c8f9b`](https://github.com/rickallauigan/mythicmapper/commit/9f14605c8f9b1b5d58fe0c4d3328b8abd64c0742) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41843285165/job/126620191442) |
| [#3](https://github.com/rickallauigan/mythicmapper/pull/3) | Merged after green checks | [`b0e45b2eb925`](https://github.com/rickallauigan/mythicmapper/commit/b0e45b2eb9259f8c067b915b88725d97816df4dc) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41843294631/job/126620221053), [AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41843294612/job/126620224436) |
| [#4](https://github.com/rickallauigan/mythicmapper/pull/4) | Merged after green checks | [`a527eaaa94f8`](https://github.com/rickallauigan/mythicmapper/commit/a527eaaa94f893afc51d5501af8a6468861380ab) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41843377833/job/126621105392) |
| [#5](https://github.com/rickallauigan/mythicmapper/pull/5) | Merged after green checks | [`a00386f01948`](https://github.com/rickallauigan/mythicmapper/commit/a00386f0194809d62982f9024de033bda9f68cef) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291901/job/126630739339), [AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41845291995/job/126627091866) |
| [#6](https://github.com/rickallauigan/mythicmapper/pull/6) | Merged after green checks | [`02c402ebbd86`](https://github.com/rickallauigan/mythicmapper/commit/02c402ebbd869b49e9a94100705e2f631bcc8e8c) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41843385730/job/126621177503) |
| [#7](https://github.com/rickallauigan/mythicmapper/pull/7) | Merged after green checks | [`115eab041b58`](https://github.com/rickallauigan/mythicmapper/commit/115eab041b5806aa3520b11273cfa8ad7e831d61) | [Host](https://github.com/rickallauigan/mythicmapper/actions/runs/41843329102/job/126620338893), [AVD](https://github.com/rickallauigan/mythicmapper/actions/runs/41843329056/job/126620338754) |

The first five rows use the final combined integration checks as evidence; their
earlier merges were not repeated. Full hashes, tested heads and immutable job URLs
are recorded in the adjacent JSON. All accepted heads are ancestors of dev and
all listed merge commits retain two parents. No PR remains open or blocked.

## Corrections and dependency review

The failed Home assertion was a destination/accessibility publication race: actual
screenshots showed the correct simulated warning. Tests now wait for the real
Activity and exact visible label, verify Back/ownership teardown, and navigate
both directions after filtering clamps the list or rotation restores focused Search.
Both orientations run, including an explicit Search-first regression. Failure
hierarchies/screenshots are captured before Activity cleanup. No assertions were
disabled. Scroll boundaries avoid no-op gestures; the expanded whole-Home budget
is180seconds while destination deadlines remain10seconds and workflow25minutes.

The existing single owned-AVD SystemUI startup Wait recovery now allows asynchronous
window removal for at most10seconds; persistent or application ANRs still fail.
Three added tooling regressions cover delayed dismissal, deadline and app refusal.

Each dependency PR was reviewed and refreshed normally against consolidated dev.
Checkout conflict resolution retains checkout7/setup-java6/upload-artifact7/Crowdin3
and both Maven updates, with no force push. Only the six original checkout tag
edits differ from its refreshed base. WebKit1.18.0 and SnakeYAML2.7 passed combined
330-test/lint/debug/release compilation, actual AVD smoke/JNI and all seven replays.
Their observed Apache2.0 metadata updates only the corresponding license purls.
Unchanged existing attribution records are retained. Crowdin CLI5.5.0 returned its
version in an offline, uncredentialed container; no cloud synchronization occurred.

Checkout combined-head host attempt1 stopped before tests on a Maven Central HTTP429
for unchanged Robolectric nativeruntime-dist-compat1.0.19. Attempt2 reran the full
job at the same head and passed; no repository/dependency/test bypass was added.

## Verification and scope

330 Android tests,15 tooling tests, Rust/native regressions, host/packaged JNI,
schema/generator/fixture checks, lint/build, seven recording-sink scenarios and both
AVD UX suites passed. Each replay finished with zero synthetic pointers. The full
combined CI head includes every accepted feature and dependency. Simulator validation
does not certify physical G8 or MLBB gameplay.

No physical S10e commands, installation changes, profile/favorite edits or XtMapper
data operations were performed in this consolidation. Bennett/Kimmy behavior,
historical HUD fixtures and mapping/native/Rust code are unchanged. GPLv3 and inherited
copyright/provenance notices are retained. Issue20 remains OPEN/deferred. Issue15
was not started. Crowdin and production release workflows were not dispatched;
GitHub reports no releases. Only expected development APK artifacts are generated.

The two original untracked reference PNGs remain preserved in the working directory.
The final dev APK workflow is checked separately after this documentation commit.
