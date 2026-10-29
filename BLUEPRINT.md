# Vianshield Architecture Blueprint & Running Ledger

## 1. Project Overview
* **Application Name:** Vianshield
* **Role:** Android firewall and VPN application using sing-box as its core networking engine, engineered for rock-solid stability, zero secret leakage, and high-performance packet routing.
* **Architecture:** Modular Clean Architecture (Jetpack Compose UI -> ViewModel / Lifecycle Controller -> VpnService Layer -> Sing-Box Engine Adapter -> Native libbox core).
* **Reference System:** BitProxy integration patterns for lifecycle management, resource cleanup, and failure recovery.

---

## 2. Development Phases

### Phase 1: Engine Foundation, Build Pipeline & Operational Log Catcher (Active)
- [x] Initial repository audit & baseline build verification (`compile_applet` passed).
- [x] Security scan protocol & credential remediation (debug keystore analysis, gitignore hardening, env-based config).
- [x] Architecture Blueprint (`BLUEPRINT.md`) & Receipts Log (`/receipts/RECEIPTS_001.md`).
- [x] Sing-box engine abstraction adapter (`SingBoxEngine`, `SingBoxAdapter`).
- [x] Genuine Android `VpnService` lifecycle implementation (`VianshieldVpnService`, `VpnLifecycleController`, `VpnState`).
- [x] WireGuard configuration parser & sing-box JSON transformer (`WireGuardConfigParser`).
- [x] Operational Log Catcher with Master On/Off switch, bounded circular storage, secret redaction, and export API (`OperationalLogCatcher`).
- [x] Diagnostic UI with real-time state, control toggle, and live log viewer.
- [x] Pinned GitHub Actions build workflow (`.github/workflows/build-vianshield.yml`) targeting sing-box v1.11.0.
- [x] Verification via JVM/Robolectric unit tests and `compile_applet`.

### Phase 2: Native libbox.aar Linkage & Profile Storage (Planned)
- Import and validation of multi-profile WireGuard `.conf` files.
- Persistent local profile store via Room.
- Direct TUN file descriptor passing to gomobile native bindings.
- Connection stats, active latency, and bytes transferred.

### Phase 3: Firewall Policy Engine & App Inventory (Deferred)
- Installed application discovery and package manager mapping.
- Per-app traffic rules (Block / VPN / Direct).
- Default-deny ruleset configuration.
- Screen state aware policy triggers.

### Phase 4: DNS Filtering & Ad Blocking (Deferred)
- AdGuard DNS filter format support.
- Encrypted DNS upstream configuration (DoH / DoT).
- Per-app ad-filter exemptions.

### Phase 5: Sidebar Integration (Deferred)
- Refactoring core VPN module into an Android AAR library for seamless embedding into Sidebar.
- Multi-client routing and unified tunnel lifecycle.

---

## 3. Component Architecture & Responsibility Map

```
+-------------------------------------------------------------+
|                      Diagnostic UI                          |
|  - Main Control Toggle (testTag: "vpn_toggle_button")       |
|  - Real-time State Badge (Stopped/Starting/Connected/Error) |
|  - Live Operational Log Viewer with Severity Filter         |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                 VpnLifecycleController                      |
|  - Permission verification (VpnService.prepare)             |
|  - Service start/stop intent coordination                   |
|  - Bounded retry and failure handling                       |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                  VianshieldVpnService                       |
|  - Subclass of android.net.VpnService                       |
|  - Foreground service notification & lifecycle              |
|  - TUN interface creation (Builder.establish)               |
|  - ParcelFileDescriptor lifecycle & cleanup                 |
+-------------------------------------------------------------+
         |                                           |
         v                                           v
+--------------------------+        +--------------------------+
|      SingBoxAdapter      |        |  OperationalLogCatcher   |
|  - Engine abstraction    |        |  - Master On/Off switch  |
|  - JSON configuration    |        |  - Bounded circular buf  |
|  - Lifecycle invocation  |        |  - Strict secret redact  |
|  - Native libbox binding |        |  - Rotating disk cache   |
+--------------------------+        +--------------------------+
```

---

## 4. Running Ledger of Changes

| Date & Time | Component | Description | Status |
|---|---|---|---|
| 2026-10-25 15:00 | Baseline | Verified baseline build (AGP 9.1.1, Kotlin 2.2.10, SDK 36). | Complete |
| 2026-10-25 15:05 | Security | Ran security audit; flagged committed debug keystore & hardcoded signing passwords. | Complete |
| 2026-10-25 15:45 | Blueprint | Initialized `BLUEPRINT.md` with system architecture and phase map. | Complete |
| 2026-10-25 15:50 | Signing & CI | Updated `.gitignore` for keystores and parameterized debug signing configuration in `build.gradle.kts`. | Complete |
| 2026-10-25 16:00 | Engine & VPN | Implemented `SingBoxAdapter`, `VianshieldVpnService`, and `WireGuardConfigParser`. | Complete |
| 2026-10-25 16:15 | Diagnostics | Implemented bounded `OperationalLogCatcher` and Compose Diagnostic UI. | Complete |
| 2026-10-25 16:30 | CI Pipeline | Created reproducible GitHub Actions workflow `.github/workflows/build-vianshield.yml`. | Complete |
| 2026-10-25 16:45 | Verification | Ran `:app:testDebugUnitTest` (all 8 tests passed) & verified `compile_applet`. | Complete |
| 2026-10-25 22:35 | Phase 1.5 | Audited real upstream libbox API: corrected openTun detachFd flow, implemented protect(fd) socket loop prevention, aligned GitHub Actions build_libbox with recursive submodules, and enforced truthful native presence checking. | Complete |
| 2026-10-25 23:15 | CI Fix | Resolved :app:validateSigningDebug on missing keystore: implemented standard AGP debug signing fallback, removed unnecessary Google Services plugin, and guarded artifact upload. | Complete |
| 2026-10-25 23:30 | Log Keeper | Completed Log Keeper audit & integration: built full LogKeeperScreen with BackHandler, added open_log_keeper_button in DiagnosticScreen, fixed 6-flow combine in DiagnosticViewModel, and verified all 10 unit tests & compile_applet. | Complete |
| 2026-10-25 23:50 | Log Dumps & Crashes | Enforced strict 3 MiB persistent log limit (2-file rolling rotation), zero-permission Downloads export via MediaStore, robust chained UncaughtExceptionHandler with bounded crash dumps, pending crash launch detection & dismissal, honest native signal documentation, and 5 new unit tests. | Complete |
| 2026-10-26 00:08 | Hardening & Audit | Final Phase 1.5 hardening: prevented BoxService leak on start failure, hardened stop cleanup when native instance exists, guarded TUN fd race on close, and added defense-in-depth secret redaction on log exports. All 15 tests and compile_applet verified. | Complete |
| 2026-10-26 00:30 | UI Redesign | Implemented clean Light Sky Blue theme: centralized SkyBlue tokens in Color.kt and Theme.kt (SkyBluePrimary #0284C7, SkyBlueBackground #F6FAFD, SkyBlueSurface #FFFFFF, SkyBlueSurfaceBorder #D6E6F5, near-black Slate 900 typography, accessible emerald/amber/crimson status colors). Redesigned DiagnosticScreen & LogKeeperScreen with white cards, subtle light-blue borders, and sky-blue selected states. All 15 tests passed. | Complete |
| 2026-10-26 01:15 | Critical Fix Phase | Fixed sing-box startup null error: unwrapped InvocationTargetException, logged full Go root cause & cause chain, corrected tun inbound stack to mixed, added v1.11 address schema & auto_detect_interface. Implemented SAF WireGuard .conf import with comment/whitespace/base64 key validation & secret redaction preview. Verified dedicated Log Keeper BackHandler navigation. Configured -PtargetAbi for ~30MB ARM64 APKs alongside universal. | Complete |
| 2026-10-26 02:00 | Phase 1.6 Verification | Verified and resolved VPN startup & recovery defects: fixed missing stopSelf() in VpnService error paths, guarded onDestroy() to preserve VpnState.Error diagnostics, corrected sing-box TUN stack to gvisor, added DNS outbound hijacking (dns-out) and detour to wg-out, extended WireGuardProfile and parser with dual-stack IPv4/IPv6 CIDR handling, multi-DNS, PersistentKeepalive, and strict multi-peer rejection without silent discard. All 32 unit tests and compile_applet passed. | Complete |
| 2026-10-27 10:50 | Phase 1.7 BitProxy Audit | Audited BitProxy reference patches: adopted Mutex lifecycle serialization and startId-aware stopSelfResult() to prevent race conditions during rapid start/retry, added 10s watchdog timeout (withTimeoutOrNull) on engine startup to prevent deadlocks, inverted teardown order to stop native BoxService before releasing TUN fd (preventing adoptFd double-close/fd recycling), clamped and defaulted MTU to 1280 (BitProxy mobile standard) to eliminate cellular packet drop/PMTU blackholes, added 4 MTU regression tests, verified compile_applet and JVM unit tests. | Complete |
| 2026-10-27 18:15 | CI libbox.aar Fix | Resolved GitHub Actions native libbox compilation panic (exit status 1 at gomobile init.go:311): eliminated breaking GOFLAGS=-ldflags from go run build_libbox, fixed NDK r26d path resolution overriding premature ANDROID_NDK_LATEST_HOME (NDK 30), and added diagnostic toolchain verification step. | Complete |
| 2026-10-27 19:15 | CI APK Staging Fix | Resolved APK verification exit code 1: isolated ARM64 and universal APK staging into dist/ outside Gradle-managed output directory (preventing AGP clean reconciliation wipe), updated verification to test -s, and added failure diagnostic listing. | Complete |
| 2026-10-28 04:10 | Consolidated Fix | Fixed sing-box startup failure: migrated WireGuard config generation to root `endpoints` schema with `peers` array, natively supporting `persistent_keepalive_interval`. Enforced Light Sky Blue theme as the ONLY theme (removed dark-mode tokens/toggles, light system bars). Modularized UI into 5 dedicated screens (MainScreen, ProfilesScreen, DiagnosticsScreen, SettingsScreen, LogKeeperScreen). Configured single Dual-ARM (armeabi-v7a + arm64-v8a) APK build and CI workflow. Verified unit tests & compile_applet. | Complete |
| 2026-10-28 12:15 | Phase 1.9 Cache Fix | Resolved native sing-box cache startup failure (`open cache.db: read-only file system`): implemented `SingBoxStorageManager` providing dedicated app-private internal storage (`context.filesDir/singbox/cache.db`), zero-byte corrupt database recovery, safe isolated clearing, and fallback to `experimental.cache_file.enabled = false` if unwritable. Integrated into `WireGuardConfigParser.toSingBoxJson()` and `VianshieldVpnService`. Added safe diagnostics and 6 new unit tests. | Complete |
| 2026-10-28 20:00 | UI Reorganization | Reorganized UI into minimalistic 4-tab persistent layout (Main, Firewall, VPN, Settings) with Light Sky Blue theme and honest inactive placeholders. | Complete |
| 2026-10-28 22:25 | Phase 1.11 SFA Upgrade | Ported SFA/BitProxy lifecycle (Libbox.setup, Libbox.checkConfig, rollback), upgraded engine to stable sing-box v1.14.4 (SFA commit f87c2eb), eliminated experimental.cache_file workaround, consolidated CI workflow into single-job dual-ARM APK pipeline. All unit tests & compile_applet passed. | Complete |
| 2026-10-29 03:40 | Phase 1.12 SFA Integration | Verified and completed actual sing-box v1.14.4 SFA contract: implemented CommandServer lifecycle and 26-method PlatformInterface / CommandServerHandler proxy dispatch, wired typed SetupOptions sandbox directories, eliminated obsolete cache.db dead code from SingBoxStorageManager & VpnService, hardened watchdog concurrency against descriptor reuse, added AAR dual-ABI verification to CI workflow, and updated unit test suite. | Complete |









