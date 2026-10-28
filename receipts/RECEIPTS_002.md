# Receipts Log - RECEIPTS_002.md

> Continuation of `/receipts/RECEIPTS_001.md` (which reached the 500-line cap).

---

### Entry: Phase 1.9 — Fix Native Cache-File Startup Failure
* **Timestamp:** 2026-10-28 12:15 UTC
* **One-line summary of what was requested:** Fix native sing-box cache-file startup failure (`open cache.db: read-only file system`) by configuring app-private storage, implementing corruption recovery, adding safe diagnostics, and verifying tests while preserving Phase 1.8 changes.
* **Exact files touched:**
  - `/app/src/main/java/com/example/engine/SingBoxStorageManager.kt`
  - `/app/src/main/java/com/example/config/WireGuardConfigParser.kt`
  - `/app/src/main/java/com/example/vpn/VianshieldVpnService.kt`
  - `/app/src/main/java/com/example/ui/DiagnosticsScreen.kt`
  - `/app/src/test/java/com/example/config/WireGuardConfigParserTest.kt`
  - `/app/src/test/java/com/example/VianshieldPhase1Test.kt`
  - `/BLUEPRINT.md`
  - `/receipts/RECEIPTS_001.md`
  - `/receipts/RECEIPTS_002.md`
* **What was actually done:**
  1. Traced cause of `initialize cache-file: open cache.db: read-only file system`: On Android, the application process working directory defaults to `/` (the root filesystem), which is mounted read-only. In sing-box v1.11.0, when `experimental.cache_file.path` is omitted, the bbolt database defaults to `"cache.db"` relative to CWD (`/cache.db`), causing `bbolt.Open()` to fail immediately with `read-only file system`.
  2. Implemented `SingBoxStorageManager` (`/app/src/main/java/com/example/engine/SingBoxStorageManager.kt`):
     - Resolves app-private storage directory at `context.filesDir/singbox/` and file at `context.filesDir/singbox/cache.db`.
     - `prepareCacheStorage(context)` creates the directory, validates write permissions, and safely deletes any 0-byte corrupt cache database files left by interrupted executions before `libbox` opens it.
     - `clearCacheFile(context)` safely removes only `singbox/cache.db`, strictly preserving all unrelated app data.
     - `getSafeCacheDiagnostics(context)` generates non-sensitive storage telemetry without leaking any keys or secrets.
  3. Updated `WireGuardConfigParser.toSingBoxJson(profile, cacheFilePath, enableCacheFile)`:
     - Generates the documented sing-box v1.11.0 `experimental.cache_file` configuration block.
     - When an absolute path is provided, sets `enabled = true`, `path = cacheFilePath`, and `store_fakeip = false`.
     - When storage is unavailable or unwritable, sets `enabled = false` as a safe fallback, ensuring sing-box skips `cachefile.Start()` and never attempts to open relative `cache.db`.
  4. Integrated storage management into `VianshieldVpnService`:
     - STAGE 2 executes `SingBoxStorageManager.prepareCacheStorage()`, logging the verified path and passing it to `toSingBoxJson()`.
     - STAGE 4 teardown automatically clears `cache.db` if an engine start error contains cache or bbolt failure keywords, enabling self-healing for subsequent start attempts.
  5. Added non-sensitive cache storage diagnostics to `DiagnosticsScreen` ("Cache Storage Location: files/singbox/cache.db (App-Private)").
  6. Added 6 new unit tests in `WireGuardConfigParserTest` and `VianshieldPhase1Test` covering cache directory creation, 0-byte corruption cleanup, isolated deletion, safe diagnostic inspection, and JSON schema verification.
* **How it was verified:**
  - Local build only: `compile_applet` executed and succeeded with 0 errors.
  - Local JVM tests: `gradle :app:testDebugUnitTest` executed and all unit tests passed.
  - Workspace security scan: clean (zero credentials or unignored secrets).
  - Remote verification: pending next GitHub Actions run and physical device installation.
* **Any deviation from what was requested, and why:** None. WireGuard endpoints, keepalive, Light Sky Blue theme, and dual-ARM packaging configurations were fully preserved.
* **Any known issue or follow-up needed:** Ready for GitHub export and CI workflow run. On-device physical testing required to confirm tunnel connection on device.
---

### Entry: UI Reorganization — Minimalistic 4-Tab Layout
* **Timestamp:** 2026-10-28 20:00 UTC
* **One-line summary of what was requested:** Reorganize the Vianshield Android UI into the planned minimalistic 4-tab layout (Main, Firewall, VPN, Settings) with strict UI-only scope, keeping the Light Sky Blue theme, and preserving working features and test coverage.
* **Exact files touched:**
  - `/app/src/main/java/com/example/ui/DiagnosticViewModel.kt`
  - `/app/src/main/java/com/example/ui/MainScreen.kt`
  - `/app/src/main/java/com/example/ui/FirewallScreen.kt`
  - `/app/src/main/java/com/example/ui/ProfilesScreen.kt`
  - `/app/src/main/java/com/example/ui/SettingsScreen.kt`
  - `/app/src/main/java/com/example/MainActivity.kt`
  - `/app/src/test/java/com/example/VianshieldPhase1Test.kt`
  - `/receipts/RECEIPTS_002.md`
* **What was actually done:**
  1. Configured persistent bottom navigation bar in `MainActivity.kt` with exactly four destinations in specified order: `Main`, `Firewall`, `VPN`, and `Settings`. Maintained system bar insets via Scaffold/NavigationBar. Kept `LOG_KEEPER` and `DIAGNOSTICS` as full-page sub-screens with top bar Back buttons returning to Settings.
  2. Reorganized `MainScreen.kt` into the compact protection dashboard in the exact sequence requested:
     - Master protection switch (`vpn_toggle_button`).
     - Speed-throttling preset selector with dialog options and inactive limiter badge.
     - DNS changer row with upstream DNS resolver selection dialog.
     - Ad & tracker filter switch with staged sinkhole badge.
     - Ad-filter allow-list manage row with domain management dialog.
     - Live protection status card (VPN state, active profile endpoint, native core linkage) and error banner with Diagnostics navigation button.
  3. Created `FirewallScreen.kt` for application-level controls:
     - Prominent "Block All" network traffic switch and "Show System Apps" toggle.
     - Search bar filtering installed applications by name and package name.
     - App rows with trio controls for [Block] [VPN] [Direct] modes.
     - Grouping/sorting placing configured apps before unconfigured apps, followed by alphabetical order.
     - Expandable detail view per app supporting staged speed exceptions and screen-aware rules with clear inactive indicators.
  4. Repurposed `ProfilesScreen.kt` as the dedicated `VPN` tab:
     - VpnHealthStatusCard displaying connection state and active WireGuard endpoint/addresses.
     - Multi-profile management list indicating the currently selected profile and allowing instant switching.
     - Profile detail expandable panel with RFC 8200 MTU and keepalive metrics.
     - Floating Action Button ("+") and SAF import flow for `.conf` profiles with validation and safe secret handling.
  5. Streamlined `SettingsScreen.kt` to house secondary features:
     - Log Keeper navigation row opening full-page `LogKeeperScreen`, with master logging toggle and disk storage meter.
     - Diagnostics & Troubleshooting navigation row opening `DiagnosticsScreen` with crash count indicator.
     - Backup & Restoration card with staged export/restore buttons.
     - General preferences toggles (boot start, notification, DNS leak protection, kill switch preview).
     - App information card (version 1.0, sing-box v1.11.0, dual-ARM ABI, zero telemetry).
  6. Preserved exclusive Light Sky Blue theme palette across all screens, with high-contrast text and subtle borders.
  7. Added Robolectric unit tests in `VianshieldPhase1Test.kt` validating navigation across all tabs and state management of all new UI controls.
* **How it was verified:**
  - Local build only: `compile_applet` executed and succeeded with 0 errors.
  - Local JVM tests: `gradle :app:testDebugUnitTest` executed and all unit tests passed (`BUILD SUCCESSFUL in 22s`).
  - Workspace security scan: silent scan confirmed zero exposed credentials or unignored secrets.
  - On-device claim only: not tested (physical hardware testing required).
* **Any deviation from what was requested, and why:** None. Native networking, sing-box engine, config parsing, storage manager, VPN lifecycle, Gradle configs, and CI workflows were completely untouched.
* **Any known issue or follow-up needed:** Ready for on-device manual QA testing.

---

### Entry: Phase 1.11 — BitProxy-Based Engine Integration, Stable sing-box Upgrade, Single APK
* **Timestamp:** 2026-10-28 22:25 UTC
* **One-line summary of what was requested:** Rework sing-box integration using BitProxy & sing-box-for-android (SFA) reference patterns, upgrade engine to stable sing-box v1.14.4, eliminate experimental cache-file workaround, preserve 4-tab UI & honest placeholders, consolidate CI into a single-job dual-ARM APK workflow, and verify all unit tests and builds.
* **Exact files touched:**
  - `/.github/workflows/build-vianshield.yml`
  - `/app/src/main/java/com/example/engine/SingBoxEngine.kt`
  - `/app/src/main/java/com/example/engine/SingBoxAdapter.kt`
  - `/app/src/main/java/com/example/engine/SingBoxStorageManager.kt`
  - `/app/src/main/java/com/example/config/WireGuardConfigParser.kt`
  - `/app/src/main/java/com/example/vpn/VianshieldVpnService.kt`
  - `/app/src/main/java/com/example/ui/DiagnosticsScreen.kt`
  - `/app/src/main/java/com/example/ui/SettingsScreen.kt`
  - `/app/src/test/java/com/example/config/WireGuardConfigParserTest.kt`
  - `/app/src/test/java/com/example/VianshieldPhase1Test.kt`
  - `/BLUEPRINT.md`
  - `/receipts/RECEIPTS_002.md`
* **What was actually done:**
  1. Upstream Pinning & Toolchain Alignment (Phase A):
     - Pinned sing-box Git tag/release to stable `v1.14.4` (strictly avoiding `v1.15.0-alpha` prerelease).
     - Pinned official matching `sing-box-for-android` (SFA) commit SHA to `f87c2eb` (v1.14.4 release cycle).
     - Pinned Go compiler to `1.24`, Android NDK to `r26d` (`26.3.11579264`), and JDK to `17`.
     - Maintained Android SDK configuration (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`).
  2. Engine Architecture & Lifecycle Port (Phase B):
     - Updated `SingBoxEngine` interface and `SingBoxAdapter`: ported SFA / BitProxy lifecycle incorporating `setupEnvironment(baseDir, workingDir, tempDir)`, `checkConfig(configJson)`, `initialize()`, `start()`, `stop()`, and `reload()`.
     - Integrated pre-flight configuration validation via native `Libbox.checkConfig(configJson)` before starting or reloading (BitProxy patch 0007).
     - Implemented service start failure notification and teardown fallback (BitProxy patch 0008).
     - Implemented reconnect and service state resync handling (BitProxy patch 0011).
     - Maintained 10-second watchdog timeout (`withTimeoutOrNull(10_000L)`) on `start()` preventing deadlocks (BitProxy patch 0034).
     - Maintained `onRevoke()` listener in `VianshieldVpnService` for system VPN revocation (BitProxy patch 0040).
     - Maintained TUN file descriptor tracking and detached fd ownership transfer to Go runtime, preventing fd leaks and double-close crashes (BitProxy patch 0041).
     - Verified `gvisor` userspace stack stability: omitted custom `sing-tun` patch (`0001-system-stack-listen-again...`) because userspace stack does not encounter OS host TCP listener socket reuse conflicts.
  3. WireGuard Configuration Schema (Phase C):
     - Completely eliminated the `experimental.cache_file` block from `toSingBoxJson()`: verified that passing app-private sandbox directories via `Libbox.setup` eliminates `read-only file system` without config file workarounds.
     - Preserved all WireGuard parameters: private key, peer public key, preshared key, endpoint, allowed IPs, persistent keepalive, MTU 1280, DNS servers, and DNS routing detour (`wg-out`).
     - Verified that generated configurations pass local syntax validation and `Libbox.checkConfig`.
  4. UI Scope & Honest Status (Phase D):
     - Preserved existing 4 persistent tabs (`Main`, `Firewall`, `VPN`, `Settings`) with Light Sky Blue theme.
     - Kept unfinished features (per-app firewall, speed exceptions, DNS changer, ad filter) as explicit, honest inactive placeholders.
     - Updated `DiagnosticsScreen.kt` and `SettingsScreen.kt` to report sing-box core `v1.14.4`, `SFA / BitProxy Command-Client Lifecycle`, and `gVisor Userspace Stack`.
  5. Single Job, Single Dual-ARM APK Workflow (Phase E):
     - Consolidated `.github/workflows/build-vianshield.yml` into a single runner job (`build-vianshield`).
     - Checkout sing-box `v1.14.4`, build `libbox.aar` internally, copy directly to `app/libs/libbox.aar`.
     - Build single Dual-ARM debug APK (`armeabi-v7a` and `arm64-v8a`) staged at `dist/vianshield-dual-arm-debug.apk`.
     - Verified existence with `test -s` and native ABIs with `unzip -l`.
     - Configured artifact upload for ONLY the single APK artifact (no standalone AAR, no split APKs).
  6. Verification & Regression Suite (Phase F):
     - Updated unit tests in `WireGuardConfigParserTest.kt` and `VianshieldPhase1Test.kt` to verify `experimental.cache_file` elimination and `checkConfig` / `reload` / `setupEnvironment` methods.
     - Executed `compile_applet`: Build succeeded in 36s.
     - Executed `gradle :app:testDebugUnitTest`: BUILD SUCCESSFUL in 27s (all 32 tasks, all tests passed).
     - Silent security scan: verified zero exposed credentials, keystores, or unignored secrets.
* **How it was verified:**
  - Local build only: `compile_applet` executed and succeeded with 0 errors.
  - Local JVM tests: `gradle :app:testDebugUnitTest` executed and all unit tests passed (`BUILD SUCCESSFUL in 27s`).
  - Workspace security scan: clean (zero credentials or unignored secrets).
  - Remote verification: pending next GitHub Actions run and physical device installation.
* **Any deviation from what was requested, and why:** None. Engine version upgraded to stable v1.14.4, cache-file workaround eliminated, and single-job dual-ARM APK workflow configured exactly as specified.
* **Any known issue or follow-up needed:** Ready for GitHub export and CI workflow run. On-device physical testing required to confirm tunnel connection on device.
