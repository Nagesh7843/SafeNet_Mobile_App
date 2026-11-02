package com.safenet.android.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safenet.android.engine.vpn.SafeNetVpnService
import com.safenet.android.ui.blocklists.BlocklistsScreen
import com.safenet.android.ui.browsers.BrowsersScreen
import com.safenet.android.ui.dashboard.DashboardScreen
import com.safenet.android.ui.protection.ProtectionScreen
import com.safenet.android.ui.reports.ReportsScreen
import com.safenet.android.ui.settings.SettingsScreen
import com.safenet.android.ui.theme.*

enum class SafeNetDestination(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    PROTECTION("Protection", Icons.Default.Security),
    BLOCKLISTS("Blocklists", Icons.Default.FormatListBulleted),
    BROWSERS("Browsers", Icons.Default.Language),
    REPORTS("Reports", Icons.Default.Assessment),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            launchVpnService()
        } else {
            Toast.makeText(this, "VPN permission required for network filtering", Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SafeNetTheme {
                var currentDestination by remember { mutableStateOf(SafeNetDestination.DASHBOARD) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row {
                                    Text(
                                        text = "SafeNet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SHIELD",
                                        fontWeight = FontWeight.Light,
                                        fontSize = 18.sp,
                                        color = CyberGreen
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = CyberSurface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = CyberSurface,
                            tonalElevation = 8.dp
                        ) {
                            SafeNetDestination.values().forEach { destination ->
                                NavigationBarItem(
                                    selected = currentDestination == destination,
                                    onClick = { currentDestination = destination },
                                    icon = {
                                        Icon(
                                            destination.icon,
                                            contentDescription = destination.title,
                                            tint = if (currentDestination == destination) CyberGreen else TextMuted
                                        )
                                    },
                                    label = {
                                        Text(
                                            destination.title,
                                            fontSize = 10.sp,
                                            color = if (currentDestination == destination) CyberGreen else TextMuted
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = CyberGreenGlow
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(CyberBackground)
                    ) {
                        when (currentDestination) {
                            SafeNetDestination.DASHBOARD -> DashboardScreen(
                                onToggleVpn = { toggleVpn() }
                            )
                            SafeNetDestination.PROTECTION -> ProtectionScreen()
                            SafeNetDestination.BLOCKLISTS -> BlocklistsScreen()
                            SafeNetDestination.BROWSERS -> BrowsersScreen()
                            SafeNetDestination.REPORTS -> ReportsScreen()
                            SafeNetDestination.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }

    private fun toggleVpn() {
        if (SafeNetVpnService.isServiceActive) {
            val intent = Intent(this, SafeNetVpnService::class.java).apply {
                action = SafeNetVpnService.ACTION_STOP_VPN
            }
            startService(intent)
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                launchVpnService()
            }
        }
    }

    private fun launchVpnService() {
        val intent = Intent(this, SafeNetVpnService::class.java).apply {
            action = SafeNetVpnService.ACTION_START_VPN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
