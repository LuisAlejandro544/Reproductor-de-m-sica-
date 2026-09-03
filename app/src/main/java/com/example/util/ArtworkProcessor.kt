package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Random
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
     * Genera automáticamente una portada audiófila procedural y única para canciones sin carátula.
     * Utiliza un algoritmo determinista basado en el título y artista de la pista para crear
     * un arte con gradientes armónicos, ondas acústicas concéntricas, visualizador espectral estilizado
     * y monograma central. El resultado se comprime en formato WebP sin pérdida (Lossless WebP).
     *
     * @param context Contexto de la aplicación
     * @param title Título de la pista musical
     * @param artist Nombre del artista
     * @param trackId Identificador o marca temporal de la pista
     * @param oldArtworkPath Ruta previa a eliminar si existiese
     * @return Ruta absoluta del nuevo archivo WebP creado en covers/
     */
    suspend fun generateProceduralArtworkLosslessWebP(
        context: Context,
        title: String,
        artist: String,
        trackId: Long,
        oldArtworkPath: String? = null
    ): String = withContext(Dispatchers.IO) {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Semilla determinista basada en el contenido de la canción
        val seedInput = "${title.trim()}_#_${artist.trim()}".lowercase()
        val seed = seedInput.hashCode().toLong() and 0xFFFFFFFFL
        val random = Random(seed)

        // Paletas armónicas audiófilas de alto contraste y saturación elegante
        val palettes = listOf(
            Triple(Color.rgb(26, 35, 126), Color.rgb(0, 229, 255), Color.rgb(124, 77, 255)),   // Neon Cyan & Violet
            Triple(Color.rgb(74, 20, 140), Color.rgb(233, 30, 99), Color.rgb(255, 64, 129)),   // Magenta Pulse
            Triple(Color.rgb(0, 77, 64), Color.rgb(0, 230, 118), Color.rgb(29, 233, 182)),     // Emerald Wave
            Triple(Color.rgb(191, 54, 12), Color.rgb(255, 171, 0), Color.rgb(255, 109, 0)),   // Solar Flare
            Triple(Color.rgb(13, 71, 161), Color.rgb(0, 180, 216), Color.rgb(144, 224, 239)),  // Deep Sapphire
            Triple(Color.rgb(49, 27, 146), Color.rgb(101, 31, 255), Color.rgb(0, 230, 118)),   // Cyber Lime
            Triple(Color.rgb(38, 50, 56), Color.rgb(0, 191, 165), Color.rgb(255, 214, 0)),    // Audiophile Slate
            Triple(Color.rgb(136, 14, 79), Color.rgb(245, 0, 87), Color.rgb(255, 138, 128))    // Velvet Coral
        )
        val palette = palettes[random.nextInt(palettes.size)]
        val colorDark = palette.first
        val colorBright = palette.second
        val colorAccent = palette.third

        // 1. Fondo de gradiente suave diagonal
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(colorDark, colorAccent, colorBright),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // 2. Ondas acústicas concéntricas sutiles
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
        }
        val centerX = size / 2f
        val centerY = size / 2f
        val numRings = 5
        for (i in 1..numRings) {
            val radius = 50f + (i * 44f)
            ringPaint.strokeWidth = if (i % 2 == 0) 3f else 1.5f
            ringPaint.alpha = 25 + (i * 8)
            canvas.drawCircle(centerX, centerY, radius, ringPaint)
        }

        // 3. Espectro acústico procedural en la base
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val numBars = 18
        val barWidth = 14f
        val spacing = (size - (numBars * barWidth)) / (numBars + 1)
        for (i in 0 until numBars) {
            val factor = Math.sin((i.toDouble() / numBars) * Math.PI)
            val noise = (random.nextFloat() * 0.4f + 0.6f)
            val barHeight = (40f + (factor * 90f * noise)).toFloat()
            val left = spacing + i * (barWidth + spacing)
            val top = size - 36f - barHeight
            val right = left + barWidth
            val bottom = size - 36f
            barPaint.alpha = (40 + (factor * 110)).toInt().coerceIn(30, 160)
            canvas.drawRoundRect(RectF(left, top, right, bottom), 6f, 6f, barPaint)
        }

        // 4. Disco central translúcido estilo Glassmorphism
        val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 18, 18, 24)
            style = Paint.Style.FILL
        }
        val glassBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val centerRadius = 88f
        canvas.drawCircle(centerX, centerY, centerRadius, glassPaint)
        canvas.drawCircle(centerX, centerY, centerRadius, glassBorder)

        // 5. Monograma tipográfico estilizado (Inicial del título o nota musical)
        val initialChar = title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "♪"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 80f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(12f, 0f, 4f, Color.argb(180, 0, 0, 0))
        }
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(initialChar, 0, initialChar.length, textBounds)
        val textY = centerY + (textBounds.height() / 2f) - textBounds.bottom
        canvas.drawText(initialChar, centerX, textY, textPaint)

        // 6. Guardar en formato WebP Lossless al 100%
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
