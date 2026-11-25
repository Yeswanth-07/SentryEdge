package com.enerix.sentryscan.llm

import com.enerix.sentryscan.model.ScanResult

/** Builds the prompts sent to the on-device Llama model. Pure Kotlin, no Android classes. */
object PromptTemplates {

    // Llama 3 / 3.2 Instruct chat format. If the model's replies look odd (repeated tokens,
    // ignores the format), the first thing to try is removing BOS below, because the runtime
    // may already add a beginning-of-text token itself.
    private const val BOS = "<|begin_of_text|>"

    fun llama3Chat(system: String, user: String): String =
        BOS +
            "<|start_header_id|>system<|end_header_id|>\n\n" + system + "<|eot_id|>" +
            "<|start_header_id|>user<|end_header_id|>\n\n" + user + "<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>\n\n"

    private const val EXPLAIN_SYSTEM =
        "You are SentryScan, a mobile security assistant that runs on the user's phone. " +
            "Use only the facts you are given. Never invent permissions, domains or numbers. " +
            "Reply with at most 4 short bullet points that say what is risky, why it matters, " +
            "and what the user should do. Use plain language."

    private const val MESSAGE_SYSTEM =
        "You detect scam and phishing messages for users in India: fake bank or KYC alerts, " +
            "UPI and OTP fraud, courier or customs fees, lottery prizes, job and task scams, " +
            "fake APK links, digital arrest threats. Judge the message text and the rule signals. " +
            "Reply in exactly three lines and nothing else:\n" +
            "VERDICT: SCAM or SUSPICIOUS or SAFE\n" +
            "REASON: one short sentence\n" +
            "ACTION: one short sentence"

    /** The score and tier come from the rule engine. The model only explains them. */
    fun explainApp(result: ScanResult): String {
        val b = result.breakdown
        val perms = result.heldPermissions
            .sortedByDescending { it.weight }
            .take(6)
            .joinToString(", ") { "${it.friendlyName} (+${it.weight})" }
            .ifEmpty { "none of the weighted permissions" }
        val domains = result.trackerHits.take(5).joinToString(", ").ifEmpty { "none" }
        val facts = buildString {
            appendLine("App: ${result.app.appName}")
            appendLine("Risk score: ${result.score}/100 (${result.tier.name})")
            appendLine(
                "Permission risk ${b.permissionRisk}/40, network risk ${b.networkRisk}/30, " +
                    "domain risk ${b.domainRisk}/20, behavior risk ${b.behaviorRisk}/10"
            )
            appendLine("Sensitive permissions held: $perms")
            appendLine("Known-bad or tracker domains contacted: $domains")
            append("Traffic much higher than other apps: ${if (result.isAnomalous) "yes" else "no"}")
        }
        return llama3Chat(EXPLAIN_SYSTEM, facts)
    }

    fun checkMessage(message: String, ruleSignals: List<String>): String {
        val text = message.trim().take(700)
        val signals = if (ruleSignals.isEmpty()) "none" else ruleSignals.joinToString("; ")
        val user = "Message:\n\"\"\"\n" + text + "\n\"\"\"\nSignals found by on-device rules: " + signals
        return llama3Chat(MESSAGE_SYSTEM, user)
    }
}
