package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.ui.components.*
import com.example.ui.main.*
import com.example.ui.theme.DarkBackground

/**
 * Pantalla principal modular de Ritmo Music Player.
 *
 * Arquitectura modular compuesta por componentes desacoplados:
 * - [MainTopAppBar]: Identidad, selector de motor, importación de medios y acceso a ajustes.
 * - [MainProgressBanners]: Banners no bloqueantes para importación y compresión Lossless WebP.
 * - [MainSearchBar]: Filtrado en memoria de canciones.
 * - [EmptyLibraryView] & [TrackListView]: Vistas de contenido de canciones.
 * - [PlaylistListView] & [PlaylistDetailView]: Navegación y gestión de playlists.
 * - [MiniPlayer] & [FullPlayerView]: Reproductor táctil compacto y a pantalla completa.
 * - [MainBottomNavBar]: Barra táctil con touch targets de 48dp.
 * - [EqualizerModal] & [SpatialAudio8DModal]: DSP en tiempo real C++ (10 bandas y audio 360°).
 * - [SleepTimerModal]: Temporizador de apagado.
 * - [SettingsScreen]: Vista de configuración integrada.
 * - [MainModalsHost]: Hospedador de diálogos, modales de diagnóstico y herramientas sin PC.
 */
@Composable
fun MainMusicScreen(
    viewModel: MusicPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val tracks by viewModel.displayedTracks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPosition.collectAsStateWithLifecycle()
    val durationMs by viewModel.duration.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isUpdatingArtwork by viewModel.isUpdatingArtwork.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val activeEngine by viewModel.activeEngine.collectAsStateWithLifecycle()
    val showEngineDialog by viewModel.showEngineDialog.collectAsStateWithLifecycle()

    val isEqualizerOpen by viewModel.isEqualizerOpen.collectAsStateWithLifecycle()
    val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsStateWithLifecycle()
    val equalizerBandGains by viewModel.equalizerBandGains.collectAsStateWithLifecycle()

    val isSpatialAudioModalOpen by viewModel.isSpatialAudioModalOpen.collectAsStateWithLifecycle()
    val isSpatialAudioEnabled by viewModel.isSpatialAudioEnabled.collectAsStateWithLifecycle()
    val spatialAudioSpeed by viewModel.spatialAudioSpeed.collectAsStateWithLifecycle()
    val spatialAudioDepth by viewModel.spatialAudioDepth.collectAsStateWithLifecycle()
    val spatialAudioReverb by viewModel.spatialAudioReverb.collectAsStateWithLifecycle()

    val isSleepTimerModalOpen by viewModel.isSleepTimerModalOpen.collectAsStateWithLifecycle()
    val sleepTimerStatus by viewModel.sleepTimerStatus.collectAsStateWithLifecycle()

    val editingTrack by viewModel.editingTrack.collectAsStateWithLifecycle()
    val isDebugConsoleOpen by viewModel.isDebugConsoleOpen.collectAsStateWithLifecycle()
    val isDatabaseInspectorOpen by viewModel.isDatabaseInspectorOpen.collectAsStateWithLifecycle()
    val rawErrorDialog by viewModel.rawErrorDialog.collectAsStateWithLifecycle()

    val currentNavTab by viewModel.currentNavTab.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val likedTracks by viewModel.likedTracks.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val selectedPlaylistTarget by viewModel.selectedPlaylistTarget.collectAsStateWithLifecycle()

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<PlaylistEntity?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<TrackEntity?>(null) }
    var targetTrackForArtwork by remember { mutableStateOf<TrackEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Launcher para seleccionar archivos de audio locales
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris)
        }
    }

    // Launcher con Android Photo Picker nativo para carátulas WebP
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && targetTrackForArtwork != null) {
            viewModel.updateTrackArtwork(targetTrackForArtwork!!, uri)
            targetTrackForArtwork = null
        }
    }

    // Presentar mensajes flotantes de la UI
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    // Gestión predictiva del botón Atrás (BackHandler)
    BackHandler(enabled = isPlayerExpanded || isSettingsOpen || selectedPlaylistTarget != null) {
        when {
            isPlayerExpanded -> viewModel.setPlayerExpanded(false)
            isSettingsOpen -> viewModel.closeSettings()
            selectedPlaylistTarget != null -> viewModel.closePlaylistDetail()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        containerColor = DarkBackground,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = if (currentTrack != null) 72.dp else 0.dp)
            )
        },
        bottomBar = {
            if (!isPlayerExpanded && !isSettingsOpen) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Mini player posicionado sobre la barra de navegación
                    if (currentTrack != null) {
                        MiniPlayer(
                            track = currentTrack!!,
                            isPlaying = isPlaying,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            onPlayPause = { viewModel.playPause() },
                            onNext = { viewModel.next() },
                            onExpand = { viewModel.setPlayerExpanded(true) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    // Barra de navegación inferior modular
                    MainBottomNavBar(
                        currentNavTab = currentNavTab,
                        selectedPlaylistTarget = selectedPlaylistTarget,
                        onSelectSongs = {
                            viewModel.closePlaylistDetail()
                            viewModel.selectNavTab(MainNavigationTab.SONGS)
                        },
                        onSelectPlaylists = {
                            viewModel.selectNavTab(MainNavigationTab.PLAYLISTS)
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            // Contenido principal según la pestaña y estado de navegación
            if (selectedPlaylistTarget != null) {
                PlaylistDetailView(
                    target = selectedPlaylistTarget!!,
                    likedTracksFlow = viewModel.likedTracks,
                    favoriteTracksFlow = viewModel.favoriteTracks,
                    getTracksForPlaylist = { viewModel.getTracksForPlaylist(it) },
                    currentTrackId = currentTrack?.id,
                    isPlaying = isPlaying,
                    onBack = { viewModel.closePlaylistDetail() },
                    onPlayTrack = { list, t -> viewModel.playPlaylistTrack(list, t) },
                    onPlayAll = { viewModel.playAllTracksInList(it) },
                    onPlayShuffled = { viewModel.playShuffledInList(it) },
                    onToggleLiked = { viewModel.toggleTrackLiked(it) },
                    onRemoveTrackFromPlaylist = { pId, t -> viewModel.removeTrackFromPlaylist(pId, t) },
                    onEditPlaylist = { playlistToEdit = it },
                    onDeletePlaylist = { viewModel.deletePlaylist(it) },
                    onOpenAddToPlaylist = { trackForAddToPlaylist = it },
                    onEditArtwork = { track ->
                        targetTrackForArtwork = track
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onEditMetadata = { track -> viewModel.openTrackEditor(track) },
                    onDeleteTrackFromLibrary = { viewModel.deleteTrack(it) }
                )
            } else if (currentNavTab == MainNavigationTab.PLAYLISTS) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    MainTopAppBar(
                        tracksCount = tracks.size,
                        activeEngine = activeEngine,
                        onOpenSettings = { viewModel.openSettings() },
                        onImportClicked = { filePickerLauncher.launch(arrayOf("audio/*")) },
                        onOpenDebugConsole = { viewModel.openDebugConsole() }
                    )

                    PlaylistListView(
                        playlists = playlists,
                        likedTracks = likedTracks,
                        favoriteTracks = favoriteTracks,
                        getTrackCountForPlaylist = { viewModel.getTrackCountForPlaylist(it) },
                        onOpenLiked = { viewModel.openPlaylist(PlaylistDetailTarget.Liked) },
                        onOpenFavorites = { viewModel.openPlaylist(PlaylistDetailTarget.Favorites) },
                        onOpenPlaylist = { viewModel.openPlaylist(PlaylistDetailTarget.Custom(it)) },
                        onCreatePlaylist = { showCreatePlaylistDialog = true },
                        onEditPlaylist = { playlistToEdit = it },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        currentTrackId = currentTrack?.id
                    )
                }
            } else {
                // Pestaña Canciones
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    MainTopAppBar(
                        tracksCount = tracks.size,
                        activeEngine = activeEngine,
                        onOpenSettings = { viewModel.openSettings() },
                        onImportClicked = { filePickerLauncher.launch(arrayOf("audio/*")) },
                        onOpenDebugConsole = { viewModel.openDebugConsole() }
                    )

                    MainProgressBanners(
                        isImporting = isImporting,
                        isUpdatingArtwork = isUpdatingArtwork
                    )

                    if (tracks.isNotEmpty() || searchQuery.isNotBlank()) {
                        MainSearchBar(
                            query = searchQuery,
                            onQueryChange = { viewModel.setSearchQuery(it) }
                        )
                    }

                    if (tracks.isEmpty()) {
                        EmptyLibraryView(
                            searchQuery = searchQuery,
                            onImportClicked = { filePickerLauncher.launch(arrayOf("audio/*")) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        TrackListView(
                            tracks = tracks,
                            currentTrackId = currentTrack?.id,
                            isPlaying = isPlaying,
                            onTrackClick = { viewModel.playTrack(it) },
                            onDeleteTrack = { viewModel.deleteTrack(it) },
                            onEditArtwork = { track ->
                                targetTrackForArtwork = track
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onEditMetadata = { track ->
                                viewModel.openTrackEditor(track)
                            },
                            onToggleLiked = { viewModel.toggleTrackLiked(it) },
                            onToggleFavorite = { viewModel.toggleTrackFavorite(it) },
                            onAddToPlaylist = { trackForAddToPlaylist = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Reproductor a pantalla completa con transición fluida
            AnimatedVisibility(
                visible = isPlayerExpanded && currentTrack != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(340, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(250)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(220)),
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground)
            ) {
                currentTrack?.let { track ->
                    FullPlayerView(
                        track = track,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        activeEngine = activeEngine,
                        isEqualizerEnabled = isEqualizerEnabled,
                        onOpenEqualizer = { viewModel.openEqualizer() },
                        isSpatialAudioEnabled = isSpatialAudioEnabled,
                        onOpenSpatialAudio = { viewModel.openSpatialAudioModal() },
                        sleepTimerStatus = sleepTimerStatus,
                        onOpenSleepTimer = { viewModel.openSleepTimerModal() },
                        onCollapse = { viewModel.setPlayerExpanded(false) },
                        onPlayPause = { viewModel.playPause() },
                        onNext = { viewModel.next() },
                        onPrevious = { viewModel.previous() },
                        onSeekTo = { viewModel.seekTo(it) },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onToggleRepeat = { viewModel.toggleRepeat() },
                        onDeleteTrack = { viewModel.deleteTrack(it) },
                        onEditArtwork = { trackToEdit ->
                            targetTrackForArtwork = trackToEdit
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onToggleLiked = { viewModel.toggleTrackLiked(it) },
                        onToggleFavorite = { viewModel.toggleTrackFavorite(it) },
                        onAddToPlaylist = { trackForAddToPlaylist = it },
                        onUpdateLyrics = { trk, lyrics -> viewModel.updateTrackLyrics(trk, lyrics) }
                    )
                }
            }

            // Ecualizador Paramétrico de 10 Bandas C++
            EqualizerModal(
                isVisible = isEqualizerOpen,
                isEnabled = isEqualizerEnabled,
                bandGains = equalizerBandGains,
                activeEngine = activeEngine,
                onToggleEnabled = { viewModel.setEqualizerEnabled(it) },
                onBandGainChanged = { bandIndex, gainDb ->
                    viewModel.setEqualizerBandGain(bandIndex, gainDb)
                },
                onSelectPreset = { preset ->
                    viewModel.setEqualizerPreset(preset)
                },
                onReset = {
                    viewModel.resetEqualizer()
                },
                onDismiss = {
                    viewModel.closeEqualizer()
                }
            )

            // Modal de Audio Espacial 360° / Efecto 8D Nativo C++
            SpatialAudio8DModal(
                isOpen = isSpatialAudioModalOpen,
                isEnabled = isSpatialAudioEnabled,
                speedHz = spatialAudioSpeed,
                depth = spatialAudioDepth,
                reverb = spatialAudioReverb,
                onToggleEnabled = { viewModel.setSpatialAudioEnabled(it) },
                onSpeedChange = { viewModel.setSpatialAudioSpeed(it) },
                onDepthChange = { viewModel.setSpatialAudioDepth(it) },
                onReverbChange = { viewModel.setSpatialAudioReverb(it) },
                onDismiss = { viewModel.closeSpatialAudioModal() }
            )

            // Modal del Temporizador de Sueño
            SleepTimerModal(
                isOpen = isSleepTimerModalOpen,
                status = sleepTimerStatus,
                onStartTimer = { viewModel.startSleepTimer(it) },
                onStartEndOfTrack = { viewModel.startEndOfTrackSleepTimer() },
                onAddMinutes = { viewModel.addSleepTimerMinutes(it) },
                onCancelTimer = { viewModel.cancelSleepTimer() },
                onDismiss = { viewModel.closeSleepTimerModal() }
            )

            // Pantalla de Ajustes
            AnimatedVisibility(
                visible = isSettingsOpen,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(250)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(200)),
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground)
            ) {
                SettingsScreen(
                    activeEngine = activeEngine,
                    tracksCount = tracks.size,
                    onEngineChanged = { viewModel.setAudioEngine(it) },
                    onImportRequested = { filePickerLauncher.launch(arrayOf("audio/*")) },
                    onClearLibraryRequested = { viewModel.clearAllTracks() },
                    onNavigateBack = { viewModel.closeSettings() },
                    onOpenDebugConsole = { viewModel.openDebugConsole() }
                )
            }
        }
    }

    // Anfitrión modular de todos los modales de diagnóstico, editores y cuadros de diálogo
    MainModalsHost(
        isDebugConsoleOpen = isDebugConsoleOpen,
        isDatabaseInspectorOpen = isDatabaseInspectorOpen,
        rawErrorDialog = rawErrorDialog,
        showEngineDialog = showEngineDialog,
        activeEngine = activeEngine,
        editingTrack = editingTrack,
        showCreatePlaylistDialog = showCreatePlaylistDialog,
        playlistToEdit = playlistToEdit,
        trackForAddToPlaylist = trackForAddToPlaylist,
        playlists = playlists,
        onCloseDebugConsole = { viewModel.closeDebugConsole() },
        onOpenDatabaseInspector = { viewModel.openDatabaseInspector() },
        onCloseDatabaseInspector = { viewModel.closeDatabaseInspector() },
        onDismissRawError = { viewModel.dismissRawErrorDialog() },
        onOpenDebugFromRawError = {
            viewModel.dismissRawErrorDialog()
            viewModel.openDebugConsole()
        },
        onSelectEngine = { newEngine -> viewModel.selectInitialEngine(newEngine) },
        onDismissEngineDialog = { viewModel.dismissInitialEnginePrompt() },
        onCloseTrackEditor = { viewModel.closeTrackEditor() },
        onSaveTrackMetadata = { track, title, artist ->
            viewModel.updateTrackWithRust(track, title, artist)
        },
        onDismissCreatePlaylist = { showCreatePlaylistDialog = false },
        onCreatePlaylist = { name, desc ->
            viewModel.createPlaylist(name, desc)
            showCreatePlaylistDialog = false
        },
        onDismissEditPlaylist = { playlistToEdit = null },
        onUpdatePlaylist = { pl, name, desc ->
            viewModel.updatePlaylist(pl, name, desc)
            playlistToEdit = null
        },
        onDismissAddToPlaylist = { trackForAddToPlaylist = null },
        onToggleLiked = { viewModel.toggleTrackLiked(it) },
        onToggleFavorite = { viewModel.toggleTrackFavorite(it) },
        onAddTrackToPlaylist = { pl, tr -> viewModel.addTrackToPlaylist(pl, tr) },
        onRemoveTrackFromPlaylist = { plId, tr -> viewModel.removeTrackFromPlaylist(plId, tr) },
        onRequestCreateNewPlaylist = { showCreatePlaylistDialog = true },
        getPlaylistIdsForTrack = { viewModel.getPlaylistIdsForTrack(it) }
    )
}
