package com.example.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrackEntity
import com.example.playback.AudioEngineType
import com.example.ui.theme.*

/**
 * Barra superior del reproductor a pantalla completa:
 * Botón de colapsar a mini reproductor, indicador contextual de álbum y menú de opciones rápidas.
 */
@Composable
fun PlayerHeader(
    track: TrackEntity,
    activeEngine: AudioEngineType,
    showLyrics: Boolean,
    onCollapse: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenEditLyrics: () -> Unit,
    onEditArtwork: (TrackEntity) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSpatialAudio: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón Colapsar (mínimo 48dp para cumplimiento de accesibilidad)
        IconButton(
            onClick = onCollapse,
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_collapse")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cerrar reproductor",
                tint = TextPrimary
            )
        }

        // Título contextual del reproductor
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "REPRODUCIENDO",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = GreenAccent
            )
            Text(
                text = track.album,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Menú contextual con opciones avanzadas de la canción
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("full_player_menu")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opciones de la canción",
                    tint = TextPrimary
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("Cambiar carátula (Lossless WebP)", color = TextPrimary) },
                    leadingIcon = {
                        Icon(Icons.Default.Image, contentDescription = null, tint = GreenPrimary)
                    },
                    onClick = {
                        menuExpanded = false
                        onEditArtwork(track)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(if (showLyrics) "Ver carátula" else "Ver letras sincronizadas", color = TextPrimary)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.FormatQuote, contentDescription = null, tint = GreenPrimary)
                    },
                    onClick = {
                        menuExpanded = false
                        onToggleLyrics()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Editar letras (LRC)", color = TextPrimary) },
                    leadingIcon = {
                        Icon(Icons.Default.EditNote, contentDescription = null, tint = GreenPrimary)
                    },
                    onClick = {
                        menuExpanded = false
                        onOpenEditLyrics()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Ecualizador 10 Bandas (C++)", color = TextPrimary) },
                    leadingIcon = {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = GreenPrimary)
                    },
                    onClick = {
                        menuExpanded = false
                        onOpenEqualizer()
                    }
                )
                if (activeEngine == AudioEngineType.OBOE_CPP) {
                    DropdownMenuItem(
                        text = { Text("Audio Espacial 360° / 8D C++", color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.SurroundSound, contentDescription = null, tint = GreenPrimary)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenSpatialAudio()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Temporizador de apagado", color = TextPrimary) },
                    leadingIcon = {
                        Icon(Icons.Default.Bedtime, contentDescription = null, tint = GreenPrimary)
                    },
                    onClick = {
                        menuExpanded = false
                        onOpenSleepTimer()
                    }
                )
                HorizontalDivider(color = DarkSurfaceVariant)
                DropdownMenuItem(
                    text = { Text("Eliminar de la biblioteca", color = Color(0xFFEF5350)) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350))
                    },
                    onClick = {
                        menuExpanded = false
                        onDeleteTrack(track)
                    }
                )
            }
        }
    }
}
