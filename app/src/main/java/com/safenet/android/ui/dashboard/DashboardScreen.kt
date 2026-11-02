package com.safenet.android.ui.dashboard

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safenet.android.SafeNetApplication
import com.safenet.android.core.launcher.LauncherManager
import com.safenet.android.core.state.ProtectionState
import com.safenet.android.core.state.ProtectionStateManager
import com.safenet.android.core.vpn.AlwaysOnVpnHelper
import com.safenet.android.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    onToggleVpn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = SafeNetApplication.instance
    val dpcManager = app.dpcManager

    // Collect reactive state directly from the centralized ProtectionStateManager
    val details by ProtectionStateManager.instance.details.collectAsState()

    var isIconVisible by remember {
        mutableStateOf(LauncherManager.isLauncherIconVisible(context))
    }

    val stateColor = when (details.state) {
        ProtectionState.ACTIVE -> CyberGreen
        ProtectionState.DEGRADED -> CyberAmber
        ProtectionState.NETWORK_UNAVAILABLE -> CyberAmber
        ProtectionState.NOT_CONFIGURED, ProtectionState.VPN_PERMISSION_REQUIRED -> CyberBlue
        ProtectionState.ACTIVATING -> CyberBlue
        ProtectionState.VPN_DISABLED, ProtectionState.RECOVERY_REQUIRED -> CyberRed
    }

    val stateGlow = when (details.state) {
        ProtectionState.ACTIVE -> CyberGreenGlow
        ProtectionState.DEGRADED, ProtectionState.NETWORK_UNAVAILABLE -> CyberAmberGlow
        ProtectionState.NOT_CONFIGURED, ProtectionState.VPN_PERMISSION_REQUIRED -> CyberBlueGlow
        ProtectionState.ACTIVATING -> CyberBlueGlow
        ProtectionState.VPN_DISABLED, ProtectionState.RECOVERY_REQUIRED -> CyberRedGlow
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Shield Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(stateGlow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (details.state) {
                                ProtectionState.ACTIVE -> Icons.Default.Shield
                                ProtectionState.DEGRADED -> Icons.Default.Warning
                                ProtectionState.NETWORK_UNAVAILABLE -> Icons.Default.SignalCellularConnectedNoInternet0Bar
                                else -> Icons.Default.ShieldMoon
                            },
                            contentDescription = "Shield Status",
                            tint = stateColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = details.state.displayName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = stateColor
                    )

                    Text(
                        text = when (details.state) {
                            ProtectionState.ACTIVE -> "Local TUN interface active with sub-millisecond DNS inspection"
                            ProtectionState.DEGRADED -> "Protection degraded: ${details.missingRequirementMessage ?: "Inspection warnings detected"}"
                            ProtectionState.NETWORK_UNAVAILABLE -> "Waiting for physical Wi-Fi or cellular network interface"
                            ProtectionState.VPN_DISABLED -> "Network traffic is currently unfiltered"
                            ProtectionState.RECOVERY_REQUIRED -> "Protection stopped: manual authorization or restart needed"
                            ProtectionState.ACTIVATING -> "Establishing secure local TUN interface..."
                            else -> "Configuration or VPN permissions required"
                        },
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    if (details.isVpnInterfaceUp) {
                        // Strict Lock Edition: Once DNS protection is active, deactivation is removed
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = CyberGreen.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CyberGreenGlow, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Protection Locked Active",
                                    tint = CyberGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Protection Locked & Active",
                                        fontWeight = FontWeight.Bold,
                                        color = CyberGreen,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Deactivation disabled • Permanent enforcement",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = onToggleVpn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Activate SafeNet Protection",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Interruption / Degradation Warning Alert (if applicable)
        if (details.lastInterruptionReason != null && details.state != ProtectionState.ACTIVE) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberAmberGlow, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberCard)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyberAmber,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Last Interruption", color = CyberAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = details.lastInterruptionReason ?: "Unknown cause",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            if (details.lastInterruptionTimestamp > 0) {
                                Text(
                                    text = "Interrupted at ${timeFormatter.format(Date(details.lastInterruptionTimestamp))}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Metric Counters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Blocked Requests",
                    value = details.totalQueriesBlocked.toString(),
                    icon = Icons.Default.Block,
                    accentColor = CyberRed,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Rules in Trie",
                    value = details.ruleCount.toString(),
                    icon = Icons.Default.ListAlt,
                    accentColor = CyberBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Multi-Layer System Verification Checklist
        item {
            Text(
                text = "SYSTEM PROTECTION HEALTH",
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
                    HealthStatusRow(
                        title = "VPN Protection Tunnel",
                        subtitle = if (details.isVpnInterfaceUp) "TUN bound (10.254.1.2 / fd00::2)" else "Tunnel offline",
                        isActive = details.isVpnInterfaceUp,
                        icon = Icons.Default.VpnKey
                    )
                    Divider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 10.dp))

                    HealthStatusRow(
                        title = "DNS Filtering Engine",
                        subtitle = if (details.isDnsEngineOperational) "Zero-latency radix trie active" else "Inspection inactive",
                        isActive = details.isDnsEngineOperational,
                        icon = Icons.Default.Dns
                    )
                    Divider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 10.dp))

                    HealthStatusRow(
                        title = "Upstream Security DNS",
                        subtitle = "Target: ${details.activeUpstreamResolver}",
                        isActive = details.isNetworkConnected && details.isVpnInterfaceUp,
                        icon = Icons.Default.Language
                    )
                    Divider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 10.dp))

                    HealthStatusRow(
                        title = "Launcher Icon Independence",
                        subtitle = if (isIconVisible) "Launcher icon visible in drawer" else "Launcher icon hidden (Protection unaffected)",
                        isActive = true,
                        icon = if (isIconVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    )
                }
            }
        }

        // Launcher Icon Quick Action
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isIconVisible) "Hide App Launcher Icon" else "Restore Launcher Icon",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isIconVisible) "Hiding removes the icon from drawer while DNS filtering continues without interruption." else "Restores the icon back to your app drawer.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val newVisibility = !isIconVisible
                            val success = LauncherManager.setLauncherIconVisible(context, newVisibility)
                            if (success) {
                                isIconVisible = newVisibility
                                Toast.makeText(
                                    context,
                                    if (newVisibility) "Launcher icon restored" else "Launcher icon hidden. Protection continues! Reopen via notification or safenet://open",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isIconVisible) CyberAmber else CyberGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isIconVisible) "Hide Icon" else "Show Icon", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Network Diagnostics
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Active Physical Interface", color = TextSecondary, fontSize = 12.sp)
                        Text(details.networkInterfaceType, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total Queries Processed", color = TextSecondary, fontSize = 12.sp)
                        Text(details.totalQueriesProcessed.toString(), color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HealthStatusRow(
    title: String,
    subtitle: String,
    isActive: Boolean,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isActive) CyberGreenGlow else CyberRedGlow),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) CyberGreen else CyberRed,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }

        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isActive) CyberGreen else CyberRed)
        )
    }
}
