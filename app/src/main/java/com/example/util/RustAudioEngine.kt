package com.example.util

import com.example.debug.DebugLogLevel
import com.example.debug.DebugLogManager
import org.json.JSONObject
import java.io.File

data class RustAudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val trackNumber: Int?,
    val year: Int?,
    val durationMs: Long,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val channels: Int?,
    val formatName: String,
    val hasArtwork: Boolean,
    val engineBadge: String
)

object RustAudioEngine {
    private const val TAG = "RustAudioEngine"
    private var isLoaded = false

    init {
        // Intento de carga de la librería ritmo_rust (si está presente como cdylib)
        try {
            System.loadLibrary("ritmo_rust")
            isLoaded = true
            DebugLogManager.log(TAG, "Librería nativa Rust 'ritmo_rust' cargada con éxito.", DebugLogLevel.INFO)
        } catch (_: UnsatisfiedLinkError) {
            // Si está compilado dentro de ritmo_native o no está aún separada
            try {
                System.loadLibrary("ritmo_native")
                isLoaded = true
                DebugLogManager.log(TAG, "Símbolos nativos de Rust disponibles a través de 'ritmo_native'.", DebugLogLevel.INFO)
            } catch (e2: Throwable) {
                DebugLogManager.log(TAG, "No se pudo cargar ritmo_rust ni ritmo_native: ${e2.message}", DebugLogLevel.WARN)
                isLoaded = false
            }
        } catch (t: Throwable) {
            DebugLogManager.logError(TAG, "Fallo inesperado al cargar librerías nativas", throwable = t)
            isLoaded = false
        }
    }

    fun isAvailable(): Boolean = isLoaded

    fun ping(): Int = if (isLoaded) {
        try {
            nativePing()
        } catch (t: Throwable) {
            DebugLogManager.logError(TAG, "Error invocando nativePing", throwable = t)
            -1
        }
    } else -2

    fun getVersion(): String = if (isLoaded) {
        try {
            nativeGetVersion()
        } catch (t: Throwable) {
            "Error obteniendo versión: ${t.message}"
        }
    } else "Inactivo (Sin librería nativa)"

    /**
     * Extrae e indexa metadatos audiófilos (ID3v1, ID3v2, FLAC, Vorbis, Opus, APE, MP4/M4A)
     * procesados enteramente por el núcleo en Rust.
     */
    fun extractMetadata(filePath: String): RustAudioMetadata? {
        if (!isLoaded) {
            DebugLogManager.log(TAG, "Rust no disponible para extraer metadatos de: $filePath", DebugLogLevel.WARN)
            return null
        }

        val startTime = System.currentTimeMillis()
        return try {
            val jsonStr = nativeExtractMetadata(filePath)
            val elapsed = System.currentTimeMillis() - startTime

            if (jsonStr.isBlank() || jsonStr == "{}") {
                DebugLogManager.log(TAG, "Metadatos vacíos devueltos por Rust para: $filePath", DebugLogLevel.WARN)
                return null
            }

            val json = JSONObject(jsonStr)
            val meta = RustAudioMetadata(
                title = json.optString("title").takeIf { it.isNotBlank() },
                artist = json.optString("artist").takeIf { it.isNotBlank() },
                album = json.optString("album").takeIf { it.isNotBlank() },
                albumArtist = json.optString("album_artist").takeIf { it.isNotBlank() },
                genre = json.optString("genre").takeIf { it.isNotBlank() },
                trackNumber = if (json.has("track_number") && !json.isNull("track_number")) json.optInt("track_number") else null,
                year = if (json.has("year") && !json.isNull("year")) json.optInt("year") else null,
                durationMs = json.optLong("duration_ms", 0L),
                sampleRate = if (json.has("sample_rate") && !json.isNull("sample_rate")) json.optInt("sample_rate") else null,
                bitDepth = if (json.has("bit_depth") && !json.isNull("bit_depth")) json.optInt("bit_depth") else null,
                channels = if (json.has("channels") && !json.isNull("channels")) json.optInt("channels") else null,
                formatName = json.optString("format_name", "Desconocido"),
                hasArtwork = json.optBoolean("has_artwork", false),
                engineBadge = json.optString("engine_badge", "Rust Core")
            )

            DebugLogManager.log(
                TAG,
                "Metadatos extraídos por Rust en ${elapsed}ms: '${meta.title ?: "Sin título"}' - '${meta.artist ?: "Sin artista"}' (${meta.formatName})",
                DebugLogLevel.DEBUG
            )
            meta
        } catch (t: Throwable) {
            DebugLogManager.logError(
                TAG,
                "Error en Rust al parsear metadatos de: $filePath",
                rawErrorCode = -501,
                throwable = t
            )
            null
        }
    }

    /**
     * Extrae los bytes binarios de la carátula embebida directamente desde Rust.
     */
    fun extractArtwork(filePath: String): ByteArray? {
        if (!isLoaded) return null
        return try {
            nativeExtractArtwork(filePath)
        } catch (t: Throwable) {
            DebugLogManager.logError(TAG, "Error extrayendo carátula con Rust para: $filePath", throwable = t)
            null
        }
    }

    /**
     * Actualiza metadatos avanzados (título, artista, álbum, género y año) directamente en el archivo físico utilizando Rust.
     */
    fun updateTrackMetadata(
        filePath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String = "",
        newGenre: String = "",
        newYear: String = ""
    ): Result<Unit> {
        if (!isLoaded) {
            return Result.failure(IllegalStateException("El núcleo de Rust no está cargado."))
        }

        val file = File(filePath)
        if (!file.exists() || !file.canWrite()) {
            val err = "El archivo no existe o no tiene permisos de escritura: $filePath"
            DebugLogManager.logError(TAG, err, rawErrorCode = -404)
            return Result.failure(IllegalArgumentException(err))
        }

        val startTime = System.currentTimeMillis()
        val code = try {
            nativeUpdateMetadata(filePath, newTitle, newArtist, newAlbum, newGenre, newYear)
        } catch (t: Throwable) {
            DebugLogManager.logError(TAG, "Excepción nativa al actualizar tags con Rust", rawErrorCode = -999, throwable = t)
            return Result.failure(t)
        }

        val elapsed = System.currentTimeMillis() - startTime
        return if (code == 0) {
            DebugLogManager.log(
                TAG,
                "Metadatos actualizados con éxito por Rust en ${elapsed}ms: '$newTitle' - '$newArtist' [$newAlbum, $newGenre, $newYear]",
                DebugLogLevel.INFO,
                rawErrorCode = 0
            )
            Result.success(Unit)
        } else {
            val msg = "Rust retornó código de error $code al modificar metadatos de: $filePath"
            DebugLogManager.logError(TAG, msg, rawErrorCode = code)
            Result.failure(RuntimeException(msg))
        }
    }

    // Declaraciones nativas JNI
    @JvmStatic
    private external fun nativePing(): Int

    @JvmStatic
    private external fun nativeGetVersion(): String

    @JvmStatic
    private external fun nativeExtractMetadata(filePath: String): String

    @JvmStatic
    private external fun nativeExtractArtwork(filePath: String): ByteArray?

    @JvmStatic
    private external fun nativeUpdateMetadata(
        filePath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newYear: String
    ): Int
}
