package com.example.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.playback.AudioEngineType
import com.example.playback.SleepTimerStatus
import com.example.ui.theme.*

/**
 * Fila inferior de acciones rápidas de audio audiófilo:
 * Salto -10s, acceso directo al Ecualizador C++, Audio Espacial 360° / 8D,
 * Temporizador de Sueño, alternador de Letras y salto +10s.
 */
@Composable
fun PlayerBottomQuickActions(
    currentPositionMs: Long,
    durationMs: Long,
    activeEngine: AudioEngineType,
    isEqualizerEnabled: Boolean,
    isSpatialAudioEnabled: Boolean,
    sleepTimerStatus: SleepTimerStatus,
    showLyrics: Boolean,
    onSeekTo: (Long) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSpatialAudio: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Retroceder 10 segundos
        IconButton(
            onClick = { onSeekTo((currentPositionMs - 10000L).coerceAtLeast(0L)) },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = "Retroceder 10 segundos",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Botón de Ecualizador: Activo para ambos motores (C++ DSP en Oboe y Media3)
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(
            onClick = onOpenEqualizer,
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_equalizer_button")
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Ecualizador de 10 Bandas C++",
                tint = if (isEqualizerEnabled) GreenAccent else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Botón de Audio Espacial 360° / 8D C++ (SOLO SI EL MOTOR ES OBOE_CPP)
        if (activeEngine == AudioEngineType.OBOE_CPP) {
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onOpenSpatialAudio,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("full_player_spatial_audio_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SurroundSound,
                    contentDescription = "Audio Espacial 360° (8D C++)",
                    tint = if (isSpatialAudioEnabled) GreenAccent else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Botón de Temporizador de Sueño
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(
            onClick = onOpenSleepTimer,
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_sleep_timer_button")
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "Temporizador de Sueño",
                tint = if (sleepTimerStatus.isActive) GreenAccent else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Botón de Letras (Lyrics)
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(
            onClick = onToggleLyrics,
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_lyrics_toggle_btn")
        ) {
            Icon(
                imageVector = Icons.Default.FormatQuote,
                contentDescription = if (showLyrics) "Ver carátula" else "Ver letras de la canción",
                tint = if (showLyrics) GreenAccent else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Avanzar 10 segundos
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(
            onClick = { onSeekTo((currentPositionMs + 10000L).coerceAtMost(durationMs)) },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Forward10,
                contentDescription = "Avanzar 10 segundos",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
