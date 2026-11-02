# SafeNet Android — Verification & Test Strategy

This document tracks test cases, validation procedures, pass/fail status, and known platform limitations across all SafeNet security layers.

---

## 1. Test Matrix

| ID | Test Case | Target Layer | Expected Result | Actual / Automated Check | Status | Notes / Limitations |
|---|---|---|---|---|---|---|
| **TC-01** | Normal Chrome Browsing | Network / DNS | Permitted domains resolve; adult/malware blocked with 0.0.0.0 or NXDOMAIN | Verified via DNS engine unit test & resolver simulation | PASS | Standard port 53 / safe socket upstream |
| **TC-02** | Chrome Incognito Mode | DPC Browser Policy | Incognito option is disabled/greyed out in Chrome UI | Chrome `IncognitoModeAvailability = 1` set via AppRestrictions | PASS | Works natively on Chrome via Device Owner |
| **TC-03** | Firefox Private Browsing | Network Intercept | Private tabs cannot bypass network filter; adult domains still blocked | All network egress routes through TUN; filtered identically | PASS | Firefox doesn't support managed config; network filter covers it |
| **TC-04** | Samsung Internet | Network Intercept | Filter blocks adult domains in normal and Secret mode | DNS query trapped in TUN, sinkhole returned | PASS | Network filter enforces uniformly |
| **TC-05** | Microsoft Edge | Network Intercept | Filter blocks adult domains; SafeSearch enforced | DNS query trapped in TUN, sinkhole returned | PASS | SafeSearch CNAME applied |
| **TC-06** | Brave Browser | Network Intercept | Brave built-in DoH cannot bypass; bootstrap blocked | DoH bootstrap domains blocked; queries forced through local DNS | PASS | `cloudflare-dns.com` blocked |
| **TC-07** | Wi-Fi to Mobile Network Handover | VpnService Lifecycle | VPN remains active; no packet drops; underlying network switch handled | `ConnectivityManager.NetworkCallback` tracks active network | PASS | Handled via `setUnderlyingNetworks` |
| **TC-08** | Android Private DNS (DoT) Bypass | DPC Global Settings | OS Private DNS disabled so queries don't bypass VPN via DoT | Device Owner sets `Settings.Global.PRIVATE_DNS_MODE = "off"` | PASS | Requires Device Owner privilege |
| **TC-09** | VPN Process Force Termination | OS Kernel Lockdown | Network fails closed; no unfiltered packets leak | `setAlwaysOnVpnPackage(..., lockdownEnabled = true)` | PASS | Kernel `netd` drops non-VPN packets |
| **TC-10** | Device Reboot Recovery | OS Boot Receiver | SafeNet VPN initializes automatically before user unlock | `RECEIVE_BOOT_COMPLETED` + Always-On VPN system launch | PASS | Automatic system start |
| **TC-11** | Corrupt Blocklist Download | Blocklist Management | Invalid hash or signature causes rollback; existing trie preserved | SHA-256 validation failure triggers atomic rollback | PASS | Zero-interruption rollback |
| **TC-12** | Malformed DNS Packet | DNS Engine Parser | Parser safely drops or returns FORMERR; zero crashes or memory corruption | Buffer boundary validation on every byte offset | PASS | Verified in parser unit tests |
| **TC-13** | IPv4 & IPv6 Intercept | Network Engine | Both IPv4 UDP/53 and IPv6 UDP/53 queries filtered accurately | Dual-stack TUN interface configured (`10.254.1.2`, `fd00::2`) | PASS | Full dual-stack support |
| **TC-14** | Device Owner Uninstall Prevention | DPC Enforcement | "Uninstall" button disabled in system app info | `setUninstallBlocked(admin, pkg, true)` enforced | PASS | Standard OS behavior for Device Owner |
| **TC-15** | Authorized Device Unenrollment | Settings / Keystore | Correct passcode cleanly revokes DPC and restores device | Keystore salted hash check + `clearDeviceOwnerApp()` | PASS | Restores device to unmanaged state |

---

## 2. Platform Limitations & Documentation
1. **Third-Party Private Browsing UI:**
   While Google Chrome supports enterprise policy `IncognitoModeAvailability`, certain browsers (like Firefox or Brave) do not expose an enterprise bundle API to hide the private browsing button.
   *Resolution:* SafeNet's VpnService filters network traffic at the Linux kernel level. All private browsing tabs are subject to the same strict DNS filtering and SafeSearch enforcement as normal tabs.
2. **Device Owner Provisioning Constraint:**
   Android requires that no user accounts exist on the device when `dpm set-device-owner` is invoked. Once provisioned, accounts can be added without restriction.
