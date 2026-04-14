package com.app.jerometranslator.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class SpeechOutput(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
    }

    fun isLanguageSupported(languageCode: String): Boolean {
        if (!ready) return false
        val locale = Locale.forLanguageTag(languageCode)
        val result = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        return result >= TextToSpeech.LANG_AVAILABLE
    }

    suspend fun speak(text: String, languageCode: String): Boolean {
        if (!ready) return false
        val engine = tts ?: return false

        val locale = Locale.forLanguageTag(languageCode)
        if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return false
        engine.language = locale

        return suspendCancellableCoroutine { cont ->
            val utteranceId = "jerome_${System.currentTimeMillis()}"

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(false)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && cont.isActive) cont.resume(false)
                }
            })

            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

            cont.invokeOnCancellation {
                engine.stop()
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
