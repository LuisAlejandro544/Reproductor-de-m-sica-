package com.example.ui.delegates

import android.app.Application
import android.net.Uri
import com.example.data.TrackEntity
import com.example.data.TrackRepository
import com.example.playback.AudioPlayerManager
import com.example.util.AppStorageManager
import com.example.util.ArtworkProcessor
import com.example.util.LyricsParser
import com.example.util.MusicImporter
import com.example.util.RustAudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Delegado modular responsable de las operaciones de medios y metadatos de pistas:
 * Importación de archivos, actualización y reescritura de tags en Rust (C-ABI),
 * procesamiento de carátulas en formato Lossless WebP y persistencia de letras LRC.
 */
class TrackMediaDelegate(
    private val application: Application,
    private val trackRepository: TrackRepository,
    private val playerManager: AudioPlayerManager,
    private val scope: CoroutineScope,
    private val onShowMessage: (String) -> Unit,
    private val onRawError: (String) -> Unit
) {
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isUpdatingArtwork = MutableStateFlow(false)
    val isUpdatingArtwork: StateFlow<Boolean> = _isUpdatingArtwork.asStateFlow()

    private val _editingTrack = MutableStateFlow<TrackEntity?>(null)
    val editingTrack: StateFlow<TrackEntity?> = _editingTrack.asStateFlow()

    fun openTrackEditor(track: TrackEntity) {
        _editingTrack.value = track
    }

    fun closeTrackEditor() {
        _editingTrack.value = null
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            _isImporting.value = true
            try {
                val newTracks = MusicImporter.importAudioUris(application, uris)
                if (newTracks.isNotEmpty()) {
                    trackRepository.insertTracks(newTracks)
                    for (track in newTracks) {
                        AppStorageManager.saveTrackMetadataJson(application, track)
                    }
                    onShowMessage(
                        if (newTracks.size == 1) "Canción añadida: ${newTracks.first().title}"
                        else "${newTracks.size} canciones añadidas a tu biblioteca"
                    )
                } else {
                    onShowMessage("No se pudieron importar los archivos de audio seleccionados")
                }
            } catch (e: Exception) {
                onShowMessage("Error al importar: ${e.localizedMessage ?: "Desconocido"}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun updateTrackWithRust(
        track: TrackEntity,
        newTitle: String,
        newArtist: String,
        newAlbum: String = "",
        newGenre: String = "",
        newYear: String = ""
    ) {
        scope.launch(Dispatchers.IO) {
            val result = RustAudioEngine.updateTrackMetadata(
                track.filePath,
                newTitle.trim(),
                newArtist.trim(),
                newAlbum.trim(),
                newGenre.trim(),
                newYear.trim()
            )
            if (result.isSuccess) {
                val reanalyzed = RustAudioEngine.extractMetadata(track.filePath)
                val finalTitle = reanalyzed?.title?.takeIf { it.isNotBlank() } ?: newTitle.trim()
                val finalArtist = reanalyzed?.artist?.takeIf { it.isNotBlank() } ?: newArtist.trim()
                val finalAlbum = reanalyzed?.album?.takeIf { it.isNotBlank() } ?: newAlbum.trim().ifEmpty { track.album }

                trackRepository.updateTrackMetadata(track.id, finalTitle, finalArtist, finalAlbum)
                val updatedEntity = track.copy(title = finalTitle, artist = finalArtist, album = finalAlbum)
                AppStorageManager.saveTrackMetadataJson(application, updatedEntity)

                if (playerManager.currentTrack.value?.id == track.id) {
                    playerManager.updateCurrentTrack(updatedEntity)
                }

                onShowMessage("Tags actualizados por Rust: '$finalTitle' - '$finalArtist' (Álbum: '$finalAlbum')")
                _editingTrack.value = null
            } else {
                val err = result.exceptionOrNull()
                val msg = "Fallo en Rust al actualizar: ${err?.message}"
                onShowMessage(msg)
                onRawError("[RUST_NATIVE_ERR_TAG_WRITE] $msg")
            }
        }
    }

    fun updateTrackArtwork(track: TrackEntity, imageUri: Uri) {
        scope.launch(Dispatchers.IO) {
            _isUpdatingArtwork.value = true
            try {
                val newArtworkPath = ArtworkProcessor.processAndSaveArtworkLosslessWebP(
                    context = application,
                    sourceUri = imageUri,
                    trackId = track.id,
                    oldArtworkPath = track.artworkPath
                )

                val updatedTrack = track.copy(artworkPath = newArtworkPath)
                trackRepository.updateArtwork(track.id, newArtworkPath)
                AppStorageManager.saveTrackMetadataJson(application, updatedTrack)

                if (playerManager.currentTrack.value?.id == track.id) {
                    playerManager.updateCurrentTrack(updatedTrack)
                }

                onShowMessage("Carátula actualizada en formato WebP sin pérdida")
            } catch (e: Exception) {
                onShowMessage("Error al procesar carátula: ${e.localizedMessage ?: "Desconocido"}")
            } finally {
                _isUpdatingArtwork.value = false
            }
        }
    }

    fun updateTrackLyrics(track: TrackEntity, lyrics: String?) {
        scope.launch(Dispatchers.IO) {
            try {
                val cleanedLyrics = lyrics?.trim()?.ifEmpty { null }
                trackRepository.updateLyrics(track.id, cleanedLyrics)
                val updatedTrack = track.copy(lyrics = cleanedLyrics)
                AppStorageManager.saveTrackMetadataJson(application, updatedTrack)

                if (playerManager.currentTrack.value?.id == track.id) {
                    playerManager.updateCurrentTrack(updatedTrack)
                }

                if (!cleanedLyrics.isNullOrBlank()) {
                    LyricsParser.writeCompanionLrcFile(track.filePath, cleanedLyrics)
                }

                onShowMessage(
                    if (cleanedLyrics.isNullOrBlank()) "Letras eliminadas"
                    else "Letras sincronizadas correctamente"
                )
            } catch (e: Exception) {
                onShowMessage("Error al guardar letras: ${e.localizedMessage ?: "Desconocido"}")
            }
        }
    }

    /**
     * Asegura carátula procedural WebP y letras complementarias para una lista de canciones.
     */
    suspend fun ensureMetadataAndArtwork(tracks: List<TrackEntity>) {
        for (track in tracks) {
            if (track.artworkPath.isNullOrBlank()) {
                try {
                    val proceduralArt = ArtworkProcessor.generateProceduralArtworkLosslessWebP(
                        context = application,
                        title = track.title,
                        artist = track.artist,
                        trackId = track.id
                    )
                    val updatedTrack = track.copy(artworkPath = proceduralArt)
                    trackRepository.updateArtwork(track.id, proceduralArt)
                    AppStorageManager.saveTrackMetadataJson(application, updatedTrack)
                    if (playerManager.currentTrack.value?.id == track.id) {
                        playerManager.updateCurrentTrack(updatedTrack)
                    }
                } catch (_: Exception) {
                }
            }

            if (track.lyrics.isNullOrBlank()) {
                try {
                    val companionLyrics = LyricsParser.readCompanionLyricsFile(track.filePath)
                    if (!companionLyrics.isNullOrBlank()) {
                        trackRepository.updateLyrics(track.id, companionLyrics)
                        val trackWithLyrics = track.copy(lyrics = companionLyrics)
                        AppStorageManager.saveTrackMetadataJson(application, trackWithLyrics)
                        if (playerManager.currentTrack.value?.id == track.id) {
                            playerManager.updateCurrentTrack(trackWithLyrics)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}
