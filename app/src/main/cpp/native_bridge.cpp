#include <jni.h>
#include "native_audio.h"

// Declaración forward de función exportada por Rust (C-ABI)
extern "C" {
    int32_t ritmo_rust_core_version();
}

static std::unique_ptr<OboeAudioPlayer> gAudioPlayer;
static std::mutex gPlayerMutex;
static TenBandEqualizer gMedia3Equalizer;

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

JNIEXPORT void JNICALL
Java_com_example_playback_OboeAudioBridge_nativeSetEqualizerEnabled(JNIEnv* env, jobject /* this */, jboolean enabled) {
    getPlayer()->setEqualizerEnabled(enabled == JNI_TRUE);
    gMedia3Equalizer.setEnabled(enabled == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_example_playback_OboeAudioBridge_nativeIsEqualizerEnabled(JNIEnv* env, jobject /* this */) {
    return getPlayer()->isEqualizerEnabled() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_playback_OboeAudioBridge_nativeSetEqualizerBandGain(JNIEnv* env, jobject /* this */, jint bandIndex, jfloat gainDb) {
    getPlayer()->setEqualizerBandGain(static_cast<int>(bandIndex), static_cast<float>(gainDb));
    gMedia3Equalizer.setBandGain(static_cast<int>(bandIndex), static_cast<float>(gainDb));
}

JNIEXPORT jfloat JNICALL
Java_com_example_playback_OboeAudioBridge_nativeGetEqualizerBandGain(JNIEnv* env, jobject /* this */, jint bandIndex) {
    return static_cast<jfloat>(getPlayer()->getEqualizerBandGain(static_cast<int>(bandIndex)));
}

JNIEXPORT void JNICALL
Java_com_example_playback_OboeAudioBridge_nativeResetEqualizer(JNIEnv* env, jobject /* this */) {
    getPlayer()->resetEqualizer();
    gMedia3Equalizer.resetGains();
}

JNIEXPORT void JNICALL
Java_com_example_playback_OboeAudioBridge_nativeMedia3ProcessDirect(
    JNIEnv* env, jobject /* this */,
    jobject byteBuffer, jint offsetBytes, jint lengthBytes,
    jint sampleRate, jint channelCount) {

    if (!byteBuffer || lengthBytes <= 0 || channelCount <= 0) return;

    uint8_t* basePtr = static_cast<uint8_t*>(env->GetDirectBufferAddress(byteBuffer));
    if (!basePtr) return;

    int16_t* pcmData = reinterpret_cast<int16_t*>(basePtr + offsetBytes);
    int32_t numSamples = lengthBytes / sizeof(int16_t);
    int32_t numFrames = numSamples / channelCount;

    if (numFrames <= 0) return;

    gMedia3Equalizer.setSampleRate(static_cast<float>(sampleRate));
    gMedia3Equalizer.process(pcmData, numFrames, channelCount);
}

JNIEXPORT void JNICALL
Java_com_example_playback_OboeAudioBridge_nativeMedia3ProcessArray(
    JNIEnv* env, jobject /* this */,
    jshortArray pcmArray, jint offsetSamples, jint numSamples,
    jint sampleRate, jint channelCount) {

    if (!pcmArray || numSamples <= 0 || channelCount <= 0) return;

    jshort* elements = env->GetShortArrayElements(pcmArray, nullptr);
    if (!elements) return;

    int32_t numFrames = numSamples / channelCount;
    gMedia3Equalizer.setSampleRate(static_cast<float>(sampleRate));
    gMedia3Equalizer.process(elements + offsetSamples, numFrames, channelCount);

    env->ReleaseShortArrayElements(pcmArray, elements, 0);
}

JNIEXPORT jint JNICALL
Java_com_example_playback_OboeAudioBridge_nativeGetRustVersion(JNIEnv* env, jobject /* this */) {
    // Retorna versión del módulo nativo Rust
    return 1;
}

} // extern "C"
