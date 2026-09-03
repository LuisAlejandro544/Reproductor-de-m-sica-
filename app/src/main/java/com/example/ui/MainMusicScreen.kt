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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TrackEntity
import com.example.playback.AudioEngineType
import com.example.ui.components.EngineSelectDialog
import com.example.ui.components.EqualizerModal
import com.example.ui.components.FullPlayerView
import com.example.ui.components.MiniPlayer
import com.example.ui.components.TrackListItem
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

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
            // Main Content: Header, Search, Tracks List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(GreenDark.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = GreenAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Ritmo",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = when {
                                    tracks.isEmpty() -> "Reproductor local"
                                    tracks.size == 1 -> "1 canción"
                                    else -> "${tracks.size} canciones"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Engine Switcher Chip
                        Surface(
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { viewModel.openSettings() }
                                .testTag("switch_audio_engine_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (activeEngine == AudioEngineType.OBOE_CPP) Icons.Default.Speed else Icons.Default.Memory,
                                    contentDescription = "Motor de Audio",
                                    tint = if (activeEngine == AudioEngineType.OBOE_CPP) GreenAccent else Color(0xFF64B5F6),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = activeEngine.shortName,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Import Button
                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("audio/*")) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GreenPrimary,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .testTag("import_music_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Importar",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Importar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Settings Button
                        IconButton(
                            onClick = { viewModel.openSettings() },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configuración",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Importing indicator banner
                if (isImporting) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceElevated)
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = GreenPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Importando pistas y extrayendo metadatos...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = GreenPrimary,
                            trackColor = DarkSurface
                        )
                    }
                }

                // Banner de procesamiento de carátula en WebP (hilo secundario)
                if (isUpdatingArtwork) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceElevated)
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = GreenAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Comprimiendo carátula en WebP sin pérdida...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = GreenAccent,
                            trackColor = DarkSurface
                        )
                    }
                }

                // Search Bar (if library has items or search is active)
                if (tracks.isNotEmpty() || searchQuery.isNotBlank()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = {
                            Text(
                                text = "Buscar en tu música...",
                                color = TextTertiary,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = TextSecondary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar búsqueda",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("search_tracks_input")
                    )
                }

                // Main Content: Empty State or Track List
                if (tracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = if (searchQuery.isNotBlank()) "Sin resultados" else "Tu biblioteca está vacía",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "No se encontraron canciones que coincidan con \"$searchQuery\"."
                                } else {
                                    "Importa tus canciones favoritas directamente desde el almacenamiento de tu teléfono. Solo tendrás aquí los archivos que decidas agregar."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            if (searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { filePickerLauncher.launch(arrayOf("audio/*")) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GreenPrimary,
                                        contentColor = DarkBackground
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                    modifier = Modifier.testTag("empty_state_import_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Seleccionar archivos de música",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("track_lazy_list"),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = if (currentTrack != null) 100.dp else 24.dp
                        )
                    ) {
                        items(
                            items = tracks,
                            key = { it.id }
                        ) { track ->
                            TrackListItem(
                                track = track,
                                isCurrent = currentTrack?.id == track.id,
                                isPlaying = isPlaying && currentTrack?.id == track.id,
                                onClick = { viewModel.playTrack(track) },
                                onDelete = { viewModel.deleteTrack(track) },
                                onEditArtwork = {
                                    targetTrackForArtwork = track
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Docked Mini Player at bottom
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

            // Full Player Screen (Overlaid with smooth animation)
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

            // Settings Screen (Independent full screen with smooth animation)
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
                    onNavigateBack = { viewModel.closeSettings() }
                )
            }
        }
    }

    // Engine Selection Dialog (Shown ONLY on first launch)
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
