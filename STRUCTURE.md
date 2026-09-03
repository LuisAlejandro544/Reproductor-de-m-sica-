# Arquitectura y Estructura Técnica — Ritmo

Este documento detalla la arquitectura de software, la organización de directorios, el flujo de datos y el diseño del puente nativo (Kotlin / C++ / Rust) de **Ritmo**.

---

## 🏛️ Diagrama de Capas del Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                   Capa de Presentación                      │
│        Jetpack Compose (Material Design 3 / Dark Theme)     │
│   MainMusicScreen ──► MiniPlayer ──► FullPlayerView         │
│                           │                  │              │
│                           └────────────► EqualizerModal     │
│                                       (Oboe C++ y Media3)   │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow / Events
┌──────────────────────────────▼──────────────────────────────┐
│                    Capa de Lógica (MVVM)                    │
│                    MusicPlayerViewModel                     │
│         Gestiona estado, búsquedas, selección de motor,     │
│             ecualizador nativo y preajustes de sonido       │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
┌──────────────▼─────────────┐ ┌──────────────▼───────────────┐
│     Capa de Datos Local    │ │     Capa de Reproducción     │
│       (Room Database)      │ │     (AudioPlayerManager)     │
│  AppDatabase / TrackDao    │ │  Singleton unificado         │
└────────────────────────────┘ └───────┬──────────────┬───────┘
                                       │              │
                    ┌──────────────────▼──┐   ┌───────▼───────────────┐
                    │ Motor 1: Media3     │   │ Motor 2: Nativo C++   │
                    │ ExoPlayer (Java/KT) │   │ (Oboe / AAudio / JNI) │
                    └──────────┬──────────┘   └───────┬───────┬───────┘
                               │                      │       │
                               ▼                      ▼       ▼
                    ┌─────────────────────┐       ┌──────┐ ┌──────────┐
                    │ MediaSessionService │       │  EQ  │ │   Rust   │
                    │ + AudioProcessor    │◄──────┤ 10-B │ │  Módulo  │
                    │ (EQ C++ Biquad IIR) │       │(C++) │ │ (C-ABI)  │
                    └─────────────────────┘       └──────┘ └──────────┘
```

---

## 📂 Organización Detallada del Proyecto

```
/
├── app/
│   ├── build.gradle.kts          # Configuración de compilación de Android, NDK y dependencias
│   ├── CMakeLists.txt            # Script de CMake que compila C++ y enlaza Oboe
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml # Declaración de permisos y MediaSessionService
│   │   │   ├── cpp/              # Código fuente C++ Nativo
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   ├── equalizer.h       # Filtros Biquad IIR de 10 bandas (31Hz - 16kHz)
│   │   │   │   ├── native_audio.h    # Cabecera del reproductor Oboe con DSP
│   │   │   │   ├── native_audio.cpp  # Implementación de audio streams y filtrado en tiempo real
│   │   │   │   └── native_bridge.cpp # Funciones JNI (reproducción, seek y controles de EQ)
│   │   │   ├── rust/             # Código fuente Rust
│   │   │   │   ├── Cargo.toml    # Manifiesto del crate de Rust (staticlib/cdylib)
│   │   │   │   └── src/
│   │   │   │       └── lib.rs    # Exportaciones C-ABI
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt        # Punto de entrada de la actividad
│   │   │   │   ├── data/                  # Capa de datos y persistencia
│   │   │   │   │   ├── AppDatabase.kt     # Definición de Room Database
│   │   │   │   │   ├── TrackDao.kt        # Consultas y persistencia SQL
│   │   │   │   │   └── TrackEntity.kt     # Modelo de datos de una pista de audio
│   │   │   │   ├── playback/              # Motores y gestión de reproducción
│   │   │   │   │   ├── AudioEngineType.kt     # Enum: EXOPLAYER vs OBOE_CPP
│   │   │   │   │   ├── AudioPlayerManager.kt  # Singleton unificado con gestión de EQ y servicio
│   │   │   │   │   ├── Media3EqualizerAudioProcessor.kt # Procesador de audio PCM en Media3 con filtros C++
│   │   │   │   │   ├── OboeAudioBridge.kt     # Enlace JNI de Kotlin con librerías nativas
│   │   │   │   │   └── RitmoMediaSessionService.kt # Servicio de segundo plano y notificaciones
│   │   │   │   ├── ui/                    # Capa de interfaz de usuario
│   │   │   │   │   ├── MainMusicScreen.kt     # Pantalla principal con lista y buscador
│   │   │   │   │   ├── SettingsScreen.kt      # Pantalla independiente de configuración general
│   │   │   │   │   ├── MusicPlayerViewModel.kt# ViewModel central
│   │   │   │   │   ├── components/
│   │   │   │   │   │   ├── AlbumArtView.kt    # Renderizador de carátulas con fallback
│   │   │   │   │   │   ├── EngineSelectDialog.kt # Diálogo de primer inicio (Oboe vs ExoPlayer)
│   │   │   │   │   │   ├── EqualizerModal.kt  # Modal táctil de ecualizador de 10 bandas
│   │   │   │   │   │   ├── FullPlayerView.kt  # Reproductor a pantalla completa
│   │   │   │   │   │   ├── MiniPlayer.kt      # Barra de reproducción inferior
│   │   │   │   │   │   └── TrackListItem.kt   # Elemento con barras animadas de visualización
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── Color.kt           # Paleta de colores oscuros con verde
│   │   │   │   │       └── Theme.kt           # Tema centralizado Material 3
│   │   │   │   └── util/
│   │   │   │       ├── AppStorageManager.kt   # Gestor de carpetas (music, covers, artists) y JSON modular
│   │   │   │       ├── ArtworkProcessor.kt    # Compresión WebP sin pérdida en hilo secundario (IO)
│   │   │   │       ├── AudioMetadataHelper.kt # Extractor de ID3 y carátulas
│   │   │   │       └── FormatUtils.kt         # Formateador de tiempos (mm:ss)
│   │   │   └── res/                       # Recursos XML, cadenas de texto e iconos
│   ├── commit_message.txt        # Mensaje de commit descriptivo en español
│   └── gradle/
│       └── libs.versions.toml    # Catálogo centralizado de versiones y dependencias
```

---

## 📁 Arquitectura de Almacenamiento Modular y Metadatos JSON

El sistema organiza los archivos de forma modular y desacoplada dentro de la ruta privada de la aplicación:
`Android/data/com.example/files/` (o fallback a `filesDir`):

```
Android/data/com.example/files/
├── music/               # Archivos de música importados (.mp3, .flac, .wav, .m4a, .ogg)
│   ├── track_1710001_audio.mp3
│   └── track_1710002_audio.flac
├── covers/              # Carátulas procesadas en formato WebP a máxima compresión sin pérdida
│   ├── cover_1710001.webp
│   └── cover_1710002.webp
└── artists/             # Archivos JSON modulares e independientes por cada canción
    ├── track_1710001_metadata.json
    └── track_1710002_metadata.json
```

### 1. Archivos `.json` Modulares por Pista (`AppStorageManager.kt`)
- Cada canción dispone de su propio archivo `.json` independiente en la carpeta `artists/`.
- Conecta de manera desacoplada la canción de audio, la imagen de portada y los datos del artista/álbum:
  ```json
  {
    "id": 1,
    "title": "Song Title",
    "artist": "Artist Name",
    "album": "Album Name",
    "durationMs": 215000,
    "filePath": "/storage/.../music/track_1.mp3",
    "artworkPath": "/storage/.../covers/cover_1.webp",
    "mimeType": "audio/mpeg",
    "dateAdded": 1710000000000
  }
  ```
- Al importar o modificar pistas (como cambiar carátula), el archivo `.json` se escribe o actualiza atómicamente. Al borrar una pista, su archivo `.json` y su carátula asociada se limpian de manera segura.

### 2. Procesamiento de Carátulas en WebP sin Pérdida (`ArtworkProcessor.kt`)
- **Compresión sin Pérdida:** Emplea `Bitmap.CompressFormat.WEBP_LOSSLESS` al 100% de calidad, preservando la fidelidad cromática original y reduciendo drásticamente el espacio en disco frente a PNG/JPEG.
- **Ejecución en Hilo Secundario:** Todo el proceso de decodificación, escalado suave (si excede 1024x1024) y compresión WebP se ejecuta estrictamente en un hilo de fondo (`withContext(Dispatchers.IO)`), manteniendo la UI a 60/120 fps fluidos.
- **Acceso Directo:** Los usuarios pueden asignar o actualizar la carátula de cualquier pista (con o sin carátula previa) tanto desde el reproductor completo (`FullPlayerView`) con un botón táctil de 48dp, como desde el menú desplegable de cada canción (`TrackListItem`).

---

## ⚙️ Funcionamiento del Puente Nativo y DSP

### 1. Ecualizador de 10 Bandas en C++ (`equalizer.h`)
- **Filtros Biquad IIR:** Utiliza la forma directa II transpuesta (*Transposed Direct Form II*) para máxima estabilidad numérica en cálculos de punto flotante de 32 bits.
- **Frecuencias Centrales:** 31.25 Hz, 62.5 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz y 16 kHz.
- **Rango de Ganancia:** -12.0 dB a +12.0 dB con factor de calidad $Q = 1.414$ para transiciones musicales suaves sin solapamientos agresivos.
- **Integración en Oboe:** En `AudioPlayerCallback::onAudioReady`, cada muestra de audio PCM pasa de forma instantánea por la cadena de filtros en C++ antes de escribirse en el búfer de hardware de Oboe.
- **Integración en Media3:** A través de `Media3EqualizerAudioProcessor`, ExoPlayer transfiere el búfer PCM de 16 bits directamente a `nativeMedia3ProcessDirect` en C++, aplicando exactamente los mismos filtros Biquad antes del renderizado en `AudioTrack`.

### 2. Disponibilidad Unificada del Ecualizador
- El ecualizador comparte el mismo estado (`EqualizerState`), preajustes de sonido y ajustes de ganancia entre ambos motores.
- En la interfaz de usuario (`FullPlayerView.kt`, `EqualizerModal.kt` y `MainMusicScreen.kt`), el ecualizador está disponible para el usuario sin importar si escucha música con **Oboe C++** o con **Media3 / ExoPlayer**, mostrando en el modal el motor activo y la tecnología de filtrado en ejecución.

### 3. Servicio de Segundo Plano (`RitmoMediaSessionService`)
- Extiende `MediaSessionService` de AndroidX Media3.
- Vincula una `MediaSession` que interactúa con el sistema operativo para permitir pausa, reproducción y salto de pistas desde auriculares bluetooth, relojes inteligentes y la pantalla de bloqueo.
