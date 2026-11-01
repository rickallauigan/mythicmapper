# Baseline protocol and evidence

Baseline source: `603b11dd9f919fd447dee25a2640dad03932af97` (unchanged XtMapper).
Host native controller/mouse tests PASS; Python generator 12 and tools 4 tests PASS.
Android test/build/dependency inventory execution recorded in VALIDATION.md when complete.
No S10e access or APK installation performed. No physical latency/CPU/JNI claim available.

Before enabling Rust, capture original baseline on the same rooted device/HUD/controller:
- t0: text line read in RemoteService (software receipt, not physical USB timestamp).
- t1: looper dispatch; queue wait = t1-t0.
- t2: mapping completion; include all synchronous touch submissions and label this inclusive.
- For each touch: before/after Input.injectInputEvent; async submission duration is not delivery.
- Android input tracing/observation only where permitted; use a separate trace id for fan-out.
- Allocation/CPU with mapping steady state and diagnostics disabled/enabled separately.
- JNI empty-call and representative preallocated-batch overhead on device; desktop results
  only establish harness operation and cannot select a faster Android implementation.
- Measure controller capability cache hit/miss, discovery/reconnect and first analog/button
  separately. Include 250 ms polling phase and looper backlog. Count SYN_DROPPED/EOF/errors.

Use monotonic nanoseconds, bounded preallocated histograms/rings; snapshot/sort outside event
processing. Record clock source, APK SHA, commit, ABI, thermal state, OS, root, controller mode,
profile fixture hash, event count, warmup, trace settings. Report p50/p95/p99 and sample counts,
not just averages. Replays cover idle/hat reversal/skill overlap/LT both orders/reconnect/switch.
Never suppress outliers silently. Compare equivalent runs and set budgets from measured
baseline confidence; no fabricated millisecond budget. Physical controller-to-display latency
requires external observation and must be reported separately from software processing.
