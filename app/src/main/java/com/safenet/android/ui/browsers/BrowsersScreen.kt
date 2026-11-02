package com.safenet.android.ui.browsers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safenet.android.SafeNetApplication
import com.safenet.android.domain.model.BrowserInfo
import com.safenet.android.ui.theme.*

@Composable
fun BrowsersScreen(
    modifier: Modifier = Modifier
) {
    val app = SafeNetApplication.instance
    val browserManager = app.browserPolicyManager
    val dpcManager = app.dpcManager

    var browsers by remember { mutableStateOf(browserManager.getInstalledBrowsers()) }
    var isChromeRestricted by remember { mutableStateOf(browserManager.isChromeIncognitoRestricted()) }

    LaunchedEffect(Unit) {
        browsers = browserManager.getInstalledBrowsers()
        isChromeRestricted = browserManager.isChromeIncognitoRestricted()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Architecture Note Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyberBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Browser Incognito restrictions apply via Android Enterprise Managed Configurations. Where browsers lack managed config APIs (e.g. Firefox), SafeNet enforces identical filtering at the kernel network level.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Chrome Incognito Control
        item {
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isChromeRestricted) CyberGreenGlow else CyberAmberGlow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (isChromeRestricted) CyberGreen else CyberAmber
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Chrome Incognito", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                if (isChromeRestricted) "Incognito mode is disabled" else "Incognito mode allowed",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Switch(
                            checked = isChromeRestricted,
                            onCheckedChange = { disable ->
                                if (dpcManager.isDeviceOwner()) {
                                    browserManager.enforceChromeIncognitoRestriction(disable)
                                    isChromeRestricted = browserManager.isChromeIncognitoRestricted()
                                    browsers = browserManager.getInstalledBrowsers()
                                }
                            },
                            enabled = dpcManager.isDeviceOwner(),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberBackground,
                                checkedTrackColor = CyberGreen
                            )
                        )
                    }

                    if (!dpcManager.isDeviceOwner()) {
                        Text(
                            text = "Device Owner provisioning required to enforce Chrome managed configurations.",
                            color = CyberAmber,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // Discovered Browsers Inventory
        item {
            Text(
                text = "DISCOVERED BROWSERS & POLICIES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }

        items(browsers) { browser ->
            BrowserCard(browser = browser)
        }
    }
}

@Composable
private fun BrowserCard(browser: BrowserInfo) {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = if (browser.isInstalled) CyberGreen else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(browser.displayName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (browser.isInstalled) CyberGreenGlow else CyberSurface,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (browser.isInstalled) "Installed" else "Not Installed",
                        color = if (browser.isInstalled) CyberGreen else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Policy: ${browser.managedConfigStatus}", color = TextSecondary, fontSize = 12.sp)
            Text("Network Filter: ${browser.networkFilteringStatus}", color = CyberBlue, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
