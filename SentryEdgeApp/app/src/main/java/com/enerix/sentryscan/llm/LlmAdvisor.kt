package com.enerix.sentryscan.llm

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmGenerationConfig
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File

sealed interface AdvisorState {
    data object NoModel : AdvisorState
    data object Loading : AdvisorState
    data class Ready(val modelName: String, val loadMs: Long, val rssAfterLoadMb: Long) : AdvisorState
    data class Failed(val message: String) : AdvisorState
}

data class GenerationStats(
    val tokens: Int,
    val totalMs: Long,
    /** From the request to the first token. Includes reading the prompt (prefill). */
    val firstTokenMs: Long,
    /** Tokens per second after the first token. */
    val decodeTokensPerSec: Double,
    /** Prompt length as reported by the runtime, if it reports one. */
    val promptTokens: Int?,
    /** Memory used by this app process after the run, in MB. -1 if unavailable. */
    val rssMb: Long,
    val stoppedAtLimit: Boolean
)

data class GenerationResult(val text: String, val stats: GenerationStats, val error: String?)

/**
 * The on-device Llama model, run with ExecuTorch on the phone's Arm CPU. One model is shared by
 * the whole app and only one generation runs at a time. Nothing here uses the network.
 */
object LlmAdvisor {

    // Low temperature so the same message gets the same verdict.
    private const val TEMPERATURE = 0.2f
    private const val SEQ_LEN = 768

    private val _state = MutableStateFlow<AdvisorState>(AdvisorState.NoModel)
    val state: StateFlow<AdvisorState> = _state.asStateFlow()

    private val lock = Mutex()
    private var module: LlmModule? = null

    /** Loads the model if its files are on the phone. Safe to call again and again. */
    suspend fun ensureLoaded(context: Context): Boolean = lock.withLock {
        if (module != null) return@withLock true
        val files = ModelLocator.find(context)
        if (files == null) {
            _state.value = AdvisorState.NoModel
            return@withLock false
        }
        _state.value = AdvisorState.Loading
        withContext(Dispatchers.Default) { load(files) }
    }

    private fun load(files: ModelFiles): Boolean {
        return try {
            val m = LlmModule(files.model.absolutePath, files.tokenizer.absolutePath, TEMPERATURE)
            val t0 = SystemClock.elapsedRealtime()
            // In this ExecuTorch version load() returns nothing and throws if loading fails.
            m.load()
            val loadMs = SystemClock.elapsedRealtime() - t0
            module = m
            _state.value = AdvisorState.Ready(files.model.name, loadMs, ProcessMemory.rssMb())
            true
        } catch (t: Throwable) {
            // UnsatisfiedLinkError is an Error, not an Exception, so catch Throwable.
            _state.value = AdvisorState.Failed(
                "Could not start the on-device model: " + (t.message ?: t.javaClass.simpleName)
            )
            false
        }
    }

    /**
     * Runs the prompt and returns the full text with timing. [onPartial] gets the text so far
     * after every token. Returns null if the model is not loaded.
     */
    suspend fun generate(
        prompt: String,
        maxNewTokens: Int,
        onPartial: (String) -> Unit = {}
    ): GenerationResult? = lock.withLock {
        val m = module ?: return@withLock null
        withContext(Dispatchers.Default) { runGeneration(m, prompt, maxNewTokens, onPartial) }
    }

    /** Safe to call from any thread while a generation is running. */
    fun stop() {
        try {
            module?.stop()
        } catch (_: Throwable) {
            // Nothing is running, so there is nothing to stop.
        }
    }

    // Blocks until generation ends, so it must run off the main thread.
    private fun runGeneration(
        m: LlmModule,
        prompt: String,
        maxNewTokens: Int,
        onPartial: (String) -> Unit
    ): GenerationResult {
        val out = StringBuilder()
        var count = 0
        var firstTokenAt = 0L
        var hitLimit = false
        var runtimeStats: JSONObject? = null
        var failure: String? = null
        val start = SystemClock.elapsedRealtime()

        val callback = object : LlmCallback {
            override fun onResult(token: String) {
                if (count == 0) firstTokenAt = SystemClock.elapsedRealtime()
                count++
                out.append(token)
                onPartial(out.toString())
                if (count >= maxNewTokens && !hitLimit) {
                    hitLimit = true
                    m.stop()
                }
            }

            override fun onStats(statsJson: String) {
                runtimeStats = try {
                    JSONObject(statsJson)
                } catch (_: Exception) {
                    null
                }
            }

            override fun onError(errorCode: Int, message: String) {
                failure = "ExecuTorch error $errorCode: $message"
            }
        }

        try {
            // Each request is independent, so clear the previous conversation first.
            m.resetContext()
            val config = LlmGenerationConfig.create()
                .seqLen(SEQ_LEN)
                .temperature(TEMPERATURE)
                .echo(false)
                .build()
            m.generate(prompt, config, callback)
        } catch (t: Throwable) {
            failure = t.message ?: t.javaClass.simpleName
        }

        val end = SystemClock.elapsedRealtime()
        val totalMs = end - start
        val firstMs = if (count > 0) firstTokenAt - start else totalMs
        val decodeMs = if (count > 1) end - firstTokenAt else 0L
        val tokensPerSec = if (decodeMs > 0) (count - 1) * 1000.0 / decodeMs else 0.0
        val promptTokens = runtimeStats?.optInt("prompt_tokens", -1)?.takeIf { it >= 0 }

        val stats = GenerationStats(
            tokens = count,
            totalMs = totalMs,
            firstTokenMs = firstMs,
            decodeTokensPerSec = tokensPerSec,
            promptTokens = promptTokens,
            rssMb = ProcessMemory.rssMb(),
            stoppedAtLimit = hitLimit
        )
        return GenerationResult(out.toString(), stats, failure)
    }
}

object ProcessMemory {
    /** Memory held by this app process in MB, read from /proc. Returns -1 if unavailable. */
    fun rssMb(): Long = try {
        val line = File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS:") }
        val kb = line?.filter { it.isDigit() }?.toLongOrNull()
        if (kb == null) -1L else kb / 1024
    } catch (_: Exception) {
        -1L
    }
}
