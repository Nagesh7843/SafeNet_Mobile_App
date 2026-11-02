# SafeNet Android — Privacy Policy & Data Governance

## 1. Core Privacy Tenets
SafeNet is built on the fundamental principle that **security should never come at the expense of user privacy**.

1. **Zero Browsing History Retention:** SafeNet does not record or store visited URLs, page titles, form inputs, or search queries.
2. **Local Processing Only:** All DNS packet inspection and rule evaluation occur locally on the device in memory.
3. **No Unsolicited Telemetry:** No browsing data or query logs are transmitted to external servers without explicit user consent.
4. **Data Minimization:** SafeNet only records blocked security events (timestamp, blocked domain, category) for user auditing and diagnostic reports.

---

## 2. Collected Data & Storage Locations

| Data Category | Purpose | Storage Location | Retention Period |
|---|---|---|---|
| **Blocked Domain Events** | Displaying blocked statistics in Reports | Local SQLite DB (`BlockEvent`) | 30 days (User purgeable) |
| **System Security Logs** | VPN and DPC operational health | Local SQLite DB (`SecurityEvent`) | 30 days |
| **Protection Policies** | User's active category settings | Local SQLite DB (`ProtectionPolicy`) | Until changed |
| **Device Configuration** | Operating mode, DNS upstream | Local SQLite DB (`DeviceConfig`) | Until uninstalled |
| **Recovery Passcode** | Authenticating safe unenrollment | Hardware KeyStore (Salted Hash) | Until uninstalled |

---

## 3. User Controls & Purging
- SafeNet provides a dedicated **"Purge Event Logs"** button in Reports.
- Clicking this immediately truncates `BlockEvent` and non-critical `SecurityEvent` tables.
- Upon authorized unenrollment, all databases and keystore entries can be wiped cleanly.
