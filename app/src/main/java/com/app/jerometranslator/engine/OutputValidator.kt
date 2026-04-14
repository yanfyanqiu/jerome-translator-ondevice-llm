package com.app.jerometranslator.engine

import com.app.jerometranslator.config.LanguagePair
import org.json.JSONObject

object OutputValidator {

    private val META_PREFIXES = listOf(
        "Sure,", "Here is", "Here's", "The translation", "Translation:",
        "Note:", "Certainly", "Of course",
    )

    // Unicode script ranges for basic script-family validation
    private val SCRIPT_RANGES: Map<String, List<CharRange>> = mapOf(
        "zh" to listOf('\u4E00'..'\u9FFF', '\u3400'..'\u4DBF'),
        "ja" to listOf('\u3040'..'\u309F', '\u30A0'..'\u30FF', '\u4E00'..'\u9FFF'),
        "ko" to listOf('\uAC00'..'\uD7AF', '\u1100'..'\u11FF'),
        "ar" to listOf('\u0600'..'\u06FF', '\u0750'..'\u077F'),
        "he" to listOf('\u0590'..'\u05FF'),
        "hi" to listOf('\u0900'..'\u097F'),
        "bn" to listOf('\u0980'..'\u09FF'),
        "ta" to listOf('\u0B80'..'\u0BFF'),
        "te" to listOf('\u0C00'..'\u0C7F'),
        "th" to listOf('\u0E00'..'\u0E7F'),
        "ka" to listOf('\u10A0'..'\u10FF'),
        "hy" to listOf('\u0530'..'\u058F'),
        "ru" to listOf('\u0400'..'\u04FF'),
        "uk" to listOf('\u0400'..'\u04FF'),
        "bg" to listOf('\u0400'..'\u04FF'),
        "sr" to listOf('\u0400'..'\u04FF'),
        "mk" to listOf('\u0400'..'\u04FF'),
        "el" to listOf('\u0370'..'\u03FF'),
        "fa" to listOf('\u0600'..'\u06FF', '\u0750'..'\u077F'),
        "ur" to listOf('\u0600'..'\u06FF', '\u0750'..'\u077F'),
        "mn" to listOf('\u0400'..'\u04FF', '\u1800'..'\u18AF'),
    )

    private val THINK_REGEX = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)

    fun validate(
        input: String,
        rawOutput: String,
        pair: LanguagePair,
        jsonMode: Boolean = false,
    ): TranslationResult {
        var output = rawOutput.trim()
        var warning: String? = null

        // JSON mode: parse structured output from grammar-constrained generation
        if (jsonMode && output.startsWith("{")) {
            try {
                val json = JSONObject(output)
                output = json.getString("translation")
                return TranslationResult(output.trim(), rawOutput = rawOutput)
            } catch (_: Exception) {
                warning = "Failed to parse structured output, using raw"
            }
        }

        // Strip <think>...</think> blocks (Qwen reasoning mode)
        if (output.contains("<think>", ignoreCase = true)) {
            output = output.replace(THINK_REGEX, "").trim()
            // Also strip unclosed <think> block (model still thinking when max tokens hit)
            val thinkIdx = output.indexOf("<think>", ignoreCase = true)
            if (thinkIdx >= 0) {
                output = output.substring(0, thinkIdx).trim()
            }
        }

        // Strip meta-commentary prefixes
        for (prefix in META_PREFIXES) {
            if (output.startsWith(prefix, ignoreCase = true)) {
                val newlineIdx = output.indexOf('\n')
                output = if (newlineIdx >= 0) output.substring(newlineIdx + 1).trim() else output
                warning = "Stripped model commentary"
                break
            }
        }

        // Remove trailing ChatML tags the model may have emitted
        output = output
            .removeSuffix("<|im_end|>")
            .removeSuffix("<|endoftext|>")
            .trim()

        // Empty output check
        if (output.isBlank() && input.isNotBlank()) {
            return TranslationResult("", warning = "Model produced empty output", rawOutput = rawOutput)
        }

        // Length ratio check
        val ratio = output.length.toFloat() / input.length.coerceAtLeast(1)
        if (ratio > 4.0f) {
            warning = "Translation is unusually long — may contain extra content"
        } else if (ratio < 0.15f && input.length > 10) {
            warning = "Translation is unusually short — may be truncated"
        }

        // Script validation for the target language
        val expectedRanges = SCRIPT_RANGES[pair.target.code]
        if (expectedRanges != null && output.length > 3) {
            val hasExpectedScript = output.any { ch ->
                expectedRanges.any { range -> ch in range }
            }
            if (!hasExpectedScript) {
                warning = "Output may not be in the expected script for ${pair.target.displayName}"
            }
        }

        return TranslationResult(output, warning, rawOutput = rawOutput)
    }
}
