# SafeNet Android — Implementation Roadmap & Execution Plan

## 1. Project Overview & Environmental Profile
- **Project Name:** SafeNet
- **Platform:** Native Android (Kotlin + Jetpack Compose)
- **Architecture:** Clean Architecture (Domain / Data / Presentation / Platform Engine)
- **Core Security Engines:**
  - Android DevicePolicyManager (Device Owner / Fully Managed Device)
  - Android VpnService (Fail-Closed Always-On Packet & DNS Intercept Engine)
  - Local Zero-Latency DNS Filtering Engine (Trie-based Domain Matching & Synthesis)
  - Safe Browsing & Search Engine (Strict Category Rules, DoH/DoT Bypass Prevention)
  - Managed Browser Policy Manager (Chrome Incognito Restriction via Managed Configurations)

### Environment Specifications
- **Build System:** Gradle 9.6.0 + Android Gradle Plugin 9.4.1
- **Kotlin Version:** 2.2.10
- **Java Runtime:** JDK 17 / OpenJDK 25 (Android Studio Ladybug/Iguana JBR)
- **Compile SDK:** API 35 (Android 15) / Base SDK 37
- **Min SDK:** API 28 (Android 9.0 Pie) — Required for robust Device Owner Always-On VPN lockdown, NetworkCapabilities, NetworkSecurityConfig, and Managed Configurations.
- **Target SDK:** API 35 (Android 15)
- **Android SDK Path:** `C:\Users\Nagesh\AppData\Local\Android\Sdk`
- **Build Tools:** `36.0.0`

---

## 2. Technical Feasibility Analysis

### A. Device Owner Feasibility
- **Mechanism:** Implements `DeviceAdminReceiver` registered with `<device-admin>` metadata declaring `disable-keyguard-features`, `force-lock`, `watch-login`, `wipe-data`, and enterprise policies.
- **Provisioning Methods:**
  1. **ADB Mode (Direct Dev & Personal Device):**
     `adb shell dpm set-device-owner com.safenet.android/.receiver.SafeNetDeviceAdminReceiver`
     *Prerequisite:* Device must have zero user accounts provisioned prior to executing the command.
  2. **QR Code Provisioning (OOBE / Factory Reset):**
     Tapping welcome screen 6 times launches the camera to scan SafeNet enterprise provisioning JSON.
  3. **Managed Configurations (`AppRestrictions`):**
     Used to set Chrome `IncognitoModeAvailability = 1` (Disabled), `SafeSitesFilterBehavior = 1` (SafeSites), and strict search filters.
- **Feasibility Verdict:** **100% Native & Verified.**

### B. VpnService & Lockdown Feasibility
- **Mechanism:** Implements `android.net.VpnService`. Once provisioned as Device Owner, SafeNet enforces:
  - `devicePolicyManager.setAlwaysOnVpnPackage(adminComponent, packageName, /* lockdownEnabled */ true)`
- **Fail-Closed Lockdown:**
  - When lockdown is active, Android's OS kernel (`netd` / eBPF) drops any outbound packet that does not traverse the SafeNet VPN tunnel.
  - If SafeNet crashes or is restarted, no unencrypted or unfiltered traffic leaks.
  - SafeNet sets `DISALLOW_CONFIG_VPN` so standard users cannot disable or bypass the VPN toggle.
- **Private DNS & DoH Bypass Prevention:**
  - Device Owner can enforce `Settings.Global.PRIVATE_DNS_MODE = "off"`, preventing the OS and apps from bypassing Port 53 DNS via opportunistic or strict DNS-over-TLS (DoT).
  - The local DNS engine drops or redirects DNS queries to known DoH bootstrap endpoints (`cloudflare-dns.com`, `dns.google`, `dns.quad9.net`, etc.).
- **Feasibility Verdict:** **100% Native & Verified.**

---

## 3. Milestone Execution Sequence

| Milestone | Focus Area | Deliverables | Verification Strategy |
|---|---|---|---|
| **Milestone 1** | Project Foundation & Architecture | Gradle build, Directory structure, Clean Architecture layers, DI, Design System, UI Dashboard | Successful build, preview rendering, baseline tests |
| **Milestone 2** | Device Owner & DPC Engine | `DeviceAdminReceiver`, DpcManager, Provisioning helper, Managed configurations, Policy enforcement | Unit tests, DPC policy validation |
| **Milestone 3** | VPN Protection Engine | `SafeNetVpnService`, Packet intercept, TUN interface manager, Always-On & Lockdown orchestrator | Packet parsing tests, IP/UDP header verification, lifecycle tests |
| **Milestone 4** | DNS Filtering Engine | Raw DNS packet parser & synthesizer, Trie domain matcher, Local resolver, Upstream forwarder | DNS query/response parsing tests, NXDOMAIN synthesis tests |
| **Milestone 5** | Strict Safe Browsing & Blocklists | 9 category classifications, Safe Search rewrite, Room DB blocklist storage, SHA-256 integrity, Atomic updates | Domain classification tests, SafeSearch rewrite tests, DB integrity tests |
| **Milestone 6** | Browser & App Management | Package discovery, Chrome Incognito managed config, Firefox/Edge compatibility tracking, Tamper audit | Browser policy inspector tests, App inventory tests |
| **Milestone 7** | Security Hardening & Tamper Protection | Android Keystore encryption, Fail-closed watchdog, Heartbeat monitor, Authorized recovery/unenrollment | Unenrollment auth tests, KeyStore encryption tests, Tamper detection tests |
| **Milestone 8** | Release Engineering & Full Acceptance | Final end-to-end integration, Battery optimization, Documentation audit, Release APK artifact | Full test suite execution, Build verification |

---

## 4. Current State & Immediate Action
1. Milestone 1 setup: Create Android project structure, Gradle files (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`).
2. Implement core Design System, Theme tokens, and modern cybersecurity Dashboard UI.
3. Establish Clean Architecture packages:
   - `com.safenet.android.core` (common, security, keystore)
   - `com.safenet.android.data` (db, entities, dao, repository, blocklist)
   - `com.safenet.android.domain` (models, usecases, policies)
   - `com.safenet.android.engine.vpn` (VpnService, TUN, PacketRouter)
   - `com.safenet.android.engine.dns` (DnsPacket, DnsFilterEngine, DnsResolver, Trie)
   - `com.safenet.android.dpc` (DeviceAdminReceiver, DpcManager, BrowserPolicyManager)
   - `com.safenet.android.ui` (Compose theme, navigation, screens: Dashboard, Protection, Blocklists, Browsers, Reports, Settings)
