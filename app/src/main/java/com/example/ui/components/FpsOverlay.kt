package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.debug.FpsMetrics
import com.example.debug.FpsMonitor
import com.example.debug.FpsStatus
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Indicador visual flotante de FPS y rendimiento en tiempo real (estilo Takt / TinyDancer).
 *
 * Muestra la tasa de refresco actual y latencia de frame directamente en la interfaz móvil.
 * Un toque en el chip despliega el panel de estadísticas y opciones de depuración.
 */
@Composable
fun FpsOverlay(
    modifier: Modifier = Modifier
) {
    val metrics by FpsMonitor.metricsFlow.collectAsState()
    var showDetailsDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = metrics.isOverlayVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        val statusColor = when (metrics.status) {
            FpsStatus.EXCELLENT -> GreenAccent
            FpsStatus.GOOD -> Color(0xFF40C4FF)
            FpsStatus.MODERATE -> Color(0xFFFFD600)
            FpsStatus.JANK -> Color(0xFFFF5252)
        }

        Surface(
            color = DarkSurface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .clickable { showDetailsDialog = true }
                .testTag("fps_overlay_chip")
        ) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Indicador de pulso de color
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )

                Text(
                    text = String.format(Locale.US, "%.1f FPS", metrics.fps),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = TextPrimary
                )

                Text(
                    text = String.format(Locale.US, "%.1f ms", metrics.frameTimeMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = TextSecondary
                )
            }
        }
    }

    if (showDetailsDialog) {
        FpsDetailsDialog(
            metrics = metrics,
            onDismiss = { showDetailsDialog = false }
        )
    }
}

@Composable
fun FpsDetailsDialog(
    metrics: FpsMetrics,
    onDismiss: () -> Unit
) {
    val statusColor = when (metrics.status) {
        FpsStatus.EXCELLENT -> GreenAccent
        FpsStatus.GOOD -> Color(0xFF40C4FF)
        FpsStatus.MODERATE -> Color(0xFFFFD600)
        FpsStatus.JANK -> Color(0xFFFF5252)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Rendimiento de Renderizado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Métricas de cuadros en tiempo real (Takt / TinyDancer Engine):",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricRow(
                            label = "FPS Actual",
                            value = String.format(Locale.US, "%.2f FPS", metrics.fps),
                            valueColor = statusColor
                        )
                        MetricRow(
                            label = "Latencia por Frame",
                            value = String.format(Locale.US, "%.2f ms", metrics.frameTimeMs),
                            valueColor = TextPrimary
                        )
                        MetricRow(
                            label = "Tasa Pantalla",
                            value = "${metrics.refreshRate.toInt()} Hz",
                            valueColor = TextPrimary
                        )
                        MetricRow(
                            label = "Frames Perdidos (Jank)",
                            value = "${metrics.droppedFrames}",
                            valueColor = if (metrics.droppedFrames > 0) Color(0xFFFFB74D) else GreenAccent
                        )
                        MetricRow(
                            label = "Frames Muestreados",
                            value = "${metrics.totalFramesSampled}",
                            valueColor = TextSecondary
                        )
                        MetricRow(
                            label = "Estado",
                            value = when (metrics.status) {
                                FpsStatus.EXCELLENT -> "Óptimo (Fluido)"
                                FpsStatus.GOOD -> "Bueno"
                                FpsStatus.MODERATE -> "Moderado"
                                FpsStatus.JANK -> "Caída de Frames / Jank"
                            },
                            valueColor = statusColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { FpsMonitor.resetStatistics() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reiniciar", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            FpsMonitor.setOverlayVisible(false)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ocultar", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenAccent,
                    contentColor = DarkBackground
                )
            ) {
                Text("Aceptar", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = valueColor
        )
    }
}
