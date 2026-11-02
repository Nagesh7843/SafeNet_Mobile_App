# SafeNet Android — Device Owner Setup & Provisioning Guide

## 1. Overview
SafeNet operates as a **Device Policy Controller (DPC)** in Android **Device Owner** mode. Device Owner is Android's highest management tier, designed for complete device management, kiosk enforcement, and fail-closed corporate/personal protection.

Unlike basic Device Administrator apps (which Google deprecated for advanced policies), Device Owner mode grants SafeNet the authority to:
1. Enforce **Always-On VPN with Lockdown** without user circumvention.
2. Disable the VPN configuration UI (`DISALLOW_CONFIG_VPN`).
3. Block package uninstallation (`setUninstallBlocked`).
4. Prevent safe-mode reboot bypasses (`DISALLOW_SAFE_BOOT`).
5. Enforce browser policies (e.g., Google Chrome Incognito restriction and SafeSites).
6. Configure global settings (e.g., disabling Private DNS to force traffic through the local filter).

---

## 2. Provisioning Methods

Android enforces a strict security requirement: **A Device Owner can ONLY be provisioned when NO user accounts (such as a Google account, WhatsApp, Samsung account) exist on the device**, or during the initial Out-Of-Box Experience (OOBE) setup.

### Method A: ADB Command Line (Recommended for Development & Existing Devices)
This method allows provisioning without factory resetting if all existing accounts are temporarily removed from **Settings -> Accounts**.

1. **Enable Developer Options & USB Debugging:**
   - Go to **Settings -> About Phone**.
   - Tap **Build Number** 7 times.
   - Go to **Settings -> System -> Developer Options** and enable **USB Debugging**.
2. **Remove All Accounts:**
   - Go to **Settings -> Accounts / Passwords & Accounts**.
   - Temporarily remove Google, WhatsApp, Samsung, or work accounts. (They can be re-added immediately after provisioning).
3. **Install SafeNet:**
   ```bash
   adb install -r safenet-release.apk
   ```
4. **Provision SafeNet as Device Owner:**
   ```bash
   adb shell dpm set-device-owner com.safenet.android/.dpc.receiver.SafeNetDeviceAdminReceiver
   ```
5. **Verify Provisioning:**
   ```bash
   adb shell dumpsys device_policy | findstr "Device Owner"
   ```
   SafeNet will immediately detect Device Owner status and activate fail-closed policies.
6. **Re-add User Accounts:**
   - Open Android Settings and sign back into your Google / personal accounts.

---

### Method B: QR Code Provisioning (Fresh Device or Factory Reset)
For a freshly factory-reset device:
1. Turn on the device and stay on the initial "Hi there" / "Welcome" screen.
2. Tap the blank white space on the welcome screen **6 times** consecutively.
3. Android launches the native QR code camera scanner.
4. Scan the SafeNet Provisioning QR Code containing the following payload:
   ```json
   {
     "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME": "com.safenet.android",
     "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.safenet.android/.dpc.receiver.SafeNetDeviceAdminReceiver",
     "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://releases.safenet.internal/safenet.apk",
     "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "4a1b2c...",
     "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true
   }
   ```
5. Connect to Wi-Fi; Android will download SafeNet, install it, and grant Device Owner privileges before the setup wizard finishes.

---

## 3. Supported Browser Managed Configurations

SafeNet uses Android Enterprise `ApplicationRestrictions` to manage installed browsers:

### Google Chrome (`com.android.chrome`)
SafeNet pushes the following `Bundle` restrictions via `devicePolicyManager.setApplicationRestrictions()`:
- `IncognitoModeAvailability`: `1` (Disabled. Values: 0 = Enabled, 1 = Disabled, 2 = Forced).
- `SafeSitesFilterBehavior`: `1` (Filter explicit sites).
- `UrlBlocklist`: Array containing prohibited protocol schemes or wildcard bypasses.

### Mozilla Firefox & Firefox Focus
Firefox on Android does not natively support enterprise managed configuration for private browsing disabling. SafeNet mitigates this through:
1. **Network-Level Guarantee:** Even in Firefox Private Browsing, all DNS queries are intercepted by the local VpnService and filtered. Private browsing CANNOT bypass the network filter.
2. **Installation Restriction:** Device Owner can prevent unauthorized browsers from being installed or block execution if configured by the administrator.

---

## 4. Safe Recovery & Unenrollment Procedure

SafeNet respects user device ownership and Android security guidelines:
- SafeNet does NOT create permanent device lockouts.
- Unenrollment is authorized via a **hardware-backed recovery passcode** stored securely in the **Android Keystore**.
- When authorized unenrollment is confirmed in the SafeNet Settings screen:
  1. SafeNet lifts Always-On VPN lockdown.
  2. Disables user restrictions (`DISALLOW_CONFIG_VPN`, `DISALLOW_SAFE_BOOT`).
  3. Removes application restrictions from Chrome.
  4. Calls `devicePolicyManager.clearDeviceOwnerApp(packageName)`.
  5. The device is restored to a standard unmanaged state without data loss.
