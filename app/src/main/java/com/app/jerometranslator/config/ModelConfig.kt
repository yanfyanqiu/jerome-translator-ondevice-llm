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
            label = "轻量",
            description = "Qwen3 0.6B Q4 — 约 400MB，速度快、质量良好，支持 100+ 语言",
            userFriendlyLabel = "轻量",
            onboardingDescription = "速度快、质量良好（约 400 MB）",
            filename = "Qwen3-0.6B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf",
            sizeBytes = 397_000_000L,
        ),
        ModelPreset(
            id = "qwen3.5-0.8b-q8",
            label = "标准",
            description = "Qwen3.5 0.8B Q8 — 约 800MB，速度快、质量优秀，支持 200+ 语言",
            userFriendlyLabel = "标准",
            onboardingDescription = "速度快、质量优秀（约 800 MB）",
            filename = "Qwen3.5-0.8B-Q8_0.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q8_0.gguf",
            sizeBytes = 812_000_000L,
        ),
        ModelPreset(
            id = "qwen3.5-2b-q4",
            label = "均衡",
            description = "Qwen3.5 2B Q4 — 约 1.3GB，速度适中、质量优秀，支持 200+ 语言（推荐）",
            userFriendlyLabel = "推荐",
            onboardingDescription = "多数设备的最佳平衡（约 1.3 GB）",
            filename = "Qwen3.5-2B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
            sizeBytes = 1_300_000_000L,
        ),
        ModelPreset(
            id = "qwen3.5-2b-q8",
            label = "高质",
            description = "Qwen3.5 2B Q8 — 约 2.2GB，较慢、质量极佳，支持 200+ 语言",
            userFriendlyLabel = "高质",
            onboardingDescription = "较慢、质量极佳（约 2.2 GB）",
            filename = "Qwen3.5-2B-Q8_0.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q8_0.gguf",
            sizeBytes = 2_200_000_000L,
        ),
        ModelPreset(
            id = "qwen3.5-4b-q4",
            label = "超高",
            description = "Qwen3.5 4B Q4 — 约 2.8GB，慢、质量最佳，支持 200+ 语言（高端设备）",
            userFriendlyLabel = "超高",
            onboardingDescription = "质量最佳，仅限高性能设备（约 2.8 GB）",
            filename = "Qwen3.5-4B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
            sizeBytes = 2_800_000_000L,
        ),
    )

    val DEFAULT_PRESET = PRESETS.first { it.id == "qwen3.5-2b-q4" }
}
