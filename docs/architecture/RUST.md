# Rust reference core and JNI

The active controller mapping remains Java. `RustMappingMath` is explicitly loaded only
by a caller checking `available`; no runtime event invokes it. `engine/src/lib.rs` ports
normalization/radial math, not evdev readers, pointer ownership or injection. Invalid float
inputs return neutral. Versioned C ABI returns two packed float bit patterns in one long;
JNI allocates nothing and retains no Java objects/references. There are no crate dependencies.

ndk-build links the static Rust core and a C JNI shim into `libmythicmapper_core_jni.so` for
all four existing ABIs. Gradle builds archives before configure/build native tasks. Install
Rust via rustup, then `rustup show` at repository root installs pinned toolchain/components
and targets from rust-toolchain.toml. Run `cargo test --manifest-path engine/Cargo.toml`,
`bash tools/run-rust-jni-host.sh`, then normal Gradle Android checks. Cargo builds use the
lockfile and offline mode. Initial rustup/toolchain installation needs network access.
Local cross-toolchain for this work was installed only under `/tmp/mythicmapper-{cargo,rustup}`;
no shell startup file or system Rust installation was changed.

Host harness compiles the ACTUAL ControllerAimHandler.java with simple type stubs, links
the actual C JNI shim/core and compares 242406 valid vectors within 1e-6 plus ABI/invalid
input. Rust properties cover 121203 grid cases. A Java exact-equality expectation at full
scale was corrected to float tolerance: Java-compatible float denominator yields 0.99999994.
This is a test expectation correction, not a new input algorithm.

Sample Linux desktop run: original Java math p50/p95/p99 93/100/116 ns; Rust JNI math
89/95/102 ns, 20000 samples each after warmup. Timer and volatile sink overhead included;
JIT/order/host noise make this unsuitable as an Android performance comparison. No device
input, queueing, touch submission, allocation profiler or physical display latency measured.
Device activation and runtime JNI failure/recovery testing remain pending.

Validation on 2026-11-02: five Rust tests PASS; clippy -D warnings PASS; host JNI parity
PASS; `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` PASS (238 existing tests).
APK ZIP inspected: JNI library present for arm64-v8a, armeabi-v7a, x86 and x86_64.
S10e fixture hashes unchanged. Physical ABI loading and gameplay are still untested.
