# Running SentryScan's on-device Llama on Arm (Bharat AI-SoC Challenge, PS3)

SentryScan v0.2.0-arm adds an on-device Llama model (ExecuTorch, XNNPACK CPU backend) next to the existing rule engine.
The app is built for `arm64-v8a` only. Everything below runs on an Arm Android phone, offline.

## 1. Get the model (about 1 GB, one time)
You need two files: a `.pte` model and `tokenizer.model`.

**Option A (fastest): pre-exported model.** On Hugging Face open
`executorch-community/Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8-ET`, accept the Llama licence, and download the `.pte` file and the tokenizer from the Files tab.

**Option B: export it yourself.** Follow the Arm learning path for Llama 3.2 1B on ExecuTorch (`export_llama`, 8da4w quantisation, group size 64, 4-bit embeddings, context 1024). Use WSL or Google Colab on Windows.

## 2. Put it on the phone
1. Install and open the app, tap **Scam check** once. This creates the models folder and shows its path.
2. With USB debugging on:
```
adb push <your-model>.pte /sdcard/Android/data/com.enerix.sentryscan/files/models/
adb push tokenizer.model  /sdcard/Android/data/com.enerix.sentryscan/files/models/
```
3. Fallback if that folder is not writable: `/data/local/tmp/llama/`.
4. Back in the app tap **Check again**.

## 3. Offline demo
Turn on airplane mode, open **Scam check**, paste a scam message, tap **Check message**. The rule check and the AI opinion both work with no network. The only online screen is "Optional: check a domain online".

## 4. Benchmark for the report
Run a scan on the Dashboard, then open **Arm benchmark** and tap **Run benchmark**. Use **Copy results for the report**. It records chip, Arm features (NEON, dot product, i8mm if present), rule-scan time, model load time, memory, time to first token and tokens per second. Report your own phone's numbers only.

## Honest notes
- Runtime is ExecuTorch with the XNNPACK CPU backend. No GPU or NPU is used. Whether the prebuilt library uses Arm KleidiAI kernels has not been verified, so do not claim it.
- `org.pytorch:executorch-android:1.5.1` in `app/build.gradle.kts` may not be the newest. If Gradle cannot resolve it, use the newest version on Maven Central. API names (`LlmModule`, `LlmCallback`) may differ slightly between versions.
- Llama 3 chat format is in `PromptTemplates.kt`. If the output starts with odd text, toggle the BOS constant there.
- The unit test messages are a small hand-written sample, not an accuracy benchmark.
