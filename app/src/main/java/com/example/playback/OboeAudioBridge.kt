package com.example.playback

import android.util.Log

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

    // Puente al módulo Rust (preparado para futuras integraciones)
    external fun nativeGetRustVersion(): Int
}
