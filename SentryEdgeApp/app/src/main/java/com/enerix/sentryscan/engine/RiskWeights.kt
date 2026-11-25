package com.enerix.sentryscan.engine

/**
 * Fixed risk weight per permission, from the problem statement's table.
 * This is intentionally simple and transparent rather than learned —
 * exactly the "make the rule-based system work first" starting point the
 * spec calls for; an ML-tuned version is the documented next step.
 */
object RiskWeights {
    private val weights = mapOf(
        "Internet" to 5,
        "Camera" to 10,
        "Microphone" to 15,
        "Precise location" to 15,
        "Approximate location" to 15,
        "Contacts" to 15,
        "SMS" to 20,
        "Call logs" to 20,
        "Storage" to 8,
        "Photos" to 8,
        "Videos" to 8,
        "Body sensors" to 10,
        "Physical activity" to 8,
        "Accessibility service" to 25,
        "Device admin" to 30
    )

    /** Cap on the summed permission risk, matching the spec's "Permission Risk /40". */
    const val MAX_PERMISSION_RISK = 40

    fun weightFor(friendlyName: String): Int = weights[friendlyName] ?: 5

    fun sumFor(friendlyNames: List<String>): Int =
        friendlyNames.sumOf { weightFor(it) }.coerceAtMost(MAX_PERMISSION_RISK)
}
