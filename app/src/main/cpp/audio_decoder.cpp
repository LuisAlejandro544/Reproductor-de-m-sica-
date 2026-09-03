#include "audio_decoder.h"
#include <android/log.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cstring>
#include <algorithm>

#define DECODER_LOG_TAG "RitmoAudioDecoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, DECODER_LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, DECODER_LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, DECODER_LOG_TAG, __VA_ARGS__)

AudioDecoder::AudioDecoder() = default;

AudioDecoder::~AudioDecoder() {
    cancel();
}

void AudioDecoder::cancel() {
    if (mDecoderThread.joinable()) {
        mDecoderThread.join();
    }
}

bool AudioDecoder::openAndDecodeInitial(
    const std::string& filePath,
    std::vector<int16_t>& pcmBuffer,
    std::atomic<size_t>& decodedSamples,
    std::atomic<bool>& cancelLoading,
    std::atomic<bool>& isDecodingFinished,
    std::mutex& bufferMutex,
    AudioFileInfo& outInfo,
    int32_t& outErrorCode,
    std::string& outErrorMsg) {

    cancel();

    int fd = open(filePath.c_str(), O_RDONLY);
    if (fd < 0) {
        outErrorCode = errno;
        outErrorMsg = std::string("No se pudo abrir descriptor de archivo: ") + strerror(errno);
        LOGE("Failed to open file: %s (errno: %d - %s)", filePath.c_str(), errno, strerror(errno));
        return false;
    }

    struct stat statBuf{};
    if (fstat(fd, &statBuf) < 0) {
        outErrorCode = errno;
        outErrorMsg = std::string("Error en fstat de archivo: ") + strerror(errno);
        close(fd);
        return false;
    }
    int64_t fileSize = statBuf.st_size;

    AMediaExtractor* extractor = AMediaExtractor_new();
    media_status_t status = AMediaExtractor_setDataSourceFd(extractor, fd, 0, fileSize);
    if (status != AMEDIA_OK) {
        outErrorCode = static_cast<int32_t>(status);
        outErrorMsg = "AMediaExtractor_setDataSourceFd fallo con codigo " + std::to_string(status);
        LOGE("AMediaExtractor_setDataSourceFd failed: %d", status);
        AMediaExtractor_delete(extractor);
        close(fd);
        return false;
    }

    int numTracks = AMediaExtractor_getTrackCount(extractor);
    int audioTrackIndex = -1;
    AMediaFormat* format = nullptr;
    const char* mime = nullptr;

    for (int i = 0; i < numTracks; ++i) {
        format = AMediaExtractor_getTrackFormat(extractor, i);
        if (AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            if (strncmp(mime, "audio/", 6) == 0) {
                audioTrackIndex = i;
                break;
            }
        }
        AMediaFormat_delete(format);
        format = nullptr;
    }

    if (audioTrackIndex < 0 || format == nullptr) {
        outErrorCode = -1001;
        outErrorMsg = "No se encontro pista de audio compatible en el archivo";
        LOGE("No audio track found in file");
        AMediaExtractor_delete(extractor);
        close(fd);
        return false;
    }

    AMediaExtractor_selectTrack(extractor, audioTrackIndex);

    int32_t sampleRate = 44100;
    int32_t channelCount = 2;
    int64_t durationUs = 0;
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate);
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channelCount);
    AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &durationUs);

    outInfo.sampleRate = sampleRate > 0 ? sampleRate : 44100;
    outInfo.channelCount = channelCount > 0 ? channelCount : 2;
    outInfo.durationMs = durationUs > 0 ? (durationUs / 1000) : 0;
    outInfo.totalFrames = (durationUs > 0) ? ((durationUs * outInfo.sampleRate) / 1000000LL) : 0;

    AMediaCodec* codec = AMediaCodec_createDecoderByType(mime);
    if (!codec) {
        outErrorCode = -1002;
        outErrorMsg = std::string("Fallo al instanciar decodificador para MIME: ") + (mime ? mime : "null");
        LOGE("Failed to create decoder for MIME: %s", mime);
        AMediaFormat_delete(format);
        AMediaExtractor_delete(extractor);
        close(fd);
        return false;
    }

    status = AMediaCodec_configure(codec, format, nullptr, nullptr, 0);
    AMediaFormat_delete(format);
    if (status != AMEDIA_OK) {
        outErrorCode = static_cast<int32_t>(status);
        outErrorMsg = "AMediaCodec_configure fallo con codigo " + std::to_string(status);
        LOGE("AMediaCodec_configure failed");
        AMediaCodec_delete(codec);
        AMediaExtractor_delete(extractor);
        close(fd);
        return false;
    }

    AMediaCodec_start(codec);

    // Asignar memoria contigua fija con margen generoso
    size_t estimatedSamples = (outInfo.totalFrames > 0)
        ? static_cast<size_t>(outInfo.totalFrames * outInfo.channelCount)
        : static_cast<size_t>(outInfo.sampleRate * outInfo.channelCount * 300);
    size_t safeCapacity = estimatedSamples + static_cast<size_t>(outInfo.sampleRate * outInfo.channelCount * 30);
    pcmBuffer.assign(safeCapacity, 0);

    // Decodificar sincronicamente primer bloque para inicio inmediato
    bool sawInputEOS = false;
    bool sawOutputEOS = false;
    int emptyDequeueCount = 0;
    const int maxEmptyDequeues = 200;
    size_t initialThreshold = static_cast<size_t>(outInfo.sampleRate * outInfo.channelCount / 2);

    while (!sawOutputEOS && !cancelLoading.load() && decodedSamples.load() < initialThreshold) {
        bool progress = false;

        if (!sawInputEOS) {
            ssize_t inputBufIndex = AMediaCodec_dequeueInputBuffer(codec, 2000);
            if (inputBufIndex >= 0) {
                progress = true;
                size_t bufSize = 0;
                uint8_t* inputBuf = AMediaCodec_getInputBuffer(codec, inputBufIndex, &bufSize);
                ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, inputBuf, bufSize);
                if (sampleSize < 0) {
                    sawInputEOS = true;
                    AMediaCodec_queueInputBuffer(codec, inputBufIndex, 0, 0, 0,
                                                 AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                } else {
                    int64_t presentationTimeUs = AMediaExtractor_getSampleTime(extractor);
                    AMediaCodec_queueInputBuffer(codec, inputBufIndex, 0, sampleSize,
                                                 presentationTimeUs, 0);
                    AMediaExtractor_advance(extractor);
                }
            }
        }

        AMediaCodecBufferInfo info;
        ssize_t outputBufIndex = AMediaCodec_dequeueOutputBuffer(codec, &info, 2000);
        if (outputBufIndex >= 0) {
            progress = true;
            emptyDequeueCount = 0;
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                sawOutputEOS = true;
            }
            if (info.size > 0) {
                size_t bufSize = 0;
                uint8_t* outBuf = AMediaCodec_getOutputBuffer(codec, outputBufIndex, &bufSize);
                if (outBuf) {
                    const int16_t* samples = reinterpret_cast<const int16_t*>(outBuf + info.offset);
                    size_t sampleCount = info.size / sizeof(int16_t);
                    size_t currentDecoded = decodedSamples.load(std::memory_order_relaxed);
                    if (currentDecoded + sampleCount <= pcmBuffer.size()) {
                        std::memcpy(&pcmBuffer[currentDecoded], samples, sampleCount * sizeof(int16_t));
                        decodedSamples.fetch_add(sampleCount, std::memory_order_release);
                    }
                }
            }
            AMediaCodec_releaseOutputBuffer(codec, outputBufIndex, false);
        } else if (outputBufIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            progress = true;
            AMediaFormat* newFormat = AMediaCodec_getOutputFormat(codec);
            int32_t newRate = 0;
            int32_t newChannels = 0;
            if (AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &newRate) && newRate > 0) {
                outInfo.sampleRate = newRate;
            }
            if (AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &newChannels) && newChannels > 0) {
                outInfo.channelCount = newChannels;
            }
            AMediaFormat_delete(newFormat);
        } else if (sawInputEOS && outputBufIndex < 0) {
            emptyDequeueCount++;
            if (emptyDequeueCount > maxEmptyDequeues) {
                sawOutputEOS = true;
            }
        }

        if (!progress && !sawOutputEOS) {
            usleep(20);
        }
    }

    if (decodedSamples.load() == 0 && sawOutputEOS) {
        outErrorCode = -1003;
        outErrorMsg = "No se pudieron decodificar muestras de audio del archivo";
        LOGE("No audio samples could be decoded from file");
        AMediaCodec_stop(codec);
        AMediaCodec_delete(codec);
        AMediaExtractor_delete(extractor);
        close(fd);
        return false;
    }

    if (sawOutputEOS) {
        AMediaCodec_stop(codec);
        AMediaCodec_delete(codec);
        AMediaExtractor_delete(extractor);
        close(fd);
        outInfo.totalFrames = outInfo.channelCount > 0
            ? (decodedSamples.load(std::memory_order_acquire) / outInfo.channelCount)
            : 0;
        if (outInfo.sampleRate > 0) {
            outInfo.durationMs = (outInfo.totalFrames * 1000) / outInfo.sampleRate;
        }
        isDecodingFinished.store(true);
    } else {
        // Lanzar hilo en segundo plano para decodificacion progresiva continua
        mDecoderThread = std::thread(
            &AudioDecoder::decodeRemaining, this,
            extractor, codec, fd, sawInputEOS,
            &pcmBuffer, &decodedSamples, &cancelLoading, &isDecodingFinished, &bufferMutex,
            outInfo.sampleRate, outInfo.channelCount
        );
    }

    return true;
}

void AudioDecoder::decodeRemaining(
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
    int32_t channelCount) {

    bool sawInputEOS = initialSawInputEOS;
    bool sawOutputEOS = false;
    int emptyDequeueCount = 0;
    const int maxEmptyDequeues = 300;

    while (!sawOutputEOS && !cancelLoading->load()) {
        bool progress = false;

        if (!sawInputEOS) {
            ssize_t inputBufIndex = AMediaCodec_dequeueInputBuffer(codec, 2000);
            if (inputBufIndex >= 0) {
                progress = true;
                size_t bufSize = 0;
                uint8_t* inputBuf = AMediaCodec_getInputBuffer(codec, inputBufIndex, &bufSize);
                ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, inputBuf, bufSize);
                if (sampleSize < 0) {
                    sawInputEOS = true;
                    AMediaCodec_queueInputBuffer(codec, inputBufIndex, 0, 0, 0,
                                                 AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                } else {
                    int64_t presentationTimeUs = AMediaExtractor_getSampleTime(extractor);
                    AMediaCodec_queueInputBuffer(codec, inputBufIndex, 0, sampleSize,
                                                 presentationTimeUs, 0);
                    AMediaExtractor_advance(extractor);
                }
            }
        }

        AMediaCodecBufferInfo info;
        ssize_t outputBufIndex = AMediaCodec_dequeueOutputBuffer(codec, &info, 2000);
        if (outputBufIndex >= 0) {
            progress = true;
            emptyDequeueCount = 0;
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                sawOutputEOS = true;
            }
            if (info.size > 0) {
                size_t bufSize = 0;
                uint8_t* outBuf = AMediaCodec_getOutputBuffer(codec, outputBufIndex, &bufSize);
                if (outBuf) {
                    const int16_t* samples = reinterpret_cast<const int16_t*>(outBuf + info.offset);
                    size_t sampleCount = info.size / sizeof(int16_t);
                    size_t currentDecoded = decodedSamples->load(std::memory_order_relaxed);
                    if (currentDecoded + sampleCount <= pcmBuffer->size()) {
                        std::memcpy(&(*pcmBuffer)[currentDecoded], samples, sampleCount * sizeof(int16_t));
                        decodedSamples->fetch_add(sampleCount, std::memory_order_release);
                    } else {
                        std::lock_guard<std::mutex> lock(*bufferMutex);
                        size_t needed = currentDecoded + sampleCount + static_cast<size_t>(sampleRate * channelCount * 10);
                        pcmBuffer->resize(needed, 0);
                        std::memcpy(&(*pcmBuffer)[currentDecoded], samples, sampleCount * sizeof(int16_t));
                        decodedSamples->fetch_add(sampleCount, std::memory_order_release);
                    }
                }
            }
            AMediaCodec_releaseOutputBuffer(codec, outputBufIndex, false);
        } else if (sawInputEOS && outputBufIndex < 0) {
            emptyDequeueCount++;
            if (emptyDequeueCount > maxEmptyDequeues) {
                sawOutputEOS = true;
            }
        }

        if (!progress && !sawOutputEOS) {
            usleep(20);
        }
    }

    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    AMediaExtractor_delete(extractor);
    if (fd >= 0) {
        close(fd);
    }

    if (!cancelLoading->load()) {
        isDecodingFinished->store(true);
        LOGI("Progressive decode finished successfully");
    }
}
