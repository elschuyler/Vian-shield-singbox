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
- [ ] Sing-box engine abstraction adapter (`SingBoxEngine`, `SingBoxAdapter`).
- [ ] Genuine Android `VpnService` lifecycle implementation (`VianshieldVpnService`, `VpnLifecycleController`, `VpnState`).
- [ ] WireGuard configuration parser & sing-box JSON transformer (`WireGuardConfigParser`).
- [ ] Operational Log Catcher with Master On/Off switch, bounded circular storage, secret redaction, and export API (`OperationalLogCatcher`).
- [ ] Diagnostic UI with real-time state, control toggle, and live log viewer.
- [ ] Pinned GitHub Actions build workflow (`.github/workflows/build-vianshield.yml`) targeting sing-box v1.11.0.
- [ ] Verification via JVM/Robolectric unit tests and `compile_applet`.

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


