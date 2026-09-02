package com.example.playback

enum class AudioEngineType(
    val title: String,
    val shortName: String,
    val description: String
) {
    EXOPLAYER(
        title = "ExoPlayer (Media3)",
        shortName = "Media3",
        description = "Motor estándar del sistema Android con amplia compatibilidad y reproducción por streaming local."
    ),
    OBOE_CPP(
        title = "Oboe C++ (Nativo)",
        shortName = "Oboe C++",
        description = "Motor nativo en C++ de ultra baja latencia conectado a rutas de hardware (AAudio/OpenSL ES)."
    )
}
