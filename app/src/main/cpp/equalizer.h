#pragma once

#include <cmath>
#include <vector>
#include <array>
#include <atomic>
#include <algorithm>
#include <cstdint>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// 10 bandas estándar ISO para ecualizador gráfico paramétrico
// 31.25Hz, 62.5Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
constexpr int NUM_EQ_BANDS = 10;
constexpr float EQ_BAND_FREQUENCIES[NUM_EQ_BANDS] = {
    31.25f, 62.5f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f
};

// Coeficientes y estado de filtro Biquad IIR para estéreo (2 canales)
// Implementado mediante Direct Form II Transposed para máxima estabilidad numérica
struct BiquadBand {
    float frequency = 1000.0f;
    float gainDb = 0.0f;
    float q = 1.4142f; // Ancho de banda de 1 octava

    // Coeficientes normalizados
    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f;
    float a1 = 0.0f, a2 = 0.0f;

    // Estado del filtro para canal 0 (L) y canal 1 (R)
    float s1[2] = {0.0f, 0.0f};
    float s2[2] = {0.0f, 0.0f};

    void calculateCoefficients(float sampleRate) {
        if (sampleRate <= 0.0f) sampleRate = 44100.0f;

        // Limitar frecuencia para evitar rebasar la frecuencia de Nyquist
        float f0 = std::clamp(frequency, 20.0f, sampleRate * 0.48f);
        float w0 = static_cast<float>(2.0 * M_PI * f0 / sampleRate);
        float alpha = std::sin(w0) / (2.0f * q);
        float A = std::pow(10.0f, gainDb / 40.0f); // Peaking EQ gain factor
        float cosw0 = std::cos(w0);

        float raw_b0 = 1.0f + alpha * A;
        float raw_b1 = -2.0f * cosw0;
        float raw_b2 = 1.0f - alpha * A;
        float raw_a0 = 1.0f + alpha / A;
        float raw_a1 = -2.0f * cosw0;
        float raw_a2 = 1.0f - alpha / A;

        if (std::abs(raw_a0) > 1e-6f) {
            b0 = raw_b0 / raw_a0;
            b1 = raw_b1 / raw_a0;
            b2 = raw_b2 / raw_a0;
            a1 = raw_a1 / raw_a0;
            a2 = raw_a2 / raw_a0;
        } else {
            b0 = 1.0f; b1 = 0.0f; b2 = 0.0f;
            a1 = 0.0f; a2 = 0.0f;
        }
    }

    void resetState() {
        s1[0] = s1[1] = 0.0f;
        s2[0] = s2[1] = 0.0f;
    }

    inline float processSample(float inSample, int channel) {
        // Direct Form II Transposed:
        // y[n] = b0 * x[n] + s1
        // s1   = b1 * x[n] - a1 * y[n] + s2
        // s2   = b2 * x[n] - a2 * y[n]
        float y = b0 * inSample + s1[channel];
        s1[channel] = b1 * inSample - a1 * y + s2[channel];
        s2[channel] = b2 * inSample - a2 * y;
        return y;
    }
};

class TenBandEqualizer {
public:
    TenBandEqualizer() {
        for (int i = 0; i < NUM_EQ_BANDS; ++i) {
            mBands[i].frequency = EQ_BAND_FREQUENCIES[i];
            mBands[i].gainDb = 0.0f;
            mBands[i].q = 1.4142f;
        }
        recalculateAll(44100.0f);
    }

    void setSampleRate(float sampleRate) {
        if (sampleRate > 0.0f && std::abs(mSampleRate - sampleRate) > 1.0f) {
            mSampleRate = sampleRate;
            recalculateAll(mSampleRate);
        }
    }

    void setEnabled(bool enabled) {
        mEnabled.store(enabled);
        if (!enabled) {
            resetStates();
        }
    }

    bool isEnabled() const {
        return mEnabled.load();
    }

    void setBandGain(int bandIndex, float gainDb) {
        if (bandIndex >= 0 && bandIndex < NUM_EQ_BANDS) {
            gainDb = std::clamp(gainDb, -12.0f, 12.0f);
            mBands[bandIndex].gainDb = gainDb;
            mBands[bandIndex].calculateCoefficients(mSampleRate);
        }
    }

    float getBandGain(int bandIndex) const {
        if (bandIndex >= 0 && bandIndex < NUM_EQ_BANDS) {
            return mBands[bandIndex].gainDb;
        }
        return 0.0f;
    }

    void resetGains() {
        for (int i = 0; i < NUM_EQ_BANDS; ++i) {
            mBands[i].gainDb = 0.0f;
            mBands[i].calculateCoefficients(mSampleRate);
            mBands[i].resetState();
        }
    }

    void resetStates() {
        for (int i = 0; i < NUM_EQ_BANDS; ++i) {
            mBands[i].resetState();
        }
    }

    void process(int16_t* audioData, int32_t numFrames, int32_t channelCount) {
        if (!mEnabled.load() || numFrames <= 0 || audioData == nullptr) {
            return;
        }

        const float invScale = 1.0f / 32768.0f;
        const float scale = 32767.0f;
        int32_t processChannels = std::min<int32_t>(channelCount, 2);

        for (int32_t frame = 0; frame < numFrames; ++frame) {
            int32_t baseIdx = frame * channelCount;
            for (int32_t ch = 0; ch < processChannels; ++ch) {
                float sample = static_cast<float>(audioData[baseIdx + ch]) * invScale;

                // Cascada por las 10 bandas
                for (int b = 0; b < NUM_EQ_BANDS; ++b) {
                    sample = mBands[b].processSample(sample, ch);
                }

                // Clamping a límites de 16-bit
                float outVal = sample * scale;
                if (outVal > 32767.0f) outVal = 32767.0f;
                else if (outVal < -32768.0f) outVal = -32768.0f;
                audioData[baseIdx + ch] = static_cast<int16_t>(outVal);
            }
        }
    }

private:
    void recalculateAll(float sampleRate) {
        for (int i = 0; i < NUM_EQ_BANDS; ++i) {
            mBands[i].calculateCoefficients(sampleRate);
            mBands[i].resetState();
        }
    }

    std::array<BiquadBand, NUM_EQ_BANDS> mBands;
    float mSampleRate = 44100.0f;
    std::atomic<bool> mEnabled{false};
};
