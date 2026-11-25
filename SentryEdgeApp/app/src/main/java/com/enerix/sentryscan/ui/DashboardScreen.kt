package com.enerix.sentryscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enerix.sentryscan.DashboardStats
import com.enerix.sentryscan.ScanState
import com.enerix.sentryscan.model.RiskTier
import com.enerix.sentryscan.model.ScanResult
import com.enerix.sentryscan.ui.theme.SentryAccent
import com.enerix.sentryscan.ui.theme.SentryBlue
import com.enerix.sentryscan.ui.theme.SentryTeal

fun tierColor(tier: RiskTier) = when (tier) {
    RiskTier.CRITICAL -> SentryAccent
    RiskTier.HIGH -> SentryAccent
    RiskTier.MEDIUM -> SentryBlue
    RiskTier.LOW -> SentryTeal
}

fun tierDot(tier: RiskTier) = when (tier) {
    RiskTier.CRITICAL -> "🔴" // red circle
    RiskTier.HIGH -> "🔴"
    RiskTier.MEDIUM -> "🟠" // orange circle
    RiskTier.LOW -> "🟢"    // green circle
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    scanState: ScanState,
    dashboard: DashboardStats?,
    vpnActive: Boolean,
    onScan: () -> Unit,
    onToggleVpn: () -> Unit,
    onViewAllApps: () -> Unit,
    onAlertClick: (ScanResult) -> Unit,
    onOpenLiveMonitor: () -> Unit,
    onOpenThreatIntel: () -> Unit,
    onOpenAiAdvisor: () -> Unit,
    onOpenBenchmark: () -> Unit,
    demoThreatActive: Boolean,
    onToggleDemoThreat: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("SentryEdge") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (dashboard == null) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No scan yet", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Run a scan to build your security dashboard from this device's real installed apps.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                OverallRiskCard(dashboard)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("Apps scanned", dashboard.appsScanned.toString(), Modifier.weight(1f))
                    StatTile("Suspicious apps", dashboard.suspiciousApps.toString(), Modifier.weight(1f), alert = dashboard.suspiciousApps > 0)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("Suspicious domains", dashboard.suspiciousDomains.toString(), Modifier.weight(1f), alert = dashboard.suspiciousDomains > 0)
                    StatTile("Privacy warnings", dashboard.privacyWarnings.toString(), Modifier.weight(1f), alert = dashboard.privacyWarnings > 0)
                }

                Text("Recent alerts", fontWeight = FontWeight.SemiBold)
                if (dashboard.recentAlerts.isEmpty()) {
                    Text("No high-risk apps found.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dashboard.recentAlerts.forEach { alert ->
                            AlertRow(alert, onClick = { onAlertClick(alert) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text(if (scanState is ScanState.Scanning) "Scanning…" else if (dashboard == null) "Start security scan" else "Re-scan device")
            }
            OutlinedButton(onClick = onViewAllApps, modifier = Modifier.fillMaxWidth(), enabled = dashboard != null) {
                Text("View all scanned apps")
            }
            FilledTonalButton(onClick = onOpenAiAdvisor, modifier = Modifier.fillMaxWidth()) {
                Text("Scam check (on-device AI)")
            }
            TextButton(onClick = onOpenLiveMonitor, modifier = Modifier.fillMaxWidth()) {
                Text(if (vpnActive) "Traffic monitor running — open live view" else "Open live traffic monitor")
            }
            TextButton(onClick = onOpenBenchmark, modifier = Modifier.fillMaxWidth()) {
                Text("Arm benchmark")
            }
            TextButton(onClick = onOpenThreatIntel, modifier = Modifier.fillMaxWidth()) {
                Text("Optional: check a domain online")
            }


        }
    }
}

@Composable
private fun OverallRiskCard(dashboard: DashboardStats) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SECURITY DASHBOARD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(tierColor(dashboard.overallTier)),
                contentAlignment = Alignment.Center
            ) {
                Text("${dashboard.overallScore}", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(dashboard.overallTier.name + " RISK", fontWeight = FontWeight.Bold, color = tierColor(dashboard.overallTier))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, alert: Boolean = false) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                color = if (alert) SentryAccent else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun AlertRow(result: ScanResult, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tierDot(result.tier), modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(result.app.appName, fontWeight = FontWeight.SemiBold)
                Text(
                    result.problems.firstOrNull() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(tierColor(result.tier)).padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("${result.score}", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
