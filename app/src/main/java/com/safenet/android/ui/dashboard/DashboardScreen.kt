package com.safenet.android.ui.dashboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.safenet.android.domain.model.OverallProtectionState
import com.safenet.android.engine.vpn.SafeNetVpnService
import com.safenet.android.ui.theme.*

@Composable
fun DashboardScreen(
    onToggleVpn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = SafeNetApplication.instance
    val dpcManager = app.dpcManager
    val browserManager = app.browserPolicyManager

    // Read real system states
    var isVpnActive by remember { mutableStateOf(SafeNetVpnService.isServiceActive) }
    var isDeviceOwner by remember { mutableStateOf(dpcManager.isDeviceOwner()) }
    var isLockdown by remember { mutableStateOf(dpcManager.isAlwaysOnLockdownEnabled()) }
    var isChromeRestricted by remember { mutableStateOf(browserManager.isChromeIncognitoRestricted()) }
    var totalBlocked by remember { mutableStateOf(SafeNetVpnService.filterEngine.totalQueriesBlocked.get()) }
    var totalProcessed by remember { mutableStateOf(SafeNetVpnService.filterEngine.totalQueriesProcessed.get()) }
    var ruleCount by remember { mutableStateOf(SafeNetVpnService.filterEngine.trie.ruleCount) }

    // Periodic refresh
    LaunchedEffect(Unit) {
        while (true) {
            isVpnActive = SafeNetVpnService.isServiceActive
            isDeviceOwner = dpcManager.isDeviceOwner()
            isLockdown = dpcManager.isAlwaysOnLockdownEnabled()
            isChromeRestricted = browserManager.isChromeIncognitoRestricted()
            totalBlocked = SafeNetVpnService.filterEngine.totalQueriesBlocked.get()
            totalProcessed = SafeNetVpnService.filterEngine.totalQueriesProcessed.get()
            ruleCount = SafeNetVpnService.filterEngine.trie.ruleCount
            kotlinx.coroutines.delay(1000)
        }
    }

    val overallState = when {
        !isVpnActive -> OverallProtectionState.VPN_DISCONNECTED
        !isDeviceOwner -> OverallProtectionState.PARTIALLY_PROTECTED
        isVpnActive && isDeviceOwner && isLockdown -> OverallProtectionState.PROTECTED
        else -> OverallProtectionState.PARTIALLY_PROTECTED
    }

    val networkType = remember(isVpnActive) {
        getNetworkType(context)
    }

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
                            .background(
                                if (isVpnActive) CyberGreenGlow else CyberRedGlow
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVpnActive) Icons.Default.Shield else Icons.Default.ShieldMoon,
                            contentDescription = "Shield Status",
                            tint = if (isVpnActive) CyberGreen else CyberRed,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = overallState.displayName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (overallState) {
                            OverallProtectionState.PROTECTED -> CyberGreen
                            OverallProtectionState.PARTIALLY_PROTECTED -> CyberAmber
                            else -> CyberRed
                        }
                    )

                    Text(
                        text = if (isVpnActive) "Always-on network tunnel and DNS inspection active" else "Network traffic is currently unfiltered",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    Button(
                        onClick = onToggleVpn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVpnActive) CyberRed else CyberGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isVpnActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isVpnActive) "Deactivate Protection" else "Activate SafeNet Protection",
                            fontWeight = FontWeight.Bold
                        )
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
                    value = totalBlocked.toString(),
                    icon = Icons.Default.Block,
                    accentColor = CyberRed,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Rules in Trie",
                    value = ruleCount.toString(),
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
                        title = "VPN Protection Engine",
                        subtitle = if (isVpnActive) "TUN interface 10.254.1.2 bound" else "TUN interface offline",
                        isActive = isVpnActive,
                        icon = Icons.Default.VpnKey
                    )
                    Divider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 10.dp))

                    HealthStatusRow(
                        title = "DNS Filtering Engine",
                        subtitle = if (ruleCount > 0) "Zero-latency trie matching active" else "No rules loaded",
                        isActive = ruleCount > 0 && isVpnActive,
                        icon = Icons.Default.Dns
                    )
                    Divider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 10.dp))

                    HealthStatusRow(
                        title = "Android Device Owner (DPC)",
                        subtitle = if (isDeviceOwner) "DevicePolicyManager fully managed" else "Provisioning required via ADB/QR",
                        isActive = isDeviceOwner,
                        icon = Icons.Default.AdminPanelSettings
                    )
                    Divider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 10.dp))

                    HealthStatusRow(
                        title = "OS-Level VPN Lockdown",
                        subtitle = if (isLockdown) "Fail-closed kernel policy engaged" else "Unrestricted fallback enabled",
                        isActive = isLockdown,
                        icon = Icons.Default.Lock
                    )
                    Divider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 10.dp))

                    HealthStatusRow(
                        title = "Chrome Incognito Restriction",
                        subtitle = if (isChromeRestricted) "IncognitoModeAvailability = 1" else "Chrome unmanaged or permitted",
                        isActive = isChromeRestricted,
                        icon = Icons.Default.VisibilityOff
                    )
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
                        Text("Active Interface", color = TextSecondary, fontSize = 12.sp)
                        Text(networkType, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total Queries", color = TextSecondary, fontSize = 12.sp)
                        Text(totalProcessed.toString(), color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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

private fun getNetworkType(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNet = cm.activeNetwork ?: return "Disconnected"
    val caps = cm.getNetworkCapabilities(activeNet) ?: return "Unknown"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Network"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular LTE/5G"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN Active"
        else -> "Connected"
    }
}
