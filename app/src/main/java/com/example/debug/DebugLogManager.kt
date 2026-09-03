package com.example.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.playback.OboeAudioBridge
import com.example.util.RustAudioEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

enum class DebugLogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    CRITICAL
}

data class DebugLogEntry(
    val id: Long,
    val timestamp: String,
    val tag: String,
    val level: DebugLogLevel,
    val message: String,
    val rawErrorCode: Int? = null,
    val details: String? = null
)

object DebugLogManager {
    private const val MAX_LOGS = 400
    private val idCounter = AtomicLong(0)
    private val logBuffer = ConcurrentLinkedDeque<DebugLogEntry>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _logsFlow = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<DebugLogEntry>> = _logsFlow.asStateFlow()

    private val _lastErrorFlow = MutableStateFlow<DebugLogEntry?>(null)
    val lastErrorFlow: StateFlow<DebugLogEntry?> = _lastErrorFlow.asStateFlow()

    var isDebugModeEnabled: Boolean = true

    init {
        log(
            tag = "DebugLogManager",
            message = "Sistema de diagnóstico crudo en memoria iniciado.",
            level = DebugLogLevel.INFO
        )
    }

    fun log(
        tag: String,
        message: String,
        level: DebugLogLevel = DebugLogLevel.INFO,
        rawErrorCode: Int? = null,
        details: String? = null
    ) {
        val entry = DebugLogEntry(
            id = idCounter.incrementAndGet(),
            timestamp = timeFormat.format(Date()),
            tag = tag,
            level = level,
            message = message,
            rawErrorCode = rawErrorCode,
            details = details
        )

        logBuffer.addFirst(entry)
        while (logBuffer.size > MAX_LOGS) {
            logBuffer.removeLast()
        }

        _logsFlow.value = logBuffer.toList()

        if (level == DebugLogLevel.ERROR || level == DebugLogLevel.CRITICAL) {
            _lastErrorFlow.value = entry
            Log.e("RitmoDebug:$tag", "[RAW_CODE=$rawErrorCode] $message: $details")
        } else {
            Log.d("RitmoDebug:$tag", message)
        }
    }

    fun logError(
        tag: String,
        message: String,
        rawErrorCode: Int? = null,
        throwable: Throwable? = null,
        details: String? = null
    ) {
        val fullDetails = buildString {
            if (!details.isNullOrBlank()) {
                appendLine(details)
            }
            if (throwable != null) {
                appendLine("Excepción: ${throwable.javaClass.name}: ${throwable.message}")
                append(Log.getStackTraceString(throwable))
            }
        }.trim()

        log(
            tag = tag,
            message = message,
            level = DebugLogLevel.ERROR,
            rawErrorCode = rawErrorCode,
            details = fullDetails.ifBlank { null }
        )
    }

    fun clearLastError() {
        _lastErrorFlow.value = null
    }

    fun clearLogs() {
        logBuffer.clear()
        _logsFlow.value = emptyList()
        _lastErrorFlow.value = null
        log("DebugLogManager", "Registros de diagnóstico limpiados.", DebugLogLevel.INFO)
    }

    fun generateFullDiagnosticReport(context: Context): String {
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        return buildString {
            appendLine("# 🛠️ REPORTE DE DIAGNÓSTICO CRUDO — RITMO PLAYER")
            appendLine("Fecha/Hora: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("## 📱 Entorno de Dispositivo")
            appendLine("- Modelo: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("- Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("- ABIs Soportados: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("- Memoria JVM: ${usedMemMb}MB / ${maxMemMb}MB")
            appendLine()
            appendLine("## ⚙️ Estado de Motores Nativos")
            val oboeReady = OboeAudioBridge.isNativeReady()
            appendLine("- C++ Oboe Bridge: ${if (oboeReady) "✅ LISTO" else "❌ NO CARGADO"}")
            appendLine("- C++ Último Código de Error: ${OboeAudioBridge.getLastErrorCodeSafe()}")
            appendLine("- C++ Último Mensaje Error: ${OboeAudioBridge.getLastErrorStringSafe()}")
            appendLine("- C++ Info Dispositivo Audio: ${OboeAudioBridge.getAudioDeviceInfoSafe()}")
            appendLine("- C++ Stream Stats: ${OboeAudioBridge.getStreamStatsJsonSafe()}")
            appendLine()
            appendLine("## 🦀 Estado del Módulo Rust")
            val rustReady = RustAudioEngine.isAvailable()
            val rustPing = RustAudioEngine.ping()
            appendLine("- Rust Core Enlace: ${if (rustReady) "✅ LISTO" else "❌ INACTIVO"}")
            appendLine("- Rust Ping Test: $rustPing ${if (rustPing == 42) "(OK 42)" else "(FALLO)"}")
            appendLine("- Rust Versión del Motor: ${RustAudioEngine.getVersion()}")
            appendLine()
            appendLine("## 📜 Últimos Eventos y Códigos Crudos (${logBuffer.size})")
            logBuffer.forEach { entry ->
                val codeStr = entry.rawErrorCode?.let { " [CODE: $it]" } ?: ""
                appendLine("[${entry.timestamp}] [${entry.level}] [${entry.tag}]$codeStr ${entry.message}")
                if (!entry.details.isNullOrBlank()) {
                    appendLine("    Detalles: ${entry.details.replace("\n", "\n    ")}")
                }
            }
        }
    }

    fun copyReportToClipboard(context: Context): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val report = generateFullDiagnosticReport(context)
            val clip = ClipData.newPlainText("Ritmo Debug Report", report)
            clipboard?.setPrimaryClip(clip)
            log("DebugLogManager", "Reporte de diagnóstico copiado al portapapeles.", DebugLogLevel.INFO)
            true
        } catch (t: Throwable) {
            logError("DebugLogManager", "Fallo al copiar reporte al portapapeles", throwable = t)
            false
        }
    }
}
