package com.example.ui.main

import androidx.compose.runtime.Composable
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.playback.AudioEngineType
import com.example.ui.components.*
import kotlinx.coroutines.flow.Flow

/**
 * Anfitrión modular para todos los modales de diagnóstico, editores y cuadros de diálogo:
 * - [DebugConsoleModal] & [RoomDatabaseInspectorModal]: Herramientas de depuración móvil sin PC.
 * - [FpsOverlay]: Medición continua de la tasa de refresco táctil.
 * - [EditTrackMetadataDialog]: Reescritura nativa de tags ID3/Vorbis mediante Rust C-ABI.
 * - [RawErrorDialog]: Reporte directo con código numérico crudo.
 * - [EngineSelectDialog]: Selector inicial de motor audiófilo (ExoPlayer vs Oboe C++).
 * - [CreatePlaylistDialog] & [AddToPlaylistBottomSheet]: Operaciones de listas de reproducción.
 */
@Composable
fun MainModalsHost(
    isDebugConsoleOpen: Boolean,
    isDatabaseInspectorOpen: Boolean,
    rawErrorDialog: String?,
    showEngineDialog: Boolean,
    activeEngine: AudioEngineType,
    editingTrack: TrackEntity?,
    showCreatePlaylistDialog: Boolean,
    playlistToEdit: PlaylistEntity?,
    trackForAddToPlaylist: TrackEntity?,
    playlists: List<PlaylistEntity>,
    onCloseDebugConsole: () -> Unit,
    onOpenDatabaseInspector: () -> Unit,
    onCloseDatabaseInspector: () -> Unit,
    onDismissRawError: () -> Unit,
    onOpenDebugFromRawError: () -> Unit,
    onSelectEngine: (AudioEngineType) -> Unit,
    onDismissEngineDialog: () -> Unit,
    onCloseTrackEditor: () -> Unit,
    onSaveTrackMetadata: (TrackEntity, String, String, String, String, String) -> Unit,
    onDismissCreatePlaylist: () -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDismissEditPlaylist: () -> Unit,
    onUpdatePlaylist: (PlaylistEntity, String, String) -> Unit,
    onDismissAddToPlaylist: () -> Unit,
    onToggleLiked: (TrackEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onAddTrackToPlaylist: (PlaylistEntity, TrackEntity) -> Unit,
    onRemoveTrackFromPlaylist: (Long, TrackEntity) -> Unit,
    onRequestCreateNewPlaylist: () -> Unit,
    getPlaylistIdsForTrack: (Long) -> Flow<List<Long>>
) {
    // Modal de Consola de Debug y Diagnóstico Crudo
    if (isDebugConsoleOpen) {
        DebugConsoleModal(
            onDismiss = onCloseDebugConsole,
            onOpenDatabaseInspector = {
                onCloseDebugConsole()
                onOpenDatabaseInspector()
            }
        )
    }

    // Modal de Inspector de Base de Datos Room en Pantalla
    if (isDatabaseInspectorOpen) {
        RoomDatabaseInspectorModal(
            onDismiss = onCloseDatabaseInspector
        )
    }

    // Overlay Flotante de Monitor de FPS y Rendimiento
    FpsOverlay()

    // Diálogo de Edición de Metadatos con Rust
    editingTrack?.let { track ->
        EditTrackMetadataDialog(
            track = track,
            onDismiss = onCloseTrackEditor,
            onSave = { newTitle, newArtist, newAlbum, newGenre, newYear ->
                onSaveTrackMetadata(track, newTitle, newArtist, newAlbum, newGenre, newYear)
            }
        )
    }

    // Diálogo de Error Crudo Interceptado
    rawErrorDialog?.let { err ->
        RawErrorDialog(
            errorMessage = err,
            onDismiss = onDismissRawError,
            onOpenDebugConsole = onOpenDebugFromRawError
        )
    }

    // Diálogo de Selección Inicial de Motor (Sólo en primer inicio)
    if (showEngineDialog) {
        EngineSelectDialog(
            currentEngine = activeEngine,
            onEngineSelected = onSelectEngine,
            onDismissRequest = onDismissEngineDialog
        )
    }

    // Diálogo de Creación de Playlist
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = onDismissCreatePlaylist,
            onConfirm = onCreatePlaylist
        )
    }

    // Diálogo de Edición de Playlist existente
    playlistToEdit?.let { pl ->
        CreatePlaylistDialog(
            initialName = pl.name,
            initialDescription = pl.description,
            isEditing = true,
            onDismiss = onDismissEditPlaylist,
            onConfirm = { name, desc ->
                onUpdatePlaylist(pl, name, desc)
            }
        )
    }

    // Modal BottomSheet para Añadir canción a Playlist
    trackForAddToPlaylist?.let { trk ->
        AddToPlaylistBottomSheet(
            track = trk,
            playlists = playlists,
            getPlaylistIdsForTrack = getPlaylistIdsForTrack,
            onToggleLiked = onToggleLiked,
            onToggleFavorite = onToggleFavorite,
            onAddToPlaylist = onAddTrackToPlaylist,
            onRemoveFromPlaylist = onRemoveTrackFromPlaylist,
            onCreateNewPlaylist = onRequestCreateNewPlaylist,
            onDismiss = onDismissAddToPlaylist
        )
    }
}
