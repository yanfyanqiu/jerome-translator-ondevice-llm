package com.app.jerometranslator.engine

import com.app.jerometranslator.config.LanguagePair
import com.app.jerometranslator.config.ModelConfig
import com.app.jerometranslator.config.SystemPromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TranslationResult(
    val text: String,
    val warning: String? = null,
)

class TranslationEngine {
    private val bridge = LlamaBridge()
    private val mutex = Mutex()
    private var modelPtr: Long = 0
    private var contextPtr: Long = 0
    private var currentContextSize: Int = ModelConfig.CONTEXT_SIZE
    private var currentPair: LanguagePair? = null
    private var grammarEnabled: Boolean = true
    @Volatile var noThinkEnabled: Boolean = true

    val isLoaded: Boolean get() = modelPtr != 0L && contextPtr != 0L

    suspend fun loadModel(
        modelPath: String,
        nativeLibDir: String,
        contextSize: Int = ModelConfig.CONTEXT_SIZE,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentContextSize = contextSize
            bridge.initBackend(nativeLibDir)
            modelPtr = bridge.loadModel(modelPath, contextSize)
            if (modelPtr == 0L) error("Failed to load model from $modelPath")
            contextPtr = bridge.createContext(modelPtr, contextSize)
            if (contextPtr == 0L) error("Failed to create inference context")
            if (grammarEnabled) {
                bridge.setGrammar(contextPtr, ModelConfig.TRANSLATION_GRAMMAR)
            }
        }
    }

    /**
     * Switch the active language pair. Recreates the inference context to
     * guarantee a clean state, then prefills the new system prompt.
     * Model weights stay in memory — only the KV cache is reallocated.
     */
    suspend fun switchLanguagePair(pair: LanguagePair, force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(isLoaded) { "Model not loaded" }
            if (pair == currentPair && !force) return@withContext

            // Recreate context for a guaranteed clean state instead of just
            // clearing the KV cache, which can leave stale position tracking.
            bridge.freeContext(contextPtr)
            contextPtr = bridge.createContext(modelPtr, currentContextSize)
            if (contextPtr == 0L) error("Failed to recreate inference context")
            if (grammarEnabled) {
                bridge.setGrammar(contextPtr, ModelConfig.TRANSLATION_GRAMMAR)
            }

            val systemPrompt = SystemPromptBuilder.build(pair, noThinkEnabled)
            val n = bridge.processSystemPrompt(contextPtr, systemPrompt)
            if (n < 0) error("Failed to process system prompt")
            currentPair = pair
        }
    }

    suspend fun setGrammarEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            grammarEnabled = enabled
            if (contextPtr != 0L) {
                if (enabled) bridge.setGrammar(contextPtr, ModelConfig.TRANSLATION_GRAMMAR)
                else bridge.clearGrammar(contextPtr)
            }
        }
    }

    suspend fun translate(text: String): TranslationResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(isLoaded) { "Model not loaded" }
            checkNotNull(currentPair) { "No language pair selected" }

            // Reset context to system prompt (discard previous user turn)
            bridge.resetToSystemPrompt(contextPtr)

            val userTurn = SystemPromptBuilder.formatUserTurn(text, grammarEnabled)
            val maxTokens = minOf(text.length * 3 + 64, ModelConfig.MAX_OUTPUT_TOKENS)
            val rawOutput = bridge.generate(contextPtr, userTurn, maxTokens)

            OutputValidator.validate(text, rawOutput, currentPair!!, grammarEnabled)
        }
    }

    fun unload() {
        if (contextPtr != 0L) {
            bridge.freeContext(contextPtr)
            contextPtr = 0
        }
        if (modelPtr != 0L) {
            bridge.freeModel(modelPtr)
            modelPtr = 0
        }
        bridge.freeBackend()
        currentPair = null
    }
}
