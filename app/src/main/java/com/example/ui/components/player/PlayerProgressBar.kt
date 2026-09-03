package com.example.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.util.FormatUtils

/**
 * Barra de progreso interactiva del reproductor:
 * Control deslizante continuo (Seeker Slider) con soporte para arrastre suave y marcas de tiempo.
 */
@Composable
fun PlayerProgressBar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val playbackProgress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val sliderProgress = if (isDraggingSlider) sliderDragPosition else playbackProgress
    val effectivePosition = if (isDraggingSlider) (sliderDragPosition * safeDuration.toFloat()).toLong() else currentPositionMs

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Slider(
            value = sliderProgress,
            onValueChange = { newValue ->
                isDraggingSlider = true
                sliderDragPosition = newValue
            },
            onValueChangeFinished = {
                val targetMs = (sliderDragPosition * safeDuration.toFloat()).toLong()
                onSeekTo(targetMs)
                isDraggingSlider = false
            },
            colors = SliderDefaults.colors(
                thumbColor = TextPrimary,
                activeTrackColor = GreenPrimary,
                inactiveTrackColor = SliderInactive
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("full_player_slider")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = FormatUtils.formatDuration(effectivePosition),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
            Text(
                text = FormatUtils.formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}
