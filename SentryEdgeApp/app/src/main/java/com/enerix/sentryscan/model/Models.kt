package com.enerix.sentryscan.model

/**
 * A permission an app declares, mapped to a friendly label and whether
 * the OS actually granted it (a declared-but-denied permission carries
 * no risk, so the engine ignores those).
 */
data class DeclaredPermission(
    val manifestName: String,   // e.g. android.permission.CAMERA
    val friendlyName: String,   // e.g. "Camera"
    val isDangerous: Boolean,   // only "dangerous" protection-level perms matter for risk
    val isGranted: Boolean
)

/**
 * One installed app as SentryScan sees it: static permission info from
 * PackageManager, joined at scoring time with the domains it has been
 * observed contacting.
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val category: String,          // Play Store category if available, else "Unknown"
    val permissions: List<DeclaredPermission>,
    val uid: Int
)

/** One domain contacted by an app's traffic, captured by the VPN logger. */
data class ObservedDomain(
    val host: String,
    val packageName: String,
    val isTracker: Boolean,
    val timestampMs: Long
)

/** LOW / MEDIUM / HIGH / CRITICAL, banded from the 0-100 total score. */
enum class RiskTier { LOW, MEDIUM, HIGH, CRITICAL }

/**
 * The four-part breakdown the report screen shows, each capped at its own
 * max so the reasoning is auditable component-by-component instead of a
 * single opaque number:
 *   Permission Risk  /40  — sum of held dangerous-permission weights
 *   Network Risk     /30  — tracker contact volume relative to total traffic
 *   Domain Risk      /20  — how many distinct known-bad domains were hit
 *   Behavior Risk    /10  — whether traffic volume broke from this app's baseline
 */
data class RiskBreakdown(
    val permissionRisk: Int,
    val networkRisk: Int,
    val domainRisk: Int,
    val behaviorRisk: Int
) {
    val total: Int get() = (permissionRisk + networkRisk + domainRisk + behaviorRisk).coerceAtMost(100)
}

/** One held permission and the risk weight it contributes, for the per-app breakdown list. */
data class WeightedPermission(
    val friendlyName: String,
    val weight: Int
)

data class ScanResult(
    val app: InstalledApp,
    val breakdown: RiskBreakdown,
    val tier: RiskTier,
    val heldPermissions: List<WeightedPermission>,
    val trackerHits: List<String>,
    val isAnomalous: Boolean,
    val problems: List<String>,      // bullet list for the "problems detected" panel
    val recommendations: List<String>
) {
    val score: Int get() = breakdown.total
}
