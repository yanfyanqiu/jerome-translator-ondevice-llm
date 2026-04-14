#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <algorithm>

#include "llama.h"
#include "ggml-backend.h"

#define TAG "JeromeJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct jerome_model {
    llama_model * model;
    int n_ctx;
};

struct jerome_context {
    llama_context * ctx;
    llama_sampler * sampler;
    const llama_vocab * vocab; // borrowed from model
    int n_system_tokens;       // position after system prompt for fast reset
    bool has_grammar;          // whether grammar sampler is active
};

static std::vector<llama_token> tokenize(const llama_vocab * vocab,
                                         const char * text, int text_len,
                                         bool add_bos, bool parse_special) {
    int n_max = text_len + 128;
    std::vector<llama_token> tokens(n_max);
    int n = llama_tokenize(vocab, text, text_len, tokens.data(), n_max,
                           add_bos, parse_special);
    if (n < 0) {
        tokens.resize(-n);
        n = llama_tokenize(vocab, text, text_len, tokens.data(), -n,
                           add_bos, parse_special);
    }
    tokens.resize(n >= 0 ? n : 0);
    return tokens;
}

extern "C" {

// ---------------------------------------------------------------------------
// Backend init / free
// ---------------------------------------------------------------------------

JNIEXPORT void JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_initBackend(JNIEnv * env, jobject, jstring nativeLibDir) {
    const char * path = env->GetStringUTFChars(nativeLibDir, nullptr);
    LOGI("Loading backends from %s", path);
    ggml_backend_load_all_from_path(path);
    env->ReleaseStringUTFChars(nativeLibDir, path);

    llama_backend_init();
    LOGI("Backend initialized");
}

JNIEXPORT void JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_freeBackend(JNIEnv *, jobject) {
    llama_backend_free();
    LOGI("Backend freed");
}

// ---------------------------------------------------------------------------
// Model load / free
// ---------------------------------------------------------------------------

JNIEXPORT jlong JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_loadModel(
        JNIEnv * env, jobject, jstring modelPath, jint nCtx) {

    const char * path = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params params = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(path, params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load model from %s", path);
        return 0;
    }

    auto * w = new jerome_model{model, static_cast<int>(nCtx)};
    LOGI("Model loaded, requested ctx=%d", static_cast<int>(nCtx));
    return reinterpret_cast<jlong>(w);
}

JNIEXPORT void JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_freeModel(
        JNIEnv *, jobject, jlong ptr) {
    auto * w = reinterpret_cast<jerome_model *>(ptr);
    if (w) {
        if (w->model) llama_model_free(w->model);
        delete w;
    }
    LOGI("Model freed");
}

// ---------------------------------------------------------------------------
// Context create / free
// ---------------------------------------------------------------------------

JNIEXPORT jlong JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_createContext(
        JNIEnv *, jobject, jlong modelPtr, jint nCtx) {

    auto * mw = reinterpret_cast<jerome_model *>(modelPtr);

    const int n_threads = std::max(2, std::min(4, (int)sysconf(_SC_NPROCESSORS_ONLN) - 2));

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx            = nCtx > 0 ? nCtx : mw->n_ctx;
    cp.n_batch          = 512;
    cp.n_threads        = n_threads;
    cp.n_threads_batch  = n_threads;

    llama_context * ctx = llama_init_from_model(mw->model, cp);
    if (!ctx) {
        LOGE("Failed to create context");
        return 0;
    }

    // Greedy sampler (temperature = 0, deterministic)
    llama_sampler * sampler =
        llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    const llama_vocab * vocab = llama_model_get_vocab(mw->model);
    auto * cw = new jerome_context{ctx, sampler, vocab, 0, false};
    LOGI("Context created, n_ctx=%d, n_threads=%d", static_cast<int>(cp.n_ctx), n_threads);
    return reinterpret_cast<jlong>(cw);
}

JNIEXPORT void JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_freeContext(
        JNIEnv *, jobject, jlong ptr) {
    auto * cw = reinterpret_cast<jerome_context *>(ptr);
    if (cw) {
        if (cw->sampler) llama_sampler_free(cw->sampler);
        if (cw->ctx)     llama_free(cw->ctx);
        delete cw;
    }
    LOGI("Context freed");
}

// ---------------------------------------------------------------------------
// KV cache operations
// ---------------------------------------------------------------------------

JNIEXPORT void JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_clearKvCache(
        JNIEnv *, jobject, jlong ptr) {
    auto * cw = reinterpret_cast<jerome_context *>(ptr);
    llama_memory_seq_rm(llama_get_memory(cw->ctx), -1, 0, -1);
    cw->n_system_tokens = 0;
    LOGI("KV cache cleared");
}

// Remove everything after the system prompt, keeping it cached.
JNIEXPORT void JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_resetToSystemPrompt(
        JNIEnv *, jobject, jlong ptr) {
    auto * cw = reinterpret_cast<jerome_context *>(ptr);
    if (cw->n_system_tokens > 0) {
        llama_memory_seq_rm(llama_get_memory(cw->ctx), -1, cw->n_system_tokens, -1);
        LOGI("Reset to system prompt (%d tokens kept)", cw->n_system_tokens);
    }
}

// ---------------------------------------------------------------------------
// Process system prompt (prefill) — records token count for fast reset
// ---------------------------------------------------------------------------

JNIEXPORT jint JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_processSystemPrompt(
        JNIEnv * env, jobject, jlong ptr, jstring prompt) {

    auto * cw = reinterpret_cast<jerome_context *>(ptr);
    const char * text = env->GetStringUTFChars(prompt, nullptr);

    auto tokens = tokenize(cw->vocab, text, (int)strlen(text), true, true);
    env->ReleaseStringUTFChars(prompt, text);

    if (tokens.empty()) {
        LOGE("System prompt tokenization failed");
        return -1;
    }

    // Reset sampler state before prefilling new system prompt
    llama_sampler_reset(cw->sampler);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    if (llama_decode(cw->ctx, batch) != 0) {
        LOGE("Decode failed during system prompt prefill");
        return -1;
    }

    cw->n_system_tokens = (int)tokens.size();
    LOGI("System prompt processed: %d tokens", cw->n_system_tokens);
    return cw->n_system_tokens;
}

// ---------------------------------------------------------------------------
// Grammar sampler control
// ---------------------------------------------------------------------------

static void rebuild_sampler(jerome_context * cw, const char * grammar_str) {
    if (cw->sampler) llama_sampler_free(cw->sampler);

    llama_sampler * sampler =
        llama_sampler_chain_init(llama_sampler_chain_default_params());

    if (grammar_str && strlen(grammar_str) > 0) {
        llama_sampler_chain_add(sampler,
            llama_sampler_init_grammar(cw->vocab, grammar_str, "root"));
        cw->has_grammar = true;
        LOGI("Grammar sampler enabled");
    } else {
        cw->has_grammar = false;
    }

    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    cw->sampler = sampler;
}

JNIEXPORT void JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_setGrammar(
        JNIEnv * env, jobject, jlong ptr, jstring grammar) {
    auto * cw = reinterpret_cast<jerome_context *>(ptr);
    const char * str = env->GetStringUTFChars(grammar, nullptr);
    rebuild_sampler(cw, str);
    env->ReleaseStringUTFChars(grammar, str);
}

JNIEXPORT void JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_clearGrammar(
        JNIEnv *, jobject, jlong ptr) {
    auto * cw = reinterpret_cast<jerome_context *>(ptr);
    rebuild_sampler(cw, nullptr);
    LOGI("Grammar sampler cleared");
}

// ---------------------------------------------------------------------------
// Generate translation
// ---------------------------------------------------------------------------

JNIEXPORT jstring JNICALL
Java_com_app_jerometranslator_engine_LlamaBridge_generate(
        JNIEnv * env, jobject, jlong ptr,
        jstring userInput, jint maxTokens) {

    auto * cw = reinterpret_cast<jerome_context *>(ptr);
    const char * input = env->GetStringUTFChars(userInput, nullptr);

    // Tokenize user turn (no BOS — already in context)
    auto tokens = tokenize(cw->vocab, input, (int)strlen(input), false, true);
    env->ReleaseStringUTFChars(userInput, input);

    if (tokens.empty()) {
        LOGE("User input tokenization failed");
        return env->NewStringUTF("");
    }

    // Reset sampler state (resets grammar automaton to root)
    llama_sampler_reset(cw->sampler);

    // Decode user tokens
    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    if (llama_decode(cw->ctx, batch) != 0) {
        LOGE("Decode failed for user input");
        return env->NewStringUTF("");
    }

    // Auto-regressive generation
    std::string result;
    int limit = maxTokens > 0 ? maxTokens : 1024;

    for (int i = 0; i < limit; i++) {
        llama_token id = llama_sampler_sample(cw->sampler, cw->ctx, -1);

        if (llama_vocab_is_eog(cw->vocab, id)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(cw->vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        llama_batch one = llama_batch_get_one(&id, 1);
        if (llama_decode(cw->ctx, one) != 0) {
            LOGE("Decode failed during generation at token %d", i);
            break;
        }
    }

    LOGI("Generated %zu chars", result.size());
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
