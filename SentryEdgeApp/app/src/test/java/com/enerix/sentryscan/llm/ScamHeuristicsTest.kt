package com.enerix.sentryscan.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A small hand-written sample, not a benchmark. It guards against the rules drifting and shows
 * the kind of message each rule is aimed at. Run from Android Studio: right-click this file,
 * then Run.
 */
class ScamHeuristicsTest {

    private val scams = listOf(
        "Dear customer your SBI account will be blocked today. Update KYC immediately http://bit.ly/3xYz9",
        "RTO Challan pending. Download RTO_Challan.apk and pay fine now to avoid court case",
        "Congratulations! You have won Rs 25,00,000 in KBC lottery. Call 9876543210 to claim your prize",
        "Work from home part time job. Earn Rs 5000 per day. Join Telegram group now",
        "Your parcel is held at customs. Pay customs fee Rs 49 at http://indiapost-track.top/pay",
        "Share your OTP and CVV to complete refund. Your card will be suspended"
    )

    private val risky = listOf(
        "Your KYC is pending. Update within 24 hours",
        "Package delivery failed. Reschedule here: https://track-parcel.example.com"
    )

    private val genuine = listOf(
        "123456 is your OTP for login to HDFC Bank. Do not share this OTP with anyone.",
        "Hey, are we still on for lunch tomorrow? I'll be there at 1.",
        "Your Amazon order #402-123 has been delivered. Rate your experience.",
        "Rs 1,200 debited from A/c XX4321 on 24-Nov. Avl bal Rs 8,430. If not you, call 18001234567",
        "Meeting moved to 3 PM, see the doc: https://docs.google.com/document/d/abc",
        "Your electricity bill for October is Rs 842. Pay by 5 Nov via the official app."
    )

    @Test
    fun scamMessagesAreFlaggedAsScam() {
        for (m in scams) {
            assertEquals("Expected SCAM for: $m", MessageVerdict.SCAM, ScamHeuristics.check(m).verdict)
        }
    }

    @Test
    fun riskyMessagesAreNotSafe() {
        for (m in risky) {
            assertNotEquals("Expected not SAFE for: $m", MessageVerdict.SAFE, ScamHeuristics.check(m).verdict)
        }
    }

    @Test
    fun genuineMessagesAreSafe() {
        for (m in genuine) {
            assertEquals("Expected SAFE for: $m", MessageVerdict.SAFE, ScamHeuristics.check(m).verdict)
        }
    }

    @Test
    fun combineKeepsTheMoreSevereVerdict() {
        assertEquals(MessageVerdict.SCAM, ScamHeuristics.combine(MessageVerdict.SAFE, MessageVerdict.SCAM))
        assertEquals(MessageVerdict.SCAM, ScamHeuristics.combine(MessageVerdict.SCAM, MessageVerdict.SAFE))
        assertEquals(MessageVerdict.SUSPICIOUS, ScamHeuristics.combine(MessageVerdict.SUSPICIOUS, null))
    }

    @Test
    fun parsesTheModelReply() {
        val raw = "VERDICT: SCAM\nREASON: Asks for your OTP and uses a short link.\nACTION: Do not click. Delete the message.<|eot_id|>"
        val o = LlmOutputParser.parseMessageOpinion(raw)
        assertEquals(MessageVerdict.SCAM, o.verdict)
        assertTrue(o.reason.startsWith("Asks for your OTP"))
        assertEquals("Do not click. Delete the message.", o.action)
    }

    @Test
    fun partialReplyHasNoVerdictYet() {
        assertNull(LlmOutputParser.parseMessageOpinion("VERD").verdict)
    }
}
