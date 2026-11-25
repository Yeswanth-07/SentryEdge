package com.enerix.sentryscan.llm

import java.util.Locale

object BenchmarkRunner {

    private const val MAX_NEW_TOKENS = 96

    // Fixed prompts so every run on every phone does the same work.
    private val prompts = listOf(
        "URGENT: Your SBI account will be blocked today. Update KYC now at http://bit.ly/3xYz9",
        "Hey, are we still meeting for lunch tomorrow at 1?",
        "You have won Rs 25,00,000 in a lottery. Call 9876543210 to claim your prize."
    )

    /** One warm-up run that is not counted, then one measured run per prompt. */
    suspend fun run(onProgress: (String) -> Unit): List<GenerationStats> {
        val results = mutableListOf<GenerationStats>()
        onProgress("Warm-up run (not counted)...")
        LlmAdvisor.generate(PromptTemplates.checkMessage(prompts[0], emptyList()), MAX_NEW_TOKENS)
        for ((i, text) in prompts.withIndex()) {
            onProgress("Measured run ${i + 1} of ${prompts.size}...")
            val r = LlmAdvisor.generate(PromptTemplates.checkMessage(text, emptyList()), MAX_NEW_TOKENS)
            if (r != null) results.add(r.stats)
        }
        onProgress("Done")
        return results
    }
}

data class BenchmarkAverages(
    val firstTokenMs: Double,
    val decodeTokensPerSec: Double,
    val totalMs: Double,
    val tokens: Double
)

object BenchmarkSummary {

    fun averages(runs: List<GenerationStats>): BenchmarkAverages? {
        val valid = runs.filter { it.tokens > 1 }
        if (valid.isEmpty()) return null
        return BenchmarkAverages(
            firstTokenMs = valid.map { it.firstTokenMs }.average(),
            decodeTokensPerSec = valid.map { it.decodeTokensPerSec }.average(),
            totalMs = valid.map { it.totalMs }.average(),
            tokens = valid.map { it.tokens }.average()
        )
    }

    private fun one(d: Double): String = "%.1f".format(Locale.US, d)
    private fun zero(d: Double): String = "%.0f".format(Locale.US, d)

    /** Plain text the user can paste into the Phase 1 report. */
    fun format(
        arm: ArmReport,
        scanApps: Int?,
        scanMs: Long?,
        model: AdvisorState,
        runs: List<GenerationStats>
    ): String = buildString {
        appendLine("SentryScan on-device benchmark")
        appendLine("Device: ${arm.soc} | ABI ${arm.abi} | ${arm.cores} CPU cores")
        appendLine(
            "Arm features: " +
                (if (arm.features.isEmpty()) "not reported by this device" else arm.features.joinToString(", "))
        )
        if (scanApps != null && scanMs != null) {
            appendLine("Rule engine: scanned $scanApps apps in $scanMs ms (read permissions, score, save)")
        }
        if (model is AdvisorState.Ready) {
            appendLine("Model: ${model.modelName} | load ${model.loadMs} ms | memory after load ${model.rssAfterLoadMb} MB")
        }
        appendLine("Runtime: ExecuTorch with the XNNPACK CPU backend. No GPU or NPU is used.")
        runs.forEachIndexed { i, r ->
            appendLine(
                "Run ${i + 1}: first token ${r.firstTokenMs} ms, ${one(r.decodeTokensPerSec)} tokens/s, " +
                    "${r.tokens} tokens, total ${r.totalMs} ms, memory ${r.rssMb} MB"
            )
        }
        val avg = averages(runs)
        if (avg != null) {
            appendLine("Average: first token ${zero(avg.firstTokenMs)} ms, ${one(avg.decodeTokensPerSec)} tokens/s")
        }
    }
}
