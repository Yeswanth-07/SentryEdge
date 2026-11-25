package com.enerix.sentryscan.llm

import android.os.Build
import java.io.File

data class ArmReport(
    val abi: String,
    val soc: String,
    val cores: Int,
    val features: List<String>
)

/** Reports which Arm CPU the app is running on and which extensions it exposes. */
object ArmInfo {

    // The Arm CPU extensions that matter when running a quantized LLM, with plain names.
    // The keys are the names Linux prints on the "Features" line of /proc/cpuinfo.
    private val labels = linkedMapOf(
        "asimd" to "NEON (asimd)",
        "asimddp" to "dot product (asimddp)",
        "i8mm" to "int8 matrix multiply (i8mm)",
        "bf16" to "bfloat16 (bf16)",
        "sve" to "SVE",
        "sve2" to "SVE2"
    )

    fun read(): ArmReport {
        val found = HashSet<String>()
        try {
            File("/proc/cpuinfo").forEachLine { line ->
                if (line.startsWith("Features")) {
                    line.substringAfter(":").trim().split(" ")
                        .filter { it.isNotEmpty() }
                        .forEach { found.add(it) }
                }
            }
        } catch (_: Exception) {
            // Some devices restrict /proc/cpuinfo. The report then lists no features.
        }

        val features = labels.filterKeys { it in found }.values.toList()
        val soc = if (Build.VERSION.SDK_INT >= 31) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim()
        } else {
            Build.HARDWARE
        }

        return ArmReport(
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            soc = soc,
            cores = Runtime.getRuntime().availableProcessors(),
            features = features
        )
    }
}
