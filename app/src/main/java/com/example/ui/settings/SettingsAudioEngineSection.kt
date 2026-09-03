package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playback.AudioEngineType
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.TextSecondary

/**
 * Sección de selección de motor de reproducción de audio.
 */
@Composable
fun SettingsAudioEngineSection(
    activeEngine: AudioEngineType,
    onEngineChanged: (AudioEngineType) -> Unit
) {
    SettingsSection(
        title = "Motor de Reproducción de Audio",
        subtitle = "Selecciona el motor que procesa y envía el flujo sonoro",
        icon = Icons.Default.Memory
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Opción ExoPlayer
            EngineSelectionCard(
                title = "ExoPlayer (Media3)",
                badge = "Estándar",
                badgeColor = Color(0xFF64B5F6),
                icon = Icons.Default.Audiotrack,
                description = "Motor multimedia de alto nivel de Android. Excelente compatibilidad de formatos (MP3, FLAC, AAC, WAV), decodificación eficiente y menor consumo de batería.",
                isSelected = activeEngine == AudioEngineType.EXOPLAYER,
                testTag = "settings_engine_exoplayer",
                onSelect = { onEngineChanged(AudioEngineType.EXOPLAYER) }
            )

            // Opción Oboe C++
            EngineSelectionCard(
                title = "Oboe C++ (Nativo)",
                badge = "Baja Latencia",
                badgeColor = GreenAccent,
                icon = Icons.Default.Speed,
                description = "Motor nativo en C++ utilizando Google Oboe con backend directo AAudio y OpenSL ES. Acceso de baja latencia al hardware de audio y respuesta inmediata.",
                isSelected = activeEngine == AudioEngineType.OBOE_CPP,
                testTag = "settings_engine_oboe",
                onSelect = { onEngineChanged(AudioEngineType.OBOE_CPP) }
            )

            // Nota de conmutación en caliente
            Surface(
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(GreenPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = GreenAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "El motor se conmuta en caliente. Si hay música sonando, la pista se reanudará en el nuevo motor sin interrupción de posición.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }
    }
}
