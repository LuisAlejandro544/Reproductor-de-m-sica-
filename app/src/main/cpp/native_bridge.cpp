#include <jni.h>
#include "native_audio.h"

// Declaración forward de función exportada por Rust (C-ABI)
extern "C" {
    int32_t ritmo_rust_core_version();
}

static std::unique_ptr<OboeAudioPlayer> gAudioPlayer;
static std::mutex gPlayerMutex;

static OboeAudioPlayer* getPlayer() {
    std::lock_guard<std::mutex> lock(gPlayerMutex);
    if (!gAudioPlayer) {
        gAudioPlayer = std::make_unique<OboeAudioPlayer>();
    }
    return gAudioPlayer.get();
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_playback_OboeAudioBridge_nativeInit(JNIEnv* env, jobject /* this */) {
    LOGI("JNI: nativeInit invocado");
    return getPlayer()->init() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_playback_OboeAudioBridge_nativeRelease(JNIEnv* env, jobject /* this */) {
    LOGI("JNI: nativeRelease invocado");
    std::lock_guard<std::mutex> lock(gPlayerMutex);
    if (gAudioPlayer) {
        gAudioPlayer->release();
        gAudioPlayer.reset();
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_playback_OboeAudioBridge_nativeLoadFile(JNIEnv* env, jobject /* this */, jstring jFilePath) {
    if (!jFilePath) return JNI_FALSE;
    const char* nativeFilePath = env->GetStringUTFChars(jFilePath, nullptr);
    std::string path(nativeFilePath);
    env->ReleaseStringUTFChars(jFilePath, nativeFilePath);

    LOGI("JNI: nativeLoadFile %s", path.c_str());
    return getPlayer()->loadFile(path) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_playback_OboeAudioBridge_nativePlay(JNIEnv* env, jobject /* this */) {
    LOGI("JNI: nativePlay invocado");
    return getPlayer()->play() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_playback_OboeAudioBridge_nativePause(JNIEnv* env, jobject /* this */) {
    LOGI("JNI: nativePause invocado");
    return getPlayer()->pause() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_playback_OboeAudioBridge_nativeStop(JNIEnv* env, jobject /* this */) {
    LOGI("JNI: nativeStop invocado");
    return getPlayer()->stop() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_playback_OboeAudioBridge_nativeSeekTo(JNIEnv* env, jobject /* this */, jlong positionMs) {
    return getPlayer()->seekTo(positionMs) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_example_playback_OboeAudioBridge_nativeGetPosition(JNIEnv* env, jobject /* this */) {
    return static_cast<jlong>(getPlayer()->getPositionMs());
}

JNIEXPORT jlong JNICALL
Java_com_example_playback_OboeAudioBridge_nativeGetDuration(JNIEnv* env, jobject /* this */) {
    return static_cast<jlong>(getPlayer()->getDurationMs());
}

JNIEXPORT jboolean JNICALL
Java_com_example_playback_OboeAudioBridge_nativeIsPlaying(JNIEnv* env, jobject /* this */) {
    return getPlayer()->isPlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_playback_OboeAudioBridge_nativeGetRustVersion(JNIEnv* env, jobject /* this */) {
    // Retorna versión del módulo nativo Rust
    return 1;
}

} // extern "C"
