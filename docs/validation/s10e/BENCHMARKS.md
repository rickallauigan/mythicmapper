# S10e development measurements

Single-session debug-build measurements on rooted SM-G970F. These are not physical
controller-to-display latency, release budgets, or gameplay validation.

| Instrumented interval | Samples | p50 ms | p95 ms | p99 ms |
|---|---:|---:|---:|---:|
| Replay Handler queue | 120 | 7.208 | 12.544 | 14.405 |
| Mapping processing including injection | 120 | 2.304 | 4.046 | 4.752 |
| Injection API submission | 566 | 0.565 | 1.454 | 1.947 |

The input is deterministic replay dispatched to the root main Handler in bursts,
not sampled controller packets. Queue time includes this scheduling pattern.
Processing includes synchronous injection. Submission includes the InputManager
API call, not screen observation. The controlled backend performs additional
foreground Binder checks before DOWN/MOVE, so these values cannot be treated as
unmodified production mapping overhead. Twenty deliberately injected SYN_DROPPED
records exercise recovery; they are not physical input loss. The actual read-only
G8 capture observed zero SYN_DROPPED in its short capture.

The surface observed 565 MotionEvents,
maximum three concurrent pointers, zero sequence errors and zero pointers left.
MOVE coalescing means event counts do not by themselves measure loss.
The entire workload used 668 CPU ms over
2.730 seconds; this is process CPU
time including instrumentation, not a sustained-game CPU benchmark. No GC was
observed in the workload counters. See injection.json for idle/normal/rapid
allocation and PSS snapshots; allocations include the harness and focus checks.

Twenty cached physical capability scans averaged
1.528 ms. This is a probe
cost, not USB polling latency. Existing capability caching is retained.
Unused scroll workers changed from 0→3 in the baseline suite to 0→0 after lazy
construction. No performance settings were changed.

## Foreground detection

The old five-second polling path measured 5011 ms away and 5008 ms back.
The final TaskStackListener run measured 64.4 ms
away and 387.0 ms back. These single transitions
include Activity launch and callback delivery, not only listener execution.
The listener is a platform hidden API used by the root service, tested on this ROM;
unsupported platforms retain the existing polling fallback.

## Reference math/JNI

On-device Java reference math: p50/p95/p99
385/1769/2115 ns.
Packed Rust JNI reference math:
192/231/231 ns.
Each has 20,000 samples after warmup and includes timer/sink overhead. Java creates
a vector array; the Rust JNI interface returns packed values, so this is not a
controlled comparison of identical allocation contracts. The 61,206 parity vectors
matched exactly. No production hot-path migration follows from this microbenchmark.

Required next measurements: sustained physical G8 idle/normal/rapid/overlap runs,
actual evdev-to-mapping correlation, true event jitter/drop statistics over long
runs, multiple repeat samples, screen observation where instrumentable, and manual
training-mode validation. Current single-run quantiles are descriptive only.
