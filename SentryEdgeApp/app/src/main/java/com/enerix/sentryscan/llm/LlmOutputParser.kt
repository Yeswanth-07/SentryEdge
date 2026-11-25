package com.enerix.sentryscan.llm

data class AiMessageOpinion(
    val verdict: MessageVerdict?,   // null until the model has written a VERDICT line
    val reason: String,
    val action: String
)

/** Reads the model's text. Pure Kotlin, no Android classes. */
object LlmOutputParser {

    private val specialToken = Regex("""<\|[^|>]*\|>""")
    private val verdictRegex = Regex("""VERDICT\s*[:\-]\s*(SCAM|SUSPICIOUS|SAFE)""", RegexOption.IGNORE_CASE)
    private val reasonRegex = Regex("""REASON\s*[:\-]\s*(.+)""", RegexOption.IGNORE_CASE)
    private val actionRegex = Regex("""ACTION\s*[:\-]\s*(.+)""", RegexOption.IGNORE_CASE)

    /** Removes Llama control tokens such as <|eot_id|> if they leak into the text. */
    fun clean(raw: String): String = specialToken.replace(raw, "").trim()

    fun parseMessageOpinion(raw: String): AiMessageOpinion {
        val text = clean(raw)
        val verdict = verdictRegex.find(text)
            ?.groupValues?.get(1)
            ?.uppercase()
            ?.let { MessageVerdict.valueOf(it) }
        val reason = reasonRegex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val action = actionRegex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        return AiMessageOpinion(verdict, reason, action)
    }
}
