package com.enerix.sentryscan.engine

import com.enerix.sentryscan.model.InstalledApp
import com.enerix.sentryscan.model.ObservedDomain
import com.enerix.sentryscan.model.RiskBreakdown
import com.enerix.sentryscan.model.RiskTier
import com.enerix.sentryscan.model.ScanResult
import com.enerix.sentryscan.model.WeightedPermission
import kotlin.math.roundToInt

/**
 * The four-part scoring model from the problem statement:
 *
 *   Permission Risk (/40)  — sum of held dangerous-permission weights (RiskWeights)
 *   Network Risk    (/30)  — what fraction of this app's observed traffic hit a
 *                            known-bad domain
 *   Domain Risk     (/20)  — how many distinct known-bad domains it contacted
 *   Behavior Risk   (/10)  — whether its traffic volume broke from the baseline
 *                            (simple rule: current > 3x the average across all
 *                            scanned apps this session — the exact rule the spec
 *                            itself suggests as the non-ML starting point)
 *
 *   Total = sum, banded LOW(0-30) / MEDIUM(31-60) / HIGH(61-80) / CRITICAL(81-100)
 */
object ScoringEngine {

    private const val TRACKER_HIT_CAP = 3       // hits at which network risk saturates
    private const val DOMAIN_RISK_PER_HIT = 7   // domainRisk points per distinct bad domain
    private const val ANOMALY_MULTIPLIER = 3.0  // "current > baseline * 3" from the spec
    private const val ANOMALY_MIN_HITS = 5      // floor so a near-zero baseline can't trivially trigger it

    /**
     * Scores every app in one pass so behavior risk can compare each app's
     * traffic volume against the average across the whole scanned set —
     * the session-relative "baseline" a hackathon build can compute
     * without days of historical data. See README for that simplification.
     */
    fun scoreAll(apps: List<InstalledApp>, domainsByPackage: Map<String, List<ObservedDomain>>): List<ScanResult> {
        val hitCounts = apps.map { domainsByPackage[it.packageName]?.size ?: 0 }
        val baseline = if (hitCounts.isNotEmpty()) hitCounts.average() else 0.0

        return apps.map { app ->
            score(app, domainsByPackage[app.packageName] ?: emptyList(), baseline)
        }
    }

    fun score(app: InstalledApp, domains: List<ObservedDomain>, baseline: Double = 0.0): ScanResult {
        val heldDangerous = app.permissions.filter { it.isGranted && it.isDangerous }
        val heldWeighted = heldDangerous.map { WeightedPermission(it.friendlyName, RiskWeights.weightFor(it.friendlyName)) }
        val permissionRisk = RiskWeights.sumFor(heldDangerous.map { it.friendlyName })

        val trackerHits = domains.filter { it.isTracker }
        val distinctTrackerDomains = trackerHits.map { it.host }.distinct()
        val trackerRatio = if (domains.isEmpty()) 0.0
            else (trackerHits.size.toDouble() / TRACKER_HIT_CAP).coerceAtMost(1.0)
        val networkRisk = (30 * trackerRatio).roundToInt()

        val domainRisk = (distinctTrackerDomains.size * DOMAIN_RISK_PER_HIT).coerceAtMost(20)

        val isAnomalous = domains.size > (baseline * ANOMALY_MULTIPLIER).coerceAtLeast(ANOMALY_MIN_HITS.toDouble())
        val behaviorRisk = if (isAnomalous) 10 else 0

        val breakdown = RiskBreakdown(permissionRisk, networkRisk, domainRisk, behaviorRisk)
        val tier = tierFor(breakdown.total)

        val problems = buildProblems(heldWeighted, distinctTrackerDomains, isAnomalous)
        val recommendations = buildRecommendations(tier, heldWeighted.isNotEmpty())

        return ScanResult(
            app = app,
            breakdown = breakdown,
            tier = tier,
            heldPermissions = heldWeighted,
            trackerHits = distinctTrackerDomains,
            isAnomalous = isAnomalous,
            problems = problems,
            recommendations = recommendations
        )
    }

    private fun tierFor(total: Int): RiskTier = when {
        total >= 81 -> RiskTier.CRITICAL
        total >= 61 -> RiskTier.HIGH
        total >= 31 -> RiskTier.MEDIUM
        else -> RiskTier.LOW
    }

    private fun buildProblems(
        held: List<WeightedPermission>,
        trackerDomains: List<String>,
        anomalous: Boolean
    ): List<String> {
        val problems = mutableListOf<String>()
        held.filter { it.weight >= 15 }
            .sortedByDescending { it.weight }
            .forEach { problems += "Requests ${it.friendlyName} permission (weight ${it.weight})" }
        if (trackerDomains.isNotEmpty()) {
            problems += "Communicates with ${trackerDomains.size} known-suspicious domain(s): ${trackerDomains.joinToString(", ")}"
        }
        if (anomalous) {
            problems += "Unusually high network activity compared to other apps on this device"
        }
        if (problems.isEmpty()) problems += "No issues detected — permissions and traffic look normal"
        return problems
    }

    private fun buildRecommendations(tier: RiskTier, hasSensitivePerms: Boolean): List<String> = when (tier) {
        RiskTier.LOW -> listOf("No action needed")
        RiskTier.MEDIUM -> listOfNotNull(
            "Review permissions".takeIf { hasSensitivePerms },
            "Keep an eye on this app's network activity"
        )
        RiskTier.HIGH -> listOf("Review permissions", "Restrict background data", "Monitor closely")
        RiskTier.CRITICAL -> listOf("Review permissions", "Restrict background data", "Uninstall if not trusted")
    }
}
