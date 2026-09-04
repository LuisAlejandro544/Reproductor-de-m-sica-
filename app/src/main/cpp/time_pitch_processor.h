#pragma once

#include <vector>
#include <cmath>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <atomic>
#include <mutex>
#include <functional>

/**
 * TimePitchProcessor: Modificador de Escala Temporal (Time-Stretching WSOLA)
 * y Transpositor de Afinación (Pitch Shifting) Audiófilo en C++20.
 *
 * Permite acelerar o desacelerar el audio (0.5x a 2.0x) preservando de forma
 * matemática el tono exacto de la voz y los instrumentos (sin efecto ardilla),
 * o ajustar el tono en semitonos (-6 a +6) de forma 100% independiente de la velocidad.
 */
class TimePitchProcessor {
public:
    TimePitchProcessor(int32_t sampleRate = 44100, int32_t channelCount = 2);
    ~TimePitchProcessor() = default;

    void configure(int32_t sampleRate, int32_t channelCount);
    void reset();

    // Configuración de Velocidad (0.5x a 2.0x)
    void setSpeed(float speed);
    float getSpeed() const { return mSpeed.load(std::memory_order_relaxed); }

    // Configuración de Afinación / Tono en semitonos (-6.0 a +6.0)
    void setPitchSemitones(float semitones);
    float getPitchSemitones() const { return mPitchSemitones.load(std::memory_order_relaxed); }

    // Preservación de Tono de Voz y Música Natural (WSOLA activo)
    void setPreservePitch(bool preserve);
    bool isPreservePitch() const { return mPreservePitch.load(std::memory_order_relaxed); }

    void resetToDefault();

    /**
     * Procesa y llena 'outputBuffer' con 'framesToGenerate' frames.
     * 'readSourceFrames' es un callback que entrega frames de audio de entrada del archivo.
     * Retorna el número de frames efectivamente generados.
     */
    int32_t process(
        int16_t* outputBuffer,
        int32_t framesToGenerate,
        const std::function<int32_t(int16_t* dest, int32_t count)>& readSourceFrames
    );

private:
    void initWindow();
    void processWsolaFrame(const std::function<int32_t(int16_t* dest, int32_t count)>& readSourceFrames);

    int32_t mSampleRate;
    int32_t mChannels;

    std::atomic<float> mSpeed{1.0f};
    std::atomic<float> mPitchSemitones{0.0f};
    std::atomic<bool> mPreservePitch{true};

    // Parámetros WSOLA
    static constexpr int32_t WINDOW_SIZE = 1024;
    static constexpr int32_t SYNTHESIS_HOP = WINDOW_SIZE / 2; // 512
    static constexpr int32_t MAX_DELTA = 128;

    std::vector<float> mHanningWindow;
    std::vector<float> mOverlapBuffer; // Longitud: SYNTHESIS_HOP * mChannels

    // Búferes internos para análisis y búsqueda de máxima similitud
    std::vector<int16_t> mInputRingBuffer;
    size_t mRingHead = 0;
    size_t mRingTail = 0;
    size_t mRingAvailable = 0;

    // Cola de salida sintetizada lista para entregar
    std::vector<int16_t> mOutputQueue;
    size_t mQueueReadPos = 0;

    // Resampler para Pitch Shifting independiente
    double mResamplePhase = 0.0;
    std::vector<int16_t> mResampleBuffer;

    mutable std::mutex mStateMutex;
    bool mHasPreviousOverlap = false;
};
