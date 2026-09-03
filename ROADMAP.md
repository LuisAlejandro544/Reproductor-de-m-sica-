# Roadmap — Ritmo Music Player

Este documento define la hoja de ruta estratégica para el desarrollo de **Ritmo**, organizando las metas alcanzadas y futuras en fases claras según el ciclo de desarrollo técnico.

---

## 📍 Estado Actual: Fase 1 — Cimientos Sólidos, Módulos Nativos C++/Rust y Suite de Debug (Completada)

- [x] **Núcleo de Metadatos Audiófilo en Rust (`ritmo_rust`):**
  - [x] Extracción e indexación profunda de metadatos (ID3v1, ID3v2, FLAC/Vorbis, Opus, APE) directamente en Rust sin intermediación de Kotlin.
  - [x] Extracción de arte de portada embebido en bytes crudos desde el archivo fuente.
  - [x] Edición y reescritura nativa de etiquetas (título y artista) delegada 100% a Rust.
  - [x] Re-análisis nativo automático de tags tras la edición para detección y sincronización de álbum.
  - [x] Puente JNI/C-ABI en `RustAudioEngine.kt` y `native_bridge.cpp`.
  - [x] Diálogo táctil de 48dp `EditTrackMetadataDialog` en Compose con confirmación nativa.
- [x] **Herramientas de Debug Avanzadas & Telemetría Cruda (Desarrollo en Móvil):**
  - [x] Sistema de registro en memoria `DebugLogManager` con búfer circular de eventos clasificados por severidad.
  - [x] Captura de códigos de error numéricos crudos de C++ Oboe (`nativeGetLastErrorCode`, `nativeGetLastErrorString`) y Media3 (`PlaybackException.errorCode`).
  - [x] Atrapador global de excepciones no controladas `RitmoCrashHandler` (`Thread.UncaughtExceptionHandler`) con captura de StackTrace y persistencia en `SharedPreferences`.
  - [x] Integración de logging profesional con `Timber` y `RitmoDebugTree` canalizado a `DebugLogManager`.
  - [x] Detección y notificación automática de fugas de memoria con `LeakCanary` para APK Debug (`debugImplementation`).
  - [x] Consola de diagnóstico táctil `DebugConsoleModal` con telemetría en tiempo real, visor de crashes y botón de "Copiar Reporte Completo".
  - [x] Copia individual de cada evento de log al portapapeles mediante botón táctil de 48dp o tap directo.
  - [x] Diálogo de alerta emergente `RawErrorDialog` para fallos críticos de reproducción o procesamiento con código exacto.
  - [x] Accesos directos a la consola en la barra superior y en la pantalla de Ajustes.
- [x] **Modularización Integral de la Arquitectura:**
  - [x] Crate de Rust (`ritmo_rust`) descompuesto en módulos limpios: `models.rs`, `id3.rs`, `vorbis.rs`, `flac.rs`, `ape.rs`, `mp4.rs`, `writer.rs` y `jni_bridge.rs`.
  - [x] Desacoplamiento de `AudioPlayerManager` delegando en `PlaybackQueueManager` (colas, shuffle, repeat) y `EqualizerController` (DSP 10 bandas y persistencia).
  - [x] Modularización de `SettingsScreen` en submódulos bajo `ui.settings` (`SettingsComponents`, `SettingsAudioEngineSection`, `SettingsArchitectureSection`, `SettingsDebugSection`, `SettingsStorageSection`, `SettingsAboutSection`).
  - [x] Modularización de `MainMusicScreen` en submódulos bajo `ui.main` (`MainTopAppBar`, `MainProgressBanners`, `MainSearchBar`, `EmptyLibraryView`, `TrackListView`).
  - [x] Creación de `RitmoApplication` para orquestar la inicialización de librerías de depuración.
- [x] **Doble Motor de Audio y Ecualizador de 10 Bandas en C++:**
  - [x] Motor nativo C++ con Google Oboe (AAudio/OpenSL ES) para baja latencia.
  - [x] Motor estándar con ExoPlayer / Media3 para compatibilidad y streaming local.
  - [x] Ecualizador gráfico y paramétrico de 10 bandas (31 Hz a 16 kHz) con filtros Biquad IIR en C++.
  - [x] Soporte unificado en Oboe y Media3 (mediante `Media3EqualizerAudioProcessor`), garantizando idéntica respuesta acústica.
  - [x] Modal interactivo `EqualizerModal` en Compose con preajustes de fábrica.
- [x] **Reproducción en Segundo Plano y Ergonomía:**
  - [x] Integración con `RitmoMediaSessionService` para control en notificaciones y pantalla de bloqueo.
  - [x] Controles táctiles de 48dp optimizados para smartphone.
  - [x] Animaciones fluidas: curvas `FastOutSlowInEasing`, escalado elástico `spring` en carátula y barras de ecualización animadas en vivo.
- [x] **Almacenamiento Modular y Carátulas WebP Sin Pérdida:**
  - [x] Subdirectorios privados dedicados: `music/`, `covers/` y `artists/` con archivos `.json` modulares por pista.
  - [x] Compresión WebP Lossless en segundo plano sin congelar la UI.
  - [x] Generación procedural automática de carátulas para pistas sin arte embebido (`ArtworkProcessor.generateProceduralArtworkLosslessWebP`).
  - [x] Selector visual Photo Picker sin permisos invasivos.
- [x] **Visualizador y Editor de Letras Sincronizadas (LRC / Plain Text):**
  - [x] Parser de marcas de tiempo LRC `[mm:ss.xx]` con detección de estrofa activa en `LyricsParser.kt`.
  - [x] Componente `LyricsView` con autodesplazamiento animado y soporte de salto temporal (*seek*) al pulsar cualquier verso.
  - [x] Transición animada en `FullPlayerView` entre carátula y visualizador de letras con botón de 48dp.
  - [x] Editor táctil `EditLyricsDialog` para añadir, modificar o pegar letras desde el portapapeles.
  - [x] Sincronización automática con archivos complementarios `.lrc` / `.txt` en almacenamiento local y persistencia en Room.
- [x] **Listas de Reproducción Inteligentes y Generación Automática por Artista:**
  - [x] Detección automática en segundo plano de artistas con 3 o más canciones en la biblioteca musical.
  - [x] Creación automática de playlists dedicadas por artista con sincronización de nuevas canciones.
  - [x] Identificación distintiva en la interfaz con badge `ARTISTA` e icono personalizado en `PlaylistListView`.
- [x] **Flujo de CI/CD en GitHub Actions:**
  - [x] Descarga y precalentamiento de dependencias de Rust (`cargo fetch`).
  - [x] Compilación limpia y firma automatizada del APK Debug.

---

## 🎧 Fase 2 — Procesamiento DSP Avanzado y Funciones por Motor (En Planificación)

- [ ] **Funciones Exclusivas del Motor Nativo (Oboe C++):**
  - **Herramienta de Diagnóstico y Telemetría para C++:**
    - Creación de una herramienta dedicada para C++ para depuración, inspección y profiling del motor nativo directamente desde el smartphone sin PC ni ADB.
    - Monitoreo en tiempo real de buffers PCM, tiempos de ciclo de renderizado nativo, detección de underruns/xruns y análisis del estado de memoria nativa.
  - **Audio Espacial 360° / Efecto 8D Gratuito:**
    - Procesamiento matemático en tiempo real a nivel de muestra PCM (*sample-by-sample*).
    - Paneo circular continuo L/R con velocidad y radio de giro ajustables.
    - Simulación de retardo interaural (*ITD*) y atenuación de sombra craneal (*head-shadowing*).
    - Micro-reverberación de sala para sensación tridimensional real sin costo ni anuncios.
  - **Modo Direct-to-DAC / Bit-Perfect:**
    - Acceso directo de ultra baja latencia para audiófilos con DACs USB y auriculares de monitoreo.
  - **Control de Tono y Afinación Alternativa (432 Hz):**
    - Modulación precisa de semitonos musicales sin alterar la velocidad.

- [ ] **Funciones Exclusivas del Motor Estándar (ExoPlayer / Media3):**
  - **Crossfade Inteligente:**
    - Transición fluida con fundido cruzado regulable (1 a 10 segundos) entre pistas consecutivas.
  - **Gapless Playback Universal:**
    - Reproducción continua sin micro-pausas entre pistas de conciertos en vivo o álbumes conceptuales.
  - **Eficiencia Energética Optimizada:**
    - Máximo aprovechamiento de decodificadores multimedia del chipset para bajo consumo de batería.

---

## 📊 Fase 3 — Listas de Reproducción Inteligentes y Exportación

- [ ] Listas de reproducción locales basadas en carpetas y etiquetas de Rust.
- [ ] Exportador de biblioteca y copia de seguridad de archivos JSON modulares.
- [ ] Visualizador de espectro FFT en tiempo real conectado al callback nativo de audio.
