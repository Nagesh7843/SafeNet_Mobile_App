package com.safenet.android.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safenet.android.SafeNetApplication
import com.safenet.android.domain.model.BlockEvent
import com.safenet.android.domain.model.SecurityEvent
import com.safenet.android.engine.vpn.SafeNetVpnService
import com.safenet.android.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportsScreen(
    modifier: Modifier = Modifier
) {
    val app = SafeNetApplication.instance
    val db = app.databaseHelper
    val filterEngine = SafeNetVpnService.filterEngine

    var blockEvents by remember { mutableStateOf<List<BlockEvent>>(emptyList()) }
    var securityEvents by remember { mutableStateOf<List<SecurityEvent>>(emptyList()) }
    var totalBlocked by remember { mutableStateOf(filterEngine.totalQueriesBlocked.get()) }
    var adultBlocked by remember { mutableStateOf(filterEngine.adultQueriesBlocked.get()) }
    var malwareBlocked by remember { mutableStateOf(filterEngine.malwareQueriesBlocked.get()) }
    var phishingBlocked by remember { mutableStateOf(filterEngine.phishingQueriesBlocked.get()) }

    var showPurgeConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        blockEvents = db.getRecentBlockEvents()
        securityEvents = db.getRecentSecurityEvents()
        totalBlocked = filterEngine.totalQueriesBlocked.get()
        adultBlocked = filterEngine.adultQueriesBlocked.get()
        malwareBlocked = filterEngine.malwareQueriesBlocked.get()
        phishingBlocked = filterEngine.phishingQueriesBlocked.get()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Analytics Summary Grid
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("THREAT & CONTENT INTERCEPTIONS", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CategoryStatCol("Adult Blocked", adultBlocked.toString(), CyberRed)
                        CategoryStatCol("Malware Blocked", malwareBlocked.toString(), CyberAmber)
                        CategoryStatCol("Phishing Blocked", phishingBlocked.toString(), CyberBlue)
                    }
                }
            }
        }

        // Action Toolbar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT FILTERED EVENTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                TextButton(
                    onClick = { showPurgeConfirmation = true }
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = CyberRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Purge Logs", color = CyberRed, fontSize = 12.sp)
                }
            }
        }

        if (blockEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberCard),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No blocked events recorded yet", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(blockEvents) { event ->
                BlockEventRow(event = event)
            }
        }
    }

    if (showPurgeConfirmation) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmation = false },
            title = { Text("Purge Event History", color = TextPrimary) },
            text = {
                Text("This will delete local block records and non-critical security telemetry. Active policies and device config will be preserved.", color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        db.purgeEventHistory()
                        blockEvents = emptyList()
                        showPurgeConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                ) {
                    Text("Purge All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmation = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CyberCard
        )
    }
}

@Composable
private fun CategoryStatCol(label: String, value: String, color: Color) {
    Column {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BlockEventRow(event: BlockEvent) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(event.timestamp) { dateFormat.format(Date(event.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.domain, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Category: ${event.category.title}", color = CyberRed, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formattedTime, color = TextSecondary, fontSize = 11.sp)
                Text(event.actionTaken, color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}
