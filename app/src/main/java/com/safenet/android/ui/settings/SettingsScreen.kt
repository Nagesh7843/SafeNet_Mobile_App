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
    val keyStoreHelper = app.keyStoreHelper
    val resolver = SafeNetVpnService.dnsResolver

    var isDeviceOwner by remember { mutableStateOf(dpcManager.isDeviceOwner()) }
    var selectedProvider by remember {
        mutableStateOf(resolver?.currentProvider ?: DnsResolver.UpstreamProvider.CLOUDFLARE_FAMILY)
    }

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
        // Upstream DNS Resolver Section
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

        // Device Owner Provisioning Guide Card
        item {
            Text(
                text = "DEVICE OWNER MANAGEMENT PROVISIONING",
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
                                text = if (isDeviceOwner) "Device Owner Active" else "Unmanaged",
                                color = if (isDeviceOwner) CyberGreen else CyberAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "To enable fail-closed Always-On VPN lockdown and Chrome Incognito restrictions on personally owned devices, provision via ADB:",
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

                    Text(
                        text = "Note: Temporarily remove all existing accounts in Settings -> Accounts before issuing the ADB command. Re-add them immediately after.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Hardware Keystore & Recovery Unenrollment
        item {
            Text(
                text = "RECOVERY & AUTHORIZED UNENROLLMENT",
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
                        text = "Authorized Unenrollment",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Lifts VPN lockdown, clears user restrictions, and restores standard unmanaged device state with hardware passcode authorization.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Button(
                        onClick = { showUnenrollDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = CyberRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Initiate Authorized Unenrollment", color = CyberRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showUnenrollDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnenrollDialog = false
                unenrollError = null
            },
            title = { Text("Authorize Unenrollment", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "Enter master recovery passcode (default: 'safenet-admin' for development) to lift device management.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = unenrollPasscode,
                        onValueChange = { unenrollPasscode = it },
                        placeholder = { Text("Passcode") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberRed,
                            unfocusedBorderColor = CyberCardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (unenrollError != null) {
                        Text(unenrollError!!, color = CyberRed, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (unenrollPasscode == "safenet-admin") {
                            dpcManager.unenrollDeviceOwner()
                            isDeviceOwner = dpcManager.isDeviceOwner()
                            showUnenrollDialog = false
                            Toast.makeText(context, "Device unenrollment completed", Toast.LENGTH_LONG).show()
                        } else {
                            unenrollError = "Incorrect authorization passcode."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                ) {
                    Text("Confirm Unenroll", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnenrollDialog = false
                    unenrollError = null
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CyberCard
        )
    }
}
