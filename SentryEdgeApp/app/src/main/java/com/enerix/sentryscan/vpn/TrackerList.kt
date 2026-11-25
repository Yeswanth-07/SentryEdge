package com.enerix.sentryscan.vpn

/**
 * Known ad/tracking domains. In production this is refreshed from
 * maintained public lists (Exodus Privacy's tracker signatures, EasyList,
 * Disconnect.me's tracking-protection list) rather than hand-typed — this
 * hardcoded set exists only so the hackathon build works with zero setup
 * and no network fetch required to demo.
 */
object TrackerList {
    private val knownTrackerSuffixes = setOf(
        "appflyer-track.com", "graph.facebook.com", "adjust-metrics.net",
        "mobvista.com", "applovin.com", "singular.net", "ironsrc.com",
        "tapjoy.com", "mopub-ads.com", "crashlytics-ext.io", "vungle.com",
        "doubleclick.net", "googlesyndication.com", "adcolony.com",
        "chartboost.com", "unityads.unity3d.com", "flurry.com"
    )

    fun isTracker(host: String): Boolean =
        knownTrackerSuffixes.any { host == it || host.endsWith(".$it") }
}
