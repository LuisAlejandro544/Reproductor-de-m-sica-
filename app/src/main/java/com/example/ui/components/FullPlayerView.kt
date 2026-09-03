package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.TrackEntity
import com.example.playback.AudioEngineType
import com.example.playback.PlaybackRepeatMode
import com.example.playback.SleepTimerStatus
import com.example.ui.components.player.*
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface

/**
 * Vista de reproductor audiófilo a pantalla completa (FullPlayerView).
 * Diseñado modularmente orquestando componentes desacoplados de alta fidelidad táctil (48dp):
 * - [PlayerHeader]: Barra superior con colapso, título de contexto y menú de opciones avanzadas.
 * - [PlayerCenterDisplay]: Alternancia fluida entre carátula (Lossless WebP) y visor de letras sincronizadas (LRC).
 * - [PlayerTrackDetails]: Información de pista y acciones directas (Like, Favorito, Playlist).
 * - [PlayerProgressBar]: Control deslizante continuo con marcas de tiempo formateadas.
 * - [PlayerControlsRow]: Controles principales de transporte (Shuffle, Prev, Play/Pause 64dp, Next, Repeat).
 * - [PlayerBottomQuickActions]: Acciones rápidas (Salto -10s, Ecualizador 10-Bandas C++, Audio 8D, Sueño, +10s).
 */
@Composable
fun FullPlayerView(
    track: TrackEntity,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isShuffle: Boolean,
    repeatMode: PlaybackRepeatMode,
    activeEngine: AudioEngineType,
    isEqualizerEnabled: Boolean = false,
    onOpenEqualizer: () -> Unit = {},
    isSpatialAudioEnabled: Boolean = false,
    onOpenSpatialAudio: () -> Unit = {},
    sleepTimerStatus: SleepTimerStatus = SleepTimerStatus(),
    onOpenSleepTimer: () -> Unit = {},
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier,
    onEditArtwork: (TrackEntity) -> Unit = {},
    onToggleLiked: (TrackEntity) -> Unit = {},
    onToggleFavorite: (TrackEntity) -> Unit = {},
    onAddToPlaylist: (TrackEntity) -> Unit = {},
    onUpdateLyrics: (TrackEntity, String?) -> Unit = { _, _ -> }
) {
    var showLyrics by remember { mutableStateOf(false) }
    var showEditLyricsDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF142C1E),
                            DarkSurface,
                            DarkBackground
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Barra Superior Modular
                PlayerHeader(
                    track = track,
                    activeEngine = activeEngine,
                    showLyrics = showLyrics,
                    onCollapse = onCollapse,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onOpenEditLyrics = { showEditLyricsDialog = true },
                    onEditArtwork = onEditArtwork,
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenSpatialAudio = onOpenSpatialAudio,
                    onOpenSleepTimer = onOpenSleepTimer,
                    onDeleteTrack = onDeleteTrack
                )

                // Área Central Modular (Carátula interactiva o Letras sincronizadas)
                PlayerCenterDisplay(
                    track = track,
                    isPlaying = isPlaying,
                    showLyrics = showLyrics,
                    currentPositionMs = currentPositionMs,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onEditArtwork = onEditArtwork,
                    onOpenEditLyrics = { showEditLyricsDialog = true },
                    onSeekTo = onSeekTo,
                    modifier = Modifier.weight(1f)
                )

                // Bloque Inferior de Controles
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Fila de información de la pista y acciones favoritas
                    PlayerTrackDetails(
                        track = track,
                        onToggleLiked = onToggleLiked,
                        onToggleFavorite = onToggleFavorite,
                        onAddToPlaylist = onAddToPlaylist
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Barra de progreso y marcas de tiempo
                    PlayerProgressBar(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        onSeekTo = onSeekTo
                    )

                    // Controles principales de transporte
                    PlayerControlsRow(
                        isPlaying = isPlaying,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat
                    )

                    // Acciones rápidas audiófilas (DSP C++, Sueño, Letras)
                    PlayerBottomQuickActions(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        activeEngine = activeEngine,
                        isEqualizerEnabled = isEqualizerEnabled,
                        isSpatialAudioEnabled = isSpatialAudioEnabled,
                        sleepTimerStatus = sleepTimerStatus,
                        showLyrics = showLyrics,
                        onSeekTo = onSeekTo,
                        onOpenEqualizer = onOpenEqualizer,
                        onOpenSpatialAudio = onOpenSpatialAudio,
                        onOpenSleepTimer = onOpenSleepTimer,
                        onToggleLyrics = { showLyrics = !showLyrics }
                    )
                }
            }
        }

        // Diálogo de edición de letras LRC
        if (showEditLyricsDialog) {
            EditLyricsDialog(
                track = track,
                currentLyrics = track.lyrics,
                onDismiss = { showEditLyricsDialog = false },
                onSaveLyrics = { newLyrics ->
                    onUpdateLyrics(track, newLyrics)
                }
            )
        }
    }
}
