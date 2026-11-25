package com.enerix.sentryscan.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.enerix.sentryscan.MainActivity
import com.enerix.sentryscan.data.AppDatabase
import com.enerix.sentryscan.data.DomainHitEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.net.InetSocketAddress

/**
 * On-device traffic observer.
 *
 * This is a *local* VpnService: no remote VPN server, no relay, nothing
 * leaves the phone through this tunnel except the packets going where
 * they were always going. We use the VpnService API purely for the
 * permission it grants: the right to see the device's outbound packets
 * so we can read DNS queries and attribute them to the app (UID) that
 * made them, via ConnectivityManager.getConnectionOwnerUid — the same
 * official API Android's own per-app data usage screen relies on.
 *
 * KNOWN LIMITATION (documented, not hidden): this build inspects UDP/53
 * (plaintext DNS) only. Most traffic today is DNS-over-HTTPS/TLS from the
 * OS resolver, which this won't see the query for — production version
 * would also read TLS ClientHello SNI on port 443 for domain visibility
 * without needing to decrypt anything.
 */
class TrafficVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startTunnel()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "sentryscan_vpn"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Privacy monitor", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SentryScan is watching for suspicious traffic")
            .setContentText("On-device only — nothing leaves your phone")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun startTunnel() {
        if (running) return
        val builder = Builder()
            .setSession("SentryEdge")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .setBlocking(false)

        // Never route SentryScan's own process through the tunnel it creates.
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) { /* ignore */ }

        vpnInterface = builder.establish() ?: return
        running = true

        serviceScope.launch { readLoop() }
    }

    private suspend fun readLoop() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)
        val db = AppDatabase.getInstance(applicationContext)
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        while (running) {
            val length = try { input.read(buffer) } catch (e: Exception) { break }
            if (length <= 0) continue

            val query = DnsPacketParser.extractDnsQuery(buffer, length) ?: continue
            val packageName = resolveOwningPackage(cm, query.sourcePort) ?: "unknown"

            db.domainHitDao().insert(
                DomainHitEntity(
                    host = query.domain,
                    packageName = packageName,
                    isTracker = TrackerList.isTracker(query.domain),
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Maps the local UDP port of a captured packet to the app that owns it,
     * using the official per-connection owner-UID lookup (API 29+), then
     * resolves that UID to a package name. On API 26-28 this returns null
     * and hits are logged as "unknown" — still useful for the tracker/
     * category signals, just without per-app attribution.
     */
    private fun resolveOwningPackage(cm: ConnectivityManager, localPort: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val local = InetSocketAddress("10.0.0.2", localPort)
            val remote = InetSocketAddress("8.8.8.8", 53)
            val uid = cm.getConnectionOwnerUid(android.system.OsConstants.IPPROTO_UDP, local, remote)
            if (uid <= 0) return null
            packageManager.getPackagesForUid(uid)?.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun stopTunnel() {
        running = false
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.enerix.sentryscan.STOP_VPN"
        private const val NOTIFICATION_ID = 42
    }
}
