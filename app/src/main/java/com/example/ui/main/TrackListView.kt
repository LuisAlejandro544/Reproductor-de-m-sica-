package com.example.ui.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.TrackEntity
import com.example.ui.components.TrackListItem

/**
 * Lista perezosa de canciones importadas con soporte para selección táctil,
 * menú contextual, edición de carátula y tags en Rust.
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
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("track_lazy_list"),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = if (currentTrackId != null) 100.dp else 24.dp
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
                onDelete = { onDeleteTrack(track) },
                onEditArtwork = { onEditArtwork(track) },
                onEditMetadata = { onEditMetadata(track) }
            )
        }
    }
}
