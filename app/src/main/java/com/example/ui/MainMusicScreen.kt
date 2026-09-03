package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TrackEntity
import com.example.ui.components.DebugConsoleModal
import com.example.ui.components.EditTrackMetadataDialog
import com.example.ui.components.EngineSelectDialog
import com.example.ui.components.EqualizerModal
import com.example.ui.components.FullPlayerView
import com.example.ui.components.MiniPlayer
import com.example.ui.components.RawErrorDialog
import com.example.ui.main.EmptyLibraryView
import com.example.ui.main.MainProgressBanners
import com.example.ui.main.MainSearchBar
import com.example.ui.main.MainTopAppBar
import com.example.ui.main.TrackListView
import com.example.ui.theme.DarkBackground

/**
 * Pantalla principal modular de Ritmo Music Player.
 *
 * Descompuesta en submódulos especializados:
 * - [MainTopAppBar]: Identidad, chip de motor, importación, debug y settings.
 * - [MainProgressBanners]: Banners reactivos de importación y WebP sin pérdida.
 * - [MainSearchBar]: Búsqueda y filtrado dinámico.
 * - [EmptyLibraryView]: Estado de biblioteca vacía.
 * - [TrackListView]: Lista perezosa de canciones.
 * - [MiniPlayer] & [FullPlayerView]: Reproductor compacto y pantalla completa.
 * - [EqualizerModal]: Ecualizador paramétrico DSP de 10 bandas C++.
 * - [SettingsScreen]: Ajustes integrados.
 * - Modales de diagnóstico crudo, edición de tags en Rust y selección inicial de motor.
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

    val editingTrack by viewModel.editingTrack.collectAsStateWithLifecycle()
    val isDebugConsoleOpen by viewModel.isDebugConsoleOpen.collectAsStateWithLifecycle()
    val rawErrorDialog by viewModel.rawErrorDialog.collectAsStateWithLifecycle()

    var targetTrackForArtwork by remember { mutableStateOf<TrackEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    // Selector de carátula con Photo Picker oficial (zero-permissions)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedUri ->
            targetTrackForArtwork?.let { track ->
                viewModel.updateTrackArtwork(track, selectedUri)
            }
        }
    }

    // Audio file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris)
        }
    }

    // Back handler for closing equalizer
    BackHandler(enabled = isEqualizerOpen) {
        viewModel.closeEqualizer()
    }

    // Back handler for collapsing full player
    BackHandler(enabled = isPlayerExpanded && !isEqualizerOpen) {
        viewModel.setPlayerExpanded(false)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (currentTrack != null && !isPlayerExpanded) 80.dp else 16.dp)
            )
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Contenido principal de la biblioteca
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
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Docked Mini Player en la parte inferior
            if (currentTrack != null && !isPlayerExpanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    MiniPlayer(
                        track = currentTrack!!,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        onPlayPause = { viewModel.playPause() },
                        onNext = { viewModel.next() },
                        onExpand = { viewModel.setPlayerExpanded(true) }
                    )
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
                        }
                    )
                }
            }

            // Ecualizador Gráfico y Paramétrico de 10 Bandas (C++ DSP en Oboe y Media3)
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

            // Pantalla de Ajustes (Completa con animación horizontal)
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

    // Modal de Consola de Debug y Diagnóstico Crudo
    if (isDebugConsoleOpen) {
        DebugConsoleModal(
            onDismiss = { viewModel.closeDebugConsole() }
        )
    }

    // Diálogo de Edición de Metadatos con Rust
    editingTrack?.let { track ->
        EditTrackMetadataDialog(
            track = track,
            onDismiss = { viewModel.closeTrackEditor() },
            onSave = { newTitle, newArtist ->
                viewModel.updateTrackWithRust(track, newTitle, newArtist)
            }
        )
    }

    // Diálogo de Error Crudo Interceptado
    rawErrorDialog?.let { err ->
        RawErrorDialog(
            errorMessage = err,
            onDismiss = { viewModel.dismissRawErrorDialog() },
            onOpenDebugConsole = {
                viewModel.dismissRawErrorDialog()
                viewModel.openDebugConsole()
            }
        )
    }

    // Diálogo de Selección Inicial de Motor (Sólo en primer inicio)
    if (showEngineDialog) {
        EngineSelectDialog(
            currentEngine = activeEngine,
            onEngineSelected = { newEngine ->
                viewModel.selectInitialEngine(newEngine)
            },
            onDismissRequest = {
                viewModel.dismissInitialEnginePrompt()
            }
        )
    }
}
