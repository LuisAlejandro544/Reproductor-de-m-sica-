package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.TrackEntity
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

                    var title: String? = null
                    var artist: String? = null
                    var album: String? = null
                    var durationMs = 0L
                    var artworkPath: String? = null

                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(targetFile.absolutePath)
                        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        durationMs = durStr?.toLongOrNull() ?: 0L

                        val embeddedArt = retriever.embeddedPicture
                        if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                            try {
                                artworkPath = ArtworkProcessor.processByteArrayToLosslessWebP(
                                    context = context,
                                    bytes = embeddedArt,
                                    trackId = System.currentTimeMillis()
                                )
                            } catch (_: Exception) {
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {
                        }
                    }

                    val cleanTitle = if (!title.isNullOrBlank()) {
                        title.trim()
                    } else {
                        displayName.substringBeforeLast(".").replace("_", " ")
                    }
                    val cleanArtist = if (!artist.isNullOrBlank()) artist.trim() else "Artista desconocido"
                    val cleanAlbum = if (!album.isNullOrBlank()) album.trim() else "Álbum desconocido"

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
