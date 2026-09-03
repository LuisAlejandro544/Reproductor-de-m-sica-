package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.data.TrackRepository
import com.example.playback.AudioEngineType
import com.example.playback.AudioPlayerManager
import com.example.playback.EqualizerPreset
import com.example.playback.PlaybackRepeatMode
import com.example.util.AppStorageManager
import com.example.util.ArtworkProcessor
import com.example.util.MusicImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainNavigationTab {
    SONGS,
    PLAYLISTS
}

sealed interface PlaylistDetailTarget {
    data object Liked : PlaylistDetailTarget
    data object Favorites : PlaylistDetailTarget
    data class Custom(val playlist: PlaylistEntity) : PlaylistDetailTarget
}

class MusicPlayerViewModel(
    application: Application,
    private val trackRepository: TrackRepository,
    val playerManager: AudioPlayerManager
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val allTracksFlow: StateFlow<List<TrackEntity>> = trackRepository.allTracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val likedTracks: StateFlow<List<TrackEntity>> = trackRepository.likedTracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val favoriteTracks: StateFlow<List<TrackEntity>> = trackRepository.favoriteTracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allPlaylists: StateFlow<List<PlaylistEntity>> = trackRepository.allPlaylists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val playlists: StateFlow<List<PlaylistEntity>> get() = allPlaylists

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

    val displayedTracks: StateFlow<List<TrackEntity>> = combine(allTracksFlow, _searchQuery) { tracks, query ->
        if (query.isBlank()) {
            tracks
        } else {
            val q = query.trim().lowercase()
            tracks.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val currentTrack: StateFlow<TrackEntity?> = playerManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val repeatMode: StateFlow<PlaybackRepeatMode> = playerManager.repeatMode
    val isShuffle: StateFlow<Boolean> = playerManager.isShuffle
    val activeEngine = playerManager.activeEngine

    // Ecualizador Paramétrico de 10 Bandas (C++)
    val isEqualizerEnabled: StateFlow<Boolean> = playerManager.isEqualizerEnabled
    val equalizerBandGains: StateFlow<List<Float>> = playerManager.equalizerBandGains

    private val _isEqualizerOpen = MutableStateFlow(false)
    val isEqualizerOpen: StateFlow<Boolean> = _isEqualizerOpen.asStateFlow()

    private val _showEngineDialog = MutableStateFlow(!playerManager.hasPromptedEngineSelection())
    val showEngineDialog: StateFlow<Boolean> = _showEngineDialog.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isUpdatingArtwork = MutableStateFlow(false)
    val isUpdatingArtwork: StateFlow<Boolean> = _isUpdatingArtwork.asStateFlow()

    private val _editingTrack = MutableStateFlow<TrackEntity?>(null)
    val editingTrack: StateFlow<TrackEntity?> = _editingTrack.asStateFlow()

    private val _isDebugConsoleOpen = MutableStateFlow(false)
    val isDebugConsoleOpen: StateFlow<Boolean> = _isDebugConsoleOpen.asStateFlow()

    private val _rawErrorDialog = MutableStateFlow<String?>(null)
    val rawErrorDialog: StateFlow<String?> = _rawErrorDialog.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        // Asegurar que toda pista en la biblioteca cuente con carátula (generación procedural si no tenía)
        viewModelScope.launch(Dispatchers.IO) {
            allTracksFlow.collect { tracks ->
                for (track in tracks) {
                    if (track.artworkPath.isNullOrBlank()) {
                        try {
                            val proceduralArt = ArtworkProcessor.generateProceduralArtworkLosslessWebP(
                                context = getApplication(),
                                title = track.title,
                                artist = track.artist,
                                trackId = track.id
                            )
                            val updatedTrack = track.copy(artworkPath = proceduralArt)
                            trackRepository.updateArtwork(track.id, proceduralArt)
                            AppStorageManager.saveTrackMetadataJson(getApplication(), updatedTrack)
                            if (playerManager.currentTrack.value?.id == track.id) {
                                playerManager.updateCurrentTrack(updatedTrack)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    fun openDebugConsole() {
        _isDebugConsoleOpen.value = true
    }

    fun closeDebugConsole() {
        _isDebugConsoleOpen.value = false
    }

    fun openTrackEditor(track: TrackEntity) {
        _editingTrack.value = track
    }

    fun closeTrackEditor() {
        _editingTrack.value = null
    }

    fun dismissRawErrorDialog() {
        _rawErrorDialog.value = null
    }

    fun updateTrackWithRust(track: TrackEntity, newTitle: String, newArtist: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.example.util.RustAudioEngine.updateTrackMetadata(
                track.filePath,
                newTitle.trim(),
                newArtist.trim()
            )
            if (result.isSuccess) {
                // El analizador nativo de Rust re-analiza el archivo en busca de tags y álbum
                val reanalyzed = com.example.util.RustAudioEngine.extractMetadata(track.filePath)
                val finalTitle = reanalyzed?.title?.takeIf { it.isNotBlank() } ?: newTitle.trim()
                val finalArtist = reanalyzed?.artist?.takeIf { it.isNotBlank() } ?: newArtist.trim()
                val finalAlbum = reanalyzed?.album?.takeIf { it.isNotBlank() } ?: track.album

                trackRepository.updateTrackMetadata(track.id, finalTitle, finalArtist, finalAlbum)
                val updatedEntity = track.copy(title = finalTitle, artist = finalArtist, album = finalAlbum)
                AppStorageManager.saveTrackMetadataJson(getApplication(), updatedEntity)

                if (currentTrack.value?.id == track.id) {
                    playerManager.updateCurrentTrack(updatedEntity)
                }

                _snackbarMessage.value = "Tags actualizados por Rust: '$finalTitle' - '$finalArtist' (Álbum: '$finalAlbum')"
                _editingTrack.value = null
            } else {
                val err = result.exceptionOrNull()
                val msg = "Fallo en Rust al actualizar: ${err?.message}"
                _snackbarMessage.value = msg
                _rawErrorDialog.value = "[RUST_NATIVE_ERR_TAG_WRITE] $msg"
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun openEqualizer() {
        _isEqualizerOpen.value = true
    }

    fun closeEqualizer() {
        _isEqualizerOpen.value = false
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        playerManager.setEqualizerEnabled(enabled)
    }

    fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) {
        playerManager.setEqualizerBandGain(bandIndex, gainDb)
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        playerManager.setEqualizerPreset(preset)
    }

    fun resetEqualizer() {
        playerManager.resetEqualizer()
    }

    fun setShowEngineDialog(show: Boolean) {
        _showEngineDialog.value = show
    }

    fun selectInitialEngine(engine: AudioEngineType) {
        playerManager.setAudioEngine(engine)
        playerManager.markEngineSelectionPrompted()
        _showEngineDialog.value = false
        _snackbarMessage.value = "Motor configurado: ${engine.title}"
    }

    fun dismissInitialEnginePrompt() {
        playerManager.markEngineSelectionPrompted()
        _showEngineDialog.value = false
    }

    fun setAudioEngine(engine: AudioEngineType) {
        playerManager.setAudioEngine(engine)
        _snackbarMessage.value = "Motor activo: ${engine.title}"
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            try {
                val newTracks = MusicImporter.importAudioUris(getApplication(), uris)
                if (newTracks.isNotEmpty()) {
                    trackRepository.insertTracks(newTracks)
                    // Guardar metadatos JSON modulares para cada pista
                    for (track in newTracks) {
                        AppStorageManager.saveTrackMetadataJson(getApplication(), track)
                    }
                    _snackbarMessage.value = if (newTracks.size == 1) {
                        "Canción añadida: ${newTracks.first().title}"
                    } else {
                        "${newTracks.size} canciones añadidas a tu biblioteca"
                    }
                } else {
                    _snackbarMessage.value = "No se pudieron importar los archivos de audio seleccionados"
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al importar: ${e.localizedMessage ?: "Desconocido"}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    /**
     * Asigna o cambia la carátula de cualquier canción (tenga o no carátula previa).
     * Convierte la imagen a WebP a máxima compresión sin pérdida (Lossless WebP) en hilo secundario
     * y actualiza el archivo JSON modular y la base de datos.
     */
    fun updateTrackArtwork(track: TrackEntity, imageUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isUpdatingArtwork.value = true
            try {
                val newArtworkPath = ArtworkProcessor.processAndSaveArtworkLosslessWebP(
                    context = getApplication(),
                    sourceUri = imageUri,
                    trackId = track.id,
                    oldArtworkPath = track.artworkPath
                )

                val updatedTrack = track.copy(artworkPath = newArtworkPath)
                trackRepository.updateArtwork(track.id, newArtworkPath)

                // Guardar/Actualizar JSON modular independiente
                AppStorageManager.saveTrackMetadataJson(getApplication(), updatedTrack)

                // Si es la pista en reproducción actual, sincronizar inmediatamente
                if (playerManager.currentTrack.value?.id == track.id) {
                    playerManager.updateCurrentTrack(updatedTrack)
                }

                _snackbarMessage.value = "Carátula actualizada en formato WebP sin pérdida"
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al procesar carátula: ${e.localizedMessage ?: "Desconocido"}"
            } finally {
                _isUpdatingArtwork.value = false
            }
        }
    }

    fun playTrack(track: TrackEntity) {
        val list = displayedTracks.value.ifEmpty { allTracksFlow.value }
        playerManager.playTrack(track, list)
    }

    fun playPause() {
        val current = currentTrack.value
        if (current == null) {
            val list = displayedTracks.value.ifEmpty { allTracksFlow.value }
            if (list.isNotEmpty()) {
                playerManager.playTrack(list.first(), list)
            }
        } else {
            playerManager.playPause()
        }
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun next() {
        playerManager.next()
    }

    fun previous() {
        playerManager.previous()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (currentTrack.value?.id == track.id) {
                playerManager.playPause()
            }
            trackRepository.deleteTrack(track)
            AppStorageManager.deleteTrackMetadataJson(getApplication(), track.id)
            _snackbarMessage.value = "Canción eliminada de la biblioteca"
        }
    }

    fun clearAllTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isPlaying.value) {
                playerManager.playPause()
            }
            trackRepository.clearAllTracks()
            _snackbarMessage.value = "Biblioteca vaciada correctamente"
        }
    }

    fun toggleLiked(track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !track.isLiked
            trackRepository.toggleLiked(track.id, newStatus)
            val updated = track.copy(isLiked = newStatus)
            AppStorageManager.saveTrackMetadataJson(getApplication(), updated)
            if (playerManager.currentTrack.value?.id == track.id) {
                playerManager.updateCurrentTrack(updated)
            }
            _snackbarMessage.value = if (newStatus) {
                "Añadida a 'Me gusta' ❤️"
            } else {
                "Eliminada de 'Me gusta'"
            }
        }
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !track.isFavorite
            trackRepository.toggleFavorite(track.id, newStatus)
            val updated = track.copy(isFavorite = newStatus)
            AppStorageManager.saveTrackMetadataJson(getApplication(), updated)
            if (playerManager.currentTrack.value?.id == track.id) {
                playerManager.updateCurrentTrack(updated)
            }
            _snackbarMessage.value = if (newStatus) {
                "Añadida a 'Mis favoritos' ⭐"
            } else {
                "Eliminada de 'Mis favoritos'"
            }
        }
    }

    fun setNavTab(tab: MainNavigationTab) {
        _currentNavTab.value = tab
    }

    fun selectNavTab(tab: MainNavigationTab) = setNavTab(tab)
    fun toggleTrackLiked(track: TrackEntity) = toggleLiked(track)
    fun toggleTrackFavorite(track: TrackEntity) = toggleFavorite(track)

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
        viewModelScope.launch(Dispatchers.IO) {
            trackRepository.createPlaylist(name, description)
            _isCreatePlaylistOpen.value = false
            _snackbarMessage.value = "Playlist creada: '$name'"
        }
    }

    fun updatePlaylist(playlist: PlaylistEntity, newName: String, newDescription: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = playlist.copy(name = newName.trim(), description = newDescription.trim())
            trackRepository.updatePlaylist(updated)
            _editingPlaylist.value = null
            if (_selectedPlaylistTarget.value is PlaylistDetailTarget.Custom &&
                (_selectedPlaylistTarget.value as PlaylistDetailTarget.Custom).playlist.id == playlist.id) {
                _selectedPlaylistTarget.value = PlaylistDetailTarget.Custom(updated)
            }
            _snackbarMessage.value = "Playlist actualizada"
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            trackRepository.deletePlaylist(playlist.id)
            if (_selectedPlaylistTarget.value is PlaylistDetailTarget.Custom &&
                (_selectedPlaylistTarget.value as PlaylistDetailTarget.Custom).playlist.id == playlist.id) {
                _selectedPlaylistTarget.value = null
            }
            _snackbarMessage.value = "Playlist '${playlist.name}' eliminada"
        }
    }

    fun addTrackToPlaylist(playlist: PlaylistEntity, track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            trackRepository.addTrackToPlaylist(playlist.id, track.id)
            _snackbarMessage.value = "Añadida a '${playlist.name}'"
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            trackRepository.removeTrackFromPlaylist(playlistId, track.id)
            _snackbarMessage.value = "Eliminada de la playlist"
        }
    }

    fun getTracksForPlaylist(playlistId: Long): kotlinx.coroutines.flow.Flow<List<TrackEntity>> {
        return trackRepository.getTracksForPlaylist(playlistId)
    }

    fun getTrackCountForPlaylist(playlistId: Long): kotlinx.coroutines.flow.Flow<Int> {
        return trackRepository.getTrackCountForPlaylist(playlistId)
    }

    fun getPlaylistIdsForTrack(trackId: Long): kotlinx.coroutines.flow.Flow<List<Long>> {
        return trackRepository.getPlaylistIdsForTrack(trackId)
    }

    fun playPlaylistTracks(tracks: List<TrackEntity>, startTrack: TrackEntity? = null) {
        if (tracks.isEmpty()) return
        val start = startTrack ?: tracks.first()
        playerManager.playTrack(start, tracks)
    }

    fun playPlaylistTrack(tracks: List<TrackEntity>, startTrack: TrackEntity) = playPlaylistTracks(tracks, startTrack)
    fun playAllTracksInList(tracks: List<TrackEntity>) = playPlaylistTracks(tracks, tracks.firstOrNull())
    fun playShuffledInList(tracks: List<TrackEntity>) = playPlaylistShuffled(tracks)

    fun playPlaylistShuffled(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val shuffled = tracks.shuffled()
        playerManager.playTrack(shuffled.first(), shuffled)
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = AppDatabase.getDatabase(application)
            val repository = TrackRepository(database.trackDao(), database.playlistDao())
            val playerManager = AudioPlayerManager.getInstance(application)
            return MusicPlayerViewModel(application, repository, playerManager) as T
        }
    }
}
