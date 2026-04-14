package com.app.jerometranslator.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.jerometranslator.JeromeApp
import com.app.jerometranslator.config.Language
import com.app.jerometranslator.config.LanguagePair
import com.app.jerometranslator.config.Languages
import com.app.jerometranslator.config.ModelConfig
import com.app.jerometranslator.config.ModelPreset
import com.app.jerometranslator.data.TranslationHistoryEntity
import com.app.jerometranslator.download.ModelDownloader
import com.app.jerometranslator.engine.TranslationEngine
import com.app.jerometranslator.voice.SpeechEvent
import com.app.jerometranslator.voice.SpeechInput
import com.app.jerometranslator.voice.SpeechOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TranslationUiState(
    val appPhase: AppPhase = AppPhase.ONBOARDING,
    val currentScreen: ScreenRoute = ScreenRoute.TRANSLATION,
    val downloadProgress: Float = 0f,
    val sourceLanguage: Language = Languages.DEFAULT_SOURCE,
    val targetLanguage: Language = Languages.DEFAULT_TARGET,
    val inputText: String = "",
    val outputText: String = "",
    val isTranslating: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val grammarEnabled: Boolean = true,
    val noThinkEnabled: Boolean = true,
    val activePreset: ModelPreset = ModelConfig.DEFAULT_PRESET,
    val showStats: Boolean = false,
    val lastTranslationTimeMs: Long? = null,
    val averageTranslationTimeMs: Long? = null,
    val totalTranslations: Int = 0,
    val downloadedModels: List<DownloadedModelInfo> = emptyList(),
    val warning: String? = null,
    val error: String? = null,
)

data class DownloadedModelInfo(
    val preset: ModelPreset,
    val fileSizeBytes: Long,
    val isActive: Boolean,
)

enum class AppPhase {
    ONBOARDING,
    CHECKING_MODEL,
    DOWNLOADING,
    LOADING_MODEL,
    READY,
}

enum class ScreenRoute {
    TRANSLATION,
    HISTORY,
    STATISTICS,
}

class TranslationViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TranslationUiState())
    val state: StateFlow<TranslationUiState> = _state

    private val prefs: SharedPreferences =
        application.getSharedPreferences("jerome", android.content.Context.MODE_PRIVATE)

    private val engine = TranslationEngine()
    private val downloader = ModelDownloader()
    private val historyDao = (application as JeromeApp).database.translationHistoryDao()
    val historyFlow: Flow<List<TranslationHistoryEntity>> = historyDao.getAll()
    val speechInput = SpeechInput(application)
    val speechOutput = SpeechOutput(application)

    private var listenJob: Job? = null
    private var translateJob: Job? = null
    private val translationTimesMs: MutableList<Long> = mutableListOf()

    private val modelDir: File
        get() = getApplication<Application>().getExternalFilesDir(null)
            ?: getApplication<Application>().filesDir

    private val activePreset: ModelPreset get() = _state.value.activePreset

    private val modelFile: File
        get() = File(modelDir, activePreset.filename)

    init {
        val savedPresetId = prefs.getString("selected_preset", null)
        val onboardingDone = prefs.getBoolean("onboarding_done", false)
        if (savedPresetId != null) {
            val preset = ModelConfig.PRESETS.firstOrNull { it.id == savedPresetId }
            if (preset != null) {
                _state.update { it.copy(activePreset = preset) }
            }
        }
        if (onboardingDone) {
            _state.update { it.copy(appPhase = AppPhase.CHECKING_MODEL) }
            checkModelAndStart()
        }
    }

    fun completeOnboarding(preset: ModelPreset) {
        prefs.edit().putString("selected_preset", preset.id).putBoolean("onboarding_done", true).apply()
        _state.update { it.copy(activePreset = preset, appPhase = AppPhase.CHECKING_MODEL) }
        checkModelAndStart()
    }

    private fun checkModelAndStart() {
        viewModelScope.launch {
            _state.update { it.copy(appPhase = AppPhase.CHECKING_MODEL) }
            val minSize = activePreset.sizeBytes * 9 / 10
            if (modelFile.exists() && modelFile.length() >= minSize) {
                android.util.Log.i("Jerome", "Model file found: ${modelFile.length()} bytes, loading")
                loadModel()
            } else {
                if (modelFile.exists()) {
                    android.util.Log.w("Jerome", "Model file incomplete (${modelFile.length()} bytes), re-downloading")
                    modelFile.delete()
                }
                _state.update { it.copy(appPhase = AppPhase.DOWNLOADING) }
                downloadModel()
            }
        }
    }

    private suspend fun downloadModel() {
        try {
            downloader.download(
                url = activePreset.downloadUrl,
                destination = modelFile,
                onProgress = { progress ->
                    _state.update { it.copy(downloadProgress = progress) }
                },
            )
            loadModel()
        } catch (e: Exception) {
            _state.update {
                it.copy(error = "Download failed: ${e.message}", appPhase = AppPhase.DOWNLOADING)
            }
        }
    }

    private suspend fun loadModel() {
        _state.update { it.copy(appPhase = AppPhase.LOADING_MODEL, error = null) }
        try {
            val nativeLibDir = getApplication<Application>().applicationInfo.nativeLibraryDir
            engine.loadModel(modelFile.absolutePath, nativeLibDir, activePreset.contextSize)
            val pair = LanguagePair(_state.value.sourceLanguage, _state.value.targetLanguage)
            engine.switchLanguagePair(pair)
            _state.update { it.copy(appPhase = AppPhase.READY) }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to load model: ${e.message}") }
        }
    }

    fun retryDownload() {
        _state.update { it.copy(error = null, downloadProgress = 0f) }
        viewModelScope.launch { downloadModel() }
    }

    fun setGrammarEnabled(enabled: Boolean) {
        _state.update { it.copy(grammarEnabled = enabled) }
        translateJob?.cancel()
        viewModelScope.launch {
            try {
                engine.setGrammarEnabled(enabled)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to update grammar: ${e.message}") }
            }
        }
    }

    fun setNoThinkEnabled(enabled: Boolean) {
        _state.update { it.copy(noThinkEnabled = enabled, outputText = "", warning = null) }
        engine.noThinkEnabled = enabled
        translateJob?.cancel()
        viewModelScope.launch {
            try {
                val pair = LanguagePair(_state.value.sourceLanguage, _state.value.targetLanguage)
                engine.switchLanguagePair(pair, force = true)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to update settings: ${e.message}") }
            }
        }
    }

    fun selectPreset(preset: ModelPreset) {
        if (preset.id == activePreset.id) return
        translateJob?.cancel()
        engine.unload()
        prefs.edit().putString("selected_preset", preset.id).apply()
        _state.update {
            it.copy(
                activePreset = preset,
                appPhase = AppPhase.CHECKING_MODEL,
                outputText = "",
                warning = null,
            )
        }
        checkModelAndStart()
    }

    fun onInputChanged(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun translate() {
        val text = _state.value.inputText.trim()
        if (text.isBlank()) return

        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            _state.update { it.copy(isTranslating = true, error = null, warning = null) }
            try {
                val startTime = System.nanoTime()
                val result = engine.translate(text)
                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

                translationTimesMs.add(elapsedMs)
                val avg = translationTimesMs.average().toLong()

                _state.update {
                    it.copy(
                        outputText = result.text,
                        warning = result.warning,
                        isTranslating = false,
                        lastTranslationTimeMs = elapsedMs,
                        averageTranslationTimeMs = avg,
                        totalTranslations = translationTimesMs.size,
                    )
                }
                val s = _state.value
                historyDao.insert(
                    TranslationHistoryEntity(
                        sourceLanguageCode = s.sourceLanguage.code,
                        sourceLanguageName = s.sourceLanguage.displayName,
                        targetLanguageCode = s.targetLanguage.code,
                        targetLanguageName = s.targetLanguage.displayName,
                        inputText = text,
                        outputText = result.text,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Translation failed: ${e.message}", isTranslating = false)
                }
            }
        }
    }

    fun setSourceLanguage(language: Language) {
        if (language == _state.value.sourceLanguage) return
        _state.update { it.copy(sourceLanguage = language, outputText = "", warning = null) }
        switchPair()
    }

    fun setTargetLanguage(language: Language) {
        if (language == _state.value.targetLanguage) return
        _state.update { it.copy(targetLanguage = language, outputText = "", warning = null) }
        switchPair()
    }

    fun swapLanguages() {
        _state.update {
            it.copy(
                sourceLanguage = it.targetLanguage,
                targetLanguage = it.sourceLanguage,
                inputText = it.outputText,
                outputText = "",
                warning = null,
            )
        }
        switchPair()
    }

    private fun switchPair() {
        translateJob?.cancel()
        viewModelScope.launch {
            try {
                val pair = LanguagePair(_state.value.sourceLanguage, _state.value.targetLanguage)
                engine.switchLanguagePair(pair)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to switch languages: ${e.message}") }
            }
        }
    }

    fun startListening() {
        if (!speechInput.isAvailable) {
            _state.update {
                it.copy(error = "Speech recognition is not available on this device. " +
                    "Install Google Speech Services or download the offline language pack " +
                    "in Settings > System > Language > Speech.")
            }
            return
        }

        listenJob = viewModelScope.launch {
            speechInput.listen(_state.value.sourceLanguage.code).collect { event ->
                when (event) {
                    is SpeechEvent.Listening -> _state.update { it.copy(isListening = true) }
                    is SpeechEvent.Partial -> _state.update { it.copy(inputText = event.text) }
                    is SpeechEvent.Result -> {
                        _state.update { it.copy(inputText = event.text, isListening = false) }
                        translate()
                    }
                    is SpeechEvent.Error -> {
                        _state.update { it.copy(error = event.message, isListening = false) }
                    }
                }
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        _state.update { it.copy(isListening = false) }
    }

    fun speakOutput() {
        val text = _state.value.outputText
        if (text.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isSpeaking = true) }
            speechOutput.speak(text, _state.value.targetLanguage.code)
            _state.update { it.copy(isSpeaking = false) }
        }
    }

    fun stopSpeaking() {
        speechOutput.stop()
        _state.update { it.copy(isSpeaking = false) }
    }

    fun toggleStats() {
        _state.update { it.copy(showStats = !it.showStats) }
    }

    fun navigateTo(screen: ScreenRoute) {
        _state.update { it.copy(currentScreen = screen) }
    }

    fun deleteModelAndSwitch(oldPreset: ModelPreset, newPreset: ModelPreset) {
        val oldFile = File(modelDir, oldPreset.filename)
        viewModelScope.launch(Dispatchers.IO) {
            if (oldFile.exists()) oldFile.delete()
        }
        selectPreset(newPreset)
    }

    fun refreshDownloadedModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val models = ModelConfig.PRESETS.mapNotNull { preset ->
                val file = File(modelDir, preset.filename)
                if (file.exists()) {
                    DownloadedModelInfo(
                        preset = preset,
                        fileSizeBytes = file.length(),
                        isActive = preset.id == _state.value.activePreset.id,
                    )
                } else null
            }
            _state.update { it.copy(downloadedModels = models) }
        }
    }

    fun deleteModel(preset: ModelPreset) {
        if (preset.id == _state.value.activePreset.id) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(modelDir, preset.filename)
            if (file.exists()) file.delete()
            refreshDownloadedModels()
        }
    }

    fun deleteHistoryEntry(entry: TranslationHistoryEntity) {
        viewModelScope.launch { historyDao.delete(entry) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyDao.deleteAll() }
    }

    fun showTtsUnavailableError(languageName: String) {
        _state.update {
            it.copy(error = "Text-to-speech for $languageName is not installed. " +
                "Go to Settings > System > Language > Text-to-speech to download the voice data.")
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        engine.unload()
        speechOutput.shutdown()
    }
}
