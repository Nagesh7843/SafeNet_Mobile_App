package com.safenet.android.ui.protection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safenet.android.SafeNetApplication
import com.safenet.android.domain.model.DomainCategory
import com.safenet.android.engine.vpn.SafeNetVpnService
import com.safenet.android.ui.theme.*

@Composable
fun ProtectionScreen(
    modifier: Modifier = Modifier
) {
    val app = SafeNetApplication.instance
    val dpcManager = app.dpcManager
    val filterEngine = SafeNetVpnService.filterEngine

    var isLockdown by remember { mutableStateOf(dpcManager.isAlwaysOnLockdownEnabled()) }
    var safeSearchEnabled by remember { mutableStateOf(filterEngine.enforceSafeSearch) }

    // Track category toggle states
    val categoryStates = remember {
        mutableStateMapOf<DomainCategory, Boolean>().apply {
            for (cat in DomainCategory.values()) {
                this[cat] = filterEngine.isCategoryEnabled(cat)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Enforced System Hardening Section
        item {
            Text(
                text = "OS-LEVEL LOCKDOWN & TAMPER PROTECTION",
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
                    ProtectionSwitchRow(
                        title = "Always-On VPN Lockdown",
                        description = "Drops all network egress if SafeNet is stopped. Requires Device Owner.",
                        isChecked = isLockdown,
                        onCheckedChange = { enable ->
                            if (dpcManager.isDeviceOwner()) {
                                dpcManager.setAlwaysOnVpn(enable)
                                isLockdown = dpcManager.isAlwaysOnLockdownEnabled()
                            }
                        },
                        icon = Icons.Default.Lock
                    )

                    Divider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 12.dp))

                    ProtectionSwitchRow(
                        title = "DNS Safe Search Enforcement",
                        description = "Rewrites Google, Bing, YouTube, and DuckDuckGo DNS to strict safe VIPs.",
                        isChecked = safeSearchEnabled,
                        onCheckedChange = { enable ->
                            safeSearchEnabled = enable
                            filterEngine.enforceSafeSearch = enable
                        },
                        icon = Icons.Default.Search
                    )
                }
            }
        }

        // Domain Categories Filtering Section
        item {
            Text(
                text = "CONTENT FILTERING CATEGORIES",
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
                    DomainCategory.values().forEachIndexed { index, category ->
                        if (index > 0) {
                            Divider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 12.dp))
                        }
                        val currentVal = categoryStates[category] ?: category.defaultEnabled
                        ProtectionSwitchRow(
                            title = category.title,
                            description = category.description,
                            isChecked = currentVal,
                            onCheckedChange = { enabled ->
                                categoryStates[category] = enabled
                                filterEngine.setCategoryEnabled(category, enabled)
                            },
                            icon = getCategoryIcon(category)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionSwitchRow(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isChecked) CyberGreen else TextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberBackground,
                checkedTrackColor = CyberGreen,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CyberSurface
            )
        )
    }
}

private fun getCategoryIcon(category: DomainCategory): ImageVector {
    return when (category) {
        DomainCategory.ADULT -> Icons.Default.NoAdultContent
        DomainCategory.PORNOGRAPHY -> Icons.Default.VisibilityOff
        DomainCategory.EXPLICIT_SEXUAL -> Icons.Default.Warning
        DomainCategory.GAMBLING -> Icons.Default.Casino
        DomainCategory.MALWARE -> Icons.Default.BugReport
        DomainCategory.PHISHING -> Icons.Default.Phishing
        DomainCategory.MALICIOUS_REDIRECTS -> Icons.Default.Directions
        DomainCategory.CUSTOM_BLOCKED -> Icons.Default.Block
        DomainCategory.CUSTOM_ALLOWED -> Icons.Default.CheckCircle
    }
}
