package com.example.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrackEntity
import com.example.ui.theme.*

/**
 * Fila de información de la canción:
 * Título de pista, artista y acciones directas (Me gusta, Favorito, Añadir a Playlist).
 */
@Composable
fun PlayerTrackDetails(
    track: TrackEntity,
    onToggleLiked: (TrackEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
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

        // Botón de Corazón (Me gusta)
        IconButton(
            onClick = { onToggleLiked(track) },
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_like_btn")
        ) {
            Icon(
                imageVector = if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (track.isLiked) "Quitar de Me gusta" else "Marcar como Me gusta",
                tint = if (track.isLiked) Color(0xFFE91E63) else TextTertiary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Botón de Favorito (Estrella)
        IconButton(
            onClick = { onToggleFavorite(track) },
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_favorite_btn")
        ) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (track.isFavorite) "Quitar de Favoritos" else "Marcar como Favorito",
                tint = if (track.isFavorite) Color(0xFFFFB300) else TextTertiary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Botón de Añadir a Playlist
        IconButton(
            onClick = { onAddToPlaylist(track) },
            modifier = Modifier
                .size(48.dp)
                .testTag("full_player_add_to_playlist_btn")
        ) {
            Icon(
                imageVector = Icons.Default.PlaylistAdd,
                contentDescription = "Añadir a playlist",
                tint = GreenAccent,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
