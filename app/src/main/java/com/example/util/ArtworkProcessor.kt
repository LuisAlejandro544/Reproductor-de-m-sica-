package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Procesador de carátulas para conversión a WebP a máxima compresión sin pérdida de calidad (Lossless WebP).
 * Ejecuta todas sus operaciones de decodificación y compresión en un hilo secundario (Dispatchers.IO).
 */
object ArtworkProcessor {
    private const val TAG = "ArtworkProcessor"

    /**
     * Procesa una imagen proveniente de un Uri, la convierte a WebP Lossless al 100% de calidad
     * y la almacena en la carpeta 'Android/data/<pkg>/files/covers/'.
     *
     * @param context Contexto de la aplicación
     * @param sourceUri Uri de la imagen seleccionada por el usuario
     * @param trackId Identificador de la canción asociada
     * @param oldArtworkPath Ruta de la carátula anterior para limpieza automática de residuos
     * @return Ruta absoluta del nuevo archivo WebP creado
     */
    suspend fun processAndSaveArtworkLosslessWebP(
        context: Context,
        sourceUri: Uri,
        trackId: Long,
        oldArtworkPath: String? = null
    ): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("No se pudo abrir el flujo de entrada de la imagen seleccionada")

        val bitmap = inputStream.use { stream ->
            decodePreservingQuality(stream)
        } ?: throw IllegalStateException("No se pudo decodificar el formato de imagen seleccionado")

        saveBitmapToLosslessWebP(context, bitmap, trackId, oldArtworkPath)
    }

    /**
     * Procesa una imagen proveniente de bytes embebidos de un archivo de audio (MediaMetadataRetriever.embeddedPicture),
     * la convierte a WebP Lossless y la almacena en la carpeta de carátulas.
     */
    suspend fun processByteArrayToLosslessWebP(
        context: Context,
        bytes: ByteArray,
        trackId: Long,
        oldArtworkPath: String? = null
    ): String = withContext(Dispatchers.IO) {
        val inputStream = ByteArrayInputStream(bytes)
        val bitmap = decodePreservingQuality(inputStream)
            ?: throw IllegalStateException("No se pudo decodificar la carátula embebida")

        saveBitmapToLosslessWebP(context, bitmap, trackId, oldArtworkPath)
    }

    /**
     * Decodifica el flujo de imagen asegurando la máxima fidelidad cromática (ARGB_8888).
     */
    private fun decodePreservingQuality(inputStream: InputStream): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = true
            inDither = false
        }
        return BitmapFactory.decodeStream(inputStream, null, options)
    }

    /**
     * Comprime el Bitmap a formato WebP sin pérdida (Lossless WebP) con calidad máxima (100)
     * y lo almacena en la carpeta covers/.
     */
    private fun saveBitmapToLosslessWebP(
        context: Context,
        bitmap: Bitmap,
        trackId: Long,
        oldArtworkPath: String?
    ): String {
        val coversDir = AppStorageManager.getCoversDir(context)
        val uniqueName = "cover_${trackId}_${UUID.randomUUID().toString().take(8)}.webp"
        val targetFile = File(coversDir, uniqueName)

        val compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        FileOutputStream(targetFile).use { outStream ->
            val success = bitmap.compress(compressFormat, 100, outStream)
            if (!success) {
                targetFile.delete()
                throw IllegalStateException("Fallo en la compresión WebP sin pérdida")
            }
            outStream.flush()
        }

        // Liberar memoria del bitmap decodificado
        bitmap.recycle()

        // Limpiar la carátula anterior si estaba en el directorio de covers
        if (!oldArtworkPath.isNullOrBlank()) {
            try {
                val oldFile = File(oldArtworkPath)
                if (oldFile.exists() && oldFile.absolutePath.startsWith(coversDir.absolutePath)) {
                    oldFile.delete()
                    Log.d(TAG, "Carátula previa eliminada: ${oldFile.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo eliminar la carátula previa", e)
            }
        }

        Log.d(TAG, "Carátula WebP Lossless guardada exitosamente: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
        return targetFile.absolutePath
    }
}
