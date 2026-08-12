package com.app.jerometranslator.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.jerometranslator.config.Language
import com.app.jerometranslator.config.LanguagePair
import com.app.jerometranslator.config.Languages
import com.app.jerometranslator.config.ModelConfig
import com.app.jerometranslator.download.LocalModelManager
import com.app.jerometranslator.engine.TranslationEngine
import com.app.jerometranslator.engine.TranslationResult
import com.app.jerometranslator.voice.SpeechEvent
import com.app.jerometranslator.voice.SpeechInput
import com.app.jerometranslator.voice.SpeechOutput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class ConferenceUiState(
    val isEnabled: Boolean = false,
    val isModelLoaded: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isTranslating: Boolean = false,
    val statusText: String = "Conference Mode is off",
    val lastInputText: String = "",
    val lastOutputText: String = "",
    val detectedLanguage: String = "",
    val sourceLanguage: Language = Languages.DEFAULT_SOURCE,
    val targetLanguage: Language = Languages.DEFAULT_TARGET,
    val availableLocalModels: List<LocalModelManager.LocalModel> = emptyList(),
    val selectedLocalModel: LocalModelManager.LocalModel? = null,
    val error: String? = null,
    val conferenceRunning: Boolean = false,
)

class ConferenceViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ConferenceUiState())
    val state: StateFlow<ConferenceUiState> = _state

    private val localModelManager = LocalModelManager(application)
    private val engine = TranslationEngine()
    private val speechInput = SpeechInput(application)
    private val speechOutput = SpeechOutput(application)

    private var listenJob: Job? = null
    private var autoLoopActive = false

    init {
        val sysLocale = Locale.getDefault()
        val isZh = sysLocale.language == "zh"
        val src = if (isZh) Languages.ALL.first { it.code == "zh" } else Languages.ALL.first { it.code == "en" }
        val tgt = if (isZh) Languages.ALL.first { it.code == "en" } else Languages.ALL.first { it.code == "zh" }
        _state.update { it.copy(sourceLanguage = src, targetLanguage = tgt) }
        refreshLocalModels()
    }

    fun refreshLocalModels() {
        val models = localModelManager.listModels()
        _state.update { it.copy(availableLocalModels = models) }
        if (models.isNotEmpty() && _state.value.selectedLocalModel == null) {
            _state.update { it.copy(selectedLocalModel = models.first()) }
        }
    }

    fun selectLocalModel(model: LocalModelManager.LocalModel) {
        _state.update { it.copy(selectedLocalModel = model) }
    }

    private suspend fun detectLanguage(text: String): String {
        if (!_state.value.isModelLoaded) return _state.value.sourceLanguage.code
        try {
            val hasChinese = text.codePoints().anyMatch { cp -> (cp in 0x4E00..0x9FFF) || (cp in 0x3400..0x4DBF) }
            if (hasChinese) return "zh"
            val raw: TranslationResult = engine.translate(text)
            val code = raw.text.trim().lowercase().split("\s+".toRegex()).firstOrNull() ?: "en"
            return if (Languages.ALL.map { it.code }.toSet().contains(code)) code else "en"
        } catch (e: Exception) { Log.w("ConferenceVM", "lang detection failed", e); return "en" }
    }

    private suspend fun autoLoop() {
        if (!autoLoopActive) return
        _state.update { it.copy(isListening = true, statusText = "Listening...") }
        speechInput.listen(_state.value.sourceLanguage.code).collect { event ->
            when (event) {
                is SpeechEvent.Listening -> _state.update { it.copy(isListening = true, statusText = "Listening...") }
                is SpeechEvent.Partial -> _state.update { it.copy(lastInputText = event.text, statusText = "Listening: ${event.text}") }
                is SpeechEvent.Result -> {
                    val text = event.text.trim()
                    if (text.isBlank()) { if (autoLoopActive) resumeListening(); return@collect }
                    _state.update { it.copy(isListening = false, isTranslating = true, lastInputText = text, statusText = "Translating...") }
                    processAndSpeak(text)
                }
                is SpeechEvent.Error -> {
                    _state.update { it.copy(error = event.message, isListening = false) }
                    if (autoLoopActive) { delay(1000L); resumeListening() }
                }
            }
        }
    }

    private suspend fun processAndSpeak(text: String) {
        try {
            val detectedLang = detectLanguage(text)
            _state.update { it.copy(detectedLanguage = detectedLang) }
            val needsSwap = detectedLang == _state.value.targetLanguage.code
            val actualSrc = if (needsSwap) _state.value.targetLanguage else _state.value.sourceLanguage
            val actualTgt = if (needsSwap) _state.value.sourceLanguage else _state.value.targetLanguage
            engine.switchLanguagePair(LanguagePair(actualSrc, actualTgt))
            val result: TranslationResult = engine.translate(text)
            val translated = result.text
            _state.update { it.copy(isTranslating = false, lastOutputText = translated, statusText = "Speaking...", isSpeaking = true) }
            speechInput.stopListening()
            _state.update { it.copy(isListening = false) }
            if (translated.isNotBlank()) {
                speechOutput.speak(translated, actualTgt.code)
                _state.update { it.copy(isSpeaking = false) }
            }
            delay(500L)
            if (autoLoopActive) resumeListening()
        } catch (e: Exception) {
            _state.update { it.copy(isTranslating = false, isSpeaking = false, error = "Error: ${e.message}") }
            if (autoLoopActive) { delay(1000L); resumeListening() }
        }
    }

    private fun resumeListening() {
        if (!autoLoopActive || !_state.value.isEnabled || !_state.value.isModelLoaded) return
        listenJob?.cancel()
        listenJob = viewModelScope.launch { autoLoop() }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(isEnabled = enabled) }
        if (enabled) startConference() else stopConference()
    }

    private suspend fun loadLocalModel() {
        val model = _state.value.selectedLocalModel
        if (model == null) { _state.update { it.copy(error = "No model selected. Import a GGUF file first.") }; return }
        _state.update { it.copy(isModelLoaded = false, statusText = "Loading model...") }
        try {
            val nativeLibDir = getApplication<Application>().applicationInfo.nativeLibraryDir
            engine.loadModel(model.absolutePath, nativeLibDir, ModelConfig.CONTEXT_SIZE)
            engine.switchLanguagePair(LanguagePair(_state.value.sourceLanguage, _state.value.targetLanguage))
            _state.update { it.copy(isModelLoaded = true, statusText = "Model ready - press Start") }
        } catch (e: Exception) { _state.update { it.copy(error = "Failed to load model: ${e.message}") }
        }
    }

    fun startConference() {
        if (!_state.value.isModelLoaded) viewModelScope.launch { loadLocalModel() }
        autoLoopActive = true
        _state.update { it.copy(conferenceRunning = true, statusText = "Starting...") }
        viewModelScope.launch { delay(500L); if (autoLoopActive) autoLoop() }
    }

    fun stopConference() {
        autoLoopActive = false
        listenJob?.cancel()
        speechInput.stopListening()
        speechOutput.stop()
        _state.update { it.copy(conferenceRunning = false, isListening = false, isSpeaking = false, isTranslating = false, statusText = "Conference Mode is off") }
    }

    fun swapLanguages() {
        _state.update { it.copy(sourceLanguage = it.targetLanguage, targetLanguage = it.sourceLanguage) }
        viewModelScope.launch {
            try { engine.switchLanguagePair(LanguagePair(_state.value.sourceLanguage, _state.value.targetLanguage)) } catch (_: Exception) {}
        }
    }

    fun dismissError() { _state.update { it.copy(error = null) } }

    override fun onCleared() {
        super.onCleared()
        stopConference()
        engine.unload()
        speechOutput.shutdown()
    }
}
