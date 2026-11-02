# SafeNet Android — Security Architecture & Threat Model

## 1. Security Philosophy
SafeNet enforces **Defense-in-Depth** and **Zero-Trust Network Filtering** on personal Android devices. Rather than relying on easily bypassed user-space accessibility services or browser extensions, SafeNet operates at the **OS-kernel network layer** (Android VpnService) reinforced by **Android Device Owner** platform privileges.

---

## 2. Threat Model & Mitigation Matrix

### Threat 1: VPN Disconnection or Bypass by User
- **Attack Vector:** User opens Android Settings, toggles off the VPN, or clears app data.
- **Mitigation:**
  - Device Owner enforces Always-On VPN with `lockdownEnabled = true`.
  - Device Owner applies `UserManager.DISALLOW_CONFIG_VPN`, completely removing the user's ability to edit, disconnect, or disable the VPN in Android Settings.
  - Fail-Closed: If the VPN process is forcibly killed by OOM or task killer, Android's `netd` firewall blocks ALL network packets until the VPN service restarts.

### Threat 2: App Uninstallation
- **Attack Vector:** User drags SafeNet to "Uninstall" or uninstalls via package manager.
- **Mitigation:**
  - Device Owner sets `devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)`.
  - Android package manager blocks uninstallation system-wide.

### Threat 3: DNS-over-HTTPS (DoH) & DNS-over-TLS (DoT) Evasion
- **Attack Vector:** An app or browser uses encrypted DoH or DoT to bypass local Port 53 UDP DNS interception.
- **Mitigation:**
  - **Android Private DNS (DoT):** Device Owner enforces `Settings.Global.PRIVATE_DNS_MODE = "off"`, preventing system-level DoT bypass.
  - **DoH Bootstrap Blocking:** SafeNet's local DNS engine immediately blocks known DoH bootstrap hostnames (`cloudflare-dns.com`, `dns.google`, `dns.quad9.net`, etc.). Without initial DNS resolution to find the DoH server IP, browsers fail back to standard DNS or fail closed.
  - **Well-Known DNS IP Trapping:** Hardcoded public DNS IPs (`8.8.8.8`, `1.1.1.1`, etc.) are routed into the TUN interface and processed by the filter engine.

### Threat 4: Browser Incognito / InPrivate Bypasses
- **Attack Vector:** User switches to Incognito mode in a browser.
- **Mitigation:**
  - **Managed Configurations:** SafeNet configures Chrome (`com.android.chrome`) with `IncognitoModeAvailability = 1`, disabling Incognito mode entirely.
  - **Independent Network Layer:** Even in browsers where Incognito cannot be disabled by enterprise policy (e.g. Firefox), all network connections generated in private tabs still traverse SafeNet's VpnService. Network filtering is 100% agnostic to browser tabs.

### Threat 5: Safe-Mode Reboot Bypass
- **Attack Vector:** User boots the device into Safe Mode to disable 3rd party apps.
- **Mitigation:**
  - Device Owner sets `UserManager.DISALLOW_SAFE_BOOT`. Android disables the safe mode boot menu.

### Threat 6: Tampered Blocklist Updates
- **Attack Vector:** Attacker performs a MITM or provides a poisoned blocklist update file.
- **Mitigation:**
  - Downloaded blocklists require HTTPS transport, SHA-256 integrity hash verification, and cryptographic RSA/ECDSA signature checks against SafeNet's embedded public key.
  - Corrupt or invalid blocklists are rejected and rolled back automatically.

---

## 3. Cryptographic Key Management
- SafeNet utilizes the hardware-backed **Android KeyStore** (`AndroidKeyStore`).
- Symmetric encryption uses `AES/GCM/NoPadding` with 256-bit keys generated inside the hardware Secure Element (SE) / StrongBox where available.
- Unenrollment passcodes are never stored in plaintext; they are hashed with PBKDF2/HMAC-SHA256 and salted.
