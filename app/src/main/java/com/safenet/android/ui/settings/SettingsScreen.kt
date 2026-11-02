package com.safenet.android.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safenet.android.SafeNetApplication
import com.safenet.android.core.launcher.LauncherManager
import com.safenet.android.core.recovery.ConfigRecoveryManager
import com.safenet.android.core.vpn.AlwaysOnVpnHelper
import com.safenet.android.engine.dns.DnsResolver
import com.safenet.android.engine.vpn.SafeNetVpnService
import com.safenet.android.ui.theme.*

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val app = SafeNetApplication.instance
    val dpcManager = app.dpcManager
    val resolver = SafeNetVpnService.dnsResolver

    var isDeviceOwner by remember { mutableStateOf(dpcManager.isDeviceOwner()) }
    var selectedProvider by remember {
        mutableStateOf(resolver?.currentProvider ?: DnsResolver.UpstreamProvider.CLOUDFLARE_FAMILY)
    }

    var isIconVisible by remember {
        mutableStateOf(LauncherManager.isLauncherIconVisible(context))
    }

    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreInputText by remember { mutableStateOf("") }
    var restoreError by remember { mutableStateOf<String?>(null) }

    var showUnenrollDialog by remember { mutableStateOf(false) }
    var unenrollPasscode by remember { mutableStateOf("") }
    var unenrollError by remember { mutableStateOf<String?>(null) }

    val adbCommand = "adb shell dpm set-device-owner ${context.packageName}/com.safenet.android.dpc.receiver.SafeNetDeviceAdminReceiver"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Always-On VPN Onboarding & Platform Lockdown
        item {
            Text(
                text = "ALWAYS-ON VPN & LOCKDOWN PROTECTION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Android System Always-On Setup",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Standard Android security permits users to configure SafeNet as the permanent Always-on VPN with kernel-level lockdown in System Settings.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("1. Tap 'Open Android VPN Settings' below.", color = TextPrimary, fontSize = 12.sp)
                            Text("2. Tap the Gear / Settings icon next to SafeNet.", color = TextPrimary, fontSize = 12.sp)
                            Text("3. Enable 'Always-on VPN'.", color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("4. Enable 'Block connections without VPN' (Lockdown).", color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            try {
                                context.startActivity(AlwaysOnVpnHelper.createOpenVpnSettingsIntent())
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open VPN settings directly. Please go to Android Settings -> Network -> VPN", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Android VPN Settings", fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "Security Notice: Normal Android APK enrollment cannot prevent force-stop or uninstall. Enabling Always-On Lockdown ensures the Android kernel drops all internet traffic if SafeNet is stopped.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // 2. Launcher Icon Independence
        item {
            Text(
                text = "LAUNCHER ICON INDEPENDENCE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide App Launcher Icon", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = "Hides SafeNet from your app drawer. VPN service and active DNS filtering continue uninterrupted.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = !isIconVisible,
                            onCheckedChange = { hide ->
                                val success = LauncherManager.setLauncherIconVisible(context, !hide)
                                if (success) {
                                    isIconVisible = !hide
                                    Toast.makeText(
                                        context,
                                        if (hide) "Icon hidden. Reopen via notification or safenet://open" else "Icon restored to launcher",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyberAmber, checkedTrackColor = CyberAmberGlow)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("How to reopen SafeNet when hidden:", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Tap the persistent SafeNet VPN notification in the status bar.", color = TextSecondary, fontSize = 11.sp)
                            Text("• Open Chrome or any browser and navigate to safenet://open", color = TextSecondary, fontSize = 11.sp)
                            Text("• Terminal: adb shell am start -a android.intent.action.VIEW -d \"safenet://open\"", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 3. Cryptographically Authenticated Configuration Backup & Recovery
        item {
            Text(
                text = "CONFIGURATION BACKUP & DISASTER RECOVERY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Zero-Backend Local Recovery",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Export and restore your categories, custom block rules, and allowlists using SHA-256 HMAC authenticated signatures without storing sensitive data in plaintext.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val exportRes = ConfigRecoveryManager.exportConfiguration(context)
                                if (exportRes.success && exportRes.jsonString != null) {
                                    clipboardManager.setText(AnnotatedString(exportRes.jsonString))
                                    Toast.makeText(context, "Authenticated configuration copied to clipboard!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Export error: ${exportRes.error}", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = CyberBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Backup", color = TextPrimary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                restoreError = null
                                restoreInputText = ""
                                showRestoreDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore Backup", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 4. Upstream DNS Resolver Section
        item {
            Text(
                text = "UPSTREAM RESOLVER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DnsResolver.UpstreamProvider.values().forEachIndexed { index, provider ->
                        if (index > 0) {
                            Divider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 10.dp))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProvider = provider
                                    resolver?.currentProvider = provider
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProvider == provider,
                                onClick = {
                                    selectedProvider = provider
                                    resolver?.currentProvider = provider
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = CyberGreen,
                                    unselectedColor = TextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(provider.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(provider.description, color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 5. Device Owner Provisioning Guide Card (Optional Managed Device Mode)
        item {
            Text(
                text = "MANAGED DEVICE OWNER MODE (OPTIONAL)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Management Mode", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isDeviceOwner) CyberGreenGlow else CyberAmberGlow,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isDeviceOwner) "Device Owner Active" else "Standard APK Mode",
                                color = if (isDeviceOwner) CyberGreen else CyberAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "For corporate or supervised devices requiring uninstall prevention and Chrome Incognito lockdown, provision via ADB before account enrollment:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CyberBackground),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = adbCommand,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = CyberGreen
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(adbCommand))
                                    Toast.makeText(context, "ADB command copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyberBlue, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Command", color = TextPrimary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Restore Configuration Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Configuration Backup", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Paste the exported SafeNet JSON backup below. Integrity signature will be validated automatically.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreInputText,
                        onValueChange = { restoreInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("Paste JSON backup here...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberGreen,
                            unfocusedBorderColor = CyberCardBorder
                        )
                    )
                    if (restoreError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(restoreError!!, color = CyberRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = ConfigRecoveryManager.importConfiguration(context, restoreInputText)
                        if (result.success) {
                            showRestoreDialog = false
                            Toast.makeText(
                                context,
                                "Restored successfully: ${result.restoredRulesCount} custom rules, ${result.restoredAllowlistCount} allowlisted domains.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            restoreError = result.error
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color.Black)
                ) {
                    Text("Validate & Restore", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CyberCard
        )
    }
}
