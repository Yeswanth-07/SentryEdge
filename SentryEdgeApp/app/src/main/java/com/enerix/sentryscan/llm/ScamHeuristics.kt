package com.enerix.sentryscan.llm

enum class MessageVerdict { SAFE, SUSPICIOUS, SCAM }

/** One rule that matched, with the points it added. */
data class RuleHit(val label: String, val weight: Int)

data class RuleCheck(val hits: List<RuleHit>, val score: Int, val verdict: MessageVerdict)

/**
 * Fast, explainable first pass over a message (SMS, WhatsApp text, email snippet).
 *
 * It runs with no model and no network, so it still works when the on-device LLM is not
 * installed, and its matches are handed to the LLM as extra evidence. The patterns are aimed
 * at scams common in India: fake bank/KYC alerts, UPI and OTP fraud, courier and customs fees,
 * lottery prizes, task and job scams, APK links, "digital arrest" threats.
 *
 * Each rule adds points. 2+ points is SUSPICIOUS, 5+ is SCAM. The thresholds and weights are
 * our own judgement, checked against a small hand-written sample in ScamHeuristicsTest. They are
 * not trained on a large labelled dataset.
 */
object ScamHeuristics {

    const val SUSPICIOUS_AT = 2
    const val SCAM_AT = 5

    private class Rule(val label: String, val weight: Int, val regex: Regex)

    private fun rule(label: String, weight: Int, pattern: String) =
        Rule(label, weight, Regex(pattern, RegexOption.IGNORE_CASE))

    private val linkPattern = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)

    private val brandPattern = Regex(
        """\b(sbi|hdfc|icici|axis bank|kotak|pnb|paytm|phonepe|google pay|gpay|amazon|flipkart|india post|rbi|epfo|uidai|irctc)\b""",
        RegexOption.IGNORE_CASE
    )

    // Real bank OTP texts say "Do not share this OTP with anyone". Strip that sentence first so
    // the "asks for OTP" rule does not fire on genuine warnings.
    private val safeAdvice = Regex(
        """\b(do not|don't|dont|never)\s+(share|disclose|give|reveal)\b[^.\n]*""",
        RegexOption.IGNORE_CASE
    )

    private val rules = listOf(
        rule(
            "Link uses a URL shortener", 3,
            """\b(bit\.ly|tinyurl\.com|t\.co|goo\.gl|cutt\.ly|rb\.gy|is\.gd|shorturl\.at|ow\.ly|tiny\.cc)/\S+"""
        ),
        rule(
            "Link ends in a domain type often used by scam sites", 3,
            """https?://[^\s/]+\.(xyz|top|click|icu|shop|live|vip|work|rest|buzz|cfd|sbs|cyou|monster)\b"""
        ),
        rule("Asks you to install an APK file", 4, """\.apk\b|\bapk file\b"""),
        rule(
            "Asks for an OTP, PIN, CVV or password", 4,
            """\b(share|send|tell|provide|enter|confirm|reply with)\b[^.\n]{0,40}\b(otp|cvv|pin|password|card number|aadhaar|aadhar)\b"""
        ),
        rule(
            "KYC, PAN or Aadhaar update pressure", 3,
            """\b(kyc|pan|aadhaar|aadhar)\b[^.\n]{0,60}\b(update|expire|expired|verify|pending|suspend|block|blocked)\b|\b(update|verify|complete)\s+(your\s+)?(kyc|pan|aadhaar|aadhar)\b"""
        ),
        rule(
            "Urgent deadline or threat", 2,
            """\b(urgent(ly)?|immediately|right now|last chance|act now|final notice|expires? (today|soon|tonight)|within \d+ ?(hours?|hrs?|minutes?|mins?))\b"""
        ),
        rule(
            "Says an account or service will be blocked", 1,
            """\b(blocked|suspended|deactivated|disconnected|terminated|frozen)\b"""
        ),
        rule(
            "Prize, lottery or reward claim", 3,
            """\b(you (have )?won|lottery|lucky draw|jackpot|claim your (prize|reward|gift|cashback|refund))\b"""
        ),
        rule(
            "Refund or cashback bait with a call to act", 3,
            """\b(refund|cashback|reward points?)\b[^.\n]{0,60}\b(click|claim|redeem|expiring|expire|link)\b"""
        ),
        rule(
            "Work-from-home or easy-income offer", 3,
            """\b(work from home|part[- ]time job|daily (income|payout|salary))\b"""
        ),
        rule(
            "Promises a fixed income per day or hour", 3,
            """\bearn\s+\S{0,4}\s?\d[\d,]*\s*(per|a|/)\s*(day|hour|week)\b"""
        ),
        rule(
            "Pushes you to a Telegram or WhatsApp group", 2,
            """\b(telegram|whatsapp) (group|channel)\b"""
        ),
        rule(
            "Parcel, courier or customs fee pressure", 3,
            """\b(parcel|package|courier|shipment)\b[^.\n]{0,60}\b(held|hold|stuck|failed|customs|undelivered|returned)\b|\bdelivery (failed|attempt)\b|\bcustoms (fee|duty)\b"""
        ),
        rule(
            "Police, CBI, customs or digital-arrest threat", 4,
            """\b(digital arrest|cbi|narcotics|money laundering|arrest warrant|trai|ed notice)\b|\bincome tax (notice|department)\b"""
        ),
        rule("Traffic challan or legal-action threat", 2, """\b(challan|court case|legal action)\b"""),
        rule(
            "Asks you to scan a QR code to receive money", 4,
            """\bscan (this |the )?qr( code)?\b[^.\n]{0,30}\b(receive|refund|claim|credit)\b"""
        ),
        rule(
            "Investment promise (guaranteed returns, double your money)", 3,
            """\b(guaranteed (returns?|profit)|double your (money|investment)|crypto (trading|investment)|vip (group|signal))\b"""
        ),
        rule(
            "Asks you to call or WhatsApp a mobile number", 2,
            """\b(call|contact|whatsapp)\b[^.\n]{0,30}\b[6-9]\d{9}\b"""
        )
    )

    fun check(message: String): RuleCheck {
        val text = safeAdvice.replace(message, " ")
        val hits = mutableListOf<RuleHit>()

        for (r in rules) {
            if (r.regex.containsMatchIn(text)) hits.add(RuleHit(r.label, r.weight))
        }

        val hasLink = linkPattern.containsMatchIn(text)
        if (hasLink) hits.add(RuleHit("Contains a link", 1))
        if (hasLink && brandPattern.containsMatchIn(text)) {
            hits.add(RuleHit("Names a bank or brand and includes a link: check the sender and the domain", 2))
        }

        val score = hits.sumOf { it.weight }
        val verdict = when {
            score >= SCAM_AT -> MessageVerdict.SCAM
            score >= SUSPICIOUS_AT -> MessageVerdict.SUSPICIOUS
            else -> MessageVerdict.SAFE
        }
        return RuleCheck(hits, score, verdict)
    }

    /**
     * For a safety tool it is better to over-warn than to under-warn, so the final answer is the
     * more severe of the rule verdict and the model's verdict. If the model gave no usable
     * verdict, the rule verdict stands.
     */
    fun combine(rule: MessageVerdict, ai: MessageVerdict?): MessageVerdict =
        if (ai == null) rule else maxOf(rule, ai)
}
