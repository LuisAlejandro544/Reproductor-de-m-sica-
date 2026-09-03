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

JNIEXPORT jboolean JNICALL
Java_com_example_playback_OboeAudioBridge_nativeIsPlaybackEnded(JNIEnv* env, jobject /* this */) {
    return getPlayer()->isPlaybackEnded() ? JNI_TRUE : JNI_FALSE;
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
    return 200;
}

JNIEXPORT jint JNICALL
Java_com_example_playback_OboeAudioBridge_nativeGetLastErrorCode(JNIEnv* env, jobject /* this */) {
    return static_cast<jint>(getPlayer()->getLastErrorCode());
}

JNIEXPORT jstring JNICALL
Java_com_example_playback_OboeAudioBridge_nativeGetLastErrorString(JNIEnv* env, jobject /* this */) {
    std::string err = getPlayer()->getLastErrorMsg();
    return env->NewStringUTF(err.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_playback_OboeAudioBridge_nativeGetAudioDeviceInfo(JNIEnv* env, jobject /* this */) {
    std::string info = getPlayer()->getAudioDeviceInfo();
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_playback_OboeAudioBridge_nativeGetStreamStatsJson(JNIEnv* env, jobject /* this */) {
    std::string stats = getPlayer()->getStreamStatsJson();
    return env->NewStringUTF(stats.c_str());
}

// -----------------------------------------------------------------------------
// JNI para com.example.util.RustAudioEngine (Puente Nativo Audiófilo)
// -----------------------------------------------------------------------------

JNIEXPORT jint JNICALL
Java_com_example_util_RustAudioEngine_nativePing(JNIEnv* /* env */, jclass /* clazz */) {
    return 42;
}

JNIEXPORT jstring JNICALL
Java_com_example_util_RustAudioEngine_nativeGetVersion(JNIEnv* env, jclass /* clazz */) {
    return env->NewStringUTF("Rust Audiophile Tag Engine v2.0 (Dual Native C++/Rust)");
}

// Parser nativo audiófilo de metadatos (ID3v2, ID3v1, FLAC, Vorbis)
JNIEXPORT jstring JNICALL
Java_com_example_util_RustAudioEngine_nativeExtractMetadata(JNIEnv* env, jclass /* clazz */, jstring jFilePath) {
    if (!jFilePath) return env->NewStringUTF("{}");
    const char* pathChars = env->GetStringUTFChars(jFilePath, nullptr);
    std::string filePath(pathChars);
    env->ReleaseStringUTFChars(jFilePath, pathChars);

    FILE* f = fopen(filePath.c_str(), "rb");
    if (!f) {
        return env->NewStringUTF("{\"format_name\":\"Error al abrir archivo\",\"has_artwork\":false}");
    }

    fseek(f, 0, SEEK_END);
    long fileLen = ftell(f);
    fseek(f, 0, SEEK_SET);

    std::string title = "";
    std::string artist = "";
    std::string album = "";
    std::string formatName = "Audio Nativo";
    bool hasArtwork = false;
    int durationMs = 0;

    unsigned char header[16];
    if (fread(header, 1, 16, f) == 16) {
        if (memcmp(header, "fLaC", 4) == 0) {
            formatName = "FLAC (Free Lossless Audio Codec)";
            // Leer STREAMINFO y Vorbis Comments
            fseek(f, 4, SEEK_SET);
            bool isLast = false;
            while (!isLast && !feof(f)) {
                unsigned char blockHdr[4];
                if (fread(blockHdr, 1, 4, f) != 4) break;
                isLast = (blockHdr[0] & 0x80) != 0;
                int blockType = blockHdr[0] & 0x7F;
                int blockLen = ((int)blockHdr[1] << 16) | ((int)blockHdr[2] << 8) | (int)blockHdr[3];
                if (blockLen <= 0 || blockLen > 10 * 1024 * 1024) break;

                if (blockType == 4) { // VORBIS_COMMENT
                    std::vector<char> vc(blockLen);
                    if (fread(vc.data(), 1, blockLen, f) == (size_t)blockLen && blockLen >= 8) {
                        uint32_t vendorLen = *(uint32_t*)&vc[0];
                        size_t pos = 4 + vendorLen;
                        if (pos + 4 <= (size_t)blockLen) {
                            uint32_t count = *(uint32_t*)&vc[pos];
                            pos += 4;
                            for (uint32_t c = 0; c < count && pos + 4 <= (size_t)blockLen; ++c) {
                                uint32_t cLen = *(uint32_t*)&vc[pos];
                                pos += 4;
                                if (pos + cLen > (size_t)blockLen) break;
                                std::string item(&vc[pos], cLen);
                                pos += cLen;
                                size_t eq = item.find('=');
                                if (eq != std::string::npos) {
                                    std::string key = item.substr(0, eq);
                                    std::string val = item.substr(eq + 1);
                                    for (auto &ch: key) ch = toupper(ch);
                                    if (key == "TITLE" && title.empty()) title = val;
                                    else if (key == "ARTIST" && artist.empty()) artist = val;
                                    else if (key == "ALBUM" && album.empty()) album = val;
                                }
                            }
                        }
                    }
                } else if (blockType == 6) { // PICTURE
                    hasArtwork = true;
                    fseek(f, blockLen, SEEK_CUR);
                } else {
                    fseek(f, blockLen, SEEK_CUR);
                }
            }
        } else if (memcmp(header, "ID3", 3) == 0) {
            formatName = "MPEG Audio (ID3v2)";
            int tagSize = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14) |
                          ((header[8] & 0x7F) << 7)  | (header[9] & 0x7F);
            if (tagSize > 0 && tagSize < 20 * 1024 * 1024) {
                std::vector<char> tagBuf(tagSize);
                if (fread(tagBuf.data(), 1, tagSize, f) == (size_t)tagSize) {
                    size_t p = 0;
                    while (p + 10 < (size_t)tagSize) {
                        char frameId[5] = {0};
                        memcpy(frameId, &tagBuf[p], 4);
                        if (frameId[0] == 0) break;
                        int frameSize = ((unsigned char)tagBuf[p+4] << 24) |
                                        ((unsigned char)tagBuf[p+5] << 16) |
                                        ((unsigned char)tagBuf[p+6] << 8)  |
                                        (unsigned char)tagBuf[p+7];
                        p += 10;
                        if (frameSize <= 0 || p + frameSize > (size_t)tagSize) break;
                        if (frameSize > 1) {
                            std::string txt(&tagBuf[p + 1], frameSize - 1);
                            if (strcmp(frameId, "TIT2") == 0 && title.empty()) title = txt;
                            else if (strcmp(frameId, "TPE1") == 0 && artist.empty()) artist = txt;
                            else if (strcmp(frameId, "TALB") == 0 && album.empty()) album = txt;
                            else if (strcmp(frameId, "APIC") == 0) hasArtwork = true;
                        }
                        p += frameSize;
                    }
                }
            }
        }
    }

    // Si aún falta título o artista, probar ID3v1 al final
    if ((title.empty() || artist.empty()) && fileLen >= 128) {
        fseek(f, fileLen - 128, SEEK_SET);
        char id3v1[128];
        if (fread(id3v1, 1, 128, f) == 128 && memcmp(id3v1, "TAG", 3) == 0) {
            if (title.empty()) {
                title = std::string(&id3v1[3], 30);
                title.erase(title.find_last_not_of(" \t\r\n\0") + 1);
            }
            if (artist.empty()) {
                artist = std::string(&id3v1[33], 30);
                artist.erase(artist.find_last_not_of(" \t\r\n\0") + 1);
            }
            if (album.empty()) {
                album = std::string(&id3v1[63], 30);
                album.erase(album.find_last_not_of(" \t\r\n\0") + 1);
            }
        }
    }

    fclose(f);

    // Escapar JSON
    auto escapeJson = [](const std::string& s) -> std::string {
        std::string out;
        for (char c : s) {
            if (c == '"') out += "\\\"";
            else if (c == '\\') out += "\\\\";
            else if (c == '\n') out += "\\n";
            else if (c == '\r') out += "\\r";
            else if (c == '\t') out += "\\t";
            else if ((unsigned char)c >= 32) out += c;
        }
        return out;
    };

    std::string json = "{"
        "\"title\":\"" + escapeJson(title) + "\","
        "\"artist\":\"" + escapeJson(artist) + "\","
        "\"album\":\"" + escapeJson(album) + "\","
        "\"format_name\":\"" + escapeJson(formatName) + "\","
        "\"has_artwork\":" + (hasArtwork ? "true" : "false") + ","
        "\"duration_ms\":" + std::to_string(durationMs) + ","
        "\"engine_badge\":\"Rust/Native Audiophile Core 2.0\""
        "}";

    return env->NewStringUTF(json.c_str());
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_util_RustAudioEngine_nativeExtractArtwork(JNIEnv* env, jclass /* clazz */, jstring jFilePath) {
    if (!jFilePath) return nullptr;
    const char* pathChars = env->GetStringUTFChars(jFilePath, nullptr);
    std::string filePath(pathChars);
    env->ReleaseStringUTFChars(jFilePath, pathChars);

    FILE* f = fopen(filePath.c_str(), "rb");
    if (!f) return nullptr;

    std::vector<unsigned char> artBytes;
    unsigned char header[10];
    if (fread(header, 1, 10, f) == 10 && memcmp(header, "ID3", 3) == 0) {
        int tagSize = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14) |
                      ((header[8] & 0x7F) << 7)  | (header[9] & 0x7F);
        if (tagSize > 0 && tagSize < 20 * 1024 * 1024) {
            std::vector<char> tagBuf(tagSize);
            if (fread(tagBuf.data(), 1, tagSize, f) == (size_t)tagSize) {
                size_t p = 0;
                while (p + 10 < (size_t)tagSize) {
                    char frameId[5] = {0};
                    memcpy(frameId, &tagBuf[p], 4);
                    int frameSize = ((unsigned char)tagBuf[p+4] << 24) |
                                    ((unsigned char)tagBuf[p+5] << 16) |
                                    ((unsigned char)tagBuf[p+6] << 8)  |
                                    (unsigned char)tagBuf[p+7];
                    p += 10;
                    if (frameSize <= 0 || p + frameSize > (size_t)tagSize) break;
                    if (strcmp(frameId, "APIC") == 0 && frameSize > 12) {
                        // Saltar encoding, mime, picType, desc
                        size_t dataPos = p + 1;
                        while (dataPos < p + frameSize && tagBuf[dataPos] != 0) dataPos++;
                        dataPos += 2; // saltar null y picture type
                        while (dataPos < p + frameSize && tagBuf[dataPos] != 0) dataPos++;
                        dataPos++; // saltar null description
                        if (dataPos < p + frameSize) {
                            artBytes.assign((unsigned char*)&tagBuf[dataPos], (unsigned char*)&tagBuf[p + frameSize]);
                            break;
                        }
                    }
                    p += frameSize;
                }
            }
        }
    }
    fclose(f);

    if (artBytes.empty()) return nullptr;

    jbyteArray result = env->NewByteArray(artBytes.size());
    if (result) {
        env->SetByteArrayRegion(result, 0, artBytes.size(), (const jbyte*)artBytes.data());
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_util_RustAudioEngine_nativeUpdateMetadata(
    JNIEnv* env, jclass /* clazz */,
    jstring jFilePath, jstring jNewTitle, jstring jNewArtist) {

    if (!jFilePath || !jNewTitle || !jNewArtist) return -1;
    const char* pChars = env->GetStringUTFChars(jFilePath, nullptr);
    const char* tChars = env->GetStringUTFChars(jNewTitle, nullptr);
    const char* aChars = env->GetStringUTFChars(jNewArtist, nullptr);

    std::string path(pChars);
    std::string title(tChars);
    std::string artist(aChars);

    env->ReleaseStringUTFChars(jFilePath, pChars);
    env->ReleaseStringUTFChars(jNewTitle, tChars);
    env->ReleaseStringUTFChars(jNewArtist, aChars);

    FILE* f = fopen(path.c_str(), "r+b");
    if (!f) return -2;

    fseek(f, 0, SEEK_END);
    long fileLen = ftell(f);
    fseek(f, 0, SEEK_SET);

    unsigned char header[10];
    if (fread(header, 1, 10, f) == 10 && memcmp(header, "ID3", 3) == 0) {
        // Actualizar ID3v2 si hay espacio o actualizar ID3v1 al final
    }

    if (fileLen >= 128) {
        fseek(f, fileLen - 128, SEEK_SET);
        char buf[128];
        if (fread(buf, 1, 128, f) == 128) {
            if (memcmp(buf, "TAG", 3) != 0) {
                memcpy(buf, "TAG", 3);
            }
            memset(&buf[3], 0, 30);
            memset(&buf[33], 0, 30);
            size_t tLen = std::min(title.length(), (size_t)30);
            size_t aLen = std::min(artist.length(), (size_t)30);
            memcpy(&buf[3], title.c_str(), tLen);
            memcpy(&buf[33], artist.c_str(), aLen);

            fseek(f, fileLen - 128, SEEK_SET);
            fwrite(buf, 1, 128, f);
            fflush(f);
        }
    }

    fclose(f);
    return 0;
}

} // extern "C"

