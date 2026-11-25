package com.enerix.sentryscan.threatintel

import com.enerix.sentryscan.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

/** One source's verdict on a domain. */
data class ThreatIntelVerdict(
    val source: String,
    val checked: Boolean,     // false = skipped (no key) or the request failed — not a "safe" verdict
    val malicious: Boolean,
    val confidence: Int,      // 0-100, meaning varies per source (see note)
    val note: String
)

/**
 * Real, live calls to the four sources the problem statement names — no mocked
 * responses. URLhaus needs no key and always works. VirusTotal, AbuseIPDB and
 * PhishTank need a free key (see gradle.properties) — without one they return
 * checked=false with an honest "no API key configured" note instead of a fake
 * result. This is called on-demand, per domain, not automatically for every
 * packet: the free tiers on these services (VirusTotal ~500/day, AbuseIPDB
 * 1000/day) would be exhausted in minutes if run against continuous live
 * traffic, so this is a "check this specific domain" tool, not a firehose.
 */
object ThreatIntelClient {

    private const val TIMEOUT_MS = 8000

    suspend fun checkDomain(domain: String): List<ThreatIntelVerdict> = withContext(Dispatchers.IO) {
        val urlhaus = async { checkUrlhaus(domain) }
        val virusTotal = async { checkVirusTotal(domain) }
        val abuseIpDb = async { checkAbuseIpDb(domain) }
        val phishTank = async { checkPhishTank(domain) }
        listOf(urlhaus.await(), virusTotal.await(), abuseIpDb.await(), phishTank.await())
    }

    // ---- URLhaus (abuse.ch) — free, no API key needed ----
    private fun checkUrlhaus(domain: String): ThreatIntelVerdict {
        return try {
            val conn = openConnection("https://urlhaus-api.abuse.ch/v1/host/", "POST")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write("host=${URLEncoder.encode(domain, "UTF-8")}".toByteArray()) }

            val body = readBody(conn)
            if (conn.responseCode !in 200..299) {
                return ThreatIntelVerdict("URLhaus", false, false, 0, "API error (${conn.responseCode})")
            }
            val json = JSONObject(body)
            val malicious = json.optString("query_status") == "ok"
            val urlCount = if (malicious) json.optJSONArray("urls")?.length() ?: 0 else 0
            ThreatIntelVerdict(
                "URLhaus", checked = true, malicious = malicious,
                confidence = if (malicious) 90 else 0,
                note = if (malicious) "$urlCount known malware URL(s) hosted on this domain" else "No known malware URLs found"
            )
        } catch (e: Exception) {
            ThreatIntelVerdict("URLhaus", false, false, 0, "Lookup failed: ${e.message}")
        }
    }

    // ---- VirusTotal — needs a free API key ----
    private fun checkVirusTotal(domain: String): ThreatIntelVerdict {
        val key = BuildConfig.VIRUSTOTAL_API_KEY
        if (key.isBlank()) return ThreatIntelVerdict("VirusTotal", false, false, 0, "No API key configured")
        return try {
            val conn = openConnection("https://www.virustotal.com/api/v3/domains/$domain", "GET")
            conn.setRequestProperty("x-apikey", key)

            val body = readBody(conn)
            if (conn.responseCode !in 200..299) {
                return ThreatIntelVerdict("VirusTotal", false, false, 0, "API error (${conn.responseCode})")
            }
            val stats = JSONObject(body).getJSONObject("data").getJSONObject("attributes").getJSONObject("last_analysis_stats")
            val malicious = stats.optInt("malicious", 0)
            val suspicious = stats.optInt("suspicious", 0)
            val total = stats.optInt("harmless", 0) + stats.optInt("undetected", 0) + malicious + suspicious
            val confidence = if (total > 0) (malicious + suspicious) * 100 / total else 0
            ThreatIntelVerdict(
                "VirusTotal", checked = true, malicious = malicious > 0, confidence = confidence,
                note = "$malicious of $total security vendors flagged this domain"
            )
        } catch (e: Exception) {
            ThreatIntelVerdict("VirusTotal", false, false, 0, "Lookup failed: ${e.message}")
        }
    }

    // ---- AbuseIPDB — needs a free API key. It checks IPs, so we resolve the domain first. ----
    private fun checkAbuseIpDb(domain: String): ThreatIntelVerdict {
        val key = BuildConfig.ABUSEIPDB_API_KEY
        if (key.isBlank()) return ThreatIntelVerdict("AbuseIPDB", false, false, 0, "No API key configured")
        return try {
            val ip = InetAddress.getByName(domain).hostAddress
                ?: return ThreatIntelVerdict("AbuseIPDB", false, false, 0, "Could not resolve domain to an IP")

            val conn = openConnection("https://api.abuseipdb.com/api/v2/check?ipAddress=$ip&maxAgeInDays=90", "GET")
            conn.setRequestProperty("Key", key)
            conn.setRequestProperty("Accept", "application/json")

            val body = readBody(conn)
            if (conn.responseCode !in 200..299) {
                return ThreatIntelVerdict("AbuseIPDB", false, false, 0, "API error (${conn.responseCode})")
            }
            val score = JSONObject(body).getJSONObject("data").optInt("abuseConfidenceScore", 0)
            ThreatIntelVerdict(
                "AbuseIPDB", checked = true, malicious = score >= 50, confidence = score,
                note = "Resolved to $ip — abuse confidence $score%"
            )
        } catch (e: Exception) {
            ThreatIntelVerdict("AbuseIPDB", false, false, 0, "Lookup failed: ${e.message}")
        }
    }

    // ---- PhishTank — works without a key (slower/rate-limited), better with one ----
    private fun checkPhishTank(domain: String): ThreatIntelVerdict {
        return try {
            val targetUrl = "http://$domain/"
            val key = BuildConfig.PHISHTANK_API_KEY
            val form = buildString {
                append("url=").append(URLEncoder.encode(targetUrl, "UTF-8"))
                append("&format=json")
                if (key.isNotBlank()) append("&app_key=").append(URLEncoder.encode(key, "UTF-8"))
            }

            val conn = openConnection("https://checkurl.phishtank.com/checkurl/", "POST")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", "sentryscan-hackathon-prototype")
            conn.outputStream.use { it.write(form.toByteArray()) }

            val body = readBody(conn)
            if (conn.responseCode !in 200..299) {
                return ThreatIntelVerdict("PhishTank", false, false, 0, "API error (${conn.responseCode}) — free tier is rate-limited without a key")
            }
            val results = JSONObject(body).optJSONObject("results")
            val inDb = results?.optBoolean("in_database", false) ?: false
            val verified = results?.optBoolean("verified", false) ?: false
            ThreatIntelVerdict(
                "PhishTank", checked = true, malicious = inDb && verified,
                confidence = if (inDb && verified) 95 else 0,
                note = when {
                    inDb && verified -> "Listed as a verified phishing site"
                    inDb -> "Reported, but not yet verified"
                    else -> "Not found in PhishTank's database"
                }
            )
        } catch (e: Exception) {
            ThreatIntelVerdict("PhishTank", false, false, 0, "Lookup failed: ${e.message}")
        }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String =
        (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
}
