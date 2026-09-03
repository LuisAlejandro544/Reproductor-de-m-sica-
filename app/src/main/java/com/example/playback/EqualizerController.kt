package com.example.playback

import android.content.Context
import android.content.SharedPreferences
import com.example.debug.DebugLogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Controlador modular para el ecualizador DSP de 10 bandas de Ritmo.
 *
 * Administra las ganancias en dB (-12 dB a +12 dB) para las frecuencias:
 * 31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz y 16kHz.
 *
 * Persiste la configuración en [SharedPreferences] y sincroniza las ganancias
 * con Oboe C++ y el AudioProcessor de Media3.
 */
class EqualizerController(
    private val context: Context,
    private val onSyncNativeGains: (enabled: Boolean, gains: List<Float>) -> Unit
) {
    companion object {
        private const val PREFS_NAME = "ritmo_audio_prefs"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_BAND_PREFIX = "eq_band_"
    }

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isEqualizerEnabled = MutableStateFlow(sharedPrefs.getBoolean(KEY_EQ_ENABLED, false))
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _equalizerBandGains = MutableStateFlow(loadSavedEqGains())
    val equalizerBandGains: StateFlow<List<Float>> = _equalizerBandGains.asStateFlow()

    private fun loadSavedEqGains(): List<Float> {
        return List(EqualizerDefaults.NUM_BANDS) { index ->
            sharedPrefs.getFloat(KEY_EQ_BAND_PREFIX + index, 0.0f)
        }
    }

    fun initialize() {
        try {
            onSyncNativeGains(_isEqualizerEnabled.value, _equalizerBandGains.value)
            Timber.d("EqualizerController inicializado (Habilitado: ${_isEqualizerEnabled.value})")
        } catch (t: Throwable) {
            DebugLogManager.logError(
                tag = "EqualizerController",
                message = "Error inicializando ecualizador",
                throwable = t
            )
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        sharedPrefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
        onSyncNativeGains(enabled, _equalizerBandGains.value)
        DebugLogManager.log(
            tag = "EqualizerController",
            message = "Ecualizador DSP 10 bandas ${if (enabled) "ACTIVADO" else "DESACTIVADO"}"
        )
    }

    fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0 until EqualizerDefaults.NUM_BANDS) return
        val current = _equalizerBandGains.value.toMutableList()
        current[bandIndex] = gainDb.coerceIn(EqualizerDefaults.MIN_GAIN_DB, EqualizerDefaults.MAX_GAIN_DB)
        _equalizerBandGains.value = current
        sharedPrefs.edit().putFloat(KEY_EQ_BAND_PREFIX + bandIndex, current[bandIndex]).apply()
        onSyncNativeGains(_isEqualizerEnabled.value, current)
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        val gains = preset.gains.take(EqualizerDefaults.NUM_BANDS)
        _equalizerBandGains.value = gains
        val editor = sharedPrefs.edit()
        gains.forEachIndexed { index, gain ->
            editor.putFloat(KEY_EQ_BAND_PREFIX + index, gain)
        }
        editor.apply()
        onSyncNativeGains(_isEqualizerEnabled.value, gains)
        DebugLogManager.log(
            tag = "EqualizerController",
            message = "Preset aplicado: ${preset.name}"
        )
    }

    fun resetEqualizer() {
        val flat = EqualizerDefaults.PRESETS.first { it.id == "flat" }
        setEqualizerPreset(flat)
    }

    fun syncWithNative() {
        onSyncNativeGains(_isEqualizerEnabled.value, _equalizerBandGains.value)
    }
}
