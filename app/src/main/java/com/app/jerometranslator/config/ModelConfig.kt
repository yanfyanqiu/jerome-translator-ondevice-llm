package com.app.jerometranslator.config

data class ModelPreset(
    val id: String,
    val label: String,
    val description: String,
    val userFriendlyLabel: String,
    val onboardingDescription: String,
    val filename: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contextSize: Int = 2048,
    val maxOutputTokens: Int = 1024,
)

object ModelConfig {
    const val CONTEXT_SIZE = 2048
    const val MAX_OUTPUT_TOKENS = 1024

    // GBNF grammar constraining output to JSON: {"translation": "..."}
    val TRANSLATION_GRAMMAR = """
root ::= "{" ws "\"translation\"" ws ":" ws string ws "}"
string ::= "\"" chars "\""
chars ::= char*
char ::= [^"\\\x7F\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
ws ::= [ \t\n]*
""".trimIndent()

    val PRESETS = listOf(
        ModelPreset(
            id = "qwen3-0.6b-q4",
            label = "Light",
            description = "Qwen 3 0.6B Q4 — ~400MB, fast, good quality, 100+ languages",
            userFriendlyLabel = "Light",
            onboardingDescription = "Fast, good translations (~400 MB)",
            filename = "Qwen3-0.6B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf",
            sizeBytes = 397_000_000L,
        ),
        ModelPreset(
            id = "qwen3.5-0.8b-q8",
            label = "Lite",
            description = "Qwen 3.5 0.8B Q8 — ~800MB, fast, great quality, 200+ languages",
            userFriendlyLabel = "Standard",
            onboardingDescription = "Fast, great quality (~800 MB)",
            filename = "Qwen3.5-0.8B-Q8_0.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q8_0.gguf",
            sizeBytes = 812_000_000L,
        ),
        ModelPreset(
            id = "qwen3.5-2b-q4",
            label = "Balanced",
            description = "Qwen 3.5 2B Q4 — ~1.3GB, medium speed, great quality, 200+ languages (recommended)",
            userFriendlyLabel = "Recommended",
            onboardingDescription = "Best balance for most devices (~1.3 GB)",
            filename = "Qwen3.5-2B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
            sizeBytes = 1_300_000_000L,
        ),
        ModelPreset(
            id = "qwen3.5-2b-q8",
            label = "Quality",
            description = "Qwen 3.5 2B Q8 — ~2.2GB, slower, excellent quality, 200+ languages",
            userFriendlyLabel = "High Quality",
            onboardingDescription = "Slower, excellent quality (~2.2 GB)",
            filename = "Qwen3.5-2B-Q8_0.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q8_0.gguf",
            sizeBytes = 2_200_000_000L,
        ),
        ModelPreset(
            id = "qwen3.5-4b-q4",
            label = "Ultra",
            description = "Qwen 3.5 4B Q4 — ~2.8GB, slow, best quality, 200+ languages (high-end devices)",
            userFriendlyLabel = "Ultra",
            onboardingDescription = "Best quality, powerful devices only (~2.8 GB)",
            filename = "Qwen3.5-4B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
            sizeBytes = 2_800_000_000L,
        ),
    )

    val DEFAULT_PRESET = PRESETS.first { it.id == "qwen3.5-2b-q4" }
}
