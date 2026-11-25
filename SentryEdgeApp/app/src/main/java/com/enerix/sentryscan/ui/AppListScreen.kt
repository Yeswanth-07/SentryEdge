package com.enerix.sentryscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enerix.sentryscan.model.ScanResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    results: List<ScanResult>,
    onBack: () -> Unit,
    onAppClick: (ScanResult) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanned apps") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "${results.size} apps · ${results.count { it.tier.name == "HIGH" || it.tier.name == "CRITICAL" }} flagged",
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { result ->
                    AppRiskRow(result, onClick = { onAppClick(result) })
                }
            }
        }
    }
}

@Composable
private fun AppRiskRow(result: ScanResult, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tierColor(result.tier)),
                contentAlignment = Alignment.Center
            ) {
                Text(result.score.toString(), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(result.app.appName, fontWeight = FontWeight.SemiBold)
                Text(result.app.category, style = MaterialTheme.typography.bodySmall)
            }
            AssistChip(onClick = onClick, label = { Text(result.tier.name) })
        }
    }
}
