package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrackEntity
import com.example.playback.AudioEngineType
import com.example.playback.PlaybackRepeatMode
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.SliderInactive
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
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
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier,
    onEditArtwork: (TrackEntity) -> Unit = {}
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }
    var menuExpanded by remember { mutableStateOf(false) }

    val effectivePosition = if (isDraggingSlider) {
        (sliderDragPosition * durationMs.toFloat()).toLong()
    } else {
        currentPositionMs
    }

    val sliderProgress = if (durationMs > 0) {
        if (isDraggingSlider) sliderDragPosition else (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.93f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "artwork_scale"
    )

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
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header: Collapse Button + Context Title + Menu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("full_player_collapse")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimizar reproductor",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "REPRODUCIENDO DESDE ARCHIVO LOCAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontSize = 10.sp
                            ),
                            color = TextSecondary
                        )
                        Text(
                            text = track.album.ifBlank { "Ritmo Player" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("full_player_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones",
                                tint = TextPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            // Opción de cambio de carátula con conversión a WebP sin pérdida
                            DropdownMenuItem(
                                text = { Text("Cambiar carátula (WebP)", color = TextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = GreenAccent
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEditArtwork(track)
                                }
                            )
                            // Opción de ecualizador de 10 bandas (C++ DSP en Oboe y Media3)
                            DropdownMenuItem(
                                text = { Text("Ecualizador 10 Bandas (C++)", color = TextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = if (isEqualizerEnabled) GreenAccent else TextSecondary
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpenEqualizer()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar de la biblioteca", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteTrack(track)
                                    onCollapse()
                                }
                            )
                        }
                    }
                }

                // Big Album Artwork with smooth breathing spring scale and direct artwork edit action
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .scale(artworkScale),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        AlbumArtView(
                            artworkPath = track.artworkPath,
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(24.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp)),
                            cornerRadius = 16.dp,
                            iconSize = 80.dp
                        )

                        // Botón táctil para cambiar o poner carátula (mínimo 48dp)
                        IconButton(
                            onClick = { onEditArtwork(track) },
                            modifier = Modifier
                                .padding(12.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(DarkSurface.copy(alpha = 0.85f))
                                .testTag("full_player_change_artwork")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Cambiar o asignar carátula en formato WebP",
                                tint = GreenAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Track Title & Artist Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp
                        ),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Seeker Slider & Timestamps
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = sliderProgress,
                        onValueChange = { newValue ->
                            isDraggingSlider = true
                            sliderDragPosition = newValue
                        },
                        onValueChangeFinished = {
                            val targetMs = (sliderDragPosition * durationMs.toFloat()).toLong()
                            onSeekTo(targetMs)
                            isDraggingSlider = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = TextPrimary,
                            activeTrackColor = GreenPrimary,
                            inactiveTrackColor = SliderInactive
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("full_player_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(effectivePosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                        Text(
                            text = FormatUtils.formatDuration(durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Playback Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button
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

                    // Previous Button
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

                    // Main Play / Pause Circular Button with smooth animated icon transition
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

                    // Next Button
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

                    // Repeat Button
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

                // Quick seek & Ecualizador C++ (SOLO SI EL MOTOR ES OBOE_CPP)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Spacer(modifier = Modifier.width(20.dp))
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
                    Spacer(modifier = Modifier.width(20.dp))

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
        }
    }
}
