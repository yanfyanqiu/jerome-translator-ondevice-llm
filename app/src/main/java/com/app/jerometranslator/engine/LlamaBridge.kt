package com.app.jerometranslator.engine

class LlamaBridge {
    companion object {
        init {
            System.loadLibrary("jerometranslator")
        }
    }

    external fun initBackend(nativeLibDir: String)
    external fun freeBackend()

    external fun loadModel(modelPath: String, nCtx: Int): Long
    external fun freeModel(modelPtr: Long)

    external fun createContext(modelPtr: Long, nCtx: Int): Long
    external fun freeContext(contextPtr: Long)

    external fun clearKvCache(contextPtr: Long)
    external fun resetToSystemPrompt(contextPtr: Long)
    external fun processSystemPrompt(contextPtr: Long, prompt: String): Int

    external fun generate(contextPtr: Long, userInput: String, maxTokens: Int): String

    external fun setGrammar(contextPtr: Long, grammar: String)
    external fun clearGrammar(contextPtr: Long)
}
