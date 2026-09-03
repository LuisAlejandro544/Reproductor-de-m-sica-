package com.example.ui.delegates

import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.data.TrackRepository
import com.example.ui.MainNavigationTab
import com.example.ui.PlaylistDetailTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Delegado modular responsable de la gestión de Playlists:
 * Estado de navegación, selección de playlists personalizadas, operaciones CRUD
 * y algoritmo de generación automática de listas por artista (3+ canciones).
 */
class PlaylistViewModelDelegate(
    private val trackRepository: TrackRepository,
    private val scope: CoroutineScope,
    private val onShowMessage: (String) -> Unit
) {
    val allPlaylists: StateFlow<List<PlaylistEntity>> = trackRepository.allPlaylists.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentNavTab = MutableStateFlow(MainNavigationTab.SONGS)
    val currentNavTab: StateFlow<MainNavigationTab> = _currentNavTab.asStateFlow()

    private val _selectedPlaylistTarget = MutableStateFlow<PlaylistDetailTarget?>(null)
    val selectedPlaylistTarget: StateFlow<PlaylistDetailTarget?> = _selectedPlaylistTarget.asStateFlow()

    private val _trackToAddToPlaylist = MutableStateFlow<TrackEntity?>(null)
    val trackToAddToPlaylist: StateFlow<TrackEntity?> = _trackToAddToPlaylist.asStateFlow()

    private val _isCreatePlaylistOpen = MutableStateFlow(false)
    val isCreatePlaylistOpen: StateFlow<Boolean> = _isCreatePlaylistOpen.asStateFlow()

    private val _editingPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val editingPlaylist: StateFlow<PlaylistEntity?> = _editingPlaylist.asStateFlow()

    fun setNavTab(tab: MainNavigationTab) {
        _currentNavTab.value = tab
    }

    fun openPlaylist(target: PlaylistDetailTarget) {
        _selectedPlaylistTarget.value = target
    }

    fun closePlaylistDetail() {
        _selectedPlaylistTarget.value = null
    }

    fun openAddToPlaylist(track: TrackEntity) {
        _trackToAddToPlaylist.value = track
    }

    fun closeAddToPlaylist() {
        _trackToAddToPlaylist.value = null
    }

    fun openCreatePlaylist() {
        _isCreatePlaylistOpen.value = true
    }

    fun closeCreatePlaylist() {
        _isCreatePlaylistOpen.value = false
    }

    fun openEditPlaylist(playlist: PlaylistEntity) {
        _editingPlaylist.value = playlist
    }

    fun closeEditPlaylist() {
        _editingPlaylist.value = null
    }

    fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) return
        scope.launch(Dispatchers.IO) {
            trackRepository.createPlaylist(name, description)
            _isCreatePlaylistOpen.value = false
            onShowMessage("Playlist creada: '$name'")
        }
    }

    fun updatePlaylist(playlist: PlaylistEntity, newName: String, newDescription: String) {
        if (newName.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val updated = playlist.copy(name = newName.trim(), description = newDescription.trim())
            trackRepository.updatePlaylist(updated)
            _editingPlaylist.value = null
            if (_selectedPlaylistTarget.value is PlaylistDetailTarget.Custom &&
                (_selectedPlaylistTarget.value as PlaylistDetailTarget.Custom).playlist.id == playlist.id) {
                _selectedPlaylistTarget.value = PlaylistDetailTarget.Custom(updated)
            }
            onShowMessage("Playlist actualizada")
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        scope.launch(Dispatchers.IO) {
            trackRepository.deletePlaylist(playlist.id)
            if (_selectedPlaylistTarget.value is PlaylistDetailTarget.Custom &&
                (_selectedPlaylistTarget.value as PlaylistDetailTarget.Custom).playlist.id == playlist.id) {
                _selectedPlaylistTarget.value = null
            }
            onShowMessage("Playlist '${playlist.name}' eliminada")
        }
    }

    fun addTrackToPlaylist(playlist: PlaylistEntity, track: TrackEntity) {
        scope.launch(Dispatchers.IO) {
            trackRepository.addTrackToPlaylist(playlist.id, track.id)
            onShowMessage("Añadida a '${playlist.name}'")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, track: TrackEntity) {
        scope.launch(Dispatchers.IO) {
            trackRepository.removeTrackFromPlaylist(playlistId, track.id)
            onShowMessage("Eliminada de la playlist")
        }
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>> {
        return trackRepository.getTracksForPlaylist(playlistId)
    }

    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int> {
        return trackRepository.getTrackCountForPlaylist(playlistId)
    }

    fun getPlaylistIdsForTrack(trackId: Long): Flow<List<Long>> {
        return trackRepository.getPlaylistIdsForTrack(trackId)
    }

    /**
     * Detección y generación automática de Playlists para artistas con 3 o más canciones.
     */
    suspend fun checkAndAutoGenerateArtistPlaylists(tracks: List<TrackEntity>) {
        try {
            val tracksByArtist = tracks.groupBy { it.artist.trim() }
                .filter { (artist, artistTracks) ->
                    artist.isNotBlank() &&
                    !artist.equals("Desconocido", ignoreCase = true) &&
                    !artist.equals("Artista Desconocido", ignoreCase = true) &&
                    !artist.equals("Unknown Artist", ignoreCase = true) &&
                    !artist.equals("<unknown>", ignoreCase = true) &&
                    artistTracks.size >= 3
                }

            if (tracksByArtist.isEmpty()) return

            val currentPlaylists = trackRepository.allPlaylists.first()

            for ((artist, artistTracks) in tracksByArtist) {
                val playlistName = artist
                val autoDescription = "Playlist automática de $artist (3+ canciones)"

                val existing = currentPlaylists.find {
                    it.name.equals(playlistName, ignoreCase = true) ||
                    it.description.contains("3+ canciones de $artist") ||
                    it.description.contains("Playlist automática de $artist")
                }

                val playlistId = if (existing == null) {
                    trackRepository.createPlaylist(name = playlistName, description = autoDescription)
                } else {
                    existing.id
                }

                val currentTracksInPlaylist = trackRepository.getTracksForPlaylist(playlistId).first().map { it.id }.toSet()
                for (trk in artistTracks) {
                    if (trk.id !in currentTracksInPlaylist) {
                        trackRepository.addTrackToPlaylist(playlistId, trk.id)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
