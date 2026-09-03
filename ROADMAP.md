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
- [x] **Doble Motor de Audio Seleccionable y Ajustes Generales:**
  - Selector exclusivo en el primer inicio con persistencia en SharedPreferences (`KEY_ENGINE_PROMPTED`).
  - Motor estándar con ExoPlayer / Media3.
  - Motor nativo C++ con Google Oboe (AAudio/OpenSL ES) para baja latencia.
  - Pantalla independiente de Configuración General (`SettingsScreen`) para alternar el motor de audio en caliente, consultar el diagnóstico de módulos nativos y gestionar la biblioteca.
- [x] **Preparación de Infraestructura Nativa:**
  - Integración de CMake y Android NDK para compilación de C++.
  - Estructura y enlace de biblioteca nativa en Rust para futuras ampliaciones seguras.

---

## 🎧 Fase 2 — Exclusividades por Motor de Audio y DSP Avanzado

El objetivo de esta fase es exprimir al máximo el potencial y la naturaleza única de cada motor de audio integrado, ofreciendo funciones avanzadas de forma completamente gratuita y sin dependencias de pago:

- [ ] **Funciones Exclusivas del Motor Nativo (Oboe C++):**
  - **Audio 8D / Audio Espacial 360° Gratuito:**
    - Procesamiento matemático en tiempo real a nivel de muestra PCM (*sample-by-sample*).
    - Paneo circular continuo L/R con velocidad y radio de giro ajustables.
    - Simulación de retardo interaural (*Interaural Time Delay - ITD*) y atenuación de cabeza (*head-shadowing filter*).
    - Micro-reverberación de sala para sensación tridimensional real sin costo ni anuncios.
  - **Control de Tono y Velocidad sin Distorsión (*Pitch & Speed Shift*):**
    - Modulación precisa de semitonos musicales y afinación alternativa (ej: 432 Hz).
  - **Ecualizador Paramétrico y Gráfico de 10 Bandas:**
    - Filtros biquad IIR directos de punto flotante en C++ con refuerzo de graves profundo (*Bass Boost*) y visualizador de curvas en Compose.
  - **Modo Direct-to-DAC / Bit-Perfect:**
    - Acceso directo de ultra baja latencia para audiófilos utilizando DACs USB y auriculares de monitoreo.

- [ ] **Funciones Exclusivas del Motor Estándar (ExoPlayer / Media3):**
  - **Crossfade Inteligente:**
    - Transición fluida con fundido cruzado regulable (1 a 10 segundos) entre canciones.
  - **Gapless Playback Universal:**
    - Reproducción continua sin micro-pausas entre pistas de álbumes en vivo y sinfonías.
  - **Eficiencia Energética Máxima:**
    - Aprovechamiento del decodificador multimedia del chipset del dispositivo para mínimo consumo de batería en reproducción prolongada con pantalla apagada.

- [ ] **Exploración de Nuevos Motores de Audio Futuros:**
  - Arquitectura desacoplada y modular pensada para poder incorporar en el futuro motores alternativos adicionales (por ejemplo: motor 100% Rust con Symphonia/Rodio, backend OpenSL ES puro para dispositivos antiguos o motores experimentales de síntesis sonora).

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
