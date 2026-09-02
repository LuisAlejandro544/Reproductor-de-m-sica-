# Arquitectura y Estructura Técnica — Ritmo

Este documento detalla la arquitectura de software, la organización de directorios, el flujo de datos y el diseño del puente nativo (Kotlin / C++ / Rust) de **Ritmo**.

---

## 🏛️ Diagrama de Capas del Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                   Capa de Presentación                      │
│        Jetpack Compose (Material Design 3 / Dark Theme)     │
│   MainMusicScreen ──► MiniPlayer ──► FullPlayerView         │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow / Events
┌──────────────────────────────▼──────────────────────────────┐
│                    Capa de Lógica (MVVM)                    │
│                    MusicPlayerViewModel                     │
│         Gestiona estado, búsquedas, selección de motor      │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
┌──────────────▼─────────────┐ ┌──────────────▼───────────────┐
│     Capa de Datos Local    │ │     Capa de Reproducción     │
│       (Room Database)      │ │     (AudioPlayerManager)     │
│  AppDatabase / TrackDao    │ │  Controlador Unificado       │
└────────────────────────────┘ └───────┬──────────────┬───────┘
                                       │              │
                    ┌──────────────────▼──┐   ┌───────▼───────────────┐
                    │ Motor 1: Media3     │   │ Motor 2: Nativo C++   │
                    │ ExoPlayer (Java/KT) │   │ (Oboe / AAudio / JNI) │
                    └─────────────────────┘   └───────────────┬───────┘
                                                              │
                                              ┌───────────────▼───────┐
                                              │ Módulo Futuro: Rust   │
                                              │ (C-ABI / Data DSP)    │
                                              └───────────────────────┘
```

---

## 📂 Organización Detallada del Proyecto

```
/
├── app/
│   ├── build.gradle.kts          # Configuración de compilación de Android, NDK y dependencias
│   ├── CMakeLists.txt            # Script de CMake que compila el código C++ y enlaza Oboe
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── cpp/              # Código fuente C++ Nativo
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   ├── native_audio.h    # Cabecera del reproductor Oboe
│   │   │   │   ├── native_audio.cpp  # Implementación de audio streams de Oboe
│   │   │   │   └── native_bridge.cpp # Funciones JNI (Java Native Interface)
│   │   │   ├── rust/             # Código fuente Rust
│   │   │   │   ├── Cargo.toml    # Manifiesto del crate de Rust (staticlib/cdylib)
│   │   │   │   └── src/
│   │   │   │       └── lib.rs    # Exportaciones C-ABI
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt        # Punto de entrada de la actividad
│   │   │   │   ├── data/                  # Capa de datos y persistencia
│   │   │   │   │   ├── AppDatabase.kt     # Definición de Room Database
│   │   │   │   │   ├── TrackDao.kt        # Métodos de consulta y modificación SQL
│   │   │   │   │   └── TrackEntity.kt     # Modelo de datos de una pista de audio
│   │   │   │   ├── playback/              # Motores y gestión de reproducción
│   │   │   │   │   ├── AudioEngineType.kt     # Enum: EXOPLAYER vs OBOE_CPP
│   │   │   │   │   ├── AudioPlayerManager.kt  # Gestor unificado con selección de motor
│   │   │   │   │   └── OboeAudioBridge.kt     # Enlace JNI de Kotlin con librerías nativas
│   │   │   │   ├── ui/                    # Capa de interfaz de usuario
│   │   │   │   │   ├── MainMusicScreen.kt     # Pantalla principal con lista y buscador
│   │   │   │   │   ├── MusicPlayerViewModel.kt# ViewModel central
│   │   │   │   │   ├── components/
│   │   │   │   │   │   ├── AlbumArtView.kt    # Renderizador de carátulas
│   │   │   │   │   │   ├── EngineSelectDialog.kt # Selector de motor (Oboe vs ExoPlayer)
│   │   │   │   │   │   ├── FullPlayerView.kt  # Reproductor a pantalla completa
│   │   │   │   │   │   ├── MiniPlayer.kt      # Barra de reproducción inferior
│   │   │   │   │   │   └── TrackListItem.kt   # Elemento de la lista de canciones
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── Color.kt           # Paleta de colores oscuros con verde
│   │   │   │   │       └── Theme.kt           # Tema centralizado Material 3
│   │   │   │   └── util/
│   │   │   │       ├── AudioMetadataHelper.kt # Extractor de ID3 y carátulas
│   │   │   │       └── FormatUtils.kt         # Formateador de tiempos (mm:ss)
│   │   │   └── res/                       # Recursos XML, cadenas de texto e iconos
├── gradle/
│   └── libs.versions.toml        # Catálogo centralizado de versiones y dependencias
├── metadata.json                 # Descriptor de la plataforma
├── README.md                     # Documentación principal
├── ROADMAP.md                    # Plan de desarrollo por fases
├── STRUCTURE.md                  # Este documento
├── AI_CONTEXT.md                 # Contexto de asistencia de IA
└── AGENTS.md                     # Reglas de desarrollo y convenciones
```

---

## ⚙️ Funcionamiento del Puente Nativo (JNI / C++ / Rust)

### 1. Motor C++ con Google Oboe (`ritmo_native`)
- Oboe es la biblioteca recomendada por Google para audio nativo en Android.
- Abstrae de forma transparente **AAudio** (en Android 8.1+ / API 27+) y **OpenSL ES** (en versiones anteriores).
- Proporciona un `AudioStream` con `PerformanceMode::LowLatency` y `SharingMode::Shared` (o `Exclusive` cuando el hardware lo permite).
- El archivo `native_bridge.cpp` expone los símbolos JNI para que `OboeAudioBridge.kt` pueda:
  - Inicializar y liberar el motor de audio.
  - Abrir y decodificar el archivo PCM de la pista actual.
  - Iniciar, pausar y detener la reproducción en hardware.
  - Realizar saltos en el búfer (*seek*).

### 2. Módulo Rust (`ritmo_rust`)
- Estructurado como una biblioteca compatible con C-ABI (`crate-type = ["staticlib", "cdylib"]`).
- No impone dependencias pesadas en tiempo de ejecución.
- Diseñado para enlazar directamente con el código C++ a través de `extern "C"` o llamarse desde JNI.
- Provee la base para cálculo de firmas de audio, algoritmos de ecualización segura y análisis de metadatos de audio sin riesgo de fugas de memoria.

---

## 🔄 Ciclo de Vida del Flujo de Audio

1. **Selección del Motor:**
   - Al iniciar la app (o al pulsar sobre el distintivo de motor en la cabecera), el usuario puede elegir entre **ExoPlayer (Estándar)** y **Oboe C++ (Nativo)**.
   - La preferencia se almacena en memoria y persistencia para futuras sesiones.
2. **Petición de Reproducción:**
   - La UI invoca `viewModel.playTrack(track)`.
   - `AudioPlayerManager` verifica el motor activo:
     - Si es `EXOPLAYER`, delega en `androidx.media3.exoplayer.ExoPlayer`.
     - Si es `OBOE_CPP`, delega en `OboeAudioBridge` mediante JNI.
3. **Progreso y Eventos:**
   - Un `Job` periódico de Coroutines emite el progreso en milisegundos a través de un `StateFlow<Long>`.
   - Tanto el mini reproductor como el reproductor completo reciben el nuevo valor de forma reactiva y actualizan sus barras de progreso sin recomposiciones innecesarias.
