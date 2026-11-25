package com.enerix.sentryscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enerix.sentryscan.llm.AdvisorState
import com.enerix.sentryscan.llm.GenerationStats
import com.enerix.sentryscan.llm.LlmAdvisor
import com.enerix.sentryscan.llm.LlmOutputParser
import com.enerix.sentryscan.llm.MessageVerdict
import com.enerix.sentryscan.llm.ModelLocator
import com.enerix.sentryscan.llm.PromptTemplates
import com.enerix.sentryscan.llm.RuleCheck
import com.enerix.sentryscan.llm.ScamHeuristics
import com.enerix.sentryscan.ui.theme.SentryAccent
import com.enerix.sentryscan.ui.theme.SentryBlue
import com.enerix.sentryscan.ui.theme.SentryTeal
import kotlinx.coroutines.launch

private fun verdictColor(v: MessageVerdict?): Color = when (v) {
    MessageVerdict.SCAM -> SentryAccent
    MessageVerdict.SUSPICIOUS -> SentryBlue
    MessageVerdict.SAFE -> SentryTeal
    null -> Color.Gray
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAdvisorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val modelState by LlmAdvisor.state.collectAsState()

    var filesPresent by remember { mutableStateOf(ModelLocator.find(context) != null) }
    var message by remember { mutableStateOf("") }
    var rule by remember { mutableStateOf<RuleCheck?>(null) }
    var aiRaw by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<GenerationStats?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    val opinion = remember(aiRaw) { LlmOutputParser.parseMessageOpinion(aiRaw) }

    fun runCheck() {
        val text = message.trim()
        if (text.isEmpty() || running) return
        val ruleResult = ScamHeuristics.check(text)
        rule = ruleResult
        aiRaw = ""
        stats = null
        note = null
        running = true
        scope.launch {
            val loaded = LlmAdvisor.ensureLoaded(context)
            filesPresent = ModelLocator.find(context) != null
            if (loaded) {
                val prompt = PromptTemplates.checkMessage(text, ruleResult.hits.map { it.label })
                val result = LlmAdvisor.generate(prompt, maxNewTokens = 90) { partial -> aiRaw = partial }
                if (result != null) {
                    aiRaw = result.text
                    stats = result.stats
                    note = result.error
                }
            }
            running = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scam check") },
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
            Text(
                "Paste a suspicious SMS or WhatsApp message. Rules and an AI model that runs on this phone both read it. Nothing is sent anywhere.",
                style = MaterialTheme.typography.bodySmall
            )

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ON-DEVICE MODEL", style = MaterialTheme.typography.labelMedium)
                    val statusLine = when (val s = modelState) {
                        is AdvisorState.Ready ->
                            "Ready: ${s.modelName}\nLoaded in ${s.loadMs} ms. Memory after load: ${s.rssAfterLoadMb} MB."
                        is AdvisorState.Loading -> "Loading the model..."
                        is AdvisorState.Failed -> s.message
                        is AdvisorState.NoModel ->
                            if (filesPresent) "Model files found. They load the first time you run a check."
                            else "Model files not found. The rule check still works. Add the model to turn on the AI opinion."
                    }
                    Text(statusLine, style = MaterialTheme.typography.bodyMedium)
                    if (modelState is AdvisorState.NoModel && !filesPresent) {
                        Text(ModelLocator.help(context), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { filesPresent = ModelLocator.find(context) != null }) {
                            Text("Check again")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message to check") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { clipboard.getText()?.text?.let { message = it } },
                    modifier = Modifier.weight(1f)
                ) { Text("Paste") }
                Button(
                    onClick = { runCheck() },
                    enabled = message.isNotBlank() && !running,
                    modifier = Modifier.weight(2f)
                ) { Text(if (running) "Checking..." else "Check message") }
            }

            val r = rule
            if (r != null) {
                val finalVerdict = ScamHeuristics.combine(r.verdict, opinion.verdict)

                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("RESULT", style = MaterialTheme.typography.labelMedium)
                        Text(
                            finalVerdict.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall,
                            color = verdictColor(finalVerdict)
                        )
                        Text(
                            if (opinion.verdict != null && opinion.verdict != r.verdict)
                                "The rule check says ${r.verdict.name} and the AI says ${opinion.verdict.name}. They disagree, so the more severe result is shown for safety."
                            else if (opinion.verdict != null)
                                "The rule check and the AI agree."
                            else if (running)
                                "Waiting for the AI opinion. This is the rule check so far."
                            else
                                "AI opinion not available, so this is the rule check only.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("RULE CHECK", style = MaterialTheme.typography.labelMedium)
                            VerdictPill(r.verdict)
                            Text("score ${r.score}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (r.hits.isEmpty()) {
                            Text("No scam signals found.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            r.hits.forEach { hit ->
                                Text("• ${hit.label} (+${hit.weight})", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ON-DEVICE AI OPINION", style = MaterialTheme.typography.labelMedium)
                            if (opinion.verdict != null) VerdictPill(opinion.verdict)
                        }
                        when {
                            running && aiRaw.isBlank() -> {
                                Text("Thinking...", style = MaterialTheme.typography.bodyMedium)
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            opinion.verdict != null || opinion.reason.isNotBlank() || opinion.action.isNotBlank() -> {
                                if (opinion.reason.isNotBlank()) Text("Why: ${opinion.reason}", style = MaterialTheme.typography.bodyMedium)
                                if (opinion.action.isNotBlank()) Text("What to do: ${opinion.action}", style = MaterialTheme.typography.bodyMedium)
                            }
                            aiRaw.isNotBlank() -> {
                                Text(LlmOutputParser.clean(aiRaw), style = MaterialTheme.typography.bodyMedium)
                            }
                            else -> {
                                Text(
                                    note ?: "No AI opinion yet. Add the model files to turn it on.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        note?.let {
                            if (aiRaw.isNotBlank()) Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        stats?.let { s ->
                            Text(
                                "First token ${s.firstTokenMs} ms, ${"%.1f".format(s.decodeTokensPerSec)} tokens/s, ${s.tokens} tokens, memory ${s.rssMb} MB",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (running && aiRaw.isNotBlank()) {
                            OutlinedButton(onClick = { LlmAdvisor.stop() }) { Text("Stop") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerdictPill(verdict: MessageVerdict?) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(verdictColor(verdict))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            verdict?.name ?: "-",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
