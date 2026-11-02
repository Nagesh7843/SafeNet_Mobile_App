# SafeNet Android — Application Modules Specification

This document details the responsibilities, package locations, and public contracts of the 11 independent architectural modules of SafeNet.

---

## Module A: Dashboard (`com.safenet.android.ui.dashboard`)
- **Responsibilities:**
  - Aggregates multi-layer security metrics into a single real-time dashboard.
  - Queries true system state via `DpcManager`, `VpnStateMonitor`, and `BlocklistRepository`.
  - Displays discrete status chips: VPN Connected, DNS Filtering Active, Device Owner Enforced, Chrome Incognito Restricted, Blocklist Version.
  - Never collapses independent security states into a single generic green badge.

## Module B: Device Enrollment & DPC (`com.safenet.android.dpc`)
- **Responsibilities:**
  - Implements `SafeNetDeviceAdminReceiver` extending `DeviceAdminReceiver`.
  - Checks `isDeviceOwnerApp()` and `isAdminActive()`.
  - Configures Always-On VPN package and enables lockdown.
  - Enforces `DISALLOW_CONFIG_VPN`, `DISALLOW_SAFE_BOOT`, `DISALLOW_DEBUGGING_FEATURES`.
  - Manages secure unenrollment (`clearDeviceOwnerApp`).

## Module C: VPN Protection Engine (`com.safenet.android.engine.vpn`)
- **Responsibilities:**
  - Implements `SafeNetVpnService` extending `android.net.VpnService`.
  - Establishes and manages the Linux TUN interface.
  - Processes IPv4 and IPv6 network packets on a dedicated thread.
  - Traps Port 53 DNS UDP queries and routes them to the local DNS Filtering Engine.
  - Transmits synthetic block responses back to TUN file descriptor.
  - Forwards non-blocked DNS queries via protected sockets.

## Module D: DNS Filtering Engine (`com.safenet.android.engine.dns`)
- **Responsibilities:**
  - Raw binary DNS packet parser (RFC 1035).
  - Domain name extraction and canonical normalization (lowercase, trailing dot stripping).
  - High-performance domain matching using a prefix/suffix Radix Trie.
  - Generates RFC-compliant DNS responses (A = `0.0.0.0`, AAAA = `::`, or `NXDOMAIN`).
  - Implements LRU memory cache for resolved upstream DNS records.

## Module E: Strict Safe Browsing (`com.safenet.android.domain.policy`)
- **Responsibilities:**
  - Implements category definitions: Adult, Pornography, Explicit, Gambling, Malware, Phishing, Malicious Redirects, Custom Block, Custom Allow.
  - Safe Search enforcement: Rewrites DNS for Google (`forcesafesearch.google.com`), Bing (`strict.bing.com`), YouTube (`restrictmoderate.youtube.com`), and DuckDuckGo (`safe.duckduckgo.com`).
  - Preserves educational, medical, and benign websites.

## Module F: Blocklist Management (`com.safenet.android.data.blocklist`)
- **Responsibilities:**
  - Local database storage and Trie memory indexer.
  - Downloads updates from trusted remote repositories.
  - SHA-256 integrity calculation and cryptographic signature verification.
  - Atomic pointer swap: updates Trie in memory without interrupting packet flow.
  - Automatic rollback on corrupt or truncated downloads.

## Module G: Browser Management (`com.safenet.android.dpc.browser`)
- **Responsibilities:**
  - Discovers installed web browsers (`com.android.chrome`, `org.mozilla.firefox`, `com.sec.android.app.sbrowser`, `com.microsoft.emmx`, `com.brave.browser`).
  - Pushes Chrome managed configurations (`IncognitoModeAvailability = 1`).
  - Tracks browser-specific capabilities.
  - Guarantees network filtering regardless of private browsing mode.

## Module H: Application Management (`com.safenet.android.dpc.app`)
- **Responsibilities:**
  - Enumerates installed user applications and network capabilities.
  - Enforces application installation restrictions when enabled.
  - Verifies SafeNet package integrity.

## Module I: Security & Tamper Protection (`com.safenet.android.core.security`)
- **Responsibilities:**
  - Hardware-backed key generation via `AndroidKeyStore`.
  - Passcode hashing and AES-256-GCM configuration encryption.
  - Unexpected VPN termination watchdog.
  - Database tamper detection.

## Module J: Local Database (`com.safenet.android.data.db`)
- **Responsibilities:**
  - Room SQLite database (`SafeNetDatabase`).
  - Entities: `DeviceConfig`, `ProtectionPolicy`, `DomainRule`, `Allowlist`, `BlocklistVersion`, `BlockEvent`, `PolicyAudit`, `BrowserPolicy`, `SecurityEvent`.
  - Auto-migrations and zero full browsing history retention.

## Module K: Reports & Analytics (`com.safenet.android.ui.reports`)
- **Responsibilities:**
  - Visual metrics for total requests, blocked adult queries, blocked malware queries.
  - Security audit log viewer with export capability.
  - User-triggered nonessential telemetry clearing.
