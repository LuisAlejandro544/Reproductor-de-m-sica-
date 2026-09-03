# Arquitectura y Estructura Técnica — Ritmo

Este documento detalla la arquitectura de software, la organización de directorios, el flujo de datos, los puentes nativos (Kotlin / C++ / Rust) y la infraestructura de depuración de **Ritmo**.

---

## 🏛️ Diagrama de Capas del Sistema

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Capa de Presentación                             │
│              Jetpack Compose (Material Design 3 / Dark Theme)               │
│   MainMusicScreen ──► MiniPlayer ──► FullPlayerView ──► EqualizerModal      │
│          │                   │                                 │            │
│          ▼                   ▼                                 ▼            │
│   DebugConsoleModal   RawErrorDialog               EditTrackMetadataDialog  │
│  (Logs y Códigos)    (Alerta Cruda)                 (Edición Nativa Rust)   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ StateFlow / Eventos
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                            Capa de Lógica (MVVM)                            │
│                            MusicPlayerViewModel                             │
│       Coordina reproducción, búsquedas, selección de motor, ecualizador,    │
│           edición de tags con Rust y telemetría en DebugLogManager          │
└──────────────┬───────────────────────┬──────────────────────────────┬───────┘
               │                       │                              │
┌──────────────▼─────────────┐ ┌───────▼──────────────┐ ┌─────────────▼───────┐
│    Capa de Datos Local     │ │ Capa de Reproducción │ │   Capa de Debug     │
│      (Room Database)       │ │ (AudioPlayerManager) │ │  (DebugLogManager)  │
│ TrackEntity / TrackDao     │ │ Singleton unificado  │ │ Búfer en memoria    │
│  y Archivos JSON modulares │ └───────┬──────────────┘ │ con códigos crudos  │
└────────────────────────────┘         │                └─────────────────────┘
                                       │
                    ┌──────────────────▼──┐   ┌───────────────────────┐
                    │ Motor 1: Media3     │   │ Motor 2: Nativo C++   │
                    │ ExoPlayer (Java/KT) │   │ (Oboe / AAudio / JNI) │
                    └──────────┬──────────┘   └───────┬───────┬───────┘
                               │                      │       │
                               ▼                      ▼       ▼
                    ┌─────────────────────┐       ┌──────┐ ┌──────────────────┐
                    │ MediaSessionService │       │  EQ  │ │ Núcleo Rust      │
                    │ + AudioProcessor    │◄──────┤ 10-B │ │ (ritmo_rust)     │
                    │ (EQ C++ Biquad IIR) │       │(C++) │ │ Metadatos / Tags │
                    └─────────────────────┘       └──────┘ └──────────────────┘
```

---

## 📂 Organización Detallada del Proyecto

```
/
├── .github/
│   └── workflows/
│       ├── build-debug-apk.yml       # Compilación limpia y descarga de dependencias de Rust (cargo fetch)
│       └── override-commit-message.yml
├── AGENTS.md                         # Directrices obligatorias para agentes de IA
├── AI_CONTEXT.md                     # Contexto técnico y operativo del proyecto
├── README.md                         # Descripción general, características y guía de compilación
├── ROADMAP.md                        # Hoja de ruta técnica organizada por fases
├── STRUCTURE.md                      # Este documento de arquitectura
├── commit_message.txt                # Mensaje de commit en español actualizado
├── app/
│   ├── build.gradle.kts              # Script Gradle con integración NDK, CMake y tarea compileRust
│   ├── CMakeLists.txt                # Enlace de C++ Oboe y librust_audio.a
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml   # Permisos de almacenamiento y servicio MediaSession
│           ├── cpp/                  # Código fuente Nativo en C++
│           │   ├── CMakeLists.txt
│           │   ├── equalizer.h       # Filtros Biquad IIR Transposed Direct Form II (10 bandas)
│           │   ├── native_audio.h    # Cabecera de OboeAudioPlayer con mutex de errores
│           │   ├── native_audio.cpp  # Implementación de audio Oboe y diagnóstico (error codes)
│           │   └── native_bridge.cpp # JNI exports de C++ y puente hacia funciones C-ABI de Rust
│           ├── rust/                 # Crate nativo de Rust (ritmo_rust)
│           │   ├── Cargo.toml        # Dependencias: id3, metaflac, lofty, jni
│           │   └── src/
│           │       ├── lib.rs        # Entrada y reexportación modular de submódulos
│           │       ├── models.rs     # Estructuras de metadatos audiófilos (AudioMetadata, ArtworkData)
│           │       ├── id3.rs        # Parser nativo de etiquetas ID3v1 e ID3v2
│           │       ├── vorbis.rs     # Parser de comentarios Vorbis para OGG y Opus
│           │       ├── flac.rs       # Parser nativo de bloques FLAC
│           │       ├── ape.rs        # Parser de etiquetas APE (Monkey's Audio)
│           │       ├── mp4.rs        # Parser de contenedores AAC/M4A
│           │       ├── writer.rs     # Motor de reescritura física de tags y reanálisis de álbum
│           │       └── jni_bridge.rs # Exportación JNI limpia y puentes C-ABI
│           ├── java/com/example/
│           │   ├── MainActivity.kt
│           │   ├── RitmoApplication.kt # Inicialización de Timber, LeakCanary y RitmoCrashHandler
│           │   ├── data/
│           │   │   ├── AppDatabase.kt
│           │   │   ├── TrackDao.kt       # Consultas Room con updateTrackMetadata
│           │   │   ├── TrackEntity.kt
│           │   │   └── TrackRepository.kt
│           │   ├── debug/
│           │   │   ├── DebugLogManager.kt # Búfer circular de logs, severidades y códigos numéricos
│           │   │   ├── RitmoCrashHandler.kt # Atrapador global de excepciones no controladas
│           │   │   └── RitmoDebugTree.kt  # Integración de Timber hacia DebugLogManager
│           │   ├── playback/
│           │   │   ├── AudioEngineType.kt
│           │   │   ├── AudioPlayerManager.kt # Orquestador modular de reproducción
│           │   │   ├── PlaybackQueueManager.kt # Gestor de colas, shuffle y repeat
│           │   │   ├── EqualizerController.kt # Controlador DSP 10 bandas y persistencia
│           │   │   ├── EqualizerState.kt
│           │   │   ├── Media3EqualizerAudioProcessor.kt # AudioProcessor Media3 con JNI C++
│           │   │   ├── OboeAudioBridge.kt # Métodos nativos de reproducción y diagnóstico
│           │   │   └── RitmoMediaSessionService.kt # Servicio de segundo plano
│           │   ├── ui/
│           │   │   ├── MainMusicScreen.kt # Orquestador principal de la interfaz
│           │   │   ├── MusicPlayerViewModel.kt
│           │   │   ├── SettingsScreen.kt # Pantalla de ajustes modularizada
│           │   │   ├── main/             # Submódulos de la pantalla principal
│           │   │   │   ├── MainTopAppBar.kt
│           │   │   │   ├── MainProgressBanners.kt
│           │   │   │   ├── MainSearchBar.kt
│           │   │   │   ├── EmptyLibraryView.kt
│           │   │   │   └── TrackListView.kt
│           │   │   ├── settings/         # Submódulos de la pantalla de ajustes
│           │   │   │   ├── SettingsComponents.kt
│           │   │   │   ├── SettingsAudioEngineSection.kt
│           │   │   │   ├── SettingsArchitectureSection.kt
│           │   │   │   ├── SettingsDebugSection.kt
│           │   │   │   ├── SettingsStorageSection.kt
│           │   │   │   └── SettingsAboutSection.kt
│           │   │   ├── components/
│           │   │   │   ├── AlbumArtView.kt
│           │   │   │   ├── DebugConsoleModal.kt # Consola táctil con logs crudos, copia individual (48dp) y visualizador de crashes
│           │   │   │   ├── EditLyricsDialog.kt # Editor táctil de letras sincronizadas LRC y texto plano
│           │   │   │   ├── EditTrackMetadataDialog.kt # Diálogo de edición de tags con Rust
│           │   │   │   ├── EngineSelectDialog.kt
│           │   │   │   ├── EqualizerModal.kt
│           │   │   │   ├── FullPlayerView.kt # Reproductor completo con switcher carátula/lyrics
│           │   │   │   ├── LyricsView.kt     # Visualizador de letras con autodesplazamiento y seek interactivo
│           │   │   │   ├── MiniPlayer.kt
│           │   │   │   ├── RawErrorDialog.kt # Alerta emergente ante errores numéricos crudos
│           │   │   │   └── TrackListItem.kt
│           │   │   └── theme/
│           │   └── util/
│           │       ├── AppStorageManager.kt # Gestión de carpetas music/, covers/, artists/
│           │       ├── ArtworkProcessor.kt  # Compresión WebP Lossless y generación procedural de carátulas
│           │       ├── FormatUtils.kt
│           │       ├── LyricsParser.kt      # Parser de marcas de tiempo LRC [mm:ss.xx] y archivos complementarios
│           │       ├── MusicImporter.kt     # Importador con RustAudioEngine y fallback de arte procedural
│           │       └── RustAudioEngine.kt   # Puente Kotlin hacia los métodos JNI de Rust
│           └── res/
```

---

## 🌉 Especificación del Puente Nativo (JNI / C-ABI)

### 1. Métodos de Rust (`librust_audio.a` exportados vía `native_bridge.cpp`)
- `Java_com_example_util_RustAudioEngine_nativeExtractMetadata(JNIEnv*, jclass, jstring filePath)`
  - Retorna un string JSON con metadatos audiófilos (título, artista, álbum, duración, bitrate, sample rate, canales).
- `Java_com_example_util_RustAudioEngine_nativeExtractArtwork(JNIEnv*, jclass, jstring filePath)`
  - Retorna un `jbyteArray` con los bytes de la imagen de portada embebida en la pista.
- `Java_com_example_util_RustAudioEngine_nativeUpdateMetadata(JNIEnv*, jclass, jstring filePath, jstring title, jstring artist)`
  - Escribe físicamente en el archivo de audio los nuevos tags de título y artista y valida su consistencia.
- `Java_com_example_util_RustAudioEngine_nativeRustPing(JNIEnv*, jclass)` y `nativeRustVersion(JNIEnv*, jclass)`
  - Métodos para verificación y prueba de enlace del núcleo nativo.

### 2. Métodos de Diagnóstico y Telemetría C++ Oboe (`OboeAudioBridge.kt`)
- `nativeGetLastErrorCode()`: Devuelve el código de error numérico crudo más reciente del motor nativo.
- `nativeGetLastErrorString()`: Devuelve el mensaje descriptivo del fallo.
- `nativeGetAudioDeviceInfo()`: Información del hardware de audio activo (`AAudio`/`OpenSL ES`).
- `nativeGetStreamStatsJson()`: Estadísticas de frames leídos, xruns y estado del stream.

---

## 🔄 Flujo de Trabajo con Códigos Crudos en Smartphone

```
[Evento de Audio / I/O]
        │
        ├── Éxito ──► Reproducción / Parseo OK
        │
        └── Fallo
              │
              ├── Oboe C++   ──► Guarda código numérico (-998, -899, etc.) bajo std::mutex
              ├── Media3     ──► Captura PlaybackException.errorCode
              └── Rust       ──► Retorna código de error o excepción I/O nativa
                    │
                    ▼
          [DebugLogManager.recordError]
                    │
                    ├── Registra en búfer circular en memoria (200 eventos)
                    ├── Dispara RawErrorDialog si es crítico
                    └── Disponible en DebugConsoleModal:
                          - Inspección táctil en pantalla de smartphone
                          - Copia individual por log (botón de 48dp o tap directo)
                          - Botón de 48dp: "Copiar Reporte Completo"
```
