# AVD development

The daily target is an owned AOSP Android 16/API 36 x86_64 AVD, independent of the
S10e and G8. Simulation does not establish physical controller, LineageOS or MLBB
compatibility. Issue #21; stacked on draft #14 → #13 → #12 → dev.

## Prerequisites and one-time setup

Linux x86_64, usable `/dev/kvm`, Python 3, Android SDK command-line tools, emulator,
platform-tools, the existing project's JDK 21/Gradle wrapper, NDK 28.0.13004108 and
pinned Rust toolchain/Android targets are required. Set `ANDROID_HOME` (defaults to
`~/Android/Sdk`). Existing build instructions and `rust-toolchain.toml` apply.
`doctor` prints KVM, tools, image, memory/storage, emulator and NDK information.
No global SDK/emulator upgrade, root policy or host service change is needed.

```sh
python3 tools/virtual_g8.py doctor
python3 tools/virtual_g8.py setup --install-sdk
python3 tools/virtual_g8.py build
python3 tools/virtual_g8.py boot
python3 tools/virtual_g8.py recover
python3 tools/virtual_g8.py install
python3 tools/virtual_g8.py smoke
```

Accept SDK licenses through the supported SDK tooling before unattended setup.
The installed tools may deprecate `sdkmanager`/`avdmanager` in favor of Android CLI;
those compatibility commands were executed successfully. Missing SDK platform,
NDK and build-tools packages can be installed with the SDK Manager. The script
never installs distribution packages, changes KVM permissions or accepts a new
license invisibly. An unavailable image is an explicit setup error; review the pin
before substituting another API/image. Do not silently change an existing AVD.

`tools/virtual-g8-avd.json` pins API 36 AOSP `default;x86_64` image revision 2,
4096 MB RAM, four cores, density 420 and a 2280×1080 display/logical mapping plane.
The host executed emulator 37.1.11.0 with KVM. The AVD boots without Google Play
services. Emulator binaries can differ on CI; actual versions are reported, not
claimed identical. Review image revision updates deliberately.

## Daily commands

```sh
python3 tools/virtual_g8.py boot                 # headless; reuses owned running AVD
python3 tools/virtual_g8.py boot --gui           # GUI on next boot
python3 tools/virtual_g8.py boot --gpu swiftshader # explicit software graphics fallback
python3 tools/virtual_g8.py ui
python3 tools/virtual_g8.py run tools/scenarios/kimmy-skill-resume.json
python3 tools/virtual_g8.py run tools/scenarios/beatrix-bennett.json
python3 tools/virtual_g8.py collect
python3 tools/virtual_g8.py recover              # only an observed owned-AVD SystemUI startup ANR
python3 tools/virtual_g8.py stop
```

All emulator commands select `emulator-5580` explicitly and verify both emulator
identity and owned AVD name. Boot refuses an occupied unowned serial; stop additionally
verifies the recorded process command line. It never kills arbitrary emulator
processes, uses bare `adb shell`, wipes data or uninstalls applications. It does
not connect to or command the S10e. Other tools should also select their target.

The workflow owns only `app/build/virtual-g8/`: AVD/config, process marker, emulator
log, JSON/JUnit reports, screenshots and logcat. Stop it before `gradlew clean`.
To remove its data, stop it, inspect that directory and explicitly delete only the
owned generated directory. No physical app rollback is needed. To roll back code,
switch to the parent feature branch; do not rewrite published history.

A 240-second boot deadline and per-suite timeouts make failures bounded. Inspect
`emulator.log`, `*.instrumentation.txt`, JSON errors and `logcat.txt`. Re-run the
same scenario file; reports include source hash and a fresh request token to reject
stale device results. No phone input or login is needed.

## Boundaries

The recording sink needs neither `adb root`, `su` nor libsu. AOSP `adb root`
availability would not prove app-level RootService equivalence; optional privileged
injection is not part of these passes. Uinput/system-visible virtual hardware is
not implemented. The production G8 allowlist and native probe are unchanged.

The logical 2280×1080 plane is not a Samsung screen/touch model. Host Robolectric
uses exact virtual time; AVD playback preserves inter-step minimum gaps and records
observed monotonic timestamps. It cannot guarantee exact wall-clock timing under
host load and does not measure physical controller-to-display latency.

One bounded API/ABI CI job runs the same local commands. The existing host workflow
is preserved. CI's KVM permission adjustment applies only to its disposable runner;
the local script does not change host permissions. Failures are retained as artifacts.

Official references: [emulator command line](https://developer.android.com/studio/run/emulator-commandline),
[AVD configuration](https://developer.android.com/studio/run/managing-avds).

On this host, later cold boots sometimes showed a SystemUI startup ANR (`failed to
complete startup`). `recover` records it and presses **Wait** only after verifying
owned emulator identity, the exact SystemUI ANR window/title and Android Wait button.
It refuses other application ANRs, never presses Close app and changes no settings.
Run the full failed suite again after recovery; this is not a clean-platform pass.
CI performs this explicit bounded pre-test check and retains recovery evidence when
used. Repeated ANRs remain a host/platform limitation, not a mapper success.
The doctor uses headless `-no-window -version` so a headless runner need not load
unused GUI Qt/audio dependencies. Missing runtime libraries are reported on stderr.

For later physical validation, first request a separate S10e/G8 session, verify its
current serial and stop any injecting mapper. Existing `tools/validate_s10e.py`
requires `--serial` and a matching device model; choose its suites deliberately.
The virtual CLI intentionally refuses physical targets. Never copy disposable
simulation geometry into calibrated personal profiles as if it were verified.
