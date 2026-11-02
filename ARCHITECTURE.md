# SafeNet Android — Architecture Specification

## 1. Architectural Philosophy
SafeNet is engineered around **Clean Architecture**, **SOLID principles**, and **Defense-in-Depth**. It treats mobile network security as a deterministic, fail-closed platform service rather than an ephemeral foreground utility.

```
+-----------------------------------------------------------------------------------+
|                              Presentation Layer (UI)                              |
|   Jetpack Compose | Cyber Dark Theme | StateFlow | Navigation | 6 Core Screens     |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                                Domain Layer (Core)                                |
|   Models | UseCases | Interceptors | Protection Policies | Audit Interfaces       |
+-----------------------------------------------------------------------------------+
                      /                         \                        \
                     v                           v                        v
+-----------------------------+   +-----------------------------+   +---------------+
|     Data Layer (Storage)    |   |     Device Owner (DPC)      |   | Engine Layer  |
|  Room DB | Keystore Auth    |   |  DevicePolicyManager        |   | VpnService    |
|  Blocklist Repositories     |   |  Managed Configurations     |   | TUN Intercept |
|  Audit & Telemetry Logs     |   |  Restrictions & Lockdown    |   | DNS Engine    |
+-----------------------------+   +-----------------------------+   +---------------+
```

---

## 2. Layer Breakdown

### A. Presentation Layer (`com.safenet.android.ui`)
- **Framework:** Jetpack Compose + Material 3.
- **State Management:** MVI / MVVM with unidirectional data flow via Kotlin `StateFlow`.
- **Aesthetics:** Deep cybersecurity navy background (`#070B14`, `#0D1527`), subtle borders (`#1E2C4A`), restrained emerald active indicators (`#00E676`, `#00C853`), warning amber (`#FFB300`), critical crimson (`#FF3D00`), and modern typography.
- **Screens:**
  1. `DashboardScreen`: System health, real-time VPN & DPC state, live packet/block stats.
  2. `ProtectionScreen`: Category toggles, Safe Search enforcement, Lockdown control.
  3. `BlocklistsScreen`: Local & remote sources, SHA-256 verification, manual & auto-sync.
  4. `BrowsersScreen`: Installed browser discovery, Incognito policy status, managed configs.
  5. `ReportsScreen`: Categorized block events, security audit logs, exportable diagnostics.
  6. `SettingsScreen`: Upstream DNS servers, DPC unenrollment authentication, recovery tools.

### B. Domain Layer (`com.safenet.android.domain`)
- Independent of Android framework SDK where possible for pure testability.
- Contains business models (`ProtectionStatus`, `DomainRule`, `DnsPacket`, `CategoryPolicy`, `AuditLog`).
- Contains business rules (`EvaluateDomainPolicyUseCase`, `VerifyBlocklistSignatureUseCase`, `EnforceSafeSearchUseCase`).

### C. Data Layer (`com.safenet.android.data`)
- **Room Database (`SafeNetDatabase`):** Indexed SQLite tables for policies, blocklists, audit logs, and status.
- **Crypto Engine (`SecurityKeyStoreHelper`):** Android Keystore AES-256-GCM hardware-backed encryption for confidential configuration and unenrollment passcodes.
- **Blocklist Storage (`BlocklistRepository`):** High-speed Trie / Bloom / HashSet memory structures backed by Room for rapid DNS filtering under 1 millisecond.

### D. Device Policy Controller Layer (`com.safenet.android.dpc`)
- Integrates with Android's `DevicePolicyManager`.
- Admin receiver: `SafeNetDeviceAdminReceiver`.
- Manages Always-On VPN enforcement with `lockdownEnabled = true`.
- Applies restrictions (`DISALLOW_CONFIG_VPN`, `DISALLOW_SAFE_BOOT`, `DISALLOW_DEBUGGING_FEATURES`, `DISALLOW_FACTORY_RESET`).
- Enforces Google Chrome managed configuration (`IncognitoModeAvailability = 1`, `SafeSitesFilterBehavior = 1`).

### E. Network Engine Layer (`com.safenet.android.engine`)
- **VpnService (`SafeNetVpnService`):**
  - Manages the Linux virtual TUN network interface.
  - Intercepts IPv4 and IPv6 traffic.
  - Transparently forwards non-DNS IP traffic while directing Port 53 DNS queries to the local DNS Filtering Engine.
- **DNS Filtering Engine (`DnsFilterEngine`):**
  - Bit-level DNS packet parser and synthesizer.
  - Zero-allocation domain normalization.
  - Radix tree / Trie fast lookup.
  - Instant synthetic response generator (NXDOMAIN, `0.0.0.0`, `::`).
  - Secure upstream resolver (DoT / DoH / protected standard DNS sockets).

---

## 3. Concurrency & Asynchronous Design
- **Coroutines & Kotlin Flow:** All database I/O, network queries, and blocklist indexing run on dedicated background dispatchers (`Dispatchers.IO`, `Dispatchers.Default`).
- **Dedicated Network Worker Thread:** The TUN interface read/write loop executes on a high-priority pinned background thread to maintain sub-millisecond packet latency.
- **Atomic Operations:** Blocklist updates are applied atomically via double-buffering; the DNS engine switches pointers to the newly validated trie with zero dropped packets.
