package com.example.ui.main

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.ui.PlaylistDetailTarget
import com.example.ui.components.AlbumArtView
import com.example.ui.components.TrackOptionsBottomSheet
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.Flow

/**
 * Vista detallada de una playlist (automática o personalizada).
 * Proporciona reproducción secuencial o aleatoria, gestión de pistas
 * y acciones contextuales para cada canción.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailView(
    target: PlaylistDetailTarget,
    likedTracksFlow: Flow<List<TrackEntity>>,
    favoriteTracksFlow: Flow<List<TrackEntity>>,
    getTracksForPlaylist: (Long) -> Flow<List<TrackEntity>>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (tracks: List<TrackEntity>, track: TrackEntity) -> Unit,
    onPlayAll: (List<TrackEntity>) -> Unit,
    onPlayShuffled: (List<TrackEntity>) -> Unit,
    onToggleLiked: (TrackEntity) -> Unit,
    onRemoveTrackFromPlaylist: (playlistId: Long, track: TrackEntity) -> Unit,
    onEditPlaylist: (PlaylistEntity) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onOpenAddToPlaylist: (TrackEntity) -> Unit,
    onEditArtwork: (TrackEntity) -> Unit,
    onEditMetadata: (TrackEntity) -> Unit,
    onDeleteTrackFromLibrary: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val tracks by when (target) {
        is PlaylistDetailTarget.Liked -> likedTracksFlow.collectAsState(initial = emptyList())
        is PlaylistDetailTarget.Favorites -> favoriteTracksFlow.collectAsState(initial = emptyList())
        is PlaylistDetailTarget.Custom -> getTracksForPlaylist(target.playlist.id).collectAsState(initial = emptyList())
    }

    val title = when (target) {
        is PlaylistDetailTarget.Liked -> "Me gusta"
        is PlaylistDetailTarget.Favorites -> "Mis favoritos"
        is PlaylistDetailTarget.Custom -> target.playlist.name
    }

    val description = when (target) {
        is PlaylistDetailTarget.Liked -> "Canciones que marcaste con el corazón."
        is PlaylistDetailTarget.Favorites -> "Tus pistas favoritas seleccionadas para acceso rápido."
        is PlaylistDetailTarget.Custom -> target.playlist.description.ifBlank { "Playlist personalizada local." }
    }

    val headerIcon = when (target) {
        is PlaylistDetailTarget.Liked -> Icons.Default.Favorite
        is PlaylistDetailTarget.Favorites -> Icons.Default.Star
        is PlaylistDetailTarget.Custom -> Icons.Default.QueueMusic
    }

    val gradientColors = when (target) {
        is PlaylistDetailTarget.Liked -> listOf(Color(0xFFE91E63), Color(0xFF880E4F))
        is PlaylistDetailTarget.Favorites -> listOf(Color(0xFFFFB300), Color(0xFFE65100))
        is PlaylistDetailTarget.Custom -> listOf(Color(0xFF1DB954), Color(0xFF104E27))
    }

    val totalDurationMs = tracks.sumOf { it.durationMs }
    var selectedTrackForMenu by remember { mutableStateOf<TrackEntity?>(null) }
    var playlistMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("playlist_detail_view")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = if (currentTrackId != null) 140.dp else 100.dp
            )
        ) {
            // Top Bar & Hero Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    gradientColors.first().copy(alpha = 0.45f),
                                    DarkBackground
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Barra superior con botón volver y menú si aplica
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("playlist_detail_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Regresar",
                                tint = TextPrimary
                            )
                        }

                        if (target is PlaylistDetailTarget.Custom) {
                            Box {
                                IconButton(
                                    onClick = { playlistMenuExpanded = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("playlist_detail_menu_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Opciones de playlist",
                                        tint = TextPrimary
                                    )
                                }

                                DropdownMenu(
                                    expanded = playlistMenuExpanded,
                                    onDismissRequest = { playlistMenuExpanded = false },
                                    modifier = Modifier.background(DarkSurface)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Editar playlist", color = TextPrimary) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = GreenAccent
                                            )
                                        },
                                        onClick = {
                                            playlistMenuExpanded = false
                                            onEditPlaylist(target.playlist)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Eliminar playlist", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            playlistMenuExpanded = false
                                            onDeletePlaylist(target.playlist)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Hero Content
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork o Icono de la lista
                        val firstArtwork = tracks.firstOrNull { !it.artworkPath.isNullOrBlank() }?.artworkPath
                        if (firstArtwork != null) {
                            AlbumArtView(
                                artworkPath = firstArtwork,
                                modifier = Modifier.size(90.dp),
                                cornerRadius = 16.dp,
                                iconSize = 36.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(gradientColors)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = headerIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
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
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${tracks.size} canciones • ${FormatUtils.formatDuration(totalDurationMs)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = GreenAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Botones de acción: Reproducir todo y Aleatorio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onPlayAll(tracks) },
                            enabled = tracks.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GreenPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("playlist_play_all_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reproducir",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        OutlinedButton(
                            onClick = { onPlayShuffled(tracks) },
                            enabled = tracks.isNotEmpty(),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("playlist_shuffle_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                tint = GreenAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Aleatorio",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = GreenAccent
                            )
                        }
                    }
                }
            }

            // Divider
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Lista de canciones
            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Esta lista está vacía",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (target) {
                                    is PlaylistDetailTarget.Liked -> "Toca el corazón en cualquier canción para verla aquí automáticamente."
                                    is PlaylistDetailTarget.Favorites -> "Marca canciones como favoritas en el menú de opciones para verlas aquí."
                                    is PlaylistDetailTarget.Custom -> "Añade canciones a esta lista desde el menú contextual de tres puntos."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(items = tracks, key = { it.id }) { track ->
                    PlaylistTrackRow(
                        track = track,
                        isCurrent = currentTrackId == track.id,
                        isPlaying = isPlaying && currentTrackId == track.id,
                        target = target,
                        onClick = { onPlayTrack(tracks, track) },
                        onToggleLiked = { onToggleLiked(track) },
                        onOptionsClick = { selectedTrackForMenu = track },
                        onQuickRemove = if (target is PlaylistDetailTarget.Custom) {
                            { onRemoveTrackFromPlaylist(target.playlist.id, track) }
                        } else null
                    )
                }
            }
        }

        // Bottom Sheet para opciones de la pista dentro de la lista
        selectedTrackForMenu?.let { track ->
            TrackOptionsBottomSheet(
                track = track,
                onDismiss = { selectedTrackForMenu = null },
                onEditArtwork = { onEditArtwork(track) },
                onEditMetadata = { onEditMetadata(track) },
                onDelete = { onDeleteTrackFromLibrary(track) },
                onAddToPlaylist = { onOpenAddToPlaylist(track) },
                onToggleLiked = { onToggleLiked(track) },
                onRemoveFromPlaylist = if (target is PlaylistDetailTarget.Custom) {
                    { onRemoveTrackFromPlaylist(target.playlist.id, track) }
                } else null
            )
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    target: PlaylistDetailTarget,
    onClick: () -> Unit,
    onToggleLiked: () -> Unit,
    onOptionsClick: () -> Unit,
    onQuickRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("playlist_track_row_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtView(
            artworkPath = track.artworkPath,
            modifier = Modifier.size(48.dp),
            cornerRadius = 8.dp,
            iconSize = 22.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = if (isCurrent) GreenPrimary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${track.artist} • ${FormatUtils.formatDuration(track.durationMs)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Botón rápido de Corazón táctil de 48dp
        IconButton(
            onClick = onToggleLiked,
            modifier = Modifier
                .size(48.dp)
                .testTag("track_heart_btn_${track.id}")
        ) {
            Icon(
                imageVector = if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (track.isLiked) "Quitar de Me gusta" else "Añadir a Me gusta",
                tint = if (track.isLiked) Color(0xFFE91E63) else TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Botón de opciones
        IconButton(
            onClick = onOptionsClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("track_menu_btn_${track.id}")
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Opciones",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
