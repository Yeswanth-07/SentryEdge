package com.enerix.sentryscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMonitorScreen(
    vpnActive: Boolean,
    onBack: () -> Unit,
    onToggleVpn: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live traffic monitor") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("On-device network observer", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Runs a local VPN tunnel to see which domains each app contacts. Nothing is routed off this device — the tunnel exists only so Android lets us read outbound packets.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = vpnActive, onCheckedChange = { onToggleVpn() })
                        Spacer(Modifier.width(8.dp))
                        Text(if (vpnActive) "Monitor: ON — logging real traffic" else "Monitor: OFF")
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("What happens next", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Turn the monitor on and accept Android's VPN prompt.\n" +
                        "2. Use your phone normally for a few minutes.\n" +
                        "3. Go back to the Dashboard and tap Re-scan device — domains logged during this window feed into each app's Network, Domain, and Behavior risk scores.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Known limitation", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This build reads plaintext DNS (UDP/53) only. Much of today's traffic uses encrypted DNS-over-HTTPS, which this won't see the query for. A production version adds TLS ClientHello SNI inspection on port 443 for full domain visibility without decrypting anything.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
