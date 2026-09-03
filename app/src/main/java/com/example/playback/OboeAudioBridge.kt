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

    // Ecualizador Paramétrico de 10 Bandas (C++)
    external fun nativeSetEqualizerEnabled(enabled: Boolean)
    external fun nativeIsEqualizerEnabled(): Boolean
    external fun nativeSetEqualizerBandGain(bandIndex: Int, gainDb: Float)
    external fun nativeGetEqualizerBandGain(bandIndex: Int): Float
    external fun nativeResetEqualizer()

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
