package com.enerix.sentryscan.llm

import android.content.Context
import java.io.File

data class ModelFiles(val model: File, val tokenizer: File)

/**
 * Finds the Llama model on the phone. The .pte file is about 1 GB, so it is not packed inside
 * the APK. It is copied onto the phone once with adb (see docs/ARM_LLM_SETUP.md).
 */
object ModelLocator {

    // Searched in order. The first is the app's own folder, which adb can write to without root.
    private fun searchDirs(context: Context): List<File> = listOfNotNull(
        context.getExternalFilesDir("models"),
        File("/data/local/tmp/llama")
    )

    fun find(context: Context): ModelFiles? {
        for (dir in searchDirs(context)) {
            val files = dir.listFiles() ?: continue
            val model = files
                .filter { it.isFile && it.name.endsWith(".pte") }
                .maxByOrNull { it.length() } ?: continue
            val tokenizer = files
                .firstOrNull { it.isFile && it.name.startsWith("tokenizer") } ?: continue
            return ModelFiles(model, tokenizer)
        }
        return null
    }

    fun modelDir(context: Context): String =
        context.getExternalFilesDir("models")?.absolutePath ?: "(app storage is not available)"

    fun help(context: Context): String {
        val dir = modelDir(context)
        return "Copy two files into this folder on the phone:\n" + dir + "\n\n" +
            "1. the Llama 3.2 1B Instruct .pte model\n" +
            "2. tokenizer.model\n\n" +
            "From your computer, with the phone connected by USB:\n" +
            "adb push <model>.pte " + dir + "/\n" +
            "adb push tokenizer.model " + dir + "/"
    }
}
