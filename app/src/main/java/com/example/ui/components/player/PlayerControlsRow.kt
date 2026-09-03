package com.example.ui.components.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.playback.PlaybackRepeatMode
import com.example.ui.theme.*

/**
 * Controles principales de transporte de reproducción:
 * Botón de aleatorio, pista anterior, botón circular destacado de reproducción/pausa (64dp),
 * pista siguiente y modo de repetición.
 */
@Composable
fun PlayerControlsRow(
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeatMode: PlaybackRepeatMode,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón Shuffle
        IconButton(
            onClick = onToggleShuffle,
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_shuffle")
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Modo aleatorio",
                tint = if (isShuffle) GreenPrimary else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Botón Canción anterior
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_prev")
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Canción anterior",
                tint = TextPrimary,
                modifier = Modifier.size(38.dp)
            )
        }

        // Botón circular principal Play / Pause con animación fluida de icono
        Surface(
            onClick = onPlayPause,
            shape = CircleShape,
            color = GreenPrimary,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(64.dp)
                .testTag("full_player_play_pause")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        (scaleIn(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220))) togetherWith
                        (scaleOut(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)))
                    },
                    label = "full_play_pause_anim"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pausar" else "Reproducir",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Botón Siguiente canción
        IconButton(
            onClick = onNext,
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_next")
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Siguiente canción",
                tint = TextPrimary,
                modifier = Modifier.size(38.dp)
            )
        }

        // Botón Repetir
        IconButton(
            onClick = onToggleRepeat,
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_repeat")
        ) {
            val icon = if (repeatMode == PlaybackRepeatMode.ONE) {
                Icons.Default.RepeatOne
            } else {
                Icons.Default.Repeat
            }
            Icon(
                imageVector = icon,
                contentDescription = "Repetir",
                tint = if (repeatMode != PlaybackRepeatMode.OFF) GreenPrimary else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
