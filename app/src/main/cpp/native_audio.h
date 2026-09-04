#pragma once

#include <oboe/Oboe.h>
#include <android/log.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include "equalizer.h"
#include "spatial_audio.h"
#include "audio_decoder.h"
#include "time_pitch_processor.h"
#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include <memory>
#include <thread>

#define LOG_TAG "RitmoNativeAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

class OboeAudioPlayer : public oboe::AudioStreamDataCallback,
                        public oboe::AudioStreamErrorCallback {
public:
    OboeAudioPlayer();
    ~OboeAudioPlayer();

    bool init();
    void release();
    bool loadFile(const std::string& filePath);
    bool play();
    bool pause();
    bool stop();
    bool seekTo(int64_t positionMs);

    int64_t getPositionMs() const;
    int64_t getDurationMs() const;
    bool isPlaying() const;

    // Métodos de Volumen y Pre-amplificación de salida
    void setVolume(float volume);
    float getVolume() const;

    // Métodos del Ecualizador Paramétrico de 10 Bandas
    void setEqualizerEnabled(bool enabled);
    bool isEqualizerEnabled() const;
    void setEqualizerBandGain(int bandIndex, float gainDb);
    float getEqualizerBandGain(int bandIndex) const;
    void resetEqualizer();

    // Métodos de Audio Espacial 360° / Efecto 8D Nativo (C++ Oboe Exclusivo)
    void setSpatialAudioEnabled(bool enabled);
    bool isSpatialAudioEnabled() const;
    void setSpatialAudioSpeed(float speedHz);
    float getSpatialAudioSpeed() const;
    void setSpatialAudioDepth(float depth);
    float getSpatialAudioDepth() const;
    void setSpatialAudioReverb(float reverb);
    float getSpatialAudioReverb() const;

    // Métodos de Velocidad y Afinación / Tono Independiente (C++ Oboe Exclusivo)
    void setPlaybackSpeed(float speed);
    float getPlaybackSpeed() const;
    void setPitchSemitones(float semitones);
    float getPitchSemitones() const;
    void setPitchPreservationEnabled(bool enabled);
    bool isPitchPreservationEnabled() const;
    void resetSpeedAndPitch();

    // Diagnósticos y Códigos de Error Crudos para Depuración
    int32_t getLastErrorCode() const;
    std::string getLastErrorMsg() const;
    std::string getAudioDeviceInfo() const;
    std::string getStreamStatsJson() const;
    bool isPlaybackEnded() const;

    // oboe::AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) override;

    // oboe::AudioStreamErrorCallback
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    bool openStream();
    void closeStream();

    std::shared_ptr<oboe::AudioStream> mAudioStream;
    std::string mCurrentFilePath;

    // PCM Audio buffer (16-bit stereo)
    std::vector<int16_t> mPcmBuffer;
    int32_t mSampleRate = 44100;
    int32_t mChannelCount = 2;
    int64_t mTotalFrames = 0;
    int64_t mDurationMs = 0;

    std::atomic<int64_t> mCurrentFrameIndex{0};
    std::atomic<size_t> mDecodedSamples{0};
    std::atomic<bool> mIsPlaying{false};
    std::atomic<bool> mIsInitialized{false};
    std::atomic<bool> mIsPlaybackEnded{false};
    std::atomic<bool> mCancelLoading{false};
    std::atomic<bool> mIsDecodingFinished{false};
    std::atomic<float> mVolume{1.0f};

    AudioDecoder mDecoder;

    TenBandEqualizer mEqualizer;
    SpatialAudio8DProcessor mSpatial8D;
    TimePitchProcessor mTimePitchProcessor;

    mutable std::mutex mBufferMutex;
    mutable std::mutex mErrorMutex;
    int32_t mLastErrorCode = 0;
    std::string mLastErrorMsg = "OK";
};
