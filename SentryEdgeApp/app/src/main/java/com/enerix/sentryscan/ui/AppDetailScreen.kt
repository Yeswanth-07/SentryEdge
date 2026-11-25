package com.enerix.sentryscan.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enerix.sentryscan.llm.LlmAdvisor
import com.enerix.sentryscan.llm.LlmOutputParser
import com.enerix.sentryscan.llm.PromptTemplates
import com.enerix.sentryscan.model.ScanResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(result: ScanResult, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var aiText by remember(result.app.packageName) { mutableStateOf("") }
    var aiBusy by remember(result.app.packageName) { mutableStateOf(false) }
    var aiNote by remember(result.app.packageName) { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(result.app.appName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            item { ReportCard(result) }

            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ON-DEVICE AI ADVICE", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "The score above comes from fixed rules. The AI model on this phone only explains it in plain words.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (aiText.isNotBlank()) Text(LlmOutputParser.clean(aiText))
                        aiNote?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (aiBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        OutlinedButton(
                            enabled = !aiBusy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                aiBusy = true
                                aiText = ""
                                aiNote = null
                                scope.launch {
                                    if (LlmAdvisor.ensureLoaded(context)) {
                                        val r = LlmAdvisor.generate(PromptTemplates.explainApp(result), 160) { aiText = it }
                                        if (r != null) { aiText = r.text; aiNote = r.error }
                                    } else {
                                        aiNote = "Model not available. Open Scam check to see how to add the model files."
                                    }
                                    aiBusy = false
                                }
                            }
                        ) { Text(if (aiBusy) "Thinking..." else "Explain this result") }
                    }
                }
            }

            item {
                Text("Problems detected", fontWeight = FontWeight.SemiBold)
            }
            items(result.problems) { problem ->
                Text("• $problem")
            }

            item {
                Text("Permissions held (risk weight)", fontWeight = FontWeight.SemiBold)
            }
            items(result.heldPermissions.sortedByDescending { it.weight }) { perm ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(perm.friendlyName)
                    Text("+${perm.weight}", fontWeight = FontWeight.Medium)
                }
            }

            if (result.trackerHits.isNotEmpty()) {
                item { Text("Suspicious domains contacted", fontWeight = FontWeight.SemiBold) }
                items(result.trackerHits) { host ->
                    Text("• $host", color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Text("Recommended actions", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if ("Review permissions" in result.recommendations) {
                        Button(onClick = {
                            context.startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", result.app.packageName, null)
                            ))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Review permissions")
                        }
                    }
                    if ("Restrict background data" in result.recommendations) {
                        OutlinedButton(onClick = {
                            // Android has no public API for one app to silently cut another
                            // app's network access. The real, non-root equivalent is the same
                            // screen Android's own Settings > Data usage uses, pre-scoped to
                            // this app, so the user can flip "Background data" off themselves.
                            context.startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", result.app.packageName, null)
                            ))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Restrict background data")
                        }
                    }
                    if ("Uninstall if not trusted" in result.recommendations) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(
                                    Intent.ACTION_DELETE,
                                    Uri.fromParts("package", result.app.packageName, null)
                                ))
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Uninstall")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(result: ScanResult) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("APP SECURITY REPORT", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(10.dp))
            BreakdownRow("Permission Risk", result.breakdown.permissionRisk, 40)
            BreakdownRow("Network Risk", result.breakdown.networkRisk, 30)
            BreakdownRow("Domain Risk", result.breakdown.domainRisk, 20)
            BreakdownRow("Behavior Risk", result.breakdown.behaviorRisk, 10)
            Divider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL", fontWeight = FontWeight.Bold)
                Text("${result.score}/100", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${tierDot(result.tier)}  ${result.tier.name} RISK",
                fontWeight = FontWeight.Bold,
                color = tierColor(result.tier)
            )
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: Int, max: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$value/$max", style = MaterialTheme.typography.bodyMedium)
    }
}
