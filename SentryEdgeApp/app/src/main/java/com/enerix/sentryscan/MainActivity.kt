package com.enerix.sentryscan

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import com.enerix.sentryscan.model.ScanResult
import com.enerix.sentryscan.ui.AiAdvisorScreen
import com.enerix.sentryscan.ui.AppDetailScreen
import com.enerix.sentryscan.ui.AppListScreen
import com.enerix.sentryscan.ui.BenchmarkScreen
import com.enerix.sentryscan.ui.DashboardScreen
import com.enerix.sentryscan.ui.LiveMonitorScreen
import com.enerix.sentryscan.ui.ThreatIntelScreen
import com.enerix.sentryscan.ui.theme.SentryScanTheme
import com.enerix.sentryscan.vpn.TrafficVpnService

private sealed interface Screen {
    data object Dashboard : Screen
    data object AllApps : Screen
    data class Detail(val result: ScanResult) : Screen
    data object LiveMonitor : Screen
    data object ThreatIntel : Screen
    data object AiAdvisor : Screen
    data object Benchmark : Screen
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpnService()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op either way — foreground notification still shows without it on <33 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            SentryScanTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
                val scanState by viewModel.scanState.collectAsState()
                val dashboard by viewModel.dashboard.collectAsState()
                val vpnActive by viewModel.vpnActive.collectAsState()
                val lastScan by viewModel.lastScan.collectAsState()

                when (val s = screen) {
                    is Screen.Dashboard -> DashboardScreen(
                        scanState = scanState,
                        dashboard = dashboard,
                        vpnActive = vpnActive,
                        onScan = { viewModel.runScan() },
                        onToggleVpn = { toggleVpn(vpnActive) },
                        onViewAllApps = { screen = Screen.AllApps },
                        onAlertClick = { screen = Screen.Detail(it) },
                        onOpenLiveMonitor = { screen = Screen.LiveMonitor },
                        onOpenThreatIntel = { screen = Screen.ThreatIntel },
                        onOpenAiAdvisor = { screen = Screen.AiAdvisor },
                        onOpenBenchmark = { screen = Screen.Benchmark },
                        demoThreatActive = viewModel.isDemoThreatActive(),
                        onToggleDemoThreat = { viewModel.toggleDemoThreat() }
                    )
                    is Screen.AllApps -> {
                        val results = (scanState as? ScanState.Done)?.results ?: emptyList()
                        AppListScreen(
                            results = results,
                            onBack = { screen = Screen.Dashboard },
                            onAppClick = { screen = Screen.Detail(it) }
                        )
                    }
                    is Screen.Detail -> AppDetailScreen(
                        result = s.result,
                        onBack = { screen = Screen.Dashboard }
                    )
                    is Screen.LiveMonitor -> LiveMonitorScreen(
                        vpnActive = vpnActive,
                        onBack = { screen = Screen.Dashboard },
                        onToggleVpn = { toggleVpn(vpnActive) }
                    )
                    is Screen.ThreatIntel -> ThreatIntelScreen(
                        onBack = { screen = Screen.Dashboard }
                    )
                    is Screen.AiAdvisor -> AiAdvisorScreen(
                        onBack = { screen = Screen.Dashboard }
                    )
                    is Screen.Benchmark -> BenchmarkScreen(
                        lastScanApps = lastScan?.first,
                        lastScanMs = lastScan?.second,
                        onBack = { screen = Screen.Dashboard }
                    )
                }
            }
        }
    }

    private fun toggleVpn(currentlyActive: Boolean) {
        if (currentlyActive) {
            startService(Intent(this, TrafficVpnService::class.java).apply {
                action = TrafficVpnService.ACTION_STOP
            })
            viewModel.setVpnActive(false)
        } else {
            requestVpnPermissionThenStart()
        }
    }

    private fun requestVpnPermissionThenStart() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        ActivityCompat.startForegroundService(this, Intent(this, TrafficVpnService::class.java))
        viewModel.setVpnActive(true)
    }
}
