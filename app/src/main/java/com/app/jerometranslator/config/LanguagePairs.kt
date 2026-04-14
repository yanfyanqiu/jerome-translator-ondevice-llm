package com.app.jerometranslator.config

data class Language(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val voiceInputSupported: Boolean = true,
    val voiceOutputSupported: Boolean = true,
)

data class LanguagePair(
    val source: Language,
    val target: Language,
)

object SystemPromptBuilder {

    fun build(pair: LanguagePair, noThink: Boolean = true): String {
        val src = pair.source.displayName
        val tgt = pair.target.displayName
        val noThinkTag = if (noThink) "\n/no_think" else ""
        // ChatML format for Qwen 3.5 Instruct
        return """<|im_start|>system
Translation engine: $src → $tgt. Output the translation only. No explanations, alternatives, or commentary. Preserve meaning, register, and tone exactly. Keep proper nouns and brand names as-is. Empty input → empty output. Input already in $tgt → output unchanged.$noThinkTag<|im_end|>
"""
    }

    fun formatUserTurn(text: String, jsonMode: Boolean = false): String {
        val instruction = if (jsonMode) {
            """Translate and respond with JSON: {"translation": "..."}: ${"\"\"\""}$text${"\"\"\""}"""
        } else {
            """Translate: ${"\"\"\""}$text${"\"\"\""}"""
        }
        return """<|im_start|>user
$instruction<|im_end|>
<|im_start|>assistant
"""
    }
}

object Languages {
    // Common languages — the full Qwen 3.5 set covers 200+.
    // Voice support flags reflect typical Android SpeechRecognizer/TTS availability.
    val ALL = listOf(
        Language("en", "English", "English"),
        Language("it", "Italian", "Italiano"),
        Language("fr", "French", "Fran\u00e7ais"),
        Language("de", "German", "Deutsch"),
        Language("es", "Spanish", "Espa\u00f1ol"),
        Language("pt", "Portuguese", "Portugu\u00eas"),
        Language("nl", "Dutch", "Nederlands"),
        Language("ru", "Russian", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439"),
        Language("zh", "Chinese", "\u4e2d\u6587"),
        Language("ja", "Japanese", "\u65e5\u672c\u8a9e"),
        Language("ko", "Korean", "\ud55c\uad6d\uc5b4"),
        Language("ar", "Arabic", "\u0627\u0644\u0639\u0631\u0628\u064a\u0629"),
        Language("hi", "Hindi", "\u0939\u093f\u0928\u094d\u0926\u0940"),
        Language("tr", "Turkish", "T\u00fcrk\u00e7e"),
        Language("pl", "Polish", "Polski"),
        Language("uk", "Ukrainian", "\u0423\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430"),
        Language("sv", "Swedish", "Svenska"),
        Language("da", "Danish", "Dansk"),
        Language("no", "Norwegian", "Norsk"),
        Language("fi", "Finnish", "Suomi"),
        Language("cs", "Czech", "\u010ce\u0161tina"),
        Language("ro", "Romanian", "Rom\u00e2n\u0103"),
        Language("hu", "Hungarian", "Magyar"),
        Language("el", "Greek", "\u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac"),
        Language("th", "Thai", "\u0e44\u0e17\u0e22"),
        Language("vi", "Vietnamese", "Ti\u1ebfng Vi\u1ec7t"),
        Language("id", "Indonesian", "Bahasa Indonesia"),
        Language("ms", "Malay", "Bahasa Melayu"),
        Language("he", "Hebrew", "\u05e2\u05d1\u05e8\u05d9\u05ea"),
        Language("fa", "Persian", "\u0641\u0627\u0631\u0633\u06cc"),
        Language("bn", "Bengali", "\u09ac\u09be\u0982\u09b2\u09be"),
        Language("ta", "Tamil", "\u0ba4\u0bae\u0bbf\u0bb4\u0bcd"),
        Language("te", "Telugu", "\u0c24\u0c46\u0c32\u0c41\u0c17\u0c41"),
        Language("ur", "Urdu", "\u0627\u0631\u062f\u0648"),
        Language("sw", "Swahili", "Kiswahili"),
        Language("af", "Afrikaans", "Afrikaans"),
        Language("ca", "Catalan", "Catal\u00e0"),
        Language("bg", "Bulgarian", "\u0411\u044a\u043b\u0433\u0430\u0440\u0441\u043a\u0438"),
        Language("hr", "Croatian", "Hrvatski"),
        Language("sk", "Slovak", "Sloven\u010dina"),
        Language("sl", "Slovenian", "Sloven\u0161\u010dina"),
        Language("sr", "Serbian", "\u0421\u0440\u043f\u0441\u043a\u0438"),
        Language("lt", "Lithuanian", "Lietuvi\u0173"),
        Language("lv", "Latvian", "Latvie\u0161u"),
        Language("et", "Estonian", "Eesti"),
        Language("ga", "Irish", "Gaeilge", voiceInputSupported = false),
        Language("cy", "Welsh", "Cymraeg", voiceInputSupported = false),
        Language("mt", "Maltese", "Malti", voiceInputSupported = false, voiceOutputSupported = false),
        Language("is", "Icelandic", "\u00cdslenska", voiceInputSupported = false),
        Language("mk", "Macedonian", "\u041c\u0430\u043a\u0435\u0434\u043e\u043d\u0441\u043a\u0438", voiceInputSupported = false),
        Language("ka", "Georgian", "\u10e5\u10d0\u10e0\u10d7\u10e3\u10da\u10d8", voiceInputSupported = false),
        Language("hy", "Armenian", "\u0540\u0561\u0575\u0565\u0580\u0565\u0576", voiceInputSupported = false, voiceOutputSupported = false),
        Language("mn", "Mongolian", "\u041c\u043e\u043d\u0433\u043e\u043b", voiceInputSupported = false, voiceOutputSupported = false),
    )

    val DEFAULT_SOURCE = ALL.first { it.code == "en" }
    val DEFAULT_TARGET = ALL.first { it.code == "it" }
}
