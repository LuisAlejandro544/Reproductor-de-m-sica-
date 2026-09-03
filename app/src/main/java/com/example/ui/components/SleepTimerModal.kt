package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.playback.SleepTimerStatus
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

/**
 * Modal táctil del Temporizador de Sueño para Ritmo Music Player.
 *
 * Permite programar el apagado automático de la música al dormir,
 * con atenuación progresiva de 15 segundos para una transición suave sin sobresaltos.
 */
@Composable
fun SleepTimerModal(
    isOpen: Boolean,
    status: SleepTimerStatus,
    onStartTimer: (Int) -> Unit,
    onStartEndOfTrack: () -> Unit,
    onAddMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var customMinutes by remember { mutableFloatStateOf(20f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("sleep_timer_modal"),
            color = DarkSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header con Título, Icono de Luna y Botón de Cerrar
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
                            color = Color(0xFF5C6BC0).copy(alpha = 0.2f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = Color(0xFF9FA8DA),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Temporizador de Sueño",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Apagado automático para dormir",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sleep_timer_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Estado Activo: Si ya hay un temporizador corriendo
                if (status.isActive) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2430)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (status.isEndOfTrack) "Se detendrá al finalizar la canción" else "Música se detendrá en",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9FA8DA)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = status.formattedRemaining,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp
                                ),
                                color = GreenAccent
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Acciones de tiempo extra (+5 min / +15 min)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                OutlinedButton(
                                    onClick = { onAddMinutes(5) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(48.dp).testTag("sleep_timer_add_5")
                                ) {
                                    Text("+5 min", color = TextPrimary)
                                }
                                OutlinedButton(
                                    onClick = { onAddMinutes(15) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(48.dp).testTag("sleep_timer_add_15")
                                ) {
                                    Text("+15 min", color = TextPrimary)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Botón de Cancelar Temporizador
                            OutlinedButton(
                                onClick = onCancelTimer,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("sleep_timer_cancel_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cancelar temporizador activo")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Opciones Predefinidas (15m, 30m, 45m, 60m)
                Text(
                    text = "Opciones predeterminadas",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                val presets = listOf(15, 30, 45, 60)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { minutes ->
                        val isCurrentPreset = status.isActive && !status.isEndOfTrack &&
                                (status.remainingMs in ((minutes * 60 * 1000L - 3000L)..(minutes * 60 * 1000L + 3000L)))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrentPreset) GreenPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable { onStartTimer(minutes) }
                                .testTag("sleep_timer_preset_$minutes")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Text(
                                    text = "$minutes min",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCurrentPreset) GreenAccent else TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Opción especial: Al terminar la canción actual
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (status.isActive && status.isEndOfTrack) GreenPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { onStartEndOfTrack() }
                        .testTag("sleep_timer_end_of_track")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = null,
                                tint = if (status.isActive && status.isEndOfTrack) GreenAccent else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Al finalizar la canción actual",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (status.isActive && status.isEndOfTrack) GreenAccent else TextPrimary
                            )
                        }

                        if (status.isActive && status.isEndOfTrack) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Activo",
                                tint = GreenAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Selector Personalizado con Slider
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tiempo personalizado",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                text = "${customMinutes.toInt()} minutos",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = GreenAccent
                            )
                        }

                        Slider(
                            value = customMinutes,
                            onValueChange = { customMinutes = it },
                            valueRange = 5f..120f,
                            steps = 22,
                            colors = SliderDefaults.colors(
                                thumbColor = GreenPrimary,
                                activeTrackColor = GreenPrimary,
                                inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("sleep_timer_custom_slider")
                        )

                        OutlinedButton(
                            onClick = { onStartTimer(customMinutes.toInt()) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sleep_timer_apply_custom")
                        ) {
                            Text("Iniciar temporizador de ${customMinutes.toInt()} min", color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Nota informativa sobre el fade-out suave
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeDown,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Atenuación suave: el volumen disminuirá progresivamente durante los últimos 15 segundos antes de pausar la música.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextTertiary
                    )
                }
            }
        }
    }
}
