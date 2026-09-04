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
import com.example.playback.SleepTimerStatus
import com.example.ui.delegates.PlaylistViewModelDelegate
import com.example.ui.delegates.TrackMediaDelegate
import com.example.util.AppStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
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

/**
 * ViewModel principal orquestador de la experiencia de audio audiófilo Ritmo.
 * Refactorizado modularmente mediante delegados especializados:
 * - [PlaylistViewModelDelegate]: Operaciones de listas de reproducción y generación automática.
 * - [TrackMediaDelegate]: Importación, metadatos en Rust C-ABI, carátulas Lossless WebP y letras LRC.
 * - [AudioPlayerManager]: Control de reproducción multicanal (ExoPlayer y Oboe C++ DSP).
 */
class MusicPlayerViewModel(
    application: Application,
    private val trackRepository: TrackRepository,
    val playerManager: AudioPlayerManager
) : AndroidViewModel(application) {

    // Mensajes y alertas para la UI
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _rawErrorDialog = MutableStateFlow<String?>(null)
    val rawErrorDialog: StateFlow<String?> = _rawErrorDialog.asStateFlow()

    // Delegados modulares
    val playlistDelegate = PlaylistViewModelDelegate(
        trackRepository = trackRepository,
        scope = viewModelScope,
        onShowMessage = { msg -> _snackbarMessage.value = msg }
    )

    val mediaDelegate = TrackMediaDelegate(
        application = application,
        trackRepository = trackRepository,
        playerManager = playerManager,
        scope = viewModelScope,
        onShowMessage = { msg -> _snackbarMessage.value = msg },
        onRawError = { err -> _rawErrorDialog.value = err }
    )

    // Búsqueda y filtrado de pistas
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

    val allPlaylists: StateFlow<List<PlaylistEntity>> get() = playlistDelegate.allPlaylists
    val playlists: StateFlow<List<PlaylistEntity>> get() = allPlaylists

    val currentNavTab: StateFlow<MainNavigationTab> get() = playlistDelegate.currentNavTab
    val selectedPlaylistTarget: StateFlow<PlaylistDetailTarget?> get() = playlistDelegate.selectedPlaylistTarget
    val trackToAddToPlaylist: StateFlow<TrackEntity?> get() = playlistDelegate.trackToAddToPlaylist
    val isCreatePlaylistOpen: StateFlow<Boolean> get() = playlistDelegate.isCreatePlaylistOpen
    val editingPlaylist: StateFlow<PlaylistEntity?> get() = playlistDelegate.editingPlaylist

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

    // Estado del reproductor de audio
    val currentTrack: StateFlow<TrackEntity?> = playerManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val repeatMode: StateFlow<PlaybackRepeatMode> = playerManager.repeatMode
    val isShuffle: StateFlow<Boolean> = playerManager.isShuffle
    val activeEngine = playerManager.activeEngine

    // Ecualizador Paramétrico de 10 Bandas C++
    val isEqualizerEnabled: StateFlow<Boolean> = playerManager.isEqualizerEnabled
    val equalizerBandGains: StateFlow<List<Float>> = playerManager.equalizerBandGains

    private val _isEqualizerOpen = MutableStateFlow(false)
    val isEqualizerOpen: StateFlow<Boolean> = _isEqualizerOpen.asStateFlow()

    // Audio Espacial 360° / Efecto 8D Nativo C++
    val isSpatialAudioEnabled: StateFlow<Boolean> = playerManager.isSpatialAudioEnabled
    val spatialAudioSpeed: StateFlow<Float> = playerManager.spatialAudioSpeed
    val spatialAudioDepth: StateFlow<Float> = playerManager.spatialAudioDepth
    val spatialAudioReverb: StateFlow<Float> = playerManager.spatialAudioReverb

    private val _isSpatialAudioModalOpen = MutableStateFlow(false)
    val isSpatialAudioModalOpen: StateFlow<Boolean> = _isSpatialAudioModalOpen.asStateFlow()

    // Temporizador de Sueño
    val sleepTimerStatus: StateFlow<SleepTimerStatus> = playerManager.sleepTimerManager.status

    private val _isSleepTimerModalOpen = MutableStateFlow(false)
    val isSleepTimerModalOpen: StateFlow<Boolean> = _isSleepTimerModalOpen.asStateFlow()

    private val _showEngineDialog = MutableStateFlow(!playerManager.hasPromptedEngineSelection())
    val showEngineDialog: StateFlow<Boolean> = _showEngineDialog.asStateFlow()

    // Modales y vistas de la interfaz
    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _isDebugConsoleOpen = MutableStateFlow(false)
    val isDebugConsoleOpen: StateFlow<Boolean> = _isDebugConsoleOpen.asStateFlow()

    private val _isDatabaseInspectorOpen = MutableStateFlow(false)
    val isDatabaseInspectorOpen: StateFlow<Boolean> = _isDatabaseInspectorOpen.asStateFlow()

    // Estado expuesto desde el delegado de medios
    val isImporting: StateFlow<Boolean> get() = mediaDelegate.isImporting
    val isUpdatingArtwork: StateFlow<Boolean> get() = mediaDelegate.isUpdatingArtwork
    val editingTrack: StateFlow<TrackEntity?> get() = mediaDelegate.editingTrack

    init {
        // Inicializar metadatos automáticos y auto-generación de listas de artistas
        viewModelScope.launch(Dispatchers.IO) {
            allTracksFlow.collect { tracks ->
                mediaDelegate.ensureMetadataAndArtwork(tracks)
                if (tracks.isNotEmpty()) {
                    playlistDelegate.checkAndAutoGenerateArtistPlaylists(tracks)
                }
            }
        }
    }

    // Navegación y Vistas
    fun openDebugConsole() { _isDebugConsoleOpen.value = true }
    fun closeDebugConsole() { _isDebugConsoleOpen.value = false }
    fun openDatabaseInspector() { _isDatabaseInspectorOpen.value = true }
    fun closeDatabaseInspector() { _isDatabaseInspectorOpen.value = false }
    fun dismissRawErrorDialog() { _rawErrorDialog.value = null }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setPlayerExpanded(expanded: Boolean) { _isPlayerExpanded.value = expanded }
    fun openSettings() { _isSettingsOpen.value = true }
    fun closeSettings() { _isSettingsOpen.value = false }
    fun clearSnackbarMessage() { _snackbarMessage.value = null }

    // Delegación de Edición de Metadatos y Medios
    fun openTrackEditor(track: TrackEntity) = mediaDelegate.openTrackEditor(track)
    fun closeTrackEditor() = mediaDelegate.closeTrackEditor()
    fun updateTrackWithRust(
        track: TrackEntity,
        newTitle: String,
        newArtist: String,
        newAlbum: String = "",
        newGenre: String = "",
        newYear: String = ""
    ) = mediaDelegate.updateTrackWithRust(track, newTitle, newArtist, newAlbum, newGenre, newYear)
    fun importUris(uris: List<Uri>) = mediaDelegate.importUris(uris)
    fun updateTrackArtwork(track: TrackEntity, imageUri: Uri) = mediaDelegate.updateTrackArtwork(track, imageUri)
    fun updateTrackLyrics(track: TrackEntity, lyrics: String?) = mediaDelegate.updateTrackLyrics(track, lyrics)

    // Delegación de Operaciones de Playlists
    fun setNavTab(tab: MainNavigationTab) = playlistDelegate.setNavTab(tab)
    fun selectNavTab(tab: MainNavigationTab) = setNavTab(tab)
    fun openPlaylist(target: PlaylistDetailTarget) = playlistDelegate.openPlaylist(target)
    fun closePlaylistDetail() = playlistDelegate.closePlaylistDetail()
    fun openAddToPlaylist(track: TrackEntity) = playlistDelegate.openAddToPlaylist(track)
    fun closeAddToPlaylist() = playlistDelegate.closeAddToPlaylist()
    fun openCreatePlaylist() = playlistDelegate.openCreatePlaylist()
    fun closeCreatePlaylist() = playlistDelegate.closeCreatePlaylist()
    fun openEditPlaylist(playlist: PlaylistEntity) = playlistDelegate.openEditPlaylist(playlist)
    fun closeEditPlaylist() = playlistDelegate.closeEditPlaylist()
    fun createPlaylist(name: String, description: String = "") = playlistDelegate.createPlaylist(name, description)
    fun updatePlaylist(playlist: PlaylistEntity, newName: String, newDescription: String) =
        playlistDelegate.updatePlaylist(playlist, newName, newDescription)
    fun deletePlaylist(playlist: PlaylistEntity) = playlistDelegate.deletePlaylist(playlist)
    fun addTrackToPlaylist(playlist: PlaylistEntity, track: TrackEntity) = playlistDelegate.addTrackToPlaylist(playlist, track)
    fun removeTrackFromPlaylist(playlistId: Long, track: TrackEntity) = playlistDelegate.removeTrackFromPlaylist(playlistId, track)
    fun getTracksForPlaylist(playlistId: Long) = playlistDelegate.getTracksForPlaylist(playlistId)
    fun getTrackCountForPlaylist(playlistId: Long) = playlistDelegate.getTrackCountForPlaylist(playlistId)
    fun getPlaylistIdsForTrack(trackId: Long) = playlistDelegate.getPlaylistIdsForTrack(trackId)

    // Ecualizador de 10 Bandas C++
    fun openEqualizer() { _isEqualizerOpen.value = true }
    fun closeEqualizer() { _isEqualizerOpen.value = false }
    fun setEqualizerEnabled(enabled: Boolean) = playerManager.setEqualizerEnabled(enabled)
    fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) = playerManager.setEqualizerBandGain(bandIndex, gainDb)
    fun setEqualizerPreset(preset: EqualizerPreset) = playerManager.setEqualizerPreset(preset)
    fun resetEqualizer() = playerManager.resetEqualizer()

    // Audio Espacial 360° / 8D C++
    fun openSpatialAudioModal() { _isSpatialAudioModalOpen.value = true }
    fun closeSpatialAudioModal() { _isSpatialAudioModalOpen.value = false }
    fun setSpatialAudioEnabled(enabled: Boolean) {
        playerManager.setSpatialAudioEnabled(enabled)
        _snackbarMessage.value = if (enabled) "Efecto 8D / 360° C++ Activado" else "Efecto 8D / 360° Desactivado"
    }
    fun setSpatialAudioSpeed(speedHz: Float) = playerManager.setSpatialAudioSpeed(speedHz)
    fun setSpatialAudioDepth(depth: Float) = playerManager.setSpatialAudioDepth(depth)
    fun setSpatialAudioReverb(reverb: Float) = playerManager.setSpatialAudioReverb(reverb)

    // Temporizador de Sueño
    fun openSleepTimerModal() { _isSleepTimerModalOpen.value = true }
    fun closeSleepTimerModal() { _isSleepTimerModalOpen.value = false }
    fun startSleepTimer(minutes: Int) {
        playerManager.startSleepTimer(minutes)
        _isSleepTimerModalOpen.value = false
        _snackbarMessage.value = "Temporizador de apagado configurado: $minutes min"
    }
    fun startEndOfTrackSleepTimer() {
        playerManager.startEndOfTrackSleepTimer()
        _isSleepTimerModalOpen.value = false
        _snackbarMessage.value = "Temporizador configurado: Al terminar la canción actual"
    }
    fun addSleepTimerMinutes(minutes: Int) {
        playerManager.addSleepTimerMinutes(minutes)
        _snackbarMessage.value = "+$minutes min añadidos al temporizador"
    }
    fun cancelSleepTimer() {
        playerManager.cancelSleepTimer()
        _snackbarMessage.value = "Temporizador de sueño cancelado"
    }

    // Motores de Audio
    fun setShowEngineDialog(show: Boolean) { _showEngineDialog.value = show }
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

    // Acciones de Reproducción
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

    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun next() = playerManager.next()
    fun previous() = playerManager.previous()
    fun toggleShuffle() = playerManager.toggleShuffle()
    fun toggleRepeat() = playerManager.toggleRepeat()

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
            _snackbarMessage.value = if (newStatus) "Añadida a 'Me gusta' ❤️" else "Eliminada de 'Me gusta'"
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
            _snackbarMessage.value = if (newStatus) "Añadida a 'Mis favoritos' ⭐" else "Eliminada de 'Mis favoritos'"
        }
    }

    fun toggleTrackLiked(track: TrackEntity) = toggleLiked(track)
    fun toggleTrackFavorite(track: TrackEntity) = toggleFavorite(track)

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
