package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.debug.DebugLogEntry
import com.example.debug.DebugLogLevel
import com.example.debug.DebugLogManager
import com.example.debug.FpsMonitor
import com.example.debug.FpsStatus
import com.example.playback.OboeAudioBridge
import com.example.util.RustAudioEngine
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleModal(
    onDismiss: () -> Unit,
    onOpenDatabaseInspector: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val logs by DebugLogManager.logsFlow.collectAsState()
    var refreshTrigger by remember { mutableStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("debug_console_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Cabecera de la Consola Debug
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Consola de Diagnóstico & Debug",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Códigos de Error Crudos y Telemetría Nativa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_debug_console_button")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tarjeta de Estado Nativo en Tiempo Real
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ESTADO DE MOTORES NATIVOS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val oboeCode = OboeAudioBridge.getLastErrorCodeSafe()
                    val oboeMsg = OboeAudioBridge.getLastErrorStringSafe()
                    val rustPing = RustAudioEngine.ping()

                    Text(
                        text = "• C++ Oboe Bridge: ${if (OboeAudioBridge.isNativeReady()) "✅ Conectado" else "❌ Desconectado"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• Oboe Último Código Crudo: $oboeCode ($oboeMsg)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (oboeCode != 0 && oboeCode != -998) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Rust Audiophile Engine: ${if (RustAudioEngine.isAvailable()) "✅ Activo" else "❌ Inactivo"} | Ping: $rustPing",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• Versión Rust: ${RustAudioEngine.getVersion()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• Timber Logging: ✅ Activo (RitmoDebugTree)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• Detección Fugas: ✅ LeakCanary listo (Debug APK)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )

                    val fpsMetrics by FpsMonitor.metricsFlow.collectAsState()
                    val fpsColor = when (fpsMetrics.status) {
                        FpsStatus.EXCELLENT -> Color(0xFF00E676)
                        FpsStatus.GOOD -> Color(0xFF40C4FF)
                        FpsStatus.MODERATE -> Color(0xFFFFD600)
                        FpsStatus.JANK -> Color(0xFFFF5252)
                    }
                    Text(
                        text = "• Monitor FPS (Takt): ${String.format(Locale.US, "%.1f", fpsMetrics.fps)} FPS (${String.format(Locale.US, "%.1f", fpsMetrics.frameTimeMs)} ms) | Drops: ${fpsMetrics.droppedFrames}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = fpsColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Herramientas Avanzadas en Pantalla (Touch targets >= 48dp)
            Text(
                text = "HERRAMIENTAS DE DEPURACIÓN EN PANTALLA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            val fpsMetricsState by FpsMonitor.metricsFlow.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón Inspector de Base de Datos Room
                Button(
                    onClick = {
                        onOpenDatabaseInspector?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_room_inspector_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Inspector Room", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Botón Monitor de FPS Overlay (Takt / TinyDancer)
                OutlinedButton(
                    onClick = {
                        FpsMonitor.toggleOverlay()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("toggle_fps_overlay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (fpsMetricsState.isOverlayVisible) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (fpsMetricsState.isOverlayVisible) "FPS: ACTIVO" else "FPS: INACTIVO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            val savedCrashReport = remember { com.example.debug.RitmoCrashHandler.getLastCrashReport(context) }
            var currentCrashReport by remember { mutableStateOf(savedCrashReport) }

            if (currentCrashReport != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ ÚLTIMO CRASH NO CONTROLADO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            IconButton(
                                onClick = {
                                    com.example.debug.RitmoCrashHandler.clearLastCrashReport(context)
                                    currentCrashReport = null
                                    Toast.makeText(context, "Registro de crash limpiado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Descartar crash",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentCrashReport ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 6
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botonera de Acciones Táctiles (Mínimo 48dp de alto para móvil)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val ok = DebugLogManager.copyReportToClipboard(context)
                        if (ok) {
                            Toast.makeText(context, "Reporte completo copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("copy_debug_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar Reporte", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        DebugLogManager.clearLogs()
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("clear_debug_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Limpiar Logs",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpiar", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "REGISTRO DE EVENTOS Y ERRORES (${logs.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Lista con Scroll de Logs
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "No hay eventos registrados en este momento.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else {
                    items(logs, key = { it.id }) { entry ->
                        LogEntryItem(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(entry: DebugLogEntry) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val levelColor = when (entry.level) {
        DebugLogLevel.CRITICAL -> Color(0xFFD50000)
        DebugLogLevel.ERROR -> MaterialTheme.colorScheme.error
        DebugLogLevel.WARN -> Color(0xFFF57F17)
        DebugLogLevel.INFO -> MaterialTheme.colorScheme.primary
        DebugLogLevel.DEBUG -> Color(0xFF00897B)
        DebugLogLevel.VERBOSE -> MaterialTheme.colorScheme.outline
    }

    val copySingleLog = {
        val singleLogText = buildString {
            append("[${entry.level.name}] ${entry.timestamp} | ${entry.tag}")
            if (entry.rawErrorCode != null) {
                append(" | CODE: ${entry.rawErrorCode}")
            }
            append("\n${entry.message}")
            if (!entry.details.isNullOrBlank()) {
                append("\nDetalles: ${entry.details}")
            }
        }
        clipboardManager.setText(AnnotatedString(singleLogText))
        Toast.makeText(context, "Log copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = copySingleLog)
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = levelColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = entry.level.name,
                    color = levelColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (entry.rawErrorCode != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "CODE: ${entry.rawErrorCode}",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = entry.tag,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.outline
            )

            // Botón táctil individual de copiado (48dp para facilidad de uso en smartphone)
            IconButton(
                onClick = copySingleLog,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("copy_log_button_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copiar este log",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (!entry.details.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.details,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
