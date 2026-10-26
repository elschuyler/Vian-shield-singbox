# Receipts Log - RECEIPTS_001.md

## Entry: 2026-10-25T15:00:20-07:00
* **Request:** Mandatory Initial Audit and Phase 1 kickoff for Vianshield (sing-box engine integration, build pipeline, operational log catcher).
* **Exact files touched:** `/receipts/RECEIPTS_001.md`
* **What was actually done:**
  1. Conducted initial audit of repository files, Gradle build files, versions, and manifest.
  2. Executed baseline compilation test (`compile_applet`) to verify pre-existing build health.
  3. Ran silent security scan protocol on workspace root and subdirectories.
  4. Identified security findings regarding committed keystore files (`/debug.keystore`, `/debug.keystore.base64`) and hardcoded signing configuration passwords in `/app/build.gradle.kts`.
  5. Halted feature implementation pursuant to Security Scan Protocol and Credential Immunity Rule to report findings and await user instruction.
* **How it was verified:** Local build only (`compile_applet` passed in 79s); directory inspection via `list_dir` and shell listing.
* **Any deviation from what was requested, and why:** Halted before writing new code/modifying build files due to active Credential Immunity Rule and Security Finding trigger in workspace scan.
* **Any known issue or follow-up needed:** User confirmation required on handling/remediating committed keystores and signingConfigs in accordance with the security protocol before proceeding with VPN architecture implementation.

## Entry: 2026-10-25T15:49:30-07:00
* **Request:** Resolve signing findings safely and resume Vianshield Phase 1 implementation (sing-box engine integration, GitHub Actions workflow, operational log catcher).
* **Exact files touched:**
  - `/.gitignore`
  - `/metadata.json`
  - `/settings.gradle.kts`
  - `/app/build.gradle.kts`
  - `/app/src/main/res/values/strings.xml`
  - `/app/src/main/res/drawable/ic_launcher_foreground.xml`
  - `/app/src/main/res/drawable/ic_launcher_background.xml`
  - `/app/src/main/res/mipmap-*/ic_launcher.png`, `ic_launcher_round.png`
  - `/app/src/main/AndroidManifest.xml`
  - `/app/src/main/java/com/example/MainActivity.kt`
  - `/app/src/main/java/com/example/ui/DiagnosticScreen.kt`
  - `/app/src/main/java/com/example/ui/DiagnosticViewModel.kt`
  - `/app/src/main/java/com/example/ui/theme/Color.kt`
  - `/app/src/main/java/com/example/ui/theme/Theme.kt`
  - `/app/src/main/java/com/example/vpn/VpnState.kt`
  - `/app/src/main/java/com/example/vpn/VianshieldVpnService.kt`
  - `/app/src/main/java/com/example/vpn/VpnLifecycleController.kt`
  - `/app/src/main/java/com/example/engine/SingBoxEngine.kt`
  - `/app/src/main/java/com/example/engine/SingBoxAdapter.kt`
  - `/app/src/main/java/com/example/config/WireGuardConfigParser.kt`
  - `/app/src/main/java/com/example/logging/OperationalLogCatcher.kt`
  - `/.github/workflows/build-vianshield.yml`
  - `/app/src/test/java/com/example/VianshieldPhase1Test.kt`
  - `/app/src/test/java/com/example/ExampleRobolectricTest.kt`
  - `/BLUEPRINT.md`
* **What was actually done:**
  1. Inspected `debug.keystore` certificate fingerprint via `keytool` (`SHA1: E0:A4:71:08:...`, `SHA256: 79:BE:28:7C:...`, standard Android debug cert).
  2. Remediated signing configuration in `app/build.gradle.kts` to query environment variables (`DEBUG_STORE_PASSWORD`, `DEBUG_KEY_ALIAS`, `DEBUG_KEY_PASSWORD`, `KEYSTORE_PATH`) with standard development defaults, preserving local compatibility while eliminating hardcoded repository secrets.
  3. Hardened `.gitignore` to ignore all `.keystore`, `.jks`, `.p12`, and `debug.keystore.base64` files.
  4. Updated application metadata and project name to `Vianshield` and `applicationId` to `com.aistudio.vianshield.vxnpqk`.
  5. Implemented custom adaptive launcher icon and mipmap raster assets across all screen densities.
  6. Implemented `WireGuardConfigParser` validating standard WireGuard configuration `.conf` profiles and converting to sing-box v1.11.0 JSON format.
  7. Built `SingBoxEngine` interface and `SingBoxAdapter` for engine isolation, reflection-based linkage to `libbox.aar`, and status tracking.
  8. Created `VianshieldVpnService` implementing Android `VpnService` lifecycle, foreground notification, and TUN interface creation.
  9. Built `OperationalLogCatcher` with bounded circular memory (500 entries), rotating 256KB disk log, master on/off switch, secret redactor, and export API.
  10. Built Material 3 Compose `DiagnosticScreen` with VPN toggle, real-time status pill, active profile summary, and live operational log viewer.
  11. Created `.github/workflows/build-vianshield.yml` for reproducible remote building of `libbox.aar` from `SagerNet/sing-box` at pinned version `v1.11.0` using Go 1.23, Android NDK r26d, and gomobile.
  12. Created unit and Robolectric tests in `VianshieldPhase1Test.kt` and updated `ExampleRobolectricTest.kt`.
* **How it was verified:**
  - Local build only: Gradle unit tests (`gradle :app:testDebugUnitTest`) executed and passed all 8 tests.
  - Applet compilation (`compile_applet`) executed and succeeded with 0 errors.
  - Physical on-device execution: pending (requires installation on Android physical hardware).
* **Any deviation from what was requested, and why:** None. Engine integration isolated cleanly, full Phase 1 scope maintained strictly within bounds.
* **Any known issue or follow-up needed:** Physical device validation needed for end-to-end TUN packet flow once CI compiles `libbox.aar` and artifacts are linked.

## Entry: 2026-10-25T22:36:30-07:00
* **Request:** Phase 1.5 focused verification and correction pass on sing-box integration, GitHub Actions workflow, TUN descriptor lifecycle, and truthful state reporting.
* **Exact files touched:**
  - `/.github/workflows/build-vianshield.yml`
  - `/app/src/main/java/com/example/engine/SingBoxEngine.kt`
  - `/app/src/main/java/com/example/engine/SingBoxAdapter.kt`
  - `/app/src/main/java/com/example/vpn/VianshieldVpnService.kt`
  - `/app/src/test/java/com/example/VianshieldPhase1Test.kt`
  - `/BLUEPRINT.md`
  - `/receipts/RECEIPTS_001.md`
* **What was actually done:**
  1. Audited upstream `SagerNet/sing-box` v1.11.0 `experimental/libbox` architecture and corrected critical API & lifecycle mismatches:
     - Discovered that sing-box calls `PlatformInterface.openTun(options)` rather than receiving a pre-opened descriptor.
     - Implemented `openTun` callback in `VianshieldVpnService` using `pfd.detachFd()` to transfer exclusive descriptor ownership to native Go space.
     - Implemented `autoDetectInterfaceControl(fd)` calling `VpnService.protect(fd)` to prevent outbound WireGuard UDP sockets from looping into the TUN interface.
  2. Updated `SingBoxAdapter` to use dynamic proxy (`java.lang.reflect.Proxy`) over `io.nekohasekai.libbox.PlatformInterface` to cleanly bridge calls when `libbox.aar` is loaded.
  3. Enforced strict state truthfulness: `SingBoxAdapter` and `VianshieldVpnService` refuse to claim `Connected` when `libbox.aar` is absent, transitioning cleanly to `VpnState.Error("ERR_NATIVE_AAR_PENDING")` with clear diagnostic logs.
  4. Audited and corrected `.github/workflows/build-vianshield.yml`:
     - Added `submodules: recursive` for `sing-tun` and `sing-dns` dependencies.
     - Replaced manual raw gomobile flags with official upstream commands (`make lib_install`, `go run ./cmd/internal/build_libbox -target android`).
     - Aligned output path `sing-box-source/libbox.aar` with artifact download into `app/libs/`.
  5. Added new unit tests in `VianshieldPhase1Test.kt` verifying truthful failure when `libbox.aar` is missing and updated all test callback implementations.
* **How it was verified:**
  - Local build only: Gradle unit tests (`gradle :app:testDebugUnitTest`) executed and passed all 9 tests (duration 59s).
  - Applet compilation (`compile_applet`) executed and succeeded with 0 errors.
  - GitHub Actions execution: pending export/push to GitHub.
  - Physical on-device execution: pending (requires physical hardware).
* **Any deviation from what was requested, and why:** None. Critical lifecycle corrections implemented without scope creep into Phase 2 features.
* **Any known issue or follow-up needed:** CI workflow must be executed on GitHub to compile the actual `libbox.aar` binary and place it in `app/libs/`.
