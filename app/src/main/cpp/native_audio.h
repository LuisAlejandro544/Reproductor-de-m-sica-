#pragma once

#include <oboe/Oboe.h>
#include <android/log.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include <memory>

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
    bool decodeAudioFile(const std::string& filePath);

    std::shared_ptr<oboe::AudioStream> mAudioStream;
    std::string mCurrentFilePath;

    // PCM Audio buffer (16-bit stereo)
    std::vector<int16_t> mPcmBuffer;
    int32_t mSampleRate = 44100;
    int32_t mChannelCount = 2;
    int64_t mTotalFrames = 0;
    int64_t mDurationMs = 0;

    std::atomic<int64_t> mCurrentFrameIndex{0};
    std::atomic<bool> mIsPlaying{false};
    std::atomic<bool> mIsInitialized{false};

    mutable std::mutex mBufferMutex;
};
