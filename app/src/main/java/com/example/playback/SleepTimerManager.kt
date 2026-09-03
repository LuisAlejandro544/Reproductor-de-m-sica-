package com.example.playback

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado reactivo del Temporizador de Sueño (Sleep Timer).
 */
data class SleepTimerStatus(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val isEndOfTrack: Boolean = false
) {
    val formattedRemaining: String
        get() {
            if (!isActive) return ""
            if (isEndOfTrack) return "Fin de pista"
            val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

/**
 * Administrador del Temporizador de Sueño para Ritmo Music Player.
 *
 * Características audiófilas:
 * - Cuenta regresiva en tiempo real con Coroutines.
 * - Desvanecimiento gradual de volumen (Fade-out suave de 15 segundos) antes de pausar
 *   para no despertar al oyente con un corte abrupto.
 * - Modo "Fin de canción actual" para completar la reproducción antes de apagar.
 * - Adición rápida de tiempo (+5 min) táctil con un solo toque.
 * - Restauración automática del volumen original tras pausar.
 */
class SleepTimerManager(
    private val onPausePlayback: () -> Unit,
    private val onSetVolume: (Float) -> Unit,
    private val onGetVolume: () -> Float
) {
    companion object {
        private const val TAG = "SleepTimerManager"
        private const val FADE_OUT_DURATION_MS = 15000L // 15 segundos de fade-out suave
    }

    private val timerScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    private val _status = MutableStateFlow(SleepTimerStatus())
    val status: StateFlow<SleepTimerStatus> = _status.asStateFlow()

    private var originalVolume: Float = 1.0f

    /**
     * Inicia el temporizador de sueño para una duración específica en minutos.
     */
    fun startTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelTimer()
            return
        }
        val durationMs = minutes * 60 * 1000L
        startTimerWithDuration(durationMs, isEndOfTrack = false)
    }

    /**
     * Inicia el temporizador de sueño en modo "Fin de la pista actual".
     * Se pausará automáticamente cuando la canción actual termine.
     */
    fun startEndOfTrackTimer() {
        cancelTimer()
        originalVolume = onGetVolume()
        _status.value = SleepTimerStatus(
            isActive = true,
            remainingMs = 0L,
            totalMs = 0L,
            isEndOfTrack = true
        )
        Log.i(TAG, "Temporizador de sueño activado en modo: Fin de pista actual")
    }

    /**
     * Añade minutos adicionales al temporizador activo.
     */
    fun addMinutes(extraMinutes: Int) {
        val current = _status.value
        if (!current.isActive) {
            startTimer(extraMinutes)
            return
        }
        if (current.isEndOfTrack) {
            // Cambiar de fin de pista a temporizador por minutos
            startTimer(extraMinutes)
            return
        }
        val extraMs = extraMinutes * 60 * 1000L
        val newRemaining = current.remainingMs + extraMs
        val newTotal = current.totalMs + extraMs
        startTimerWithDuration(newRemaining, isEndOfTrack = false, totalMs = newTotal)
    }

    /**
     * Cancela el temporizador de sueño y restaura el volumen original.
     */
    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        if (_status.value.isActive) {
            onSetVolume(originalVolume)
        }
        _status.value = SleepTimerStatus(isActive = false)
        Log.i(TAG, "Temporizador de sueño cancelado y volumen restaurado.")
    }

    /**
     * Notificación cuando una canción termina de reproducirse.
     * Si el modo "Fin de pista" está activo, ejecuta la pausa.
     */
    fun onTrackFinished() {
        if (_status.value.isActive && _status.value.isEndOfTrack) {
            Log.i(TAG, "Pista finalizada. Ejecutando apagado programado por temporizador de sueño.")
            executeSleepShutdown()
        }
    }

    private fun startTimerWithDuration(durationMs: Long, isEndOfTrack: Boolean, totalMs: Long = durationMs) {
        timerJob?.cancel()
        originalVolume = onGetVolume().takeIf { it > 0.05f } ?: 1.0f

        _status.value = SleepTimerStatus(
            isActive = true,
            remainingMs = durationMs,
            totalMs = totalMs,
            isEndOfTrack = isEndOfTrack
        )

        timerJob = timerScope.launch {
            val startTime = System.currentTimeMillis()
            val endTime = startTime + durationMs

            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = (endTime - now).coerceAtLeast(0L)

                _status.value = _status.value.copy(
                    remainingMs = remaining
                )

                // Desvanecimiento suave (fade out) durante los últimos 15 segundos
                if (remaining in 1..FADE_OUT_DURATION_MS) {
                    val progress = remaining.toFloat() / FADE_OUT_DURATION_MS.toFloat()
                    val fadedVolume = originalVolume * progress
                    onSetVolume(fadedVolume)
                }

                if (remaining <= 0L) {
                    executeSleepShutdown()
                    break
                }

                delay(500)
            }
        }
    }

    private fun executeSleepShutdown() {
        timerJob?.cancel()
        timerJob = null
        _status.value = SleepTimerStatus(isActive = false)

        try {
            onPausePlayback()
        } catch (e: Exception) {
            Log.e(TAG, "Error al pausar la reproducción en SleepTimer", e)
        }

        // Restaurar volumen normal para la próxima sesión
        onSetVolume(originalVolume)
        Log.i(TAG, "Temporizador de sueño cumplido: Reproducción pausada y volumen normal restablecido.")
    }
}
