package com.example.ui.components.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.TrackEntity
import com.example.ui.components.AlbumArtView
import com.example.ui.components.LyricsView
import com.example.ui.theme.*

/**
 * Área central del reproductor a pantalla completa:
 * Alterna suavemente entre la carátula audiófila (con animación de escala y accesos rápidos de 48dp)
 * y el componente visor de letras sincronizadas (LyricsView).
 */
@Composable
fun PlayerCenterDisplay(
    track: TrackEntity,
    isPlaying: Boolean,
    showLyrics: Boolean,
    currentPositionMs: Long,
    onToggleLyrics: () -> Unit,
    onEditArtwork: (TrackEntity) -> Unit,
    onOpenEditLyrics: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.90f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "full_player_art_scale"
    )

    AnimatedContent(
        targetState = showLyrics,
        transitionSpec = {
            fadeIn(animationSpec = tween(280)) togetherWith fadeOut(animationSpec = tween(240))
        },
        label = "lyrics_art_switcher",
        modifier = modifier
    ) { displayingLyrics ->
        if (displayingLyrics) {
            LyricsView(
                lyricsText = track.lyrics,
                currentPositionMs = currentPositionMs,
                onSeekTo = onSeekTo,
                onOpenEditLyrics = onOpenEditLyrics,
                onToggleBackToCover = onToggleLyrics,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(vertical = 12.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1f)
                        .scale(artworkScale)
                        .shadow(
                            elevation = 28.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = GreenPrimary.copy(alpha = 0.45f)
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onToggleLyrics() },
                    contentAlignment = Alignment.Center
                ) {
                    AlbumArtView(
                        artworkPath = track.artworkPath,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 24.dp,
                        iconSize = 72.dp
                    )

                    // Indicador flotante táctil (48dp) para alternar a letras sincronizadas
                    Surface(
                        onClick = onToggleLyrics,
                        shape = CircleShape,
                        color = DarkBackground.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .size(48.dp)
                            .testTag("full_player_lyrics_badge")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = "Ver letras",
                                tint = GreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Botón flotante táctil (48dp) para cambiar carátula WebP
                    Surface(
                        onClick = { onEditArtwork(track) },
                        shape = CircleShape,
                        color = DarkBackground.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                            .size(48.dp)
                            .testTag("full_player_edit_artwork_badge")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Cambiar carátula WebP",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
