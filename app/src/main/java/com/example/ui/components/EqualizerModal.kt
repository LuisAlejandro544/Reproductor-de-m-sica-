package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playback.AudioEngineType
import com.example.playback.EqualizerDefaults
import com.example.playback.EqualizerPreset
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.util.Locale

@Composable
fun EqualizerModal(
    isVisible: Boolean,
    isEnabled: Boolean,
    bandGains: List<Float>,
    onToggleEnabled: (Boolean) -> Unit,
    onBandGainChanged: (Int, Float) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    activeEngine: AudioEngineType = AudioEngineType.EXOPLAYER,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(animationSpec = tween(320)) { it } + fadeIn(tween(250)),
        exit = slideOutVertically(animationSpec = tween(280)) { it } + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = DarkBackground
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("eq_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar ecualizador",
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Ecualizador de 10 Bandas",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = TextPrimary
                            )
                            // Badge C++
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(GreenPrimary.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "C++ Nativo",
                                    color = GreenAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        val engineText = if (activeEngine == AudioEngineType.OBOE_CPP) {
                            "Filtros Biquad IIR en Oboe C++ (Baja latencia)"
                        } else {
                            "Filtros Biquad IIR en ExoPlayer (Media3 AudioProcessor C++)"
                        }
                        Text(
                            text = engineText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    // Switch principal de activación
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("eq_toggle_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = GreenAccent,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = DarkSurfaceElevated
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Lista de contenido con scroll
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
                ) {
                    // Preajustes (Presets)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Preajustes de sonido",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary
                                )

                                OutlinedButton(
                                    onClick = onReset,
                                    enabled = isEnabled,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.testTag("eq_reset_button"),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = if (isEnabled) GreenAccent else TextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Plano",
                                        fontSize = 12.sp,
                                        color = if (isEnabled) GreenAccent else TextTertiary
                                    )
                                }
                            }

                            // Carrusel horizontal de chips de presets
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                EqualizerDefaults.PRESETS.forEach { preset ->
                                    val isSelected = isPresetMatching(preset.gains, bandGains)
                                    FilterChip(
                                        selected = isSelected && isEnabled,
                                        onClick = {
                                            if (!isEnabled) onToggleEnabled(true)
                                            onSelectPreset(preset)
                                        },
                                        label = {
                                            Text(
                                                text = preset.name,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GreenPrimary.copy(alpha = 0.25f),
                                            selectedLabelColor = GreenAccent,
                                            containerColor = DarkSurface,
                                            labelColor = TextSecondary
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.testTag("eq_preset_${preset.id}")
                                    )
                                }
                            }
                        }
                    }

                    // Tarjeta informativa si está apagado
                    if (!isEnabled) {
                        item {
                            Surface(
                                color = DarkSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(TextTertiary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = TextTertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "El ecualizador está desactivado. El audio se reproduce sin alterar por el motor Oboe C++.",
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

                    // Las 10 Bandas del Ecualizador
                    itemsIndexed(EqualizerDefaults.BAND_LABELS) { index, label ->
                        val currentGain = bandGains.getOrElse(index) { 0f }
                        val bandName = EqualizerDefaults.BAND_NAMES.getOrElse(index) { "Banda ${index + 1}" }
                        BandSliderRow(
                            bandIndex = index,
                            label = label,
                            bandName = bandName,
                            frequencyHz = EqualizerDefaults.BAND_FREQUENCIES[index],
                            gainDb = currentGain,
                            isEnabled = isEnabled,
                            onGainChange = { newGain ->
                                onBandGainChanged(index, newGain)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BandSliderRow(
    bandIndex: Int,
    label: String,
    bandName: String,
    frequencyHz: Float,
    gainDb: Float,
    isEnabled: Boolean,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nombre descriptivo y frecuencia
            Column(
                modifier = Modifier.width(108.dp)
            ) {
                Text(
                    text = bandName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    color = if (isEnabled) TextPrimary else TextTertiary
                )
                Text(
                    text = "$label (${frequencyHz.toInt()} Hz)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp
                    ),
                    color = if (isEnabled) GreenAccent.copy(alpha = 0.85f) else TextTertiary
                )
            }

            // Slider horizontal con amplio touch target (mínimo 48dp)
            Slider(
                value = gainDb,
                onValueChange = onGainChange,
                valueRange = EqualizerDefaults.MIN_GAIN_DB..EqualizerDefaults.MAX_GAIN_DB,
                steps = 47, // Pasos de 0.5 dB
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(48.dp)
                    .testTag("eq_slider_$bandIndex"),
                colors = SliderDefaults.colors(
                    thumbColor = if (isEnabled) GreenAccent else TextTertiary,
                    activeTrackColor = if (isEnabled) GreenPrimary else TextTertiary.copy(alpha = 0.5f),
                    inactiveTrackColor = DarkSurfaceElevated,
                    disabledThumbColor = TextTertiary,
                    disabledActiveTrackColor = TextTertiary.copy(alpha = 0.3f)
                )
            )

            // Indicador numérico de ganancia
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isEnabled && gainDb != 0f) GreenPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val formattedGain = if (gainDb > 0) {
                    String.format(Locale.US, "+%.1f dB", gainDb)
                } else if (gainDb < 0) {
                    String.format(Locale.US, "%.1f dB", gainDb)
                } else {
                    "0 dB"
                }
                Text(
                    text = formattedGain,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = if (isEnabled && gainDb != 0f) GreenAccent else if (isEnabled) TextSecondary else TextTertiary
                )
            }
        }
    }
}

private fun isPresetMatching(presetGains: List<Float>, currentGains: List<Float>): Boolean {
    if (presetGains.size != currentGains.size) return false
    for (i in presetGains.indices) {
        if (kotlin.math.abs(presetGains[i] - currentGains[i]) > 0.3f) {
            return false
        }
    }
    return true
}
