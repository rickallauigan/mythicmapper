# Foundation validation

Executed locally against unchanged upstream 603b11d on 2026-11-02.

- Android `:app:testDebugUnitTest :app:assembleDebug :app:dependencyInventory`: PASS.
- JUnit aggregate: {'tests': 238, 'failures': 0, 'errors': 0, 'skipped': 0}.
- Native controller capability and mouse-reader suites: PASS.
- Python generator: 12 tests PASS; quality tools: 4 tests PASS; profile schema check PASS.
- APK SHA-256: `bbd253f42533741c40c6e94e1ba545af3f9d7bb5813b87386379291c52b04307`.
- Resolved runtime POM license declarations: dependency-licenses.json; unresolved values remain explicit.
- Existing NDK JNI AttachCurrentThread signature and deprecated API/build warnings remain.
- No device tests, gameplay verification, device performance baseline or JNI timings performed.

Build log retained locally at /tmp/mythicmapper-baseline-gradle.log. Host-only results.
