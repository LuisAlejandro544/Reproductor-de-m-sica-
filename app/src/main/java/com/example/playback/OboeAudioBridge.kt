package com.example.playback

import android.util.Log
import java.nio.ByteBuffer

object OboeAudioBridge {
    private const val TAG = "OboeAudioBridge"
    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("ritmo_native")
            isLibraryLoaded = true
            Log.i(TAG, "Native library 'ritmo_native' loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library 'ritmo_native' not yet loaded or not present on this architecture: ${e.message}")
            isLibraryLoaded = false
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected error loading native library", t)
            isLibraryLoaded = false
        }
    }

    fun isNativeReady(): Boolean = isLibraryLoaded

    external fun nativeInit(): Boolean
    external fun nativeRelease()
    external fun nativeLoadFile(path: String): Boolean
    external fun nativePlay(): Boolean
    external fun nativePause(): Boolean
    external fun nativeStop(): Boolean
    external fun nativeSeekTo(positionMs: Long): Boolean
    external fun nativeGetPosition(): Long
    external fun nativeGetDuration(): Long
    external fun nativeIsPlaying(): Boolean
    external fun nativeIsPlaybackEnded(): Boolean
    external fun nativeSetVolume(volume: Float)
    external fun nativeGetVolume(): Float

    // Ecualizador Paramétrico de 10 Bandas (C++)
    external fun nativeSetEqualizerEnabled(enabled: Boolean)
    external fun nativeIsEqualizerEnabled(): Boolean
    external fun nativeSetEqualizerBandGain(bandIndex: Int, gainDb: Float)
    external fun nativeGetEqualizerBandGain(bandIndex: Int): Float
    external fun nativeResetEqualizer()

    // Audio Espacial 360° / Efecto 8D Nativo en C++ (Oboe Exclusivo)
    external fun nativeSetSpatialAudioEnabled(enabled: Boolean)
    external fun nativeIsSpatialAudioEnabled(): Boolean
    external fun nativeSetSpatialAudioSpeed(speedHz: Float)
    external fun nativeGetSpatialAudioSpeed(): Float
    external fun nativeSetSpatialAudioDepth(depth: Float)
    external fun nativeGetSpatialAudioDepth(): Float
    external fun nativeSetSpatialAudioReverb(reverb: Float)
    external fun nativeGetSpatialAudioReverb(): Float

    fun setSpatialAudioEnabledSafe(enabled: Boolean) {
        if (isLibraryLoaded) {
            try { nativeSetSpatialAudioEnabled(enabled) } catch (_: Throwable) {}
        }
    }

    fun isSpatialAudioEnabledSafe(): Boolean = if (isLibraryLoaded) {
        try { nativeIsSpatialAudioEnabled() } catch (_: Throwable) { false }
    } else false

    fun setSpatialAudioSpeedSafe(speedHz: Float) {
        if (isLibraryLoaded) {
            try { nativeSetSpatialAudioSpeed(speedHz) } catch (_: Throwable) {}
        }
    }

    fun getSpatialAudioSpeedSafe(): Float = if (isLibraryLoaded) {
        try { nativeGetSpatialAudioSpeed() } catch (_: Throwable) { 0.08f }
    } else 0.08f

    fun setSpatialAudioDepthSafe(depth: Float) {
        if (isLibraryLoaded) {
            try { nativeSetSpatialAudioDepth(depth) } catch (_: Throwable) {}
        }
    }

    fun getSpatialAudioDepthSafe(): Float = if (isLibraryLoaded) {
        try { nativeGetSpatialAudioDepth() } catch (_: Throwable) { 0.85f }
    } else 0.85f

    fun setSpatialAudioReverbSafe(reverb: Float) {
        if (isLibraryLoaded) {
            try { nativeSetSpatialAudioReverb(reverb) } catch (_: Throwable) {}
        }
    }

    fun getSpatialAudioReverbSafe(): Float = if (isLibraryLoaded) {
        try { nativeGetSpatialAudioReverb() } catch (_: Throwable) { 0.22f }
    } else 0.22f

    // Control de Velocidad y Afinación / Tono Independiente (C++ Oboe Exclusivo)
    external fun nativeSetPlaybackSpeed(speed: Float)
    external fun nativeGetPlaybackSpeed(): Float
    external fun nativeSetPitchSemitones(semitones: Float)
    external fun nativeGetPitchSemitones(): Float
    external fun nativeSetPitchPreservationEnabled(enabled: Boolean)
    external fun nativeIsPitchPreservationEnabled(): Boolean
    external fun nativeResetSpeedAndPitch()

    fun setPlaybackSpeedSafe(speed: Float) {
        if (isLibraryLoaded) {
            try { nativeSetPlaybackSpeed(speed) } catch (_: Throwable) {}
        }
    }

    fun getPlaybackSpeedSafe(): Float = if (isLibraryLoaded) {
        try { nativeGetPlaybackSpeed() } catch (_: Throwable) { 1.0f }
    } else 1.0f

    fun setPitchSemitonesSafe(semitones: Float) {
        if (isLibraryLoaded) {
            try { nativeSetPitchSemitones(semitones) } catch (_: Throwable) {}
        }
    }

    fun getPitchSemitonesSafe(): Float = if (isLibraryLoaded) {
        try { nativeGetPitchSemitones() } catch (_: Throwable) { 0.0f }
    } else 0.0f

    fun setPitchPreservationEnabledSafe(enabled: Boolean) {
        if (isLibraryLoaded) {
            try { nativeSetPitchPreservationEnabled(enabled) } catch (_: Throwable) {}
        }
    }

    fun isPitchPreservationEnabledSafe(): Boolean = if (isLibraryLoaded) {
        try { nativeIsPitchPreservationEnabled() } catch (_: Throwable) { true }
    } else true

    fun resetSpeedAndPitchSafe() {
        if (isLibraryLoaded) {
            try { nativeResetSpeedAndPitch() } catch (_: Throwable) {}
        }
    }

    // Procesamiento PCM para Media3 / ExoPlayer (Filtros Biquad IIR en C++)
    external fun nativeMedia3ProcessDirect(
        byteBuffer: ByteBuffer,
        offsetBytes: Int,
        lengthBytes: Int,
        sampleRate: Int,
        channelCount: Int
    )

    external fun nativeMedia3ProcessArray(
        pcmArray: ShortArray,
        offsetSamples: Int,
        numSamples: Int,
        sampleRate: Int,
        channelCount: Int
    )

    // Puente al módulo Rust
    external fun nativeGetRustVersion(): Int

    // Diagnósticos y Códigos de Error Crudos (Depuración NDK)
    external fun nativeGetLastErrorCode(): Int
    external fun nativeGetLastErrorString(): String
    external fun nativeGetAudioDeviceInfo(): String
    external fun nativeGetStreamStatsJson(): String

    fun getLastErrorCodeSafe(): Int = if (isLibraryLoaded) {
        try { nativeGetLastErrorCode() } catch (_: Throwable) { -999 }
    } else -998

    fun getLastErrorStringSafe(): String = if (isLibraryLoaded) {
        try { nativeGetLastErrorString() } catch (t: Throwable) { "Error JNI: ${t.message}" }
    } else "Biblioteca nativa no cargada"

    fun getAudioDeviceInfoSafe(): String = if (isLibraryLoaded) {
        try { nativeGetAudioDeviceInfo() } catch (t: Throwable) { "Error JNI: ${t.message}" }
    } else "N/A (Nativo no listo)"

    fun getStreamStatsJsonSafe(): String = if (isLibraryLoaded) {
        try { nativeGetStreamStatsJson() } catch (t: Throwable) { "{}" }
    } else "{}"
}
