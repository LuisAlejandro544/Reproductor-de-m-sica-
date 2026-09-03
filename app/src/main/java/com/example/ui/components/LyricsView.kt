package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.LyricsParser

@Composable
fun LyricsView(
    lyricsText: String?,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onOpenEditLyrics: () -> Unit,
    onToggleBackToCover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedLyrics = remember(lyricsText) {
        LyricsParser.parse(lyricsText)
    }

    val activeLineIndex by remember(parsedLyrics.lines, currentPositionMs) {
        derivedStateOf {
            if (parsedLyrics.isSynced) {
                LyricsParser.findActiveLineIndex(parsedLyrics.lines, currentPositionMs)
            } else {
                -1
            }
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll fluido que centra la línea activa
    LaunchedEffect(activeLineIndex) {
        if (parsedLyrics.isSynced && activeLineIndex >= 0 && activeLineIndex < parsedLyrics.lines.size) {
            val targetScroll = maxOf(0, activeLineIndex - 2)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("lyrics_view_container"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Contenido de las letras
            if (parsedLyrics.lines.isEmpty()) {
                // Estado vacío: Sin letras
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(DarkSurfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = GreenAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Sin Letras Registradas",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Añade o pega letras en texto plano o con marcas de tiempo .LRC sincronizadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onOpenEditLyrics,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("btn_empty_add_lyrics")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Añadir Letras",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            } else {
                // Lista de líneas de letras
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 58.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(parsedLyrics.lines) { index, line ->
                        val isActive = index == activeLineIndex

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = parsedLyrics.isSynced) {
                                    if (parsedLyrics.isSynced && line.timeMs >= 0) {
                                        onSeekTo(line.timeMs)
                                    }
                                }
                                .background(
                                    if (isActive) GreenPrimary.copy(alpha = 0.16f) else Color.Transparent
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = if (isActive) 19.sp else 16.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    lineHeight = if (isActive) 26.sp else 22.sp
                                ),
                                color = when {
                                    isActive -> GreenAccent
                                    parsedLyrics.isSynced -> TextTertiary
                                    else -> TextPrimary
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Gradientes decorativos superior e inferior para desvanecimiento suave
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(DarkSurface, Color.Transparent)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, DarkSurface)
                        )
                    )
            )

            // Barra superior fija con estado y botones táctiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chip indicador de modo
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceVariant.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (parsedLyrics.isSynced) Icons.Default.Timer else Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = if (parsedLyrics.isSynced) GreenAccent else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (parsedLyrics.isSynced) "Letras Sincronizadas" else "Letras",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (parsedLyrics.isSynced) GreenAccent else TextSecondary
                        )
                    }
                }

                // Acciones rápidas (Editar letras y Volver a carátula)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenEditLyrics,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_lyrics_edit")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar o pegar letras",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleBackToCover,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_lyrics_back_to_cover")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = "Ver carátula del álbum",
                            tint = GreenAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
