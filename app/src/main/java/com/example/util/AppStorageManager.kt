package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.TrackEntity
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Administrador del almacenamiento estructurado y modular de la aplicación en:
 * 'Android/data/<package_name>/files/'
 *
 * Estructura de carpetas:
 * - music/: Archivos de audio importados
 * - covers/: Carátulas convertidas a WebP sin pérdida (Lossless WebP)
 * - artists/: Archivos JSON modulares individuales por canción conectando audio, arte y artista
 */
object AppStorageManager {
    private const val TAG = "AppStorageManager"

    private const val DIR_MUSIC = "music"
    private const val DIR_COVERS = "covers"
    private const val DIR_ARTISTS = "artists"

    /**
     * Raíz de almacenamiento de la app (context.getExternalFilesDir(null) = Android/data/<pkg>/files)
     * con fallback seguro a filesDir si el almacenamiento externo no está montado.
     */
    fun getBaseDir(context: Context): File {
        return context.getExternalFilesDir(null) ?: context.filesDir
    }

    /**
     * Carpeta dedicada a los archivos de audio: Android/data/<pkg>/files/music/
     */
    fun getMusicDir(context: Context): File {
        return File(getBaseDir(context), DIR_MUSIC).apply { mkdirs() }
    }

    /**
     * Carpeta dedicada a las carátulas e imágenes: Android/data/<pkg>/files/covers/
     */
    fun getCoversDir(context: Context): File {
        return File(getBaseDir(context), DIR_COVERS).apply { mkdirs() }
    }

    /**
     * Carpeta dedicada a la información de artista y metadatos modulares: Android/data/<pkg>/files/artists/
     */
    fun getArtistsMetadataDir(context: Context): File {
        return File(getBaseDir(context), DIR_ARTISTS).apply { mkdirs() }
    }

    /**
     * Obtiene el archivo JSON modular para una pista específica.
     */
    fun getMetadataJsonFile(context: Context, trackId: Long): File {
        return File(getArtistsMetadataDir(context), "track_${trackId}.json")
    }

    /**
     * Guarda o actualiza el archivo JSON modular e independiente para una canción.
     * Conecta el archivo de audio ('music/'), la carátula WebP ('covers/') y la metadata del artista ('artists/').
     */
    fun saveTrackMetadataJson(context: Context, track: TrackEntity) {
        try {
            val jsonFile = getMetadataJsonFile(context, track.id)
            val jsonObject = JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("durationMs", track.durationMs)
                put("audioPath", track.filePath)
                put("artworkPath", track.artworkPath ?: "")
                put("artworkFormat", if (!track.artworkPath.isNullOrBlank()) "image/webp" else "none")
                put("isArtworkLossless", !track.artworkPath.isNullOrBlank())
                put("dateAdded", track.dateAdded)
                put("isLiked", track.isLiked)
                put("isFavorite", track.isFavorite)
                put("lastModified", System.currentTimeMillis())

                val artistInfo = JSONObject().apply {
                    put("artistName", track.artist)
                    put("albumName", track.album)
                    put("storageSource", "Android/data/com.app/files")
                }
                put("artistMetadata", artistInfo)
            }

            FileOutputStream(jsonFile).use { fos ->
                fos.write(jsonObject.toString(4).toByteArray(Charsets.UTF_8))
            }
            Log.d(TAG, "Metadatos JSON modulares guardados en: ${jsonFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar metadatos JSON para la pista ${track.id}", e)
        }
    }

    /**
     * Elimina el archivo JSON modular de una pista al ser removida de la biblioteca.
     */
    fun deleteTrackMetadataJson(context: Context, trackId: Long) {
        try {
            val jsonFile = getMetadataJsonFile(context, trackId)
            if (jsonFile.exists()) {
                jsonFile.delete()
                Log.d(TAG, "Metadato JSON eliminado: ${jsonFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar JSON modular para la pista $trackId", e)
        }
    }
}
