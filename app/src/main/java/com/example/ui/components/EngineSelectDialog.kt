package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.playback.AudioEngineType

@Composable
fun EngineSelectDialog(
    currentEngine: AudioEngineType,
    onEngineSelected: (AudioEngineType) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selectedEngine by remember { mutableStateOf(currentEngine) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF1E222A),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Motor",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Motor de Audio",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Elige la tecnología de reproducción",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFA0A5B5)
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Opción 1: ExoPlayer / Media3
                EngineOptionCard(
                    title = "ExoPlayer (Media3)",
                    badge = "Estándar",
                    badgeColor = Color(0xFF64B5F6),
                    description = "Motor de Android de alta compatibilidad con todos los formatos de archivo y bajo consumo de batería.",
                    isSelected = selectedEngine == AudioEngineType.EXOPLAYER,
                    icon = Icons.Default.Audiotrack,
                    testTag = "engine_option_exoplayer",
                    onClick = { selectedEngine = AudioEngineType.EXOPLAYER }
                )

                // Opción 2: Oboe C++
                EngineOptionCard(
                    title = "Oboe C++ (Nativo)",
                    badge = "Baja Latencia",
                    badgeColor = Color(0xFF1DB954),
                    description = "Motor nativo en C++ con Google Oboe y AAudio/OpenSL ES. Latencia ultra baja y procesamiento directo en hardware.",
                    isSelected = selectedEngine == AudioEngineType.OBOE_CPP,
                    icon = Icons.Default.Speed,
                    testTag = "engine_option_oboe",
                    onClick = { selectedEngine = AudioEngineType.OBOE_CPP }
                )

                // Mensaje informativo sobre Rust
                Surface(
                    color = Color(0xFF282C37),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🦀",
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Núcleo nativo en Rust compilado e integrado para decodificación y análisis seguro en futuras versiones.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFB0B5C5),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onEngineSelected(selectedEngine)
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1DB954),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("dialog_confirm_engine_button")
            ) {
                Text(
                    text = "Aplicar Selección",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("dialog_dismiss_engine_button")
            ) {
                Text(
                    text = "Cancelar",
                    color = Color(0xFFA0A5B5)
                )
            }
        }
    )
}

@Composable
private fun EngineOptionCard(
    title: String,
    badge: String,
    badgeColor: Color,
    description: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF1DB954) else Color(0xFF2E3440)
    val bgColor = if (isSelected) Color(0xFF1DB954).copy(alpha = 0.08f) else Color(0xFF242833)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF1DB954),
                    unselectedColor = Color(0xFF6B7280)
                ),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF9CA3AF),
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}
