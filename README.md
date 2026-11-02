# SafeNet — Native Android Device Owner & Network Filtering Security Suite

SafeNet is an enterprise-grade native Android cybersecurity application designed to protect personally owned or managed devices from adult content, explicit search results, malware, phishing domains, and bypass attempts.

SafeNet combines **Android Device Owner (DevicePolicyManager)** management with an **OS-level Always-On VPN (VpnService) & Local DNS Filtering Engine** to deliver guaranteed fail-closed network security.

---

## Key Features

- **Device Owner Management (DPC):**
  - Fully managed device control.
  - Fail-closed Always-On VPN with lockdown (`lockdownEnabled = true`).
  - Google Chrome Incognito restriction (`IncognitoModeAvailability = 1`).
  - Android Private DNS (DoT) bypass prevention (`Settings.Global.PRIVATE_DNS_MODE = "off"`).
  - Uninstallation and Safe-Mode reboot protection (`DISALLOW_SAFE_BOOT`, `setUninstallBlocked`).
  - Authorized hardware-backed recovery and unenrollment.

- **VpnService & Network Filtering Engine:**
  - High-performance local TUN interface processing.
  - IPv4 and IPv6 dual-stack support.
  - Zero-latency local DNS parser and synthesizer (RFC 1035).
  - Radix Trie domain matching (< 1ms evaluation).
  - Instant synthetic response injection (`0.0.0.0`, `::`, `NXDOMAIN`).
  - Protected upstream sockets avoiding routing loops.

- **Strict Safe Browsing & Search:**
  - Separate categorization: Adult, Pornography, Explicit, Gambling, Malware, Phishing, Malicious Redirects.
  - Native DNS-level Safe Search enforcement for Google, Bing, YouTube, and DuckDuckGo.
  - Whitelist exceptions for educational and medical websites.

- **Modern Jetpack Compose Cyber UI:**
  - Dark cybersecurity aesthetic (deep navy, emerald active accents, amber warnings, crimson alerts).
  - Multi-status dashboard verifying actual system states (VPN, DPC, DNS, Browser, Blocklist).
  - 6 main navigation sections: Dashboard, Protection, Blocklists, Browsers, Reports, Settings.

- **Hardware Security & Privacy:**
  - Android Keystore AES-256-GCM hardware-backed key protection.
  - Zero browsing history logging.
  - One-click local telemetry purge.

---

## Repository Structure

```
d:/Crom_app/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/safenet/android/
│   │   │   │   ├── core/           # Keystore, Common Utilities, Dispatchers
│   │   │   │   ├── data/           # Room Database, DAOs, Entities, Blocklist Repositories
│   │   │   │   ├── domain/         # Business Models, Use Cases, Category Policies
│   │   │   │   ├── dpc/            # DeviceAdminReceiver, DpcManager, Browser Policies
│   │   │   │   ├── engine/         # VpnService, TUN Interface, Packet Router, DNS Engine
│   │   │   │   └── ui/             # Jetpack Compose Screens, Cyber Theme, Navigation
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── test/                   # Unit & Integration Tests
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── ARCHITECTURE.md
├── DATABASE_SCHEMA.md
├── DEVICE_OWNER_SETUP.md
├── IMPLEMENTATION_ROADMAP.md
├── MODULES.md
├── NETWORK_ENGINE.md
├── PRIVACY.md
├── SECURITY.md
└── TESTING.md
```

---

## Quick Setup & Provisioning

See [DEVICE_OWNER_SETUP.md](file:///d:/Crom_app/DEVICE_OWNER_SETUP.md) for complete step-by-step instructions.

```bash
# Sideload app
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk

# Provision as Device Owner
adb shell dpm set-device-owner com.safenet.android/.dpc.receiver.SafeNetDeviceAdminReceiver
```
