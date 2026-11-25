package com.enerix.sentryscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.enerix.sentryscan.llm.AdvisorState
import com.enerix.sentryscan.llm.ArmInfo
import com.enerix.sentryscan.llm.BenchmarkRunner
import com.enerix.sentryscan.llm.BenchmarkSummary
import com.enerix.sentryscan.llm.GenerationStats
import com.enerix.sentryscan.llm.LlmAdvisor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(lastScanApps: Int?, lastScanMs: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val arm = remember { ArmInfo.read() }
    val modelState by LlmAdvisor.state.collectAsState()

    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var runs by remember { mutableStateOf<List<GenerationStats>>(emptyList()) }
    var copied by remember { mutableStateOf(false) }

    fun startBenchmark() {
        if (running) return
        running = true
        copied = false
        runs = emptyList()
        progress = "Loading the model..."
        scope.launch {
            if (LlmAdvisor.ensureLoaded(context)) {
                runs = BenchmarkRunner.run { progress = it }
            } else {
                progress = "Model not available. Add the model files first (see Scam check)."
            }
            running = false
        }
    }

    val reportText = BenchmarkSummary.format(arm, lastScanApps, lastScanMs, modelState, runs)
    val avg = BenchmarkSummary.averages(runs)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arm benchmark") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("THIS DEVICE", style = MaterialTheme.typography.labelMedium)
                    Text("Chip: ${arm.soc}")
                    Text("Architecture: ${arm.abi}")
                    Text("CPU cores: ${arm.cores}")
                    Text(
                        "Arm features: " + if (arm.features.isEmpty()) "not reported by this device" else arm.features.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("RULE ENGINE", style = MaterialTheme.typography.labelMedium)
                    if (lastScanApps != null && lastScanMs != null) {
                        Text("Scanned $lastScanApps apps in $lastScanMs ms")
                        Text("Reads permissions, scores every app and saves the results.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Run a scan on the Dashboard first, then come back here.")
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ON-DEVICE LLAMA MODEL", style = MaterialTheme.typography.labelMedium)
                    when (val s = modelState) {
                        is AdvisorState.Ready -> Text("${s.modelName}\nLoad time ${s.loadMs} ms, memory after load ${s.rssAfterLoadMb} MB")
                        is AdvisorState.Loading -> Text("Loading the model...")
                        is AdvisorState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                        is AdvisorState.NoModel -> Text("Not loaded yet. Running the benchmark loads it.")
                    }
                    Text(
                        "Runs one warm-up and three measured checks on a fixed set of messages. Uses ExecuTorch with the XNNPACK CPU backend.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = { startBenchmark() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                        Text(if (running) "Running..." else "Run benchmark")
                    }
                    if (running) {
                        Text(progress, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else if (progress.isNotBlank() && runs.isEmpty()) {
                        Text(progress, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (runs.isNotEmpty()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("RESULTS", style = MaterialTheme.typography.labelMedium)
                        runs.forEachIndexed { i, r ->
                            Text(
                                "Run ${i + 1}: first token ${r.firstTokenMs} ms, ${"%.1f".format(r.decodeTokensPerSec)} tokens/s, ${r.tokens} tokens, memory ${r.rssMb} MB",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (avg != null) {
                            Text(
                                "Average: first token ${"%.0f".format(avg.firstTokenMs)} ms, ${"%.1f".format(avg.decodeTokensPerSec)} tokens/s",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(reportText))
                                copied = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (copied) "Copied. Paste it into your report." else "Copy results for the report")
                        }
                    }
                }
            }
        }
    }
}
