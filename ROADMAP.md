# Roadmap — Ritmo Music Player

Este documento define la hoja de ruta estratégica para el desarrollo de **Ritmo**, organizando las metas en fases claras según el ciclo de desarrollo técnico.

---

## 📍 Estado Actual: Fase 1 — Cimientos Sólidos & Arquitectura Híbrida (Completada)

- [x] **Interfaz Reactiva Material Design 3:**
  - Tema oscuro de alta fidelidad con acentos verdes y contraste óptimo.
  - Barra superior con contador dinámico de canciones y botón de importación directa.
  - Mini reproductor persistente con carátula, texto deslizante y controles de reproducción/avance.
  - Reproductor completo con animación fluida de apertura/cierre, barra de progreso interactiva (seeker), modo aleatorio y modos de repetición.
- [x] **Privacidad e Importación Local:**
  - Selector de archivos mediante `ActivityResultContracts.OpenMultipleDocuments()`.
  - Copia y almacenamiento local aislado en el almacenamiento de la app.
  - Extracción y almacenamiento en caché de miniaturas y carátulas integradas.
  - Persistencia de metadatos (título, artista, álbum, duración, ruta) mediante **Room Database**.
- [x] **Doble Motor de Audio Seleccionable:**
  - Selector al inicio de la aplicación y botón de conmutación en caliente.
  - Motor estándar con ExoPlayer / Media3.
  - Motor nativo C++ con Google Oboe (AAudio/OpenSL ES) para baja latencia.
- [x] **Preparación de Infraestructura Nativa:**
  - Integración de CMake y Android NDK para compilación de C++.
  - Estructura y enlace de biblioteca nativa en Rust para futuras ampliaciones seguras.

---

## 🚀 Fase 2 — Procesamiento Digital de Señales (DSP) en C++

- [ ] **Ecualizador Gráfico y Paramétrico de 10 Bandas:**
  - Procesamiento matemático de audio PCM en tiempo real en el motor Oboe.
  - Presets audiófilos (Rock, Pop, Clásica, Jazz, Refuerzo Vocal, Electrónica).
  - Control de ganancia por banda con visualización de curva de respuesta en Compose.
- [ ] **Efectos de Audio de Baja Latencia:**
  - Refuerzo de graves dinámico (*Bass Boost*).
  - Virtualizador espacial estéreo (*Stereo Widener*).
  - Normalización de volumen inteligente basada en ReplayGain / EBU R128 para evitar variaciones bruscas entre temas.
- [ ] **Reproducción Continua Sin Pausas (*Gapless Playback*):**
  - Pre-buffering de la siguiente pista en la cola de reproducción en el hilo nativo de C++ para eliminar silencios entre canciones consecutivas.
- [ ] **Crossfade Configurable:**
  - Transición suave con fundido cruzado regulable de 1 a 10 segundos entre pistas.

---

## 🦀 Fase 3 — Potenciación del Núcleo Rust

- [ ] **Motor de Metadatos Seguro en Rust:**
  - Integración de crates como `symphonia` o `lofty` para parseo de etiquetas ID3v1, ID3v2, Vorbis Comments y cabeceras FLAC.
  - Garantía total contra vulnerabilidades de desbordamiento de búfer al analizar archivos de audio provenientes de fuentes externas.
- [ ] **Búferes Circulares Sin Bloqueo (*Lock-Free Ring Buffers*):**
  - Cola de comunicación entre hilos de lectura de disco y de reproducción nativa en Rust, garantizando cero micro-tirones causados por la recolección de basura o bloqueos de E/S.
- [ ] **Decodificador de Audio Hi-Res en Rust:**
  - Decodificación directa de formatos de alta resolución (FLAC a 24 bits / 96 kHz o 192 kHz, OGG Vorbis y Opus).

---

## 🎵 Fase 4 — Gestión Avanzada de Biblioteca

- [ ] **Listas de Reproducción Personalizadas (*Playlists*):**
  - Creación, renombrado, reordenación por arrastre (*drag and drop*) y eliminación de listas de reproducción locales.
  - Lista automática de "Favoritos" (marcar canciones con corazón).
  - Lista de "Reproducidas recientemente" e "Historial de escucha".
- [ ] **Explorador por Carpetas y Artistas:**
  - Vistas agrupadas por Álbum, Artista y Carpetas del almacenamiento importado.
- [ ] **Edición de Etiquetas de Audio:**
  - Editor rápido de metadatos (cambiar título, artista o álbum de un archivo directamente desde la app).

---

## 📦 Fase 5 — Distribución y Publicación

- [ ] **Optimización para Tiendas Alternativas:**
  - Empaquetado optimizado para **Uptodown**, F-Droid y descarga directa de APK.
  - Soporte para arquitecturas ARM64 (`arm64-v8a`) y ARMv7 (`armeabi-v7a`).
- [ ] **Sistema de Actualizaciones In-App:**
  - Comprobador ligero de versiones disponibles en repositorios externos sin depender de Google Play Services.
- [ ] **Exportación y Respaldo:**
  - Exportar/importar copia de seguridad de listas de reproducción y favoritos en formato JSON.
