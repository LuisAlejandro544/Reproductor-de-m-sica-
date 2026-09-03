package com.example.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.TrackEntity
import com.example.ui.components.TrackListItem
import com.example.ui.components.TrackOptionsBottomSheet

/**
 * Lista perezosa de canciones importadas con soporte para selección táctil,
 * menú de opciones contextual mediante ModalBottomSheet táctil (previniendo fugas de
 * memoria de PopupLayout en accesibilidad), edición de carátula y tags en Rust.
 */
@Composable
fun TrackListView(
    tracks: List<TrackEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onTrackClick: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    onEditArtwork: (TrackEntity) -> Unit,
    onEditMetadata: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier,
    onToggleLiked: (TrackEntity) -> Unit = {},
    onToggleFavorite: (TrackEntity) -> Unit = {},
    onAddToPlaylist: (TrackEntity) -> Unit = {}
) {
    var selectedTrackForMenu by remember { mutableStateOf<TrackEntity?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("track_lazy_list"),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = if (currentTrackId != null) 140.dp else 100.dp
            )
        ) {
            items(
                items = tracks,
                key = { it.id }
            ) { track ->
                TrackListItem(
                    track = track,
                    isCurrent = currentTrackId == track.id,
                    isPlaying = isPlaying && currentTrackId == track.id,
                    onClick = { onTrackClick(track) },
                    onOptionsClick = { selectedTrackForMenu = track },
                    onLikeClick = { onToggleLiked(track) }
                )
            }
        }

        selectedTrackForMenu?.let { track ->
            TrackOptionsBottomSheet(
                track = track,
                onDismiss = { selectedTrackForMenu = null },
                onEditArtwork = { onEditArtwork(track) },
                onEditMetadata = { onEditMetadata(track) },
                onDelete = { onDeleteTrack(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onToggleLiked = { onToggleLiked(track) },
                onToggleFavorite = { onToggleFavorite(track) }
            )
        }
    }
}
