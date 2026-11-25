package com.enerix.sentryscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enerix.sentryscan.data.AppDatabase
import com.enerix.sentryscan.threatintel.ThreatIntelClient
import com.enerix.sentryscan.threatintel.ThreatIntelVerdict
import com.enerix.sentryscan.ui.theme.SentryAccent
import com.enerix.sentryscan.ui.theme.SentryTeal
import kotlinx.coroutines.launch

private sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data class Done(val domain: String, val results: List<ThreatIntelVerdict>) : CheckState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatIntelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var domainInput by remember { mutableStateOf("") }
    var checkState by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var observedHosts by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        observedHosts = AppDatabase.getInstance(context).domainHitDao().distinctHosts()
    }

    fun runCheck(domain: String) {
        if (domain.isBlank()) return
        checkState = CheckState.Checking
        scope.launch {
            val results = ThreatIntelClient.checkDomain(domain.trim())
            checkState = CheckState.Done(domain.trim(), results)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Threat intelligence") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Optional online check. SentryScan's scoring and AI advice work fully offline; this screen is the only part that uses the internet, and only when you tap Check. Sources: URLhaus, VirusTotal, AbuseIPDB, PhishTank.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = domainInput,
                onValueChange = { domainInput = it },
                label = { Text("Domain, e.g. example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { runCheck(domainInput) },
                enabled = domainInput.isNotBlank() && checkState !is CheckState.Checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (checkState is CheckState.Checking) "Checking…" else "Check this domain")
            }

            if (observedHosts.isNotEmpty()) {
                Text("Or pick a domain seen on this device:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(observedHosts) { host ->
                        AssistChip(
                            onClick = { domainInput = host; runCheck(host) },
                            label = { Text(host, maxLines = 1) }
                        )
                    }
                }
            }

            when (val state = checkState) {
                is CheckState.Idle -> {}
                is CheckState.Checking -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is CheckState.Done -> {
                    Spacer(Modifier.height(4.dp))
                    Text("Results for ${state.domain}", fontWeight = FontWeight.SemiBold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.results) { verdict -> VerdictCard(verdict) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerdictCard(verdict: ThreatIntelVerdict) {
    val statusColor = when {
        !verdict.checked -> MaterialTheme.colorScheme.onSurfaceVariant
        verdict.malicious -> SentryAccent
        else -> SentryTeal
    }
    val statusLabel = when {
        !verdict.checked -> "SKIPPED"
        verdict.malicious -> "SUSPICIOUS"
        else -> "SAFE"
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(verdict.source, fontWeight = FontWeight.SemiBold)
                Text(verdict.note, style = MaterialTheme.typography.bodySmall)
            }
            Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}
