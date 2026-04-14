package com.app.jerometranslator.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

sealed class SpeechEvent {
    data object Listening : SpeechEvent()
    data class Partial(val text: String) : SpeechEvent()
    data class Result(val text: String) : SpeechEvent()
    data class Error(val message: String) : SpeechEvent()
}

class SpeechInput(private val context: Context) {

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Check if offline speech recognition is likely available for the given language.
     * Falls back to online if offline is not supported.
     */
    fun isLanguageLikelySupported(languageCode: String): Boolean {
        // SpeechRecognizer doesn't expose per-language offline availability,
        // so we rely on the static flag + general availability.
        return isAvailable
    }

    fun listen(languageCode: String): Flow<SpeechEvent> = callbackFlow {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechEvent.Listening)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(SpeechEvent.Partial(matches[0]))
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(SpeechEvent.Result(matches[0]))
                } else {
                    trySend(SpeechEvent.Error("No speech recognized"))
                }
                channel.close()
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_NETWORK ->
                        "Speech recognition requires an internet connection or an offline language pack. " +
                            "Download it in Settings > System > Language > Speech."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                        "This language is not supported for voice input. " +
                            "Try downloading the offline speech pack in Settings > System > Language > Speech."
                    else -> "Recognition error ($error)"
                }
                trySend(SpeechEvent.Error(msg))
                channel.close()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }

    companion object {
        fun localeFor(languageCode: String): Locale = Locale.forLanguageTag(languageCode)
    }
}
