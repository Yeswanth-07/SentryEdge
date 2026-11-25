package com.enerix.sentryscan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enerix.sentryscan.data.AppDatabase
import com.enerix.sentryscan.data.ScanResultEntity
import com.enerix.sentryscan.engine.ScoringEngine
import com.enerix.sentryscan.model.InstalledApp
import com.enerix.sentryscan.model.ObservedDomain
import com.enerix.sentryscan.model.RiskBreakdown
import com.enerix.sentryscan.model.RiskTier
import com.enerix.sentryscan.model.ScanResult
import com.enerix.sentryscan.model.WeightedPermission
import com.enerix.sentryscan.permissions.PermissionReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class Done(val results: List<ScanResult>) : ScanState
}

/** Everything the Dashboard screen's stat tiles need, computed from the latest scan. */
data class DashboardStats(
    val overallScore: Int,
    val overallTier: RiskTier,
    val appsScanned: Int,
    val suspiciousApps: Int,        // HIGH or CRITICAL
    val suspiciousDomains: Int,     // distinct tracker/known-bad domains seen across all apps
    val privacyWarnings: Int,       // apps with at least one non-empty "problem"
    val recentAlerts: List<ScanResult> // top-N by score, HIGH/CRITICAL first
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _vpnActive = MutableStateFlow(false)
    val vpnActive: StateFlow<Boolean> = _vpnActive.asStateFlow()

    private val _dashboard = MutableStateFlow<DashboardStats?>(null)
    val dashboard: StateFlow<DashboardStats?> = _dashboard.asStateFlow()

    /** Apps scanned and milliseconds taken by the last full scan, for the Arm benchmark screen. */
    private val _lastScan = MutableStateFlow<Pair<Int, Long>?>(null)
    val lastScan: StateFlow<Pair<Int, Long>?> = _lastScan.asStateFlow()

    fun setVpnActive(active: Boolean) {
        _vpnActive.value = active
    }

    /** Runs a full scan: read permissions for every installed app, join with
     * observed domains logged so far by the VPN service, score each app. */
    fun runScan() {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            val scanStartNs = System.nanoTime()
            val results = withContext(Dispatchers.Default) {
                val context = getApplication<Application>()
                val reader = PermissionReader(context.packageManager)
                val db = AppDatabase.getInstance(context)

                val apps = reader.readInstalledApps()
                val domainsByPackage = apps.associate { app ->
                    val hits = db.domainHitDao().hitsFor(app.packageName)
                    app.packageName to hits.map { ObservedDomain(it.host, it.packageName, it.isTracker, it.timestampMs) }
                }

                val scored = ScoringEngine.scoreAll(apps, domainsByPackage)

                scored.forEach { result ->
                    db.scanResultDao().upsert(
                        ScanResultEntity(
                            packageName = result.app.packageName,
                            appName = result.app.appName,
                            category = result.app.category,
                            permissionRisk = result.breakdown.permissionRisk,
                            networkRisk = result.breakdown.networkRisk,
                            domainRisk = result.breakdown.domainRisk,
                            behaviorRisk = result.breakdown.behaviorRisk,
                            score = result.score,
                            tier = result.tier.name,
                            isAnomalous = result.isAnomalous,
                            lastScannedMs = System.currentTimeMillis()
                        )
                    )
                }
                scored.sortedByDescending { it.score }
            }
            _lastScan.value = results.size to ((System.nanoTime() - scanStartNs) / 1_000_000)
            _scanState.value = ScanState.Done(results)
            _dashboard.value = buildDashboard(results)
        }
    }

    private fun buildDashboard(results: List<ScanResult>): DashboardStats {
        val overall = if (results.isEmpty()) 0 else results.sumOf { it.score } / results.size
        val suspiciousApps = results.count { it.tier == RiskTier.HIGH || it.tier == RiskTier.CRITICAL }
        val suspiciousDomains = results.flatMap { it.trackerHits }.distinct().size
        val privacyWarnings = results.count { it.problems.isNotEmpty() && it.problems[0] != "No issues detected — permissions and traffic look normal" }
        val alerts = results.filter { it.tier == RiskTier.HIGH || it.tier == RiskTier.CRITICAL }
            .sortedByDescending { it.score }
            .take(5)

        return DashboardStats(
            overallScore = overall,
            overallTier = tierFor(overall),
            appsScanned = results.size,
            suspiciousApps = suspiciousApps,
            suspiciousDomains = suspiciousDomains,
            privacyWarnings = privacyWarnings,
            recentAlerts = alerts
        )
    }

    private fun tierFor(total: Int): RiskTier = when {
        total >= 81 -> RiskTier.CRITICAL
        total >= 61 -> RiskTier.HIGH
        total >= 31 -> RiskTier.MEDIUM
        else -> RiskTier.LOW
    }

    /**
     * Demo-only: injects (or removes) one fabricated, clearly-labeled "malicious app"
     * result so the CRITICAL path — scoring, alerts, and recommended actions — can be
     * shown live without needing a second real app installed. Every string it produces
     * says SIMULATED so it's never mistaken for a real finding.
     */
    fun toggleDemoThreat() {
        val current = (_scanState.value as? ScanState.Done)?.results ?: emptyList()
        val hasDemo = current.any { it.app.packageName == DEMO_PACKAGE_NAME }
        val updated = if (hasDemo) {
            current.filterNot { it.app.packageName == DEMO_PACKAGE_NAME }
        } else {
            (current + buildDemoThreatResult()).sortedByDescending { it.score }
        }
        _scanState.value = ScanState.Done(updated)
        _dashboard.value = buildDashboard(updated)
    }

    fun isDemoThreatActive(): Boolean =
        (_scanState.value as? ScanState.Done)?.results.orEmpty().any { it.app.packageName == DEMO_PACKAGE_NAME }

    private fun buildDemoThreatResult(): ScanResult {
        val app = InstalledApp(
            packageName = DEMO_PACKAGE_NAME,
            appName = "SnapCalc Pro [SIMULATED]",
            category = "Utilities",
            permissions = emptyList(),
            uid = -1
        )
        val breakdown = RiskBreakdown(
            permissionRisk = 40,
            networkRisk = 30,
            domainRisk = 20,
            behaviorRisk = 10
        )
        return ScanResult(
            app = app,
            breakdown = breakdown,
            tier = RiskTier.CRITICAL,
            heldPermissions = listOf(
                WeightedPermission("Device admin", 30),
                WeightedPermission("SMS", 20),
                WeightedPermission("Contacts", 15)
            ),
            trackerHits = listOf("malicious-c2-drop.net", "phishing-bank-login.cc"),
            isAnomalous = true,
            problems = listOf(
                "[SIMULATED DEMO — not a real installed app] Declares a Device Admin receiver, a common banking-trojan technique",
                "[SIMULATED DEMO] Communicates with 2 known-malicious domains: malicious-c2-drop.net, phishing-bank-login.cc",
                "[SIMULATED DEMO] Traffic volume is far above this session's baseline — a classic data-exfiltration pattern"
            ),
            recommendations = listOf(
                "Review permissions",
                "Restrict background data",
                "Uninstall if not trusted"
            )
        )
    }

    companion object {
        private const val DEMO_PACKAGE_NAME = "com.demo.snapcalc.simulated"
    }
}
