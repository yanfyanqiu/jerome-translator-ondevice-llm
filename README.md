# jerome-translator-ondevice-llm

Offline AI translator for Android. Runs LLMs on-device via llama.cpp -- 50+ languages, no internet needed.

<p align="center">
  <img src="jerome.png" alt="Jerome Translator" width="200"/>
</p>

## Features

- **Fully offline** -- translations run on-device via llama.cpp with no data sent to any server
- **50+ languages** -- supports all major world languages, from English to Mongolian
- **Voice input** -- speak to translate using Android's speech recognition
- **Text-to-speech** -- hear translations read aloud
- **Multiple model tiers** -- choose your quality/speed tradeoff, from 0.6B to 4B parameters
- **Structured output** -- optional GBNF grammar constrains the model to produce clean JSON, preventing hallucinations
- **System prompt caching** -- the KV cache preserves the system prompt across translations for faster inference
- **Translation history** -- all translations are saved locally with Room and can be browsed or deleted
- **Material You** -- dynamic colors on Android 12+, with light/dark theme support

## Supported Models

All models are open-weight and licensed under **Apache 2.0**.

| Preset | Model | Size | Quality | Languages |
|--------|-------|------|---------|-----------|
| Light | Qwen 3 0.6B Q4 | ~400 MB | Good | 100+ |
| Lite | Qwen 3.5 0.8B Q8 | ~800 MB | Great | 200+ |
| **Balanced** | **Qwen 3.5 2B Q4** | **~1.3 GB** | **Great** | **200+** |
| Quality | Qwen 3.5 2B Q8 | ~2.2 GB | Excellent | 200+ |
| Ultra | Qwen 3.5 4B Q4 | ~2.8 GB | Best | 200+ |

Models are downloaded from Hugging Face on first launch. The user picks a quality level during onboarding.

## Architecture

```
app/src/main/
├── java/com/app/jerometranslator/
│   ├── MainActivity.kt              # Entry point, screen routing
│   ├── JeromeApp.kt                 # Application class, Room database init
│   ├── config/
│   │   ├── ModelConfig.kt            # Model presets, GBNF grammar, download URLs
│   │   └── LanguagePairs.kt          # 50+ language definitions, system prompt builder
│   ├── engine/
│   │   ├── LlamaBridge.kt            # JNI interface to llama.cpp
│   │   ├── TranslationEngine.kt      # Model lifecycle, language pair switching, inference
│   │   └── OutputValidator.kt        # Post-processing: JSON parsing, script validation, cleanup
│   ├── download/
│   │   └── ModelDownloader.kt        # OkHttp-based downloader with resume support
│   ├── voice/
│   │   ├── SpeechInput.kt            # Android SpeechRecognizer wrapper (Flow-based)
│   │   └── SpeechOutput.kt           # Android TextToSpeech wrapper (coroutine-based)
│   ├── data/
│   │   ├── AppDatabase.kt            # Room database
│   │   ├── TranslationHistoryDao.kt  # DAO for translation history
│   │   └── TranslationHistoryEntity.kt
│   └── ui/
│       ├── TranslationScreen.kt      # Main translation UI
│       ├── TranslationViewModel.kt   # State management, orchestration
│       ├── OnboardingScreen.kt       # First-launch model selection
│       ├── DownloadScreen.kt         # Model download progress
│       ├── HistoryScreen.kt          # Translation history browser
│       ├── StatisticsScreen.kt       # Session performance stats
│       ├── SettingsSheet.kt          # Settings bottom sheet (grammar, model, storage)
│       ├── LanguageSelector.kt       # Searchable language picker
│       └── theme/Theme.kt            # Material You theme with dynamic colors
├── cpp/
│   └── llama_jni.cpp                 # C++ JNI bridge: model loading, inference, grammar sampling
└── AndroidManifest.xml
```

### How inference works

1. **Model loading** -- `llama_jni.cpp` loads GGUF weights via llama.cpp and creates an inference context with auto-tuned thread count
2. **System prompt prefill** -- a ChatML-formatted system prompt is decoded and its KV cache position is saved, so it can be reused across translations without re-processing
3. **Grammar-constrained generation** -- an optional GBNF grammar forces the model to output `{"translation": "..."}`, which prevents hallucination and commentary
4. **Output validation** -- `OutputValidator` strips `<think>` blocks, ChatML tags, meta-commentary prefixes, and validates the output script matches the target language
5. **KV cache reset** -- between translations, only the user turn is discarded; the system prompt stays cached

## Building

### Prerequisites

- Android Studio (Hedgehog or later)
- Android NDK (installed via SDK Manager)
- CMake 3.22.1+ (installed via SDK Manager)
- Git (for CMake to fetch llama.cpp)
- An arm64-v8a Android device

### Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/SpeederX/jerome-translator-ondevice-llm.git
   cd jerome-translator-ondevice-llm
   ```

2. Open in Android Studio and sync Gradle.

3. Build and run on an arm64 device.

CMake automatically fetches [llama.cpp `b8739`](https://github.com/ggerganov/llama.cpp/releases/tag/b8739) during the first build -- no manual setup required. The first build will take longer as llama.cpp is downloaded and compiled.

### Notes

- Built against **llama.cpp `b8739`** (commit `d132f22f`, April 2026). The version is pinned in `app/CMakeLists.txt` via CMake `FetchContent`.
- Only `arm64-v8a` is targeted (see `app/build.gradle.kts`). x86_64 emulators won't work for inference.
- The build uses `android:largeHeap="true"` since models occupy significant memory.
- Release builds enable ProGuard minification.

## How it works for the user

1. **Onboarding** -- pick a translation quality level (determines which model to download)
2. **Download** -- the GGUF model is downloaded from Hugging Face with progress indication and resume support
3. **Translate** -- type or speak text, tap Translate, see the result with copy and read-aloud options
4. **Swap languages** -- one-tap language swap with input/output text exchange
5. **Settings** -- switch models (auto-downloads if needed), toggle structured output and reasoning mode, manage downloaded model files

## License

This project is licensed under the [MIT License](LICENSE).

All bundled model presets are downloaded from Hugging Face and are licensed under Apache 2.0:
- [Qwen 3](https://huggingface.co/Qwen/Qwen3-0.6B) by Alibaba Qwen -- Apache 2.0
- [Qwen 3.5](https://huggingface.co/Qwen/Qwen3.5-2B) by Alibaba Qwen -- Apache 2.0

This project uses [llama.cpp](https://github.com/ggerganov/llama.cpp) `b8739` (MIT License).
