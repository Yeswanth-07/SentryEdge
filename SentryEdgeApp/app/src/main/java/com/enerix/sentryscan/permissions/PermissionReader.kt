package com.enerix.sentryscan.permissions

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.enerix.sentryscan.model.DeclaredPermission
import com.enerix.sentryscan.model.InstalledApp

/**
 * Reads every installed app's declared permission set straight from
 * PackageManager. No scanning, no network call — this is metadata the
 * OS already has, so it's instant and works fully offline.
 */
class PermissionReader(private val pm: PackageManager) {

    /** Friendly names for the "dangerous" permission group the scoring engine cares about. */
    private val friendlyNames = mapOf(
        "android.permission.INTERNET" to "Internet",
        "android.permission.CAMERA" to "Camera",
        "android.permission.RECORD_AUDIO" to "Microphone",
        "android.permission.ACCESS_FINE_LOCATION" to "Precise location",
        "android.permission.ACCESS_COARSE_LOCATION" to "Approximate location",
        "android.permission.READ_CONTACTS" to "Contacts",
        "android.permission.WRITE_CONTACTS" to "Contacts",
        "android.permission.READ_SMS" to "SMS",
        "android.permission.SEND_SMS" to "SMS",
        "android.permission.RECEIVE_SMS" to "SMS",
        "android.permission.READ_CALL_LOG" to "Call logs",
        "android.permission.READ_EXTERNAL_STORAGE" to "Storage",
        "android.permission.WRITE_EXTERNAL_STORAGE" to "Storage",
        "android.permission.READ_MEDIA_IMAGES" to "Photos",
        "android.permission.READ_MEDIA_VIDEO" to "Videos",
        "android.permission.BODY_SENSORS" to "Body sensors",
        "android.permission.ACTIVITY_RECOGNITION" to "Physical activity"
    )

    fun readInstalledApps(includeSystemApps: Boolean = false): List<InstalledApp> {
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val result = mutableListOf<InstalledApp>()

        for (pkg in packages) {
            val appInfo: ApplicationInfo = pkg.applicationInfo ?: continue
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystemApp && !includeSystemApps) continue

            val requested = pkg.requestedPermissions ?: emptyArray()
            val grantedFlags = pkg.requestedPermissionsFlags

            val declared = requested.mapIndexedNotNull { index, permName ->
                val friendly = friendlyNames[permName] ?: return@mapIndexedNotNull null
                val granted = grantedFlags != null &&
                    index < grantedFlags.size &&
                    (grantedFlags[index] and PackageManager.PERMISSION_GRANTED) != 0

                DeclaredPermission(
                    manifestName = permName,
                    friendlyName = friendly,
                    isDangerous = true, // everything in friendlyNames is a "dangerous" perm
                    isGranted = granted
                )
            }.toMutableList()

            // Accessibility Service and Device Admin aren't normal runtime permissions —
            // they're special access an app registers for via a component in its manifest.
            // These are real, well-known malware techniques (most Android banking trojans
            // abuse Accessibility Service to read screen content and auto-tap buttons), so
            // we detect them the real way: querying for a matching declared component,
            // not from the requestedPermissions[] array.
            if (declaresAccessibilityService(pkg.packageName)) {
                declared += DeclaredPermission(
                    manifestName = "android.accessibilityservice.AccessibilityService",
                    friendlyName = "Accessibility service",
                    isDangerous = true,
                    isGranted = true // presence of the component is the signal; user must have enabled it in Settings
                )
            }
            if (declaresDeviceAdminReceiver(pkg.packageName)) {
                declared += DeclaredPermission(
                    manifestName = "android.app.action.DEVICE_ADMIN_ENABLED",
                    friendlyName = "Device admin",
                    isDangerous = true,
                    isGranted = true
                )
            }

            result += InstalledApp(
                packageName = pkg.packageName,
                appName = pm.getApplicationLabel(appInfo).toString(),
                category = categoryLabel(appInfo.category),
                permissions = declared,
                uid = appInfo.uid
            )
        }
        return result
    }

    /** True if this package declares a <service> that handles the AccessibilityService intent action. */
    private fun declaresAccessibilityService(packageName: String): Boolean = try {
        val intent = Intent("android.accessibilityservice.AccessibilityService")
        pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
            .any { it.serviceInfo?.packageName == packageName }
    } catch (_: Exception) {
        false
    }

    /** True if this package declares a <receiver> that handles the DEVICE_ADMIN_ENABLED action. */
    private fun declaresDeviceAdminReceiver(packageName: String): Boolean = try {
        val intent = Intent("android.app.action.DEVICE_ADMIN_ENABLED")
        pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
            .any { it.activityInfo?.packageName == packageName }
    } catch (_: Exception) {
        false
    }

    private fun categoryLabel(category: Int): String = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "Game"
        ApplicationInfo.CATEGORY_AUDIO -> "Audio"
        ApplicationInfo.CATEGORY_VIDEO -> "Video"
        ApplicationInfo.CATEGORY_IMAGE -> "Photography"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_NEWS -> "News"
        ApplicationInfo.CATEGORY_MAPS -> "Maps & Navigation"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
        // ApplicationInfo.CATEGORY_ACCESSIBILITY / others exist on newer SDKs
        else -> "Unknown"
    }
}
