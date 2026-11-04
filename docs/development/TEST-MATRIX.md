# Development test matrix

| Tier | Executed target | What it establishes | What it cannot establish |
|---|---|---|---|
| A | Robolectric/JVM + Rust/native/JNI/Python | Real mapping handlers with virtual time; ledger, schemas, fixtures, error paths | Android framework behavior, USB or game effects |
| B | AOSP API 36 x86_64 AVD | Debug APK runs, Compose screens/orientation, same mapping handlers with recording sink | G8 firmware/USB, Samsung/Lineage root injection, MLBB |
| C | Existing S10e/G8 suites | Only separately recorded physical/root observations | Untested controls or heroes |

| Case | Host | AVD | Physical status |
|---|---|---|---|
| Basic button identities/grid coordinates, two held buttons, raw axes | Same JSON replay | Same JSON replay | PENDING PHYSICAL for new harness; prior tester evidence preserved |
| Bennett radius180/cadence400, aiming, release cancellation | Same real hero scheduler with virtual time | Observed recording trace; minimum time gaps | Prior personal profile acceptance unchanged; no new gameplay certification |
| Kimmy continuous aim, RT handoff, shared B/L3 | Same JSON replay | Observed recording trace | Prior Training evidence unchanged |
| Logical lock/unlock and foreground safety | Adapter gate + inherited TouchPointer tests | Adapter scenario | Active-shot lock issue #20 still OPEN/deferred |
| Held disconnect/reconnect, initial neutral gating, SYN_DROPPED | Same JSON replay | Same JSON replay | Real USB reconnection remains separate |
| Profile switch while held | Same ownership ledger/cleanup | Same JSON replay | Real game effects PENDING PHYSICAL |
| Diagnostics exclusivity | Virtual request-generation gate + inherited root protocol tests | Recording-mode gate; no root Binder claim | Root lease suite requires rooted target |
| Home, offline catalog/five slots, tester, editor, virtual UI | Existing UI/store tests + new source tests | Activity/render smoke and orientation | Root editor save/overlay gameplay not certified by AVD |
| Missing release, invalid input, bounded history | Explicit watchdog/negative/overflow tests | Watchdog also enabled in UI | No physical event-loss claim |
| Kernel/system-visible virtual gamepad/uinput | NOT IMPLEMENTED | NOT TESTED | Optional feasibility work only |
| Injected Android MotionEvent delivery/libsu | Existing host backend tests | NOT TESTED by recording sink | Existing S10e validation; separate gate |

No physical test is skipped and renamed PASS because the emulator succeeds. The
standard host workflow still runs all existing tests; the new AVD workflow adds one
API/ABI job. Optional physical tests remain explicitly PENDING PHYSICAL. There is
no new automated game launch, match entry, anti-cheat work or release publication.

Commands and exact executed results: [AVD setup](AVD-SETUP.md),
[virtual source](VIRTUAL-G8.md), [validation report](../validation/virtual-g8/REPORT.md).
