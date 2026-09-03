package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.playback.AudioEngineType
import com.example.ui.settings.SettingsAboutSection
import com.example.ui.settings.SettingsArchitectureSection
import com.example.ui.settings.SettingsAudioEngineSection
import com.example.ui.settings.SettingsDebugSection
import com.example.ui.settings.SettingsStorageSection
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla modular de Ajustes de Ritmo Music Player.
 *
 * Descompuesta en secciones modulares:
 * - [SettingsAudioEngineSection]: Selector de motor de audio (ExoPlayer vs Oboe C++).
 * - [SettingsArchitectureSection]: Estado de módulos nativos (C++, Rust, NDK, Uptodown).
 * - [SettingsDebugSection]: Consola de telemetría y diagnósticos crudos en smartphone.
 * - [SettingsStorageSection]: Biblioteca, importación y vaciado local en Room.
 * - [SettingsAboutSection]: Información de versión y filosofía audiófila y offline.
 */
@Composable
fun SettingsScreen(
    activeEngine: AudioEngineType,
    tracksCount: Int,
    onEngineChanged: (AudioEngineType) -> Unit,
    onImportRequested: () -> Unit,
    onClearLibraryRequested: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenDebugConsole: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Soporte táctil ergonómico de retroceso para smartphones
    BackHandler(enabled = true) {
        onNavigateBack()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Barra superior de navegación (Touch target >= 48dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver a la biblioteca",
                        tint = TextPrimary
                    )
                }

                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = "Configuración",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Motor, Arquitectura y Almacenamiento",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary
                        )
                    )
                }
            }

            // Contenedor vertical con scroll fluido
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SettingsAudioEngineSection(
                    activeEngine = activeEngine,
                    onEngineChanged = onEngineChanged
                )

                SettingsArchitectureSection()

                SettingsDebugSection(
                    onOpenDebugConsole = onOpenDebugConsole
                )

                SettingsStorageSection(
                    tracksCount = tracksCount,
                    onImportRequested = onImportRequested,
                    onClearLibraryRequested = onClearLibraryRequested
                )

                SettingsAboutSection()

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
