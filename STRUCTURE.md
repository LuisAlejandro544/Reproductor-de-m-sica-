# Arquitectura y Estructura Técnica — Ritmo

Este documento detalla la arquitectura de software, la organización de directorios, el flujo de datos, los puentes nativos (Kotlin / C++ / Rust) y la infraestructura de depuración de **Ritmo**.

---

## 🏛️ Diagrama de Capas del Sistema

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                  Capa de Presentación                                   │
│                    Jetpack Compose (Material Design 3 / Dark Theme)                     │
│   MainMusicScreen ──► MainBottomNavBar ──► MiniPlayer ──► FullPlayerView                │
│          │                                                        │                     │
│          ├─────────────────────────────────────────┐              ├─► PlayerHeader      │
│          ▼                                         ▼              ├─► PlayerCenterDisplay│
│   MainModalsHost                           EqualizerModal (C++)   ├─► PlayerTrackDetails│
│   ├─► DebugConsoleModal (Logs y Códigos)   SpatialAudio8DModal    ├─► PlayerProgressBar │
│   ├─► RawErrorDialog (Alerta Cruda)        SleepTimerModal        ├─► PlayerControlsRow │
│   ├─► EditTrackMetadataDialog (Rust)                              └─► PlayerQuickActions│
│   └─► RoomDatabaseInspectorModal & FpsOverlay                                           │
└────────────────────────────────────────────┬────────────────────────────────────────────┘
                                             │ StateFlow / Eventos
┌────────────────────────────────────────────▼────────────────────────────────────────────┐
│                                  Capa de Lógica (MVVM)                                  │
│                                  MusicPlayerViewModel                                   │
│                     Orquestador desacoplado en delegados modulares:                     │
│        ├─► PlaylistViewModelDelegate: CRUD de listas y auto-generación por artista      │
│        ├─► TrackMediaDelegate: Tags nativos en Rust, WebP Lossless y letras LRC         │
│        └─► AudioPlayerManager: Coordinación multicanal de reproducción y DSP C++        │
└──────────────┬─────────────────────────────┬──────────────────────────────┬─────────────┘
               │                             │                              │
┌──────────────▼───────────┐   ┌─────────────▼────────────┐   ┌─────────────▼─────────────┐
│    Capa de Datos Local   │   │   Capa de Reproducción   │   │       Capa de Debug       │
│      (Room Database)     │   │   (AudioPlayerManager)   │   │     (DebugLogManager)     │
│ TrackEntity / TrackDao   │   │   Singleton unificado    │   │     Búfer en memoria      │
│ PlaylistDao / CrossRef   │   └─────────────┬────────────┘   │    con códigos crudos     │
│ y Archivos JSON modulares│                 │                └───────────────────────────┘
└──────────────────────────┘                 │
                              ┌──────────────▼──────┐   ┌───────────────────────────┐
                              │ Motor 1: Media3     │   │ Motor 2: Nativo C++       │
                              │ ExoPlayer (Java/KT) │   │ (Oboe / AAudio / JNI)     │
                              └──────────────┬──────┘   └─────────────┬─────────────┘
                                             │                        │
                                             ▼                        ▼
                              ┌─────────────────────┐   ┌───────────────────────────┐
                              │ MediaSessionService │   │ AudioDecoder (NDK C++)    │
                              │ + AudioProcessor    │   │ Decodificación multihilo  │
                              │ (EQ C++ Biquad IIR) │   └─────────────┬─────────────┘
                              └──────────────┬──────┘                 │
                                             │          ┌─────────────▼─────────────┐
                                             ▼          │ DSP 10-Bandas & Audio 8D  │
                                      ┌─────────────┐   │ Filtros Biquad IIR C++    │
                                      │ Núcleo Rust │◄──┤ (native_audio / equalizer)│
                                      │(ritmo_rust) │   └───────────────────────────┘
                                      │ Tags / C-ABI│
                                      └─────────────┘
```

---

## 📂 Organización Detallada del Proyecto

```
/
├── .github/
│   └── workflows/
│       ├── build-debug-apk.yml       # Compilación limpia y descarga de dependencias de Rust (cargo fetch)
│       ├── override-commit-message.yml
│       └── purge-rust-cache.yml      # Purga manual de artefactos target del historial Git con git-filter-repo
├── AGENTS.md                         # Directrices obligatorias para agentes de IA
├── AI_CONTEXT.md                     # Contexto técnico y operativo del proyecto
├── README.md                         # Descripción general, características y guía de compilación
├── ROADMAP.md                        # Hoja de ruta técnica organizada por fases
├── STRUCTURE.md                      # Este documento de arquitectura
├── commit_message.txt                # Mensaje de commit en español actualizado
├── app/
│   ├── build.gradle.kts              # Script Gradle con integración NDK, CMake y tarea compileRust
│   ├── CMakeLists.txt                # Enlace de C++ Oboe, AudioDecoder y librust_audio.a
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml   # Permisos de almacenamiento y servicio MediaSession
│           ├── cpp/                  # Código fuente Nativo en C++
│           │   ├── CMakeLists.txt
│           │   ├── audio_decoder.h   # Cabecera del decodificador modular multihilo NDK AMediaCodec
│           │   ├── audio_decoder.cpp # Decodificador asíncrono con AMediaExtractor y gestión de búfers PCM
│           │   ├── equalizer.h       # Filtros Biquad IIR Transposed Direct Form II (10 bandas)
│           │   ├── spatial_audio.h   # Algoritmo de paneo binaural y convolución 360° / Efecto 8D
│           │   ├── native_audio.h    # Cabecera de OboeAudioPlayer con delegación hacia AudioDecoder
│           │   ├── native_audio.cpp  # Implementación de audio Oboe, DSP y diagnóstico (error codes crudos)
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
│           │   │   ├── PlaylistDao.kt    # Operaciones Room para playlists y referencias cruzadas
│           │   │   ├── PlaylistEntity.kt
│           │   │   ├── PlaylistTrackCrossRef.kt
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
│           │   │   ├── SleepTimerManager.kt # Gestor del temporizador de sueño
│           │   │   └── RitmoMediaSessionService.kt # Servicio de segundo plano
│           │   ├── ui/
│           │   │   ├── MainMusicScreen.kt # Orquestador principal modularizado de la interfaz
│           │   │   ├── MusicPlayerViewModel.kt # ViewModel desacoplado mediante delegados modulares
│           │   │   ├── SettingsScreen.kt # Pantalla de ajustes modularizada
│           │   │   ├── delegates/        # Delegados modulares de lógica de negocio (MVVM)
│           │   │   │   ├── PlaylistViewModelDelegate.kt # Gestión de playlists y auto-generación de artistas
│           │   │   │   └── TrackMediaDelegate.kt        # Tags Rust, carátulas Lossless WebP y letras LRC
│           │   │   ├── main/             # Submódulos de la pantalla principal
│           │   │   │   ├── MainTopAppBar.kt
│           │   │   │   ├── MainProgressBanners.kt
│           │   │   │   ├── MainSearchBar.kt
│           │   │   │   ├── MainBottomNavBar.kt # Barra de navegación táctil 48dp (Canciones / Playlists)
│           │   │   │   ├── MainModalsHost.kt   # Anfitrión desacoplado de modales, alertas y consola
│           │   │   │   ├── EmptyLibraryView.kt
│           │   │   │   ├── PlaylistDetailView.kt
│           │   │   │   ├── PlaylistListView.kt
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
│           │   │   │   ├── AddToPlaylistBottomSheet.kt
│           │   │   │   ├── CreatePlaylistDialog.kt
│           │   │   │   ├── DebugConsoleModal.kt # Consola táctil con logs crudos, copia individual (48dp)
│           │   │   │   ├── EditLyricsDialog.kt # Editor táctil de letras sincronizadas LRC y texto plano
│           │   │   │   ├── EditTrackMetadataDialog.kt # Diálogo de edición de tags con Rust
│           │   │   │   ├── EngineSelectDialog.kt
│           │   │   │   ├── EqualizerModal.kt
│           │   │   │   ├── FpsOverlay.kt
│           │   │   │   ├── FullPlayerView.kt # Reproductor completo modularizado
│           │   │   │   ├── LyricsView.kt     # Visualizador de letras con autodesplazamiento y seek interactivo
│           │   │   │   ├── MiniPlayer.kt
│           │   │   │   ├── RawErrorDialog.kt # Alerta emergente ante errores numéricos crudos
│           │   │   │   ├── RoomDatabaseInspectorModal.kt
│           │   │   │   ├── SleepTimerModal.kt
│           │   │   │   ├── SpatialAudio8DModal.kt
│           │   │   │   ├── TrackListItem.kt
│           │   │   │   └── player/           # Subcomponentes desacoplados de FullPlayerView
│           │   │   │       ├── PlayerHeader.kt
│           │   │   │       ├── PlayerCenterDisplay.kt
│           │   │   │       ├── PlayerTrackDetails.kt
│           │   │   │       ├── PlayerProgressBar.kt
│           │   │   │       ├── PlayerControlsRow.kt
│           │   │   │       └── PlayerBottomQuickActions.kt
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

### 1. Métodos de Rust (`ritmo_rust` enlazado y empaquetado en `jniLibs`)
- `Java_com_example_util_RustAudioEngine_nativeExtractMetadata(JNIEnv*, jclass, jstring filePath)`
  - Retorna un string JSON con metadatos audiófilos (título, artista, álbum, duración, bitrate, sample rate, canales).
- `Java_com_example_util_RustAudioEngine_nativeExtractArtwork(JNIEnv*, jclass, jstring filePath)`
  - Retorna un `jbyteArray` con los bytes de la imagen de portada embebida en la pista.
- `Java_com_example_util_RustAudioEngine_nativeUpdateMetadata(JNIEnv*, jclass, jstring filePath, jstring title, jstring artist, jstring album, jstring genre, jstring year)`
  - Escribe físicamente en el archivo de audio los nuevos tags de título, artista, álbum, género y año directamente desde el crate nativo en Rust.
- `Java_com_example_util_RustAudioEngine_nativePing(JNIEnv*, jclass)` y `nativeGetVersion(JNIEnv*, jclass)`
  - Métodos para verificación y prueba de enlace del núcleo nativo.

### 2. Métodos de Diagnóstico y Telemetría C++ Oboe (`OboeAudioBridge.kt`)
- `nativeGetLastErrorCode()`: Devuelve el código de error numérico crudo más reciente del motor nativo.
- `nativeGetLastErrorString()`: Devuelve el mensaje descriptivo del fallo.
- `nativeGetAudioDeviceInfo()`: Información del hardware de audio activo (`AAudio`/`OpenSL ES`).
- `nativeGetStreamStatsJson()`: Estadísticas de frames leídos, xruns y estado del stream.

### 3. Decodificación Nativa C++ con AMediaCodec (`AudioDecoder`)
- `audio_decoder.cpp`: Extrae pistas de audio comprimidas (`AMediaExtractor`) y decodifica a muestras PCM en coma flotante de 32 bits a través de `AMediaCodec`.
- Ejecución asíncrona mediante hilo en segundo plano (`std::thread`), evitando bloqueos en el hilo de reproducción de audio y garantizando búfers continuos libres de xruns.

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
