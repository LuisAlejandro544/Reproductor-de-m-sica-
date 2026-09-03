package com.example.playback

data class EqualizerPreset(
    val id: String,
    val name: String,
    val gains: List<Float>
)

object EqualizerDefaults {
    const val NUM_BANDS = 10
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f

    val BAND_FREQUENCIES = listOf(
        31.25f, 62.5f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f
    )

    val BAND_LABELS = listOf(
        "31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
    )

    val BAND_NAMES = listOf(
        "Subgraves",
        "Graves profundos",
        "Bajos",
        "Medios graves",
        "Cuerpo vocal",
        "Claridad vocal",
        "Presencia",
        "Brillo",
        "Aire / Detalle",
        "Ultra agudos"
    )

    val PRESETS = listOf(
        EqualizerPreset("flat", "Plano", List(10) { 0.0f }),
        EqualizerPreset("bass_boost", "Graves", listOf(7.0f, 6.0f, 4.5f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 2.0f)),
        EqualizerPreset("rock", "Rock", listOf(4.5f, 3.0f, -1.0f, -2.0f, -0.5f, 2.0f, 3.5f, 4.5f, 5.0f, 5.0f)),
        EqualizerPreset("pop", "Pop", listOf(-1.5f, 1.0f, 3.0f, 4.0f, 3.5f, 1.5f, -1.0f, -1.5f, -1.0f, -1.0f)),
        EqualizerPreset("jazz", "Jazz", listOf(3.5f, 2.5f, 1.0f, 1.5f, -1.5f, -1.5f, 0.0f, 1.5f, 3.0f, 3.5f)),
        EqualizerPreset("vocal", "Vocal", listOf(-2.0f, -1.0f, 1.0f, 3.0f, 4.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f)),
        EqualizerPreset("electronic", "Electrónica", listOf(5.0f, 4.5f, 2.0f, 0.0f, -1.5f, 2.0f, 3.0f, 4.0f, 4.5f, 5.0f)),
        EqualizerPreset("treble_boost", "Agudos", listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 2.5f, 4.5f, 6.5f, 7.5f))
    )
}
