package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.TrackEntity
import com.example.debug.DebugLogLevel
import com.example.debug.DebugLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MusicImporter {

    suspend fun importAudioUris(context: Context, uris: List<Uri>): List<TrackEntity> =
        withContext(Dispatchers.IO) {
            val importedList = mutableListOf<TrackEntity>()
            val musicDir = AppStorageManager.getMusicDir(context)

            for (uri in uris) {
                try {
                    val displayName = getFileName(context, uri) ?: "audio_${System.currentTimeMillis()}.mp3"
                    val safeName = "${UUID.randomUUID()}_$displayName"
                    val targetFile = File(musicDir, safeName)

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(targetFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    if (!targetFile.exists() || targetFile.length() == 0L) {
                        continue
                    }

                    // Extracción OBLIGATORIA de metadatos mediante el motor nativo Rust
                    val rustMeta = RustAudioEngine.extractMetadata(targetFile.absolutePath)
                    var title: String? = rustMeta?.title
                    var artist: String? = rustMeta?.artist
                    var album: String? = rustMeta?.album
                    var durationMs = rustMeta?.durationMs ?: 0L
                    var artworkPath: String? = null

                    // 1. Carátula procesada directamente desde el binario por Rust
                    val rustArtworkBytes = RustAudioEngine.extractArtwork(targetFile.absolutePath)
                    if (rustArtworkBytes != null && rustArtworkBytes.isNotEmpty()) {
                        try {
                            artworkPath = ArtworkProcessor.processByteArrayToLosslessWebP(
                                context = context,
                                bytes = rustArtworkBytes,
                                trackId = System.currentTimeMillis()
                            )
                            DebugLogManager.log("MusicImporter", "Carátula audiófila extraída por Rust para: $displayName", DebugLogLevel.DEBUG)
                        } catch (e: Exception) {
                            DebugLogManager.logError("MusicImporter", "Error procesando carátula extraída por Rust", throwable = e)
                        }
                    }

                    // 2. Soporte complementario de duración o carátula si el archivo carece de tags binarios
                    if (durationMs == 0L || artworkPath == null) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(targetFile.absolutePath)
                            if (durationMs == 0L) {
                                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                durationMs = durStr?.toLongOrNull() ?: 0L
                            }
                            if (artworkPath == null) {
                                val embeddedArt = retriever.embeddedPicture
                                if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                                    artworkPath = ArtworkProcessor.processByteArrayToLosslessWebP(
                                        context = context,
                                        bytes = embeddedArt,
                                        trackId = System.currentTimeMillis()
                                    )
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            try {
                                retriever.release()
                            } catch (_: Exception) {
                            }
                        }
                    }

                    val cleanTitle = if (!title.isNullOrBlank()) {
                        title.trim()
                    } else {
                        displayName.substringBeforeLast(".").replace("_", " ")
                    }
                    val cleanArtist = if (!artist.isNullOrBlank()) artist.trim() else "Artista desconocido"
                    val cleanAlbum = if (!album.isNullOrBlank()) album.trim() else "Álbum desconocido"

                    // Generación procedural automática de portada si la pista no posee carátula embebida
                    if (artworkPath == null) {
                        try {
                            artworkPath = ArtworkProcessor.generateProceduralArtworkLosslessWebP(
                                context = context,
                                title = cleanTitle,
                                artist = cleanArtist,
                                trackId = System.currentTimeMillis()
                            )
                            DebugLogManager.log("MusicImporter", "Portada procedural generada automáticamente para: $cleanTitle", DebugLogLevel.INFO)
                        } catch (e: Exception) {
                            DebugLogManager.logError("MusicImporter", "Fallo al generar portada procedural", throwable = e)
                        }
                    }

                    val track = TrackEntity(
                        title = cleanTitle,
                        artist = cleanArtist,
                        album = cleanAlbum,
                        durationMs = durationMs,
                        filePath = targetFile.absolutePath,
                        artworkPath = artworkPath,
                        dateAdded = System.currentTimeMillis()
                    )
                    importedList.add(track)
                } catch (_: Exception) {
                }
            }
            importedList
        }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return name ?: uri.lastPathSegment
    }
}
