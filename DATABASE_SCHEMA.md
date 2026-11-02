# SafeNet Android — Database Schema Specification

SafeNet uses **Room** (SQLite) for structured local storage. The database is strictly designed for operational security, policy enforcement, and aggregated telemetry.

> **Privacy Rule:** SafeNet **never** logs full browsing history, full URLs, search queries, or permitted traffic. Only blocked DNS events and security system logs are recorded with user-controlled retention periods.

---

## 1. Entity Definitions

### 1. `DeviceConfig`
Stores singleton hardware and management configuration.
- `id` (INTEGER, PRIMARY KEY) = 1 (Singleton)
- `isDeviceOwner` (INTEGER, BOOLEAN)
- `isAlwaysOnVpnConfigured` (INTEGER, BOOLEAN)
- `isLockdownEnabled` (INTEGER, BOOLEAN)
- `recoveryPasscodeHash` (TEXT, Nullable, Keystore-HMAC)
- `enforceSafeSearch` (INTEGER, BOOLEAN)
- `upstreamDnsType` (TEXT) - e.g. "CLOUDFLARE_FAMILY", "QUAD9", "CLEANBROWSING", "CUSTOM"
- `customUpstreamIp` (TEXT, Nullable)
- `updatedAt` (INTEGER, TIMESTAMP)

### 2. `ProtectionPolicy`
Fine-grained category toggles.
- `category` (TEXT, PRIMARY KEY) - e.g. "ADULT", "PORNOGRAPHY", "EXPLICIT", "GAMBLING", "MALWARE", "PHISHING", "REDIRECTS"
- `isEnabled` (INTEGER, BOOLEAN)
- `action` (TEXT) - "BLOCK_NXDOMAIN", "BLOCK_SINKHOLE", "ALLOW"
- `description` (TEXT)
- `ruleCount` (INTEGER)

### 3. `DomainRule`
Active domain block rules indexed for fast memory loading.
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT)
- `domain` (TEXT, UNIQUE, INDEXED) - Canonical lowercase domain name
- `category` (TEXT, INDEXED)
- `source` (TEXT) - "BUILTIN", "LOCAL_OVERRIDE", "REMOTE_FEED"
- `createdAt` (INTEGER)

### 4. `Allowlist`
Explicit domains exempted from filtering (e.g. educational/medical exceptions).
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT)
- `domain` (TEXT, UNIQUE, INDEXED)
- `notes` (TEXT)
- `createdAt` (INTEGER)

### 5. `BlocklistVersion`
Tracks versioning, cryptographic signatures, and update rollback states.
- `versionId` (TEXT, PRIMARY KEY)
- `sourceUrl` (TEXT)
- `sha256Hash` (TEXT)
- `signatureValid` (INTEGER, BOOLEAN)
- `domainCount` (INTEGER)
- `installedAt` (INTEGER)
- `isActive` (INTEGER, BOOLEAN)

### 6. `BlockEvent`
Recorded when a prohibited DNS query is intercepted.
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT)
- `timestamp` (INTEGER, INDEXED)
- `blockedDomain` (TEXT, INDEXED)
- `category` (TEXT)
- `actionTaken` (TEXT)
- `requesterUid` (INTEGER, Nullable)

### 7. `PolicyAudit`
Audit trail of security and configuration changes.
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT)
- `timestamp` (INTEGER)
- `action` (TEXT) - e.g. "POLICY_CHANGED", "VPN_RESTARTED", "LOCKDOWN_ENGAGED"
- `details` (TEXT)

### 8. `VPNStatus`
Tracks VPN runtime sessions and uptime diagnostics.
- `id` (INTEGER, PRIMARY KEY) = 1 (Singleton)
- `isConnected` (INTEGER, BOOLEAN)
- `connectionTime` (INTEGER)
- `bytesIn` (INTEGER)
- `bytesOut` (INTEGER)
- `queriesProcessed` (INTEGER)
- `queriesBlocked` (INTEGER)
- `lastError` (TEXT, Nullable)

### 9. `BrowserPolicy`
Per-browser managed configuration state.
- `packageName` (TEXT, PRIMARY KEY) - e.g. "com.android.chrome"
- `browserName` (TEXT)
- `isInstalled` (INTEGER, BOOLEAN)
- `supportsIncognitoRestriction` (INTEGER, BOOLEAN)
- `isIncognitoRestricted` (INTEGER, BOOLEAN)
- `lastAppliedTimestamp` (INTEGER)

### 10. `SecurityEvent`
High-priority security telemetry (tamper attempts, lockdown failures, DoH bypass detections).
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT)
- `timestamp` (INTEGER, INDEXED)
- `severity` (TEXT) - "INFO", "WARNING", "CRITICAL"
- `eventType` (TEXT)
- `description` (TEXT)

---

## 2. Retention & Data Governance
- `BlockEvent` records are automatically purged after 30 days (configurable by user).
- A one-click "Purge Local Event History" function allows instant sanitization.
- No remote telemetry is sent without explicit user opt-in.
