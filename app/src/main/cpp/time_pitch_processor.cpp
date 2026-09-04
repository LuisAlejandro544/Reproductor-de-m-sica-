#include "time_pitch_processor.h"
#include <cmath>
#include <cstring>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

TimePitchProcessor::TimePitchProcessor(int32_t sampleRate, int32_t channelCount)
    : mSampleRate(sampleRate > 0 ? sampleRate : 44100),
      mChannels(channelCount > 0 ? channelCount : 2) {
    configure(mSampleRate, mChannels);
}

void TimePitchProcessor::configure(int32_t sampleRate, int32_t channelCount) {
    std::lock_guard<std::mutex> lock(mStateMutex);
    mSampleRate = sampleRate > 0 ? sampleRate : 44100;
    mChannels = channelCount > 0 ? channelCount : 2;

    initWindow();

    mOverlapBuffer.assign(SYNTHESIS_HOP * mChannels, 0.0f);
    mHasPreviousOverlap = false;

    // Buffer de entrada con margen suficiente para búsqueda WSOLA
    size_t ringCapacity = static_cast<size_t>((WINDOW_SIZE + 2 * MAX_DELTA) * 16 * mChannels);
    mInputRingBuffer.assign(ringCapacity, 0);
    mRingHead = 0;
    mRingTail = 0;
    mRingAvailable = 0;

    mOutputQueue.clear();
    mOutputQueue.reserve(4096 * mChannels);
    mQueueReadPos = 0;

    mResamplePhase = 0.0;
    mResampleBuffer.clear();
}

void TimePitchProcessor::initWindow() {
    mHanningWindow.resize(WINDOW_SIZE);
    for (int32_t i = 0; i < WINDOW_SIZE; ++i) {
        // Ventana Hanning simétrica normalizada
        mHanningWindow[i] = 0.5f * (1.0f - std::cos(static_cast<float>(2.0 * M_PI * i / (WINDOW_SIZE - 1))));
    }
}

void TimePitchProcessor::reset() {
    std::lock_guard<std::mutex> lock(mStateMutex);
    mOverlapBuffer.assign(SYNTHESIS_HOP * mChannels, 0.0f);
    mHasPreviousOverlap = false;

    mRingHead = 0;
    mRingTail = 0;
    mRingAvailable = 0;

    mOutputQueue.clear();
    mQueueReadPos = 0;
    mResamplePhase = 0.0;
    mResampleBuffer.clear();
}

void TimePitchProcessor::setSpeed(float speed) {
    // Rango de velocidad seguro: 0.5x hasta 2.0x
    float clamped = std::clamp(speed, 0.5f, 2.0f);
    mSpeed.store(clamped, std::memory_order_relaxed);
}

void TimePitchProcessor::setPitchSemitones(float semitones) {
    // Rango de semitonos: -6.0 a +6.0 semitonos
    float clamped = std::clamp(semitones, -6.0f, 6.0f);
    mPitchSemitones.store(clamped, std::memory_order_relaxed);
}

void TimePitchProcessor::setPreservePitch(bool preserve) {
    mPreservePitch.store(preserve, std::memory_order_relaxed);
}

void TimePitchProcessor::resetToDefault() {
    setSpeed(1.0f);
    setPitchSemitones(0.0f);
    setPreservePitch(true);
    reset();
}

int32_t TimePitchProcessor::process(
    int16_t* outputBuffer,
    int32_t framesToGenerate,
    const std::function<int32_t(int16_t* dest, int32_t count)>& readSourceFrames
) {
    if (!outputBuffer || framesToGenerate <= 0) return 0;

    float currentSpeed = mSpeed.load(std::memory_order_relaxed);
    float currentPitch = mPitchSemitones.load(std::memory_order_relaxed);
    bool preservePitch = mPreservePitch.load(std::memory_order_relaxed);

    std::lock_guard<std::mutex> lock(mStateMutex);

    // Si velocidad = 1.0x y afinación = 0 semitonos, bypass transparente sin colas
    bool isStandardSpeed = std::abs(currentSpeed - 1.0f) < 0.005f;
    bool isStandardPitch = std::abs(currentPitch) < 0.05f;

    if (isStandardSpeed && isStandardPitch && mOutputQueue.empty()) {
        mHasPreviousOverlap = false;
        return readSourceFrames(outputBuffer, framesToGenerate);
    }

    int32_t framesDelivered = 0;

    // Primero entregar lo que ya tengamos en la cola de salida
    while (framesDelivered < framesToGenerate && mQueueReadPos < mOutputQueue.size()) {
        size_t availableInQueue = (mOutputQueue.size() - mQueueReadPos) / mChannels;
        size_t toCopy = std::min<size_t>(availableInQueue, static_cast<size_t>(framesToGenerate - framesDelivered));

        std::memcpy(
            outputBuffer + framesDelivered * mChannels,
            mOutputQueue.data() + mQueueReadPos,
            toCopy * mChannels * sizeof(int16_t)
        );

        framesDelivered += static_cast<int32_t>(toCopy);
        mQueueReadPos += toCopy * mChannels;

        if (mQueueReadPos >= mOutputQueue.size()) {
            mOutputQueue.clear();
            mQueueReadPos = 0;
            break;
        }
    }

    // Si aún necesitamos fotogramas, sintetizamos más mediante WSOLA
    int maxIterations = 64;
    while (framesDelivered < framesToGenerate && maxIterations-- > 0) {
        processWsolaFrame(readSourceFrames);

        if (mQueueReadPos < mOutputQueue.size()) {
            size_t availableInQueue = (mOutputQueue.size() - mQueueReadPos) / mChannels;
            size_t toCopy = std::min<size_t>(availableInQueue, static_cast<size_t>(framesToGenerate - framesDelivered));

            std::memcpy(
                outputBuffer + framesDelivered * mChannels,
                mOutputQueue.data() + mQueueReadPos,
                toCopy * mChannels * sizeof(int16_t)
            );

            framesDelivered += static_cast<int32_t>(toCopy);
            mQueueReadPos += toCopy * mChannels;

            if (mQueueReadPos >= mOutputQueue.size()) {
                mOutputQueue.clear();
                mQueueReadPos = 0;
            }
        } else {
            // El lector de audio de origen ya no tiene más datos (fin de archivo)
            break;
        }
    }

    // Si no se pudieron entregar todos por fin de archivo, rellenar el remanente con silencio
    if (framesDelivered < framesToGenerate) {
        std::memset(
            outputBuffer + framesDelivered * mChannels,
            0,
            (framesToGenerate - framesDelivered) * mChannels * sizeof(int16_t)
        );
    }

    return framesDelivered;
}

void TimePitchProcessor::processWsolaFrame(
    const std::function<int32_t(int16_t* dest, int32_t count)>& readSourceFrames
) {
    float currentSpeed = mSpeed.load(std::memory_order_relaxed);
    float currentPitch = mPitchSemitones.load(std::memory_order_relaxed);
    bool preserve = mPreservePitch.load(std::memory_order_relaxed);

    float pitchRatio = 1.0f;
    if (std::abs(currentPitch) > 0.05f) {
        pitchRatio = std::pow(2.0f, currentPitch / 12.0f);
    }

    // Si se preserva el tono, la escala temporal del WSOLA absorbe la velocidad
    float wsolaSpeed = currentSpeed;
    if (preserve && std::abs(currentPitch) > 0.05f) {
        wsolaSpeed = currentSpeed * pitchRatio;
    }

    // Si no se preserva tono (modo cinta), no se ejecuta WSOLA y el cambio de pitch sigue la velocidad
    if (!preserve) {
        wsolaSpeed = 1.0f;
        pitchRatio = currentSpeed;
    }

    // Asegurar fotogramas suficientes en el buffer circular
    int32_t nominalHop = static_cast<int32_t>(std::round(SYNTHESIS_HOP * wsolaSpeed));
    if (nominalHop < 1) nominalHop = 1;

    int32_t neededFrames = WINDOW_SIZE + 2 * MAX_DELTA + nominalHop;

    while (static_cast<int32_t>(mRingAvailable / mChannels) < neededFrames) {
        int16_t tempRead[1024 * 2];
        int32_t toRead = std::min<int32_t>(1024, neededFrames - static_cast<int32_t>(mRingAvailable / mChannels));
        int32_t got = readSourceFrames(tempRead, toRead);
        if (got <= 0) {
            break; // No hay más datos en el origen
        }

        // Insertar en el buffer circular
        for (int32_t i = 0; i < got * mChannels; ++i) {
            mInputRingBuffer[mRingTail] = tempRead[i];
            mRingTail = (mRingTail + 1) % mInputRingBuffer.size();
        }
        mRingAvailable += got * mChannels;
    }

    int32_t availableFrames = static_cast<int32_t>(mRingAvailable / mChannels);
    if (availableFrames < WINDOW_SIZE) {
        return; // Esperar a tener al menos una ventana completa
    }

    // Búsqueda de máxima similitud (Waveform Similarity) sobre la forma de onda
    int32_t bestOffset = 0;
    if (mHasPreviousOverlap && availableFrames >= WINDOW_SIZE + MAX_DELTA) {
        int32_t searchMin = -std::min<int32_t>(MAX_DELTA, nominalHop / 2);
        int32_t searchMax = std::min<int32_t>(MAX_DELTA, availableFrames - WINDOW_SIZE - nominalHop);
        if (searchMax < searchMin) searchMax = searchMin;

        int64_t minSad = -1;
        // Evaluar cada 2 muestras para acelerar sin perder precisión de fase
        for (int32_t offset = searchMin; offset <= searchMax; offset += 2) {
            int64_t currentSad = 0;
            size_t sampleOffset = (mRingHead + (nominalHop + offset) * mChannels) % mInputRingBuffer.size();

            // Comparar primeros SYNTHESIS_HOP frames con la cola previa mOverlapBuffer
            for (int32_t i = 0; i < SYNTHESIS_HOP; i += 4) {
                for (int32_t ch = 0; ch < mChannels; ++ch) {
                    size_t bufIdx = (sampleOffset + i * mChannels + ch) % mInputRingBuffer.size();
                    int16_t s = mInputRingBuffer[bufIdx];
                    float prev = mOverlapBuffer[i * mChannels + ch];
                    int32_t diff = static_cast<int32_t>(s) - static_cast<int32_t>(prev);
                    currentSad += std::abs(diff);
                }
            }

            if (minSad < 0 || currentSad < minSad) {
                minSad = currentSad;
                bestOffset = offset;
            }
        }
    }

    int32_t selectedHop = nominalHop + bestOffset;
    if (selectedHop < 0) selectedHop = 0;

    size_t windowStart = (mRingHead + selectedHop * mChannels) % mInputRingBuffer.size();

    // Extraer y aplicar ventana Hanning y Overlap-Add
    std::vector<int16_t> wsolaBlock(SYNTHESIS_HOP * mChannels);

    for (int32_t i = 0; i < SYNTHESIS_HOP; ++i) {
        float win = mHanningWindow[i];
        for (int32_t ch = 0; ch < mChannels; ++ch) {
            size_t idx = (windowStart + i * mChannels + ch) % mInputRingBuffer.size();
            float sampleVal = static_cast<float>(mInputRingBuffer[idx]) * win;

            float combined = mHasPreviousOverlap ? (mOverlapBuffer[i * mChannels + ch] + sampleVal) : sampleVal;
            combined = std::clamp(combined, -32768.0f, 32767.0f);
            wsolaBlock[i * mChannels + ch] = static_cast<int16_t>(combined);
        }
    }

    // Actualizar búfer de overlap para la siguiente ventana
    for (int32_t i = 0; i < SYNTHESIS_HOP; ++i) {
        int32_t winIdx = i + SYNTHESIS_HOP;
        float win = (winIdx < WINDOW_SIZE) ? mHanningWindow[winIdx] : 0.0f;
        for (int32_t ch = 0; ch < mChannels; ++ch) {
            size_t idx = (windowStart + winIdx * mChannels + ch) % mInputRingBuffer.size();
            mOverlapBuffer[i * mChannels + ch] = static_cast<float>(mInputRingBuffer[idx]) * win;
        }
    }
    mHasPreviousOverlap = true;

    // Avanzar lectura en el búfer circular
    size_t consumedSamples = selectedHop * mChannels;
    mRingHead = (mRingHead + consumedSamples) % mInputRingBuffer.size();
    if (mRingAvailable >= consumedSamples) {
        mRingAvailable -= consumedSamples;
    } else {
        mRingAvailable = 0;
    }

    // Si se requiere Pitch Shift (afinación independiente): aplicar remuestreo
    if (std::abs(pitchRatio - 1.0f) > 0.01f) {
        double step = 1.0 / static_cast<double>(pitchRatio);
        for (double p = mResamplePhase; p < SYNTHESIS_HOP - 1.0; p += step) {
            int32_t idx0 = static_cast<int32_t>(p);
            int32_t idx1 = idx0 + 1;
            float frac = static_cast<float>(p - idx0);

            for (int32_t ch = 0; ch < mChannels; ++ch) {
                float s0 = static_cast<float>(wsolaBlock[idx0 * mChannels + ch]);
                float s1 = static_cast<float>(wsolaBlock[idx1 * mChannels + ch]);
                float interp = s0 + frac * (s1 - s0);
                interp = std::clamp(interp, -32768.0f, 32767.0f);
                mOutputQueue.push_back(static_cast<int16_t>(interp));
            }
        }
        mResamplePhase = std::fmod(mResamplePhase + (SYNTHESIS_HOP * step), 1.0);
    } else {
        // Encolar directamente el bloque de síntesis WSOLA
        mOutputQueue.insert(mOutputQueue.end(), wsolaBlock.begin(), wsolaBlock.end());
    }
}
