#pragma once

#include <cmath>
#include <vector>
#include <atomic>
#include <algorithm>
#include <cstdint>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/**
 * Procesador de Audio Espacial 360° / Efecto 8D Nativo en C++ para Google Oboe.
 *
 * Implementa simulación acústica binaural tridimensional en tiempo real:
 * 1. Rotación azimutal continua (0 a 360 grados) a velocidad angular configurable.
 * 2. Diferencia de Nivel Interaural (ILD - Interaural Level Difference) con paneo de potencia constante.
 * 3. Diferencia de Tiempo Interaural (ITD - Interaural Time Difference) mediante retardos fraccionales binaurales (~0.65ms).
 * 4. Micro-reverberación y reflexiones tempranas de sala para sensación acústica "fuera de la cabeza".
 */
class SpatialAudio8DProcessor {
public:
    SpatialAudio8DProcessor() {
        resetBuffers();
    }

    void setSampleRate(float sampleRate) {
        if (sampleRate > 0.0f) {
            mSampleRate = sampleRate;
            mMaxDelaySamples = static_cast<int>(sampleRate * 0.00075f); // ~0.75 ms máximo ITD
            if (mMaxDelaySamples < 4) mMaxDelaySamples = 4;
            if (mMaxDelaySamples > MAX_ITD_BUFFER) mMaxDelaySamples = MAX_ITD_BUFFER - 1;
        }
    }

    void setEnabled(bool enabled) {
        mEnabled.store(enabled);
        if (!enabled) {
            resetBuffers();
        }
    }

    bool isEnabled() const {
        return mEnabled.load();
    }

    // Velocidad de giro azimutal en Hertz (ej. 0.04 Hz a 0.25 Hz; 0.08 Hz = 1 vuelta cada 12.5 segundos)
    void setRotationSpeed(float speedHz) {
        mSpeedHz.store(std::clamp(speedHz, 0.02f, 0.40f));
    }

    float getRotationSpeed() const {
        return mSpeedHz.load();
    }

    // Intensidad / Profundidad del paneo y separación 3D (0.0f a 1.0f)
    void setSpatialDepth(float depth) {
        mDepth.store(std::clamp(depth, 0.1f, 1.0f));
    }

    float getSpatialDepth() const {
        return mDepth.load();
    }

    // Nivel de reverberación espacial de sala binaural (0.0f a 1.0f)
    void setRoomReverb(float reverb) {
        mReverb.store(std::clamp(reverb, 0.0f, 0.8f));
    }

    float getRoomReverb() const {
        return mReverb.load();
    }

    void resetBuffers() {
        mAzimuth = 0.0f;
        mDelayIndex = 0;
        std::fill(mDelayBufL.begin(), mDelayBufL.end(), 0.0f);
        std::fill(mDelayBufR.begin(), mDelayBufR.end(), 0.0f);
        mEarlyReflIndex = 0;
        std::fill(mEarlyReflL.begin(), mEarlyReflL.end(), 0.0f);
        std::fill(mEarlyReflR.begin(), mEarlyReflR.end(), 0.0f);
    }

    void process(int16_t* audioData, int32_t numFrames, int32_t channelCount) {
        if (!mEnabled.load() || numFrames <= 0 || audioData == nullptr || channelCount < 2) {
            return;
        }

        const float invScale = 1.0f / 32768.0f;
        const float scale = 32767.0f;
        const float speed = mSpeedHz.load();
        const float depth = mDepth.load();
        const float reverb = mReverb.load();
        const float sampleRate = mSampleRate;

        // Incremento angular por muestra: 2 * pi * speed / sampleRate
        const float angleDelta = static_cast<float>(2.0 * M_PI * speed / sampleRate);
        const float maxItd = static_cast<float>(mMaxDelaySamples);

        for (int32_t frame = 0; frame < numFrames; ++frame) {
            int32_t baseIdx = frame * channelCount;
            float inL = static_cast<float>(audioData[baseIdx]) * invScale;
            float inR = static_cast<float>(audioData[baseIdx + 1]) * invScale;

            // 1. Calcular posición azimutal 360° actual
            mAzimuth += angleDelta;
            if (mAzimuth >= 2.0f * M_PI) {
                mAzimuth -= static_cast<float>(2.0 * M_PI);
            }

            float sinVal = std::sin(mAzimuth);
            float cosVal = std::cos(mAzimuth);

            // 2. Interaural Level Difference (ILD): Paneo binaural de potencia constante
            // Cuando sinVal > 0, sonido está a la derecha; sinVal < 0, a la izquierda.
            // cosVal representa adelante (> 0) / atrás (< 0).
            float panAngle = (sinVal * depth) * static_cast<float>(M_PI * 0.25f); // [-pi/4, +pi/4]
            float gainL = std::cos(static_cast<float>(M_PI * 0.25f) + panAngle);
            float gainR = std::sin(static_cast<float>(M_PI * 0.25f) + panAngle);

            // Efecto sutil adelante/atrás: amortiguación leve de agudos cuando el sonido está detrás
            float backFactor = 1.0f - (cosVal < 0.0f ? (-cosVal * 0.15f * depth) : 0.0f);
            gainL *= backFactor;
            gainR *= backFactor;

            // 3. Interaural Time Difference (ITD): Retardo de tiempo binaural
            // Guardar en buffer circular de retardo
            mDelayBufL[mDelayIndex] = inL;
            mDelayBufR[mDelayIndex] = inR;

            float delayL = 0.0f;
            float delayR = 0.0f;
            if (sinVal > 0.0f) {
                // Sonido a la derecha: el oído izquierdo se retrasa
                delayL = sinVal * maxItd * depth;
            } else {
                // Sonido a la izquierda: el oído derecho se retrasa
                delayR = (-sinVal) * maxItd * depth;
            }

            // Lectura interpolada del buffer circular
            float delayedL = readDelay(mDelayBufL, mDelayIndex, delayL);
            float delayedR = readDelay(mDelayBufR, mDelayIndex, delayR);

            mDelayIndex = (mDelayIndex + 1) % MAX_ITD_BUFFER;

            // Combinar señal directa modulada con retardo temporal
            float outL = delayedL * gainL;
            float outR = delayedR * gainR;

            // 4. Reflexión temprana espacial (micro-sala binaural 360°)
            if (reverb > 0.01f) {
                mEarlyReflL[mEarlyReflIndex] = outL;
                mEarlyReflR[mEarlyReflIndex] = outR;

                int tap1 = (mEarlyReflIndex - 600 + MAX_ROOM_BUFFER) % MAX_ROOM_BUFFER;
                int tap2 = (mEarlyReflIndex - 1200 + MAX_ROOM_BUFFER) % MAX_ROOM_BUFFER;
                int tap3 = (mEarlyReflIndex - 1800 + MAX_ROOM_BUFFER) % MAX_ROOM_BUFFER;

                float roomEchoL = (mEarlyReflL[tap1] * 0.35f + mEarlyReflR[tap2] * 0.25f - mEarlyReflL[tap3] * 0.15f) * reverb;
                float roomEchoR = (mEarlyReflR[tap1] * 0.35f + mEarlyReflL[tap2] * 0.25f - mEarlyReflR[tap3] * 0.15f) * reverb;

                outL += roomEchoL;
                outR += roomEchoR;

                mEarlyReflIndex = (mEarlyReflIndex + 1) % MAX_ROOM_BUFFER;
            }

            // Clamping a límites de 16-bit
            float sampleL = outL * scale;
            float sampleR = outR * scale;

            if (sampleL > 32767.0f) sampleL = 32767.0f;
            else if (sampleL < -32768.0f) sampleL = -32768.0f;

            if (sampleR > 32767.0f) sampleR = 32767.0f;
            else if (sampleR < -32768.0f) sampleR = -32768.0f;

            audioData[baseIdx] = static_cast<int16_t>(sampleL);
            audioData[baseIdx + 1] = static_cast<int16_t>(sampleR);
        }
    }

private:
    static constexpr int MAX_ITD_BUFFER = 128;
    static constexpr int MAX_ROOM_BUFFER = 2400;

    inline float readDelay(const std::array<float, MAX_ITD_BUFFER>& buffer, int currentIndex, float delaySamples) const {
        float readPos = static_cast<float>(currentIndex) - delaySamples;
        while (readPos < 0.0f) readPos += static_cast<float>(MAX_ITD_BUFFER);
        int idx0 = static_cast<int>(readPos) % MAX_ITD_BUFFER;
        int idx1 = (idx0 + 1) % MAX_ITD_BUFFER;
        float frac = readPos - std::floor(readPos);
        return buffer[idx0] + frac * (buffer[idx1] - buffer[idx0]);
    }

    std::atomic<bool> mEnabled{false};
    std::atomic<float> mSpeedHz{0.08f};   // ~12.5 seg por revolución 360°
    std::atomic<float> mDepth{0.85f};     // 85% de profundidad espacial
    std::atomic<float> mReverb{0.22f};    // Reflexión sutil de sala
    float mSampleRate = 44100.0f;
    float mAzimuth = 0.0f;

    int mMaxDelaySamples = 32;
    int mDelayIndex = 0;
    std::array<float, MAX_ITD_BUFFER> mDelayBufL{};
    std::array<float, MAX_ITD_BUFFER> mDelayBufR{};

    int mEarlyReflIndex = 0;
    std::array<float, MAX_ROOM_BUFFER> mEarlyReflL{};
    std::array<float, MAX_ROOM_BUFFER> mEarlyReflR{};
};
