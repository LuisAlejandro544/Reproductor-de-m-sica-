package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GreenAccent

/**
 * Sección de arquitectura y estado de módulos nativos compilados en el APK.
 */
@Composable
fun SettingsArchitectureSection() {
    SettingsSection(
        title = "Arquitectura y Módulos Nativos",
        subtitle = "Estado de las bibliotecas de bajo nivel integradas en el APK",
        icon = Icons.Default.Security
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArchitectureStatusItem(
                label = "Google Oboe (C++20)",
                detail = "Puente JNI compilado con NDK r26b / CMake",
                status = "Operativo",
                statusColor = GreenAccent
            )
            ArchitectureStatusItem(
                label = "Ecualizador 10 Bandas (C++)",
                detail = "Filtros Biquad IIR activos en Oboe y Media3 (AudioProcessor)",
                status = "Activo (Ambos motores)",
                statusColor = GreenAccent
            )
            ArchitectureStatusItem(
                label = "Velocidad y Afinación WSOLA (C++)",
                detail = "Time-stretching y cambio de tono independiente exclusivo en Oboe",
                status = "Operativo",
                statusColor = GreenAccent
            )
            ArchitectureStatusItem(
                label = "MediaSessionService",
                detail = "Reproducción continua en segundo plano y controles",
                status = "Enlazado",
                statusColor = GreenAccent
            )
            ArchitectureStatusItem(
                label = "Núcleo Rust (C-ABI)",
                detail = "Enlazado en build.gradle.kts (librust_audio.a)",
                status = "Integrado",
                statusColor = GreenAccent
            )
            ArchitectureStatusItem(
                label = "Canal de Distribución",
                detail = "100% Offline e independiente (Uptodown / Direct APK)",
                status = "Autónomo",
                statusColor = Color(0xFF64B5F6)
            )
        }
    }
}
