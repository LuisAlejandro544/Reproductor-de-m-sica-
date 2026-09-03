#include "native_audio.h"
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <cstring>
#include <algorithm>

OboeAudioPlayer::OboeAudioPlayer() {
    LOGI("OboeAudioPlayer constructor");
}

OboeAudioPlayer::~OboeAudioPlayer() {
    release();
}

bool OboeAudioPlayer::init() {
    std::lock_guard<std::mutex> lock(mBufferMutex);
    if (mIsInitialized.load()) {
        return true;
    }
    mIsInitialized.store(true);
    LOGI("OboeAudioPlayer initialized");
    return true;
}

void OboeAudioPlayer::release() {
    stop();
    mCancelLoading.store(true);
    if (mDecoderThread.joinable()) {
        mDecoderThread.join();
    }
    closeStream();
    {
        std::lock_guard<std::mutex> lock(mBufferMutex);
        mPcmBuffer.clear();
        mPcmBuffer.shrink_to_fit();
        mTotalFrames = 0;
        mCurrentFrameIndex.store(0);
        mDecodedSamples.store(0);
        mDurationMs = 0;
    }
    mIsInitialized.store(false);
    LOGI("OboeAudioPlayer released");
}

bool OboeAudioPlayer::openStream() {
    if (mAudioStream) {
        oboe::StreamState state = mAudioStream->getState();
        bool compatible = (mAudioStream->getSampleRate() == (mSampleRate > 0 ? mSampleRate : 44100)) &&
                          (mAudioStream->getChannelCount() == (mChannelCount > 0 ? mChannelCount : 2));
        if (compatible && (state == oboe::StreamState::Open ||
                           state == oboe::StreamState::Started ||
                           state == oboe::StreamState::Paused)) {
            return true;
        }
        closeStream();
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    // Modo None para utilizar la ruta estándar de AudioFlinger y ecualización de hardware del sistema Android,
    // garantizando volumen original, amplificación plena y acústica idéntica a ExoPlayer/Media3
    builder.setPerformanceMode(oboe::PerformanceMode::None);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setUsage(oboe::Usage::Media);
    builder.setContentType(oboe::ContentType::Music);
    builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);
    builder.setChannelCount(mChannelCount > 0 ? mChannelCount : 2);
    builder.setSampleRate(mSampleRate > 0 ? mSampleRate : 44100);
    builder.setDataCallback(this);
    builder.setErrorCallback(this);

    oboe::Result result = builder.openStream(mAudioStream);
    if (result != oboe::Result::OK) {
        LOGW("Failed to open stream in None mode (%s), attempting LowLatency fallback...",
             oboe::convertToText(result));
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
        result = builder.openStream(mAudioStream);
    }
    if (result != oboe::Result::OK) {
        LOGW("Failed to open stream with AAudio (%s), attempting OpenSL ES fallback...",
             oboe::convertToText(result));
        builder.setAudioApi(oboe::AudioApi::OpenSLES);
        result = builder.openStream(mAudioStream);
    }
    if (result != oboe::Result::OK) {
        {
            std::lock_guard<std::mutex> lock(mErrorMutex);
            mLastErrorCode = static_cast<int32_t>(result);
            mLastErrorMsg = std::string("Fallo al abrir Oboe stream: ") + oboe::convertToText(result);
        }
        LOGE("Failed to open Oboe audio stream: %s", oboe::convertToText(result));
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(mErrorMutex);
        mLastErrorCode = 0;
        mLastErrorMsg = "Stream abierto con éxito (" + std::string(mAudioStream->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSLES") + ")";
    }

    LOGI("Oboe stream opened successfully: rate=%d, channels=%d, bufferSize=%d, api=%s",
         mAudioStream->getSampleRate(),
         mAudioStream->getChannelCount(),
         mAudioStream->getBufferSizeInFrames(),
         mAudioStream->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSLES");
    mEqualizer.setSampleRate(static_cast<float>(mAudioStream->getSampleRate()));
    return true;
}

void OboeAudioPlayer::closeStream() {
    if (mAudioStream) {
        mAudioStream->stop();
        mAudioStream->close();
        mAudioStream.reset();
    }
}

void OboeAudioPlayer::setVolume(float volume) {
    mVolume.store(std::clamp(volume, 0.0f, 2.0f));
}

float OboeAudioPlayer::getVolume() const {
    return mVolume.load();
}

bool OboeAudioPlayer::loadFile(const std::string& filePath) {
    // Cancelar cualquier decodificación previa en curso
    mCancelLoading.store(true);
    if (mDecoderThread.joinable()) {
        mDecoderThread.join();
    }

    std::lock_guard<std::mutex> lock(mBufferMutex);
    mCancelLoading.store(false);
    mIsPlaying.store(false);
    mIsPlaybackEnded.store(false);
    mIsDecodingFinished.store(false);
    mCurrentFrameIndex.store(0);
    mDecodedSamples.store(0);

    if (mAudioStream) {
        oboe::StreamState state = mAudioStream->getState();
        if (state == oboe::StreamState::Started) {
            mAudioStream->requestPause();
        }
    }

    mCurrentFilePath = filePath;

    int fd = open(filePath.c_str(), O_RDONLY);
    if (fd < 0) {
        {
            std::lock_guard<std::mutex> lock(mErrorMutex);
            mLastErrorCode = errno;
            mLastErrorMsg = std::string("No se pudo abrir descriptor de archivo: ") + strerror(errno);
        }
        LOGE("Failed to open file: %s (errno: %d - %s)", filePath.c_str(), errno, strerror(errno));
        return false;
    }

    struct stat statBuf{};
    if (fstat(fd, &statBuf) < 0) {
        close(fd);
        return false;
    }
    int64_t fileSize = statBuf.st_size;

    AMediaExtractor *extractor = AMediaExtractor_new();
    media_status_t status = AMediaExtractor_setDataSourceFd(extractor, fd, 0, fileSize);
    if (status != AMEDIA_OK) {
        LOGE("AMediaExtractor_setDataSourceFd failed: %d", status);
        AMediaExtractor_delete(extractor);
        close(fd);
        return false;
    }

    int numTracks = AMediaExtractor_getTrackCount(extractor);
    int audioTrackIndex = -1;
    AMediaFormat *format = nullptr;
    const char *mime = nullptr;

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

    mSampleRate = sampleRate > 0 ? sampleRate : 44100;
    mChannelCount = channelCount > 0 ? channelCount : 2;
    mDurationMs = durationUs > 0 ? (durationUs / 1000) : 0;
    mTotalFrames = (durationUs > 0) ? ((durationUs * mSampleRate) / 1000000LL) : 0;

    AMediaCodec *codec = AMediaCodec_createDecoderByType(mime);
    if (!codec) {
        LOGE("Failed to create decoder for MIME: %s", mime);
        AMediaFormat_delete(format);
        AMediaExtractor_delete(extractor);
        close(fd);
        return false;
    }

    status = AMediaCodec_configure(codec, format, nullptr, nullptr, 0);
    AMediaFormat_delete(format);
    if (status != AMEDIA_OK) {
        LOGE("AMediaCodec_configure failed");
        AMediaCodec_delete(codec);
        AMediaExtractor_delete(extractor);
        close(fd);
        return false;
    }

    AMediaCodec_start(codec);

    // Asignar memoria contigua fija con margen generoso para que onAudioReady lea de forma lock-free
    size_t estimatedSamples = (mTotalFrames > 0) ? static_cast<size_t>(mTotalFrames * mChannelCount) : static_cast<size_t>(mSampleRate * mChannelCount * 300);
    size_t safeCapacity = estimatedSamples + static_cast<size_t>(mSampleRate * mChannelCount * 30);
    mPcmBuffer.assign(safeCapacity, 0);

    // Decodificar sincrónicamente sólo un búfer inicial ultrarrápido (~0.5 segundos)
    // para iniciar la reproducción de inmediato sin latencia perceptible
    bool sawInputEOS = false;
    bool sawOutputEOS = false;
    int emptyDequeueCount = 0;
    const int maxEmptyDequeues = 200;
    size_t initialThreshold = static_cast<size_t>(mSampleRate * mChannelCount / 2);

    while (!sawOutputEOS && !mCancelLoading.load() && mDecodedSamples.load() < initialThreshold) {
        bool progress = false;

        if (!sawInputEOS) {
            ssize_t inputBufIndex = AMediaCodec_dequeueInputBuffer(codec, 2000);
            if (inputBufIndex >= 0) {
                progress = true;
                size_t bufSize = 0;
                uint8_t *inputBuf = AMediaCodec_getInputBuffer(codec, inputBufIndex, &bufSize);
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
                uint8_t *outBuf = AMediaCodec_getOutputBuffer(codec, outputBufIndex, &bufSize);
                if (outBuf) {
                    const int16_t *samples = reinterpret_cast<const int16_t*>(outBuf + info.offset);
                    size_t sampleCount = info.size / sizeof(int16_t);
                    size_t currentDecoded = mDecodedSamples.load(std::memory_order_relaxed);
                    if (currentDecoded + sampleCount <= mPcmBuffer.size()) {
                        std::memcpy(&mPcmBuffer[currentDecoded], samples, sampleCount * sizeof(int16_t));
                        mDecodedSamples.fetch_add(sampleCount, std::memory_order_release);
                    }
                }
            }
            AMediaCodec_releaseOutputBuffer(codec, outputBufIndex, false);
        } else if (outputBufIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            progress = true;
            AMediaFormat *newFormat = AMediaCodec_getOutputFormat(codec);
            int32_t newRate = 0;
            int32_t newChannels = 0;
            if (AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &newRate) && newRate > 0) {
                mSampleRate = newRate;
            }
            if (AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &newChannels) && newChannels > 0) {
                mChannelCount = newChannels;
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

    if (mDecodedSamples.load() == 0 && sawOutputEOS) {
        LOGE("No audio samples could be decoded from file");
        AMediaCodec_stop(codec);
        AMediaCodec_delete(codec);
        AMediaExtractor_delete(extractor);
        close(fd);
        return false;
    }

    if (!openStream()) {
        LOGE("Could not open Oboe stream for decoded audio");
        mCancelLoading.store(true);
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
        mTotalFrames = mChannelCount > 0 ? (mDecodedSamples.load(std::memory_order_acquire) / mChannelCount) : 0;
        if (mSampleRate > 0) {
            mDurationMs = (mTotalFrames * 1000) / mSampleRate;
        }
        mIsDecodingFinished.store(true);
    } else {
        // Lanzar hilo en segundo plano para continuar decodificando progresivamente a alta velocidad
        mDecoderThread = std::thread(&OboeAudioPlayer::decodeRemaining, this, extractor, codec, fd, sawInputEOS);
    }

    LOGI("Track initialized instantaneously: initialFrames=%lld, totalFramesEst=%lld, rate=%d, channels=%d",
         (long long)(mDecodedSamples.load() / mChannelCount), (long long)mTotalFrames, mSampleRate, mChannelCount);
    return true;
}

void OboeAudioPlayer::decodeRemaining(AMediaExtractor *extractor, AMediaCodec *codec, int fd, bool initialSawInputEOS) {
    bool sawInputEOS = initialSawInputEOS;
    bool sawOutputEOS = false;
    int emptyDequeueCount = 0;
    const int maxEmptyDequeues = 300;

    while (!sawOutputEOS && !mCancelLoading.load()) {
        bool progress = false;

        if (!sawInputEOS) {
            ssize_t inputBufIndex = AMediaCodec_dequeueInputBuffer(codec, 2000);
            if (inputBufIndex >= 0) {
                progress = true;
                size_t bufSize = 0;
                uint8_t *inputBuf = AMediaCodec_getInputBuffer(codec, inputBufIndex, &bufSize);
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
                uint8_t *outBuf = AMediaCodec_getOutputBuffer(codec, outputBufIndex, &bufSize);
                if (outBuf) {
                    const int16_t *samples = reinterpret_cast<const int16_t*>(outBuf + info.offset);
                    size_t sampleCount = info.size / sizeof(int16_t);
                    size_t currentDecoded = mDecodedSamples.load(std::memory_order_relaxed);
                    if (currentDecoded + sampleCount <= mPcmBuffer.size()) {
                        std::memcpy(&mPcmBuffer[currentDecoded], samples, sampleCount * sizeof(int16_t));
                        mDecodedSamples.fetch_add(sampleCount, std::memory_order_release);
                    } else {
                        std::lock_guard<std::mutex> lock(mBufferMutex);
                        size_t needed = currentDecoded + sampleCount + static_cast<size_t>(mSampleRate * mChannelCount * 10);
                        mPcmBuffer.resize(needed, 0);
                        std::memcpy(&mPcmBuffer[currentDecoded], samples, sampleCount * sizeof(int16_t));
                        mDecodedSamples.fetch_add(sampleCount, std::memory_order_release);
                    }
                }
            }
            AMediaCodec_releaseOutputBuffer(codec, outputBufIndex, false);
        } else if (outputBufIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            progress = true;
            AMediaFormat *newFormat = AMediaCodec_getOutputFormat(codec);
            int32_t newRate = 0;
            int32_t newChannels = 0;
            if (AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &newRate) && newRate > 0) {
                mSampleRate = newRate;
            }
            if (AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &newChannels) && newChannels > 0) {
                mChannelCount = newChannels;
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

    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    AMediaExtractor_delete(extractor);
    if (fd >= 0) {
        close(fd);
    }

    if (!mCancelLoading.load()) {
        if (mChannelCount > 0) {
            int64_t finalFrames = mDecodedSamples.load(std::memory_order_acquire) / mChannelCount;
            mTotalFrames = finalFrames;
            if (mSampleRate > 0) {
                mDurationMs = (finalFrames * 1000) / mSampleRate;
            }
        }
        mIsDecodingFinished.store(true);
        LOGI("Progressive decode finished: totalFrames=%lld, durationMs=%lld",
             (long long)mTotalFrames, (long long)mDurationMs);
    }
}

bool OboeAudioPlayer::play() {
    if (!openStream()) {
        return false;
    }
    if (!mAudioStream) {
        return false;
    }
    mIsPlaybackEnded.store(false);
    if (mCurrentFrameIndex.load() >= mTotalFrames && mTotalFrames > 0) {
        mCurrentFrameIndex.store(0);
    }
    oboe::StreamState state = mAudioStream->getState();
    if (state == oboe::StreamState::Started) {
        mIsPlaying.store(true);
        return true;
    }
    oboe::Result result = mAudioStream->requestStart();
    if (result == oboe::Result::OK) {
        mIsPlaying.store(true);
        LOGI("OboeAudioPlayer started playback");
        return true;
    }
    LOGE("Failed to start Oboe stream: %s", oboe::convertToText(result));
    return false;
}

bool OboeAudioPlayer::pause() {
    mIsPlaying.store(false);
    if (mAudioStream) {
        oboe::StreamState state = mAudioStream->getState();
        if (state == oboe::StreamState::Started || state == oboe::StreamState::Starting) {
            mAudioStream->requestPause();
            LOGI("OboeAudioPlayer paused playback");
        }
    }
    return true;
}

bool OboeAudioPlayer::stop() {
    mIsPlaying.store(false);
    mCurrentFrameIndex.store(0);
    mIsPlaybackEnded.store(false);
    if (mAudioStream) {
        oboe::StreamState state = mAudioStream->getState();
        if (state != oboe::StreamState::Stopped && state != oboe::StreamState::Stopping &&
            state != oboe::StreamState::Closed) {
            mAudioStream->requestStop();
            LOGI("OboeAudioPlayer stopped playback");
        }
    }
    return true;
}

bool OboeAudioPlayer::seekTo(int64_t positionMs) {
    if (mSampleRate <= 0) return false;
    int64_t targetFrame = (positionMs * mSampleRate) / 1000;

    size_t decoded = mDecodedSamples.load(std::memory_order_acquire);
    int64_t availableFrames = (mChannelCount > 0) ? static_cast<int64_t>(decoded / mChannelCount) : 0;

    // Si la búsqueda excede lo decodificado hasta ahora, esperar brevemente a la decodificación progresiva
    if (targetFrame > availableFrames && !mIsDecodingFinished.load()) {
        int waitAttempts = 0;
        while (targetFrame > availableFrames && !mIsDecodingFinished.load() && waitAttempts < 40) {
            usleep(10000); // 10ms
            decoded = mDecodedSamples.load(std::memory_order_acquire);
            availableFrames = (mChannelCount > 0) ? static_cast<int64_t>(decoded / mChannelCount) : 0;
            waitAttempts++;
        }
    }

    int64_t limitFrames = mTotalFrames > 0 ? mTotalFrames : availableFrames;
    targetFrame = std::max<int64_t>(0, std::min<int64_t>(targetFrame, limitFrames));
    mCurrentFrameIndex.store(targetFrame);
    if (targetFrame < limitFrames) {
        mIsPlaybackEnded.store(false);
    }
    LOGI("Seeked to %lld ms (frame %lld / %lld)", (long long)positionMs,
         (long long)targetFrame, (long long)limitFrames);
    return true;
}

int64_t OboeAudioPlayer::getPositionMs() const {
    if (mSampleRate <= 0) return 0;
    return (mCurrentFrameIndex.load() * 1000) / mSampleRate;
}

int64_t OboeAudioPlayer::getDurationMs() const {
    return mDurationMs;
}

bool OboeAudioPlayer::isPlaying() const {
    return mIsPlaying.load();
}

oboe::DataCallbackResult OboeAudioPlayer::onAudioReady(
    oboe::AudioStream *oboeStream,
    void *audioData,
    int32_t numFrames) {

    int16_t *output = static_cast<int16_t*>(audioData);
    int32_t streamChannels = oboeStream ? oboeStream->getChannelCount() : 2;
    int32_t fileChannels = mChannelCount > 0 ? mChannelCount : streamChannels;
    int64_t currentFrame = mCurrentFrameIndex.load(std::memory_order_relaxed);
    
    size_t decodedSamples = mDecodedSamples.load(std::memory_order_acquire);
    int64_t availableFrames = (fileChannels > 0) ? static_cast<int64_t>(decodedSamples / fileChannels) : 0;
    int64_t effectiveTotal = mTotalFrames > 0 ? mTotalFrames : availableFrames;

    if (!mIsPlaying.load(std::memory_order_relaxed)) {
        std::memset(output, 0, numFrames * streamChannels * sizeof(int16_t));
        return oboe::DataCallbackResult::Continue;
    }

    if (mIsDecodingFinished.load(std::memory_order_relaxed) && currentFrame >= effectiveTotal && effectiveTotal > 0) {
        std::memset(output, 0, numFrames * streamChannels * sizeof(int16_t));
        mIsPlaying.store(false);
        mIsPlaybackEnded.store(true);
        return oboe::DataCallbackResult::Continue;
    }

    int32_t framesAvailable = (currentFrame < availableFrames) ? static_cast<int32_t>(availableFrames - currentFrame) : 0;
    int32_t framesToRead = std::min<int32_t>(numFrames, framesAvailable);

    if (framesToRead > 0) {
        if (fileChannels == streamChannels) {
            size_t sampleOffset = currentFrame * fileChannels;
            size_t samplesToCopy = framesToRead * fileChannels;
            if (sampleOffset + samplesToCopy <= mPcmBuffer.size()) {
                std::memcpy(output, &mPcmBuffer[sampleOffset], samplesToCopy * sizeof(int16_t));
            } else {
                std::memset(output, 0, samplesToCopy * sizeof(int16_t));
            }
        } else if (fileChannels == 1 && streamChannels == 2) {
            // Mono to stereo upmix
            size_t sampleOffset = currentFrame;
            for (int32_t i = 0; i < framesToRead; ++i) {
                if (sampleOffset + i < mPcmBuffer.size()) {
                    int16_t s = mPcmBuffer[sampleOffset + i];
                    output[i * 2] = s;
                    output[i * 2 + 1] = s;
                } else {
                    output[i * 2] = 0;
                    output[i * 2 + 1] = 0;
                }
            }
        } else {
            // Fallback for channel format variations
            size_t sampleOffset = currentFrame * fileChannels;
            for (int32_t i = 0; i < framesToRead; ++i) {
                for (int32_t ch = 0; ch < streamChannels; ++ch) {
                    int32_t srcCh = ch < fileChannels ? ch : (fileChannels - 1);
                    size_t idx = sampleOffset + i * fileChannels + srcCh;
                    output[i * streamChannels + ch] = (idx < mPcmBuffer.size()) ? mPcmBuffer[idx] : 0;
                }
            }
        }
        mCurrentFrameIndex.store(currentFrame + framesToRead, std::memory_order_relaxed);
    }

    if (framesToRead < numFrames) {
        int32_t remainingSamples = (numFrames - framesToRead) * streamChannels;
        std::memset(output + framesToRead * streamChannels, 0, remainingSamples * sizeof(int16_t));

        if (mIsDecodingFinished.load(std::memory_order_relaxed) && (currentFrame + framesToRead >= effectiveTotal)) {
            mCurrentFrameIndex.store(effectiveTotal);
            mIsPlaying.store(false);
            mIsPlaybackEnded.store(true);
        }
    }

    // Procesar ecualizador paramétrico de 10 bandas si está activo
    if (framesToRead > 0) {
        mEqualizer.process(output, framesToRead, streamChannels);
    }

    // Procesar volumen / ganancia de salida
    float volume = mVolume.load(std::memory_order_relaxed);
    if (framesToRead > 0 && std::abs(volume - 1.0f) > 0.001f && volume >= 0.0f) {
        int32_t sampleCount = framesToRead * streamChannels;
        for (int32_t i = 0; i < sampleCount; ++i) {
            float val = static_cast<float>(output[i]) * volume;
            if (val > 32767.0f) val = 32767.0f;
            else if (val < -32768.0f) val = -32768.0f;
            output[i] = static_cast<int16_t>(val);
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void OboeAudioPlayer::setEqualizerEnabled(bool enabled) {
    mEqualizer.setEnabled(enabled);
    LOGI("OboeAudioPlayer: Equalizer %s", enabled ? "ENABLED" : "DISABLED");
}

bool OboeAudioPlayer::isEqualizerEnabled() const {
    return mEqualizer.isEnabled();
}

void OboeAudioPlayer::setEqualizerBandGain(int bandIndex, float gainDb) {
    mEqualizer.setBandGain(bandIndex, gainDb);
}

float OboeAudioPlayer::getEqualizerBandGain(int bandIndex) const {
    return mEqualizer.getBandGain(bandIndex);
}

void OboeAudioPlayer::resetEqualizer() {
    mEqualizer.resetGains();
    LOGI("OboeAudioPlayer: Equalizer reset to flat");
}

void OboeAudioPlayer::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGW("Oboe stream error after close: %s", oboe::convertToText(error));
    {
        std::lock_guard<std::mutex> lock(mErrorMutex);
        mLastErrorCode = static_cast<int32_t>(error);
        mLastErrorMsg = std::string("Stream error after close: ") + oboe::convertToText(error);
    }
    if (mIsPlaying.load()) {
        closeStream();
        if (openStream()) {
            if (mAudioStream) {
                mAudioStream->requestStart();
            }
        }
    }
}

int32_t OboeAudioPlayer::getLastErrorCode() const {
    std::lock_guard<std::mutex> lock(mErrorMutex);
    return mLastErrorCode;
}

std::string OboeAudioPlayer::getLastErrorMsg() const {
    std::lock_guard<std::mutex> lock(mErrorMutex);
    return mLastErrorMsg;
}

std::string OboeAudioPlayer::getAudioDeviceInfo() const {
    if (!mAudioStream) {
        return "Stream inactivo o cerrado";
    }
    std::string api = (mAudioStream->getAudioApi() == oboe::AudioApi::AAudio) ? "AAudio (Baja Latencia)" : "OpenSL ES";
    std::string state = oboe::convertToText(mAudioStream->getState());
    int32_t sr = mAudioStream->getSampleRate();
    int32_t ch = mAudioStream->getChannelCount();
    int32_t buf = mAudioStream->getBufferSizeInFrames();
    int32_t deviceId = mAudioStream->getDeviceId();

    return "API=" + api + " | Estado=" + state + " | SR=" + std::to_string(sr) +
           "Hz | Ch=" + std::to_string(ch) + " | Buffer=" + std::to_string(buf) +
           " frames | DeviceId=" + std::to_string(deviceId);
}

std::string OboeAudioPlayer::getStreamStatsJson() const {
    int32_t sr = mAudioStream ? mAudioStream->getSampleRate() : mSampleRate;
    int32_t ch = mAudioStream ? mAudioStream->getChannelCount() : mChannelCount;
    int32_t buf = mAudioStream ? mAudioStream->getBufferSizeInFrames() : 0;
    int32_t underrun = 0;
    if (mAudioStream) {
        auto xrun = mAudioStream->getXRunCount();
        if (xrun) {
            underrun = xrun.value();
        }
    }
    std::string api = mAudioStream ? ((mAudioStream->getAudioApi() == oboe::AudioApi::AAudio) ? "AAudio" : "OpenSLES") : "None";

    return "{\"sampleRate\":" + std::to_string(sr) +
           ",\"channelCount\":" + std::to_string(ch) +
           ",\"bufferFrames\":" + std::to_string(buf) +
           ",\"underruns\":" + std::to_string(underrun) +
           ",\"api\":\"" + api +
           "\",\"isPlaying\":" + (mIsPlaying.load() ? "true" : "false") +
           ",\"lastErrorCode\":" + std::to_string(getLastErrorCode()) +
           ",\"lastErrorMsg\":\"" + getLastErrorMsg() + "\"}";
}

bool OboeAudioPlayer::isPlaybackEnded() const {
    return mIsPlaybackEnded.load();
}

