#pragma once

#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include <thread>
#include <cstdint>

struct AudioFileInfo {
    int32_t sampleRate = 44100;
    int32_t channelCount = 2;
    int64_t durationMs = 0;
    int64_t totalFrames = 0;
};

/**
 * Decodificador nativo modular para archivos de audio mediante Android NDK MediaExtractor y MediaCodec.
 * Realiza decodificación híbrida: primer búfer ultrarrápido síncrono para latencia cero,
 * y decodificación progresiva asíncrona en hilo dedicado para el resto de la pista.
 */
class AudioDecoder {
public:
    AudioDecoder();
    ~AudioDecoder();

    bool openAndDecodeInitial(
        const std::string& filePath,
        std::vector<int16_t>& pcmBuffer,
        std::atomic<size_t>& decodedSamples,
        std::atomic<bool>& cancelLoading,
        std::atomic<bool>& isDecodingFinished,
        std::mutex& bufferMutex,
        AudioFileInfo& outInfo,
        int32_t& outErrorCode,
        std::string& outErrorMsg);

    void cancel();

private:
    void decodeRemaining(
        AMediaExtractor* extractor,
        AMediaCodec* codec,
        int fd,
        bool initialSawInputEOS,
        std::vector<int16_t>* pcmBuffer,
        std::atomic<size_t>* decodedSamples,
        std::atomic<bool>* cancelLoading,
        std::atomic<bool>* isDecodingFinished,
        std::mutex* bufferMutex,
        int32_t sampleRate,
        int32_t channelCount);

    std::thread mDecoderThread;
};
