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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.playback.AudioEngineType
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.sin

/**
 * Modal táctil de control de Velocidad y Afinación / Tono Independiente en C++ (Oboe).
 *
 * Exclusivo para el motor de audio Oboe C++.
 * Permite modular de forma independiente:
 * - Velocidad de reproducción (0.50x a 2.00x) con algoritmo WSOLA para estiramiento temporal sin distorsión tonal.
 * - Afinación de tono (-6 a +6 semitonos) con re-muestreo PCM nativo de interpolación lineal.
 * - Switch de preservación de afinación natural (evita el "efecto ardilla" o voces graves anormales al acelerar).
 */
@Composable
fun PlaybackSpeedPitchModal(
    activeEngine: AudioEngineType,
    playbackSpeed: Float,
    pitchSemitones: Float,
    isPitchPreservationEnabled: Boolean,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onPitchPreservationToggle: (Boolean) -> Unit,
    onResetDefaults: () -> Unit,
    onSwitchToOboe: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("speed_pitch_modal_dialog"),
            color = DarkSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Cabecera con icono, título y acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(GreenPrimary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Velocidad y Afinación",
                                tint = GreenAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Velocidad y Tono",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Procesador C++ WSOLA Nativo",
                                style = MaterialTheme.typography.bodySmall,
                                color = GreenAccent,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onResetDefaults,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("speed_pitch_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restablecer velocidad y tono",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("speed_pitch_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Banner de estado del motor Oboe C++
                if (activeEngine != AudioEngineType.OBOE_CPP) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Motor Oboe Requerido",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFFB74D),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "El estiramiento de tiempo con algoritmo WSOLA y la afinación independiente se procesan en C++ dentro de Google Oboe.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onSwitchToOboe,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GreenPrimary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("switch_to_oboe_for_speed_pitch")
                            ) {
                                Text(
                                    text = "Activar Motor Oboe C++",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Animador visual acústico en vivo
                WaveformSpeedVisualizer(
                    speed = playbackSpeed,
                    pitchSemitones = pitchSemitones,
                    preservePitch = isPitchPreservationEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Switch de Preservación de Tono Natural (Anti-Efecto Ardilla)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Preservar Tono Natural (WSOLA)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isPitchPreservationEnabled) {
                                    "La música se acelera o ralentiza sin cambiar la afinación de la voz ni los instrumentos."
                                } else {
                                    "Modo cinta analógica: cambiar la velocidad alterará la afinación en proporción directa."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isPitchPreservationEnabled,
                            onCheckedChange = onPitchPreservationToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = GreenAccent,
                                uncheckedThumbColor = TextTertiary,
                                uncheckedTrackColor = DarkSurface
                            ),
                            modifier = Modifier.testTag("pitch_preservation_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECCIÓN: Velocidad de Reproducción
                Text(
                    text = "VELOCIDAD DE REPRODUCCIÓN",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = GreenAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Ritmo / Tempo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.2fx", playbackSpeed),
                                style = MaterialTheme.typography.titleMedium,
                                color = GreenAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = playbackSpeed,
                            onValueChange = onSpeedChange,
                            valueRange = 0.50f..2.00f,
                            steps = 29, // Pasos de 0.05x
                            colors = SliderDefaults.colors(
                                thumbColor = GreenAccent,
                                activeTrackColor = GreenPrimary,
                                inactiveTrackColor = DarkSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("playback_speed_slider")
                        )

                        // Presets rápidos de velocidad
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val speedPresets = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            speedPresets.forEach { preset ->
                                val isSelected = kotlin.math.abs(playbackSpeed - preset) < 0.03f
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSpeedChange(preset) },
                                    label = {
                                        Text(
                                            text = if (preset == 1.0f) "1.0x (Normal)" else "${preset}x",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GreenPrimary.copy(alpha = 0.25f),
                                        selectedLabelColor = GreenAccent,
                                        containerColor = DarkSurface,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("speed_preset_${(preset * 100).toInt()}")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECCIÓN: Afinación / Tono Independiente
                Text(
                    text = "AFINACIÓN / PITCH INDEPENDIENTE",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = GreenAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Cambio de Tono",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            val pitchText = when {
                                pitchSemitones > 0.05f -> String.format(Locale.US, "+%.1f semitonos", pitchSemitones)
                                pitchSemitones < -0.05f -> String.format(Locale.US, "%.1f semitonos", pitchSemitones)
                                else -> "Original (0 st)"
                            }
                            Text(
                                text = pitchText,
                                style = MaterialTheme.typography.titleMedium,
                                color = GreenAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = pitchSemitones,
                            onValueChange = onPitchChange,
                            valueRange = -6.0f..6.0f,
                            steps = 23, // Pasos de 0.5 semitonos
                            colors = SliderDefaults.colors(
                                thumbColor = GreenAccent,
                                activeTrackColor = GreenPrimary,
                                inactiveTrackColor = DarkSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pitch_semitones_slider")
                        )

                        // Presets rápidos de afinación
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val pitchPresets = listOf(-3.0f, -1.0f, 0.0f, 1.0f, 3.0f)
                            pitchPresets.forEach { preset ->
                                val isSelected = kotlin.math.abs(pitchSemitones - preset) < 0.2f
                                val labelText = when {
                                    preset == 0.0f -> "0 (Original)"
                                    preset > 0 -> "+${preset.toInt()} st"
                                    else -> "${preset.toInt()} st"
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onPitchChange(preset) },
                                    label = {
                                        Text(
                                            text = labelText,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GreenPrimary.copy(alpha = 0.25f),
                                        selectedLabelColor = GreenAccent,
                                        containerColor = DarkSurface,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("pitch_preset_${(preset + 10).toInt()}")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Información técnica de WSOLA y C++
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = GreenAccent.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "El algoritmo WSOLA analiza la correlación de onda cruzada en ventanas de 30ms con solapamiento y adición armónica en C++, garantizando fidelidad de estudio sin artefactos metálicos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visualizador acústico animado que reacciona a la velocidad y afinación.
 */
@Composable
private fun WaveformSpeedVisualizer(
    speed: Float,
    pitchSemitones: Float,
    preservePitch: Boolean,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(speed) {
        while (isActive) {
            // La animación corre proporcionalmente a la velocidad de reproducción
            phase = (phase + 0.08f * speed) % (2f * Math.PI.toFloat())
            delay(16)
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val points = 60
        val dx = width / (points - 1)

        // Frecuencia visual ajustada por afinación (si preservePitch es falso o si hay pitch shift)
        val effectivePitchFactor = if (preservePitch) {
            Math.pow(2.0, (pitchSemitones / 12.0).toDouble()).toFloat()
        } else {
            speed * Math.pow(2.0, (pitchSemitones / 12.0).toDouble()).toFloat()
        }
        val visualFreq = 2.5f * effectivePitchFactor

        // Dibujar líneas de cuadrícula sutiles
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1f
        )

        // Dibujar onda secundaria
        for (i in 0 until points - 1) {
            val x1 = i * dx
            val normX1 = i.toFloat() / (points - 1)
            val envelope1 = sin(normX1 * Math.PI.toFloat()).toFloat()
            val y1 = centerY + sin(normX1 * visualFreq * 2f * Math.PI.toFloat() + phase * 0.7f) * (height * 0.22f) * envelope1

            val x2 = (i + 1) * dx
            val normX2 = (i + 1).toFloat() / (points - 1)
            val envelope2 = sin(normX2 * Math.PI.toFloat()).toFloat()
            val y2 = centerY + sin(normX2 * visualFreq * 2f * Math.PI.toFloat() + phase * 0.7f) * (height * 0.22f) * envelope2

            drawLine(
                color = GreenPrimary.copy(alpha = 0.35f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Dibujar onda principal
        for (i in 0 until points - 1) {
            val x1 = i * dx
            val normX1 = i.toFloat() / (points - 1)
            val envelope1 = sin(normX1 * Math.PI.toFloat()).toFloat()
            val y1 = centerY + sin(normX1 * visualFreq * 2f * Math.PI.toFloat() - phase) * (height * 0.38f) * envelope1

            val x2 = (i + 1) * dx
            val normX2 = (i + 1).toFloat() / (points - 1)
            val envelope2 = sin(normX2 * Math.PI.toFloat()).toFloat()
            val y2 = centerY + sin(normX2 * visualFreq * 2f * Math.PI.toFloat() - phase) * (height * 0.38f) * envelope2

            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(GreenPrimary, GreenAccent, Color.White, GreenAccent, GreenPrimary)
                ),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
