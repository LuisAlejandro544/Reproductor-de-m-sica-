package com.example.util

import java.io.File
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class ParsedLyrics(
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val rawText: String
)

object LyricsParser {
    // Expresión regular para capturar timestamps tipo [01:23.45] o [01:23.456] o [01:23]
    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{2,3}))?\\]")

    fun parse(lyricsText: String?): ParsedLyrics {
        if (lyricsText.isNullOrBlank()) {
            return ParsedLyrics(isSynced = false, lines = emptyList(), rawText = "")
        }

        val rawLines = lyricsText.lines()
        val syncedLines = mutableListOf<LyricLine>()
        var hasAtLeastOneTimestamp = false

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Ignorar tags de metadatos como [ti:...], [ar:...], [al:...]
            if (trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") ||
                trimmed.startsWith("[offset:") || trimmed.startsWith("[length:")
            ) {
                continue
            }

            val matcher = TIME_TAG_PATTERN.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastEnd = 0

            while (matcher.find()) {
                hasAtLeastOneTimestamp = true
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val millisRaw = matcher.group(3)
                val millis = when {
                    millisRaw == null -> 0L
                    millisRaw.length == 2 -> millisRaw.toLongOrNull()?.times(10) ?: 0L
                    millisRaw.length == 3 -> millisRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                timestamps.add(totalMs)
                lastEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val lineContent = trimmed.substring(lastEnd).trim()
                for (time in timestamps) {
                    syncedLines.add(LyricLine(timeMs = time, text = lineContent))
                }
            }
        }

        return if (hasAtLeastOneTimestamp && syncedLines.isNotEmpty()) {
            syncedLines.sortBy { it.timeMs }
            ParsedLyrics(
                isSynced = true,
                lines = syncedLines,
                rawText = lyricsText
            )
        } else {
            // Letras en texto plano sin marcas de tiempo
            val plainLines = rawLines
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { LyricLine(timeMs = -1L, text = it) }

            ParsedLyrics(
                isSynced = false,
                lines = plainLines,
                rawText = lyricsText
            )
        }
    }

    /**
     * Localiza el índice de la línea actualmente activa según la posición de reproducción actual.
     */
    fun findActiveLineIndex(lines: List<LyricLine>, currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var activeIndex = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= currentPositionMs) {
                activeIndex = i
            } else {
                break
            }
        }
        return if (activeIndex >= 0) activeIndex else 0
    }

    /**
     * Busca archivos de letras complementarios (.lrc o .txt) en la misma ruta del archivo de audio.
     */
    fun readCompanionLyricsFile(audioFilePath: String): String? {
        return try {
            val audioFile = File(audioFilePath)
            val parent = audioFile.parentFile ?: return null
            val baseName = audioFile.nameWithoutExtension

            val candidates = listOf(
                File(parent, "$baseName.lrc"),
                File(parent, "$baseName.LRC"),
                File(parent, "$baseName.txt"),
                File(parent, "$baseName.TXT")
            )

            for (file in candidates) {
                if (file.exists() && file.canRead()) {
                    val content = file.readText(Charsets.UTF_8).trim()
                    if (content.isNotBlank()) {
                        return content
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Guarda o exporta un archivo .lrc en la misma carpeta que el archivo de audio si es escribible.
     */
    fun writeCompanionLrcFile(audioFilePath: String, content: String): Boolean {
        return try {
            val audioFile = File(audioFilePath)
            val parent = audioFile.parentFile ?: return false
            val lrcFile = File(parent, "${audioFile.nameWithoutExtension}.lrc")
            lrcFile.writeText(content, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }
}
