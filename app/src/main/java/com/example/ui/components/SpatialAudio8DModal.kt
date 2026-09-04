package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modal táctil de configuración del efecto Audio Espacial 360° / 8D Nativo en C++ (Oboe).
 *
 * Exclusivo para el motor Oboe C++. Permite ajustar parámetros DSP en tiempo real:
 * - Activación binaural (ILD + ITD)
 * - Velocidad de órbita circular azimutal (Hz)
 * - Amplitud y profundidad espacial
 * - Acústica y micro-reverberación de sala
 */
@Composable
fun SpatialAudio8DModal(
    isOpen: Boolean,
    isEnabled: Boolean,
    speedHz: Float,
    depth: Float,
    reverb: Float,
    onToggleEnabled: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onDepthChange: (Float) -> Unit,
    onReverbChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("spatial_audio_modal"),
            color = DarkSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header con Título, Badge y Botón de Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GreenPrimary.copy(alpha = 0.18f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SurroundSound,
                                    contentDescription = null,
                                    tint = GreenAccent,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Audio Espacial 360° (8D)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "DSP Nativo C++ (360° Binaural)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = GreenAccent
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• Binaural",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("spatial_audio_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Switch Principal de Activación con indicador visual
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEnabled) "Efecto 8D Activado" else "Efecto 8D Desactivado",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isEnabled) GreenAccent else TextPrimary
                            )
                            Text(
                                text = "Inmersión binaural 360° mediante filtros ITD/ILD calculados en C++.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = onToggleEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GreenPrimary,
                                checkedTrackColor = GreenPrimary.copy(alpha = 0.4f),
                                uncheckedThumbColor = TextTertiary,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("spatial_audio_switch")
                        )
                    }
                }

                // Visualizador Orbital y Controles (Visibles solo si está activo)
                AnimatedVisibility(
                    visible = isEnabled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Visualizador Orbital 360° Animado
                        SpatialOrbitVisualizer(speedHz = speedHz)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Control 1: Velocidad de Rotación Órbital
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Velocidad de Órbita",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextPrimary
                                    )
                                    val speedDesc = when {
                                        speedHz < 0.05f -> "Muy lenta (${String.format("%.2f", speedHz)} Hz)"
                                        speedHz < 0.10f -> "Relajante (${String.format("%.2f", speedHz)} Hz)"
                                        speedHz < 0.18f -> "Dinámica (${String.format("%.2f", speedHz)} Hz)"
                                        else -> "Rápida (${String.format("%.2f", speedHz)} Hz)"
                                    }
                                    Text(
                                        text = speedDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GreenAccent
                                    )
                                }
                                Slider(
                                    value = speedHz,
                                    onValueChange = onSpeedChange,
                                    valueRange = 0.02f..0.25f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = GreenPrimary,
                                        activeTrackColor = GreenPrimary,
                                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("spatial_speed_slider")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Control 2: Amplitud Espacial / Profundidad
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Profundidad Espacial (ILD/ITD)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${(depth * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GreenAccent
                                    )
                                }
                                Slider(
                                    value = depth,
                                    onValueChange = onDepthChange,
                                    valueRange = 0.20f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = GreenPrimary,
                                        activeTrackColor = GreenPrimary,
                                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("spatial_depth_slider")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Control 3: Micro-Reverberación de Sala
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Acústica de Sala (Reverb)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${(reverb * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GreenAccent
                                    )
                                }
                                Slider(
                                    value = reverb,
                                    onValueChange = onReverbChange,
                                    valueRange = 0.0f..0.50f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = GreenPrimary,
                                        activeTrackColor = GreenPrimary,
                                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("spatial_reverb_slider")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Nota informativa para audífonos y botón de restablecer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Se recomiendan auriculares estéreo para apreciar el efecto binaural 360°.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextTertiary
                        )
                    }

                    if (isEnabled) {
                        IconButton(
                            onClick = {
                                onSpeedChange(0.08f)
                                onDepthChange(0.85f)
                                onReverbChange(0.22f)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("spatial_audio_reset_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restablecer valores predeterminados",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Visualizador Canvas de la órbita de Audio Espacial en 360°.
 * Dibuja la cabeza del oyente en el centro y una esfera de sonido orbitando en tiempo real.
 */
@Composable
private fun SpatialOrbitVisualizer(
    speedHz: Float,
    modifier: Modifier = Modifier
) {
    var currentAngleRad by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(speedHz) {
        val frameDelayMs = 16L
        while (isActive) {
            val deltaAngle = (2f * Math.PI.toFloat()) * speedHz * (frameDelayMs / 1000f)
            currentAngleRad = (currentAngleRad + deltaAngle) % (2f * Math.PI.toFloat())
            delay(frameDelayMs)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF0D2418),
                        DarkSurfaceVariant
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val orbitRadius = (size.minDimension / 2f) - 14.dp.toPx()

            // 1. Círculo de la trayectoria orbital
            drawCircle(
                color = GreenPrimary.copy(alpha = 0.25f),
                radius = orbitRadius,
                center = centerOffset,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // 2. Líneas cardinales tenues (L, R, F, B)
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(centerOffset.x - orbitRadius, centerOffset.y),
                end = Offset(centerOffset.x + orbitRadius, centerOffset.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(centerOffset.x, centerOffset.y - orbitRadius),
                end = Offset(centerOffset.x, centerOffset.y + orbitRadius),
                strokeWidth = 1.dp.toPx()
            )

            // 3. Cabeza de escucha en el centro
            drawCircle(
                color = TextSecondary.copy(alpha = 0.4f),
                radius = 12.dp.toPx(),
                center = centerOffset
            )
            drawCircle(
                color = TextPrimary,
                radius = 6.dp.toPx(),
                center = centerOffset
            )

            // 4. Posición del orbe de sonido en 360°
            val soundX = centerOffset.x + orbitRadius * cos(currentAngleRad)
            val soundY = centerOffset.y + orbitRadius * sin(currentAngleRad)
            val soundPos = Offset(soundX, soundY)

            // Halo del orbe
            drawCircle(
                color = GreenAccent.copy(alpha = 0.35f),
                radius = 14.dp.toPx(),
                center = soundPos
            )
            // Núcleo del orbe
            drawCircle(
                color = GreenAccent,
                radius = 7.dp.toPx(),
                center = soundPos
            )
        }
    }
}
