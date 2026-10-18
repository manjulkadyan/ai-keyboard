/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// JNI bridge for com.manjul.aikeyboard.lib.whisper.WhisperJni. Input guards (0 handles, empty PCM,
// double free, concurrent use) are in Kotlin (WhisperBridge); this file only marshals and calls whisper.cpp.
// Nothing here logs: whisper.cpp's log output is discarded so no transcript text reaches any log.

#include <jni.h>

#include <cstdint>
#include <new>
#include <string>

#include "whisper.h"

namespace {

// What a Kotlin handle points to.
struct Session {
    whisper_context *ctx;
    int threads;
};

void discard_log(enum ggml_log_level, const char *, void *) {}

Session *to_session(jlong handle) {
    return reinterpret_cast<Session *>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_manjul_aikeyboard_lib_whisper_WhisperJni_nativeInit(
        JNIEnv *env, jclass, jstring model_path, jint threads) {
    whisper_log_set(discard_log, nullptr);

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;  // OutOfMemoryError pending

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (ctx == nullptr) return 0;

    auto *session = new (std::nothrow) Session{ctx, threads};
    if (session == nullptr) {
        whisper_free(ctx);
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(session));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_manjul_aikeyboard_lib_whisper_WhisperJni_nativeTranscribe(
        JNIEnv *env, jclass, jlong handle, jfloatArray pcm, jstring lang, jboolean translate) {
    Session *session = to_session(handle);

    const char *lang_chars = env->GetStringUTFChars(lang, nullptr);
    if (lang_chars == nullptr) return nullptr;
    const std::string language(lang_chars);
    env->ReleaseStringUTFChars(lang, lang_chars);
    if (language != "auto" && whisper_lang_id(language.c_str()) < 0) return nullptr;

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = session->threads;
    params.language = language.c_str();
    params.translate = translate == JNI_TRUE;
    params.no_timestamps = true;
    params.single_segment = false;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;

    // Not GetPrimitiveArrayCritical: whisper_full runs for seconds and must not block the GC.
    const jsize n_samples = env->GetArrayLength(pcm);
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);
    if (samples == nullptr) return nullptr;
    const int rc = whisper_full(session->ctx, params, samples, n_samples);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (rc != 0) return nullptr;

    std::string text;
    const int n_segments = whisper_full_n_segments(session->ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *segment = whisper_full_get_segment_text(session->ctx, i);
        if (segment != nullptr) text += segment;
    }
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_manjul_aikeyboard_lib_whisper_WhisperJni_nativeFree(JNIEnv *, jclass, jlong handle) {
    Session *session = to_session(handle);
    if (session == nullptr) return;
    whisper_free(session->ctx);
    delete session;
}
