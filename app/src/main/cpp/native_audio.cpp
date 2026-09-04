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
    mDecoder.cancel();
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
    mSpatial8D.setSampleRate(static_cast<float>(mAudioStream->getSampleRate()));
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
    mCancelLoading.store(true);
    mDecoder.cancel();

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

    AudioFileInfo info;
    int32_t errorCode = 0;
    std::string errorMsg = "OK";

    bool success = mDecoder.openAndDecodeInitial(
        filePath,
        mPcmBuffer,
        mDecodedSamples,
        mCancelLoading,
        mIsDecodingFinished,
        mBufferMutex,
        info,
        errorCode,
        errorMsg
    );

    if (!success) {
        std::lock_guard<std::mutex> errLock(mErrorMutex);
        mLastErrorCode = errorCode;
        mLastErrorMsg = errorMsg;
        return false;
    }

    mSampleRate = info.sampleRate;
    mChannelCount = info.channelCount;
    mDurationMs = info.durationMs;
    mTotalFrames = info.totalFrames;

    if (!openStream()) {
        LOGE("Could not open Oboe stream for decoded audio");
        mCancelLoading.store(true);
        mDecoder.cancel();
        return false;
    }

    mTimePitchProcessor.configure(mSampleRate, mChannelCount);
    mTimePitchProcessor.reset();

    LOGI("Track initialized instantaneously: initialFrames=%lld, totalFramesEst=%lld, rate=%d, channels=%d",
         (long long)(mDecodedSamples.load() / (mChannelCount > 0 ? mChannelCount : 2)),
         (long long)mTotalFrames, mSampleRate, mChannelCount);
    return true;
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
    mTimePitchProcessor.reset();
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
    mTimePitchProcessor.reset();
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

    auto readSourceFrames = [&](int16_t* dest, int32_t count) -> int32_t {
        int64_t curr = mCurrentFrameIndex.load(std::memory_order_relaxed);
        int32_t avail = (curr < availableFrames) ? static_cast<int32_t>(availableFrames - curr) : 0;
        int32_t toRead = std::min<int32_t>(count, avail);
        if (toRead > 0) {
            if (fileChannels == streamChannels) {
                size_t sampleOffset = curr * fileChannels;
                size_t samplesToCopy = toRead * fileChannels;
                if (sampleOffset + samplesToCopy <= mPcmBuffer.size()) {
                    std::memcpy(dest, &mPcmBuffer[sampleOffset], samplesToCopy * sizeof(int16_t));
                } else {
                    std::memset(dest, 0, samplesToCopy * sizeof(int16_t));
                }
            } else if (fileChannels == 1 && streamChannels == 2) {
                size_t sampleOffset = curr;
                for (int32_t i = 0; i < toRead; ++i) {
                    if (sampleOffset + i < mPcmBuffer.size()) {
                        int16_t s = mPcmBuffer[sampleOffset + i];
                        dest[i * 2] = s;
                        dest[i * 2 + 1] = s;
                    } else {
                        dest[i * 2] = 0;
                        dest[i * 2 + 1] = 0;
                    }
                }
            } else {
                size_t sampleOffset = curr * fileChannels;
                for (int32_t i = 0; i < toRead; ++i) {
                    for (int32_t ch = 0; ch < streamChannels; ++ch) {
                        int32_t srcCh = ch < fileChannels ? ch : (fileChannels - 1);
                        size_t idx = sampleOffset + i * fileChannels + srcCh;
                        dest[i * streamChannels + ch] = (idx < mPcmBuffer.size()) ? mPcmBuffer[idx] : 0;
                    }
                }
            }
            mCurrentFrameIndex.store(curr + toRead, std::memory_order_relaxed);
        }
        return toRead;
    };

    int32_t framesDelivered = mTimePitchProcessor.process(output, numFrames, readSourceFrames);

    if (framesDelivered < numFrames) {
        int32_t remainingSamples = (numFrames - framesDelivered) * streamChannels;
        std::memset(output + framesDelivered * streamChannels, 0, remainingSamples * sizeof(int16_t));

        int64_t curr = mCurrentFrameIndex.load(std::memory_order_relaxed);
        if (mIsDecodingFinished.load(std::memory_order_relaxed) && (curr >= effectiveTotal)) {
            mCurrentFrameIndex.store(effectiveTotal);
            mIsPlaying.store(false);
            mIsPlaybackEnded.store(true);
        }
    }

    // Procesar ecualizador paramétrico de 10 bandas si está activo
    if (framesDelivered > 0) {
        mEqualizer.process(output, framesDelivered, streamChannels);
        mSpatial8D.process(output, framesDelivered, streamChannels);
    }

    // Procesar volumen / ganancia de salida
    float volume = mVolume.load(std::memory_order_relaxed);
    if (framesDelivered > 0 && std::abs(volume - 1.0f) > 0.001f && volume >= 0.0f) {
        int32_t sampleCount = framesDelivered * streamChannels;
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

void OboeAudioPlayer::setSpatialAudioEnabled(bool enabled) {
    mSpatial8D.setEnabled(enabled);
    LOGI("OboeAudioPlayer: Spatial 360/8D audio %s", enabled ? "ENABLED" : "DISABLED");
}

bool OboeAudioPlayer::isSpatialAudioEnabled() const {
    return mSpatial8D.isEnabled();
}

void OboeAudioPlayer::setSpatialAudioSpeed(float speedHz) {
    mSpatial8D.setRotationSpeed(speedHz);
}

float OboeAudioPlayer::getSpatialAudioSpeed() const {
    return mSpatial8D.getRotationSpeed();
}

void OboeAudioPlayer::setSpatialAudioDepth(float depth) {
    mSpatial8D.setSpatialDepth(depth);
}

float OboeAudioPlayer::getSpatialAudioDepth() const {
    return mSpatial8D.getSpatialDepth();
}

void OboeAudioPlayer::setSpatialAudioReverb(float reverb) {
    mSpatial8D.setRoomReverb(reverb);
}

float OboeAudioPlayer::getSpatialAudioReverb() const {
    return mSpatial8D.getRoomReverb();
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

void OboeAudioPlayer::setPlaybackSpeed(float speed) {
    mTimePitchProcessor.setSpeed(speed);
    LOGI("OboeAudioPlayer: Playback speed set to %.2fx", speed);
}

float OboeAudioPlayer::getPlaybackSpeed() const {
    return mTimePitchProcessor.getSpeed();
}

void OboeAudioPlayer::setPitchSemitones(float semitones) {
    mTimePitchProcessor.setPitchSemitones(semitones);
    LOGI("OboeAudioPlayer: Pitch semitones set to %.2f st", semitones);
}

float OboeAudioPlayer::getPitchSemitones() const {
    return mTimePitchProcessor.getPitchSemitones();
}

void OboeAudioPlayer::setPitchPreservationEnabled(bool enabled) {
    mTimePitchProcessor.setPreservePitch(enabled);
    LOGI("OboeAudioPlayer: Pitch preservation %s", enabled ? "ENABLED" : "DISABLED");
}

bool OboeAudioPlayer::isPitchPreservationEnabled() const {
    return mTimePitchProcessor.isPreservePitch();
}

void OboeAudioPlayer::resetSpeedAndPitch() {
    mTimePitchProcessor.resetToDefault();
    LOGI("OboeAudioPlayer: Speed and pitch reset to default");
}


