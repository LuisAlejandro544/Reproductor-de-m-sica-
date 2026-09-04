# Ritmo — Reproductor de Música Local Audiófilo con Motores Nativos C++ y Rust

**Ritmo** es un reproductor de audio local moderno, privado y de alto rendimiento para Android. Ha sido diseñado específicamente para usuarios y desarrolladores audiófilos que buscan el control total de su biblioteca musical, sin rastreo, sin telemetría externa y sin permisos invasivos de escaneo en segundo plano.

La aplicación está concebida para su distribución independiente en tiendas de aplicaciones y repositorios de APKs de terceros (como **Uptodown**), funcionando de manera 100% autónoma y offline sin requerir servicios privativos de Google Play.

---

## 🎯 Características Principales

### 1. 🦀 Núcleo de Metadatos Audiófilo en Rust (`ritmo_rust`)
- **Parsing Nativo Directo:** La extracción, indexación y lectura profunda de metadatos (ID3v1, ID3v2, FLAC/Vorbis, Opus y APE) se ejecuta íntegramente en el módulo nativo de Rust compilado con C-ABI, sin pasar por analizadores de Kotlin.
- **Edición Avanzada y Reescritura Multi-Campo de Etiquetas:** Permite editar de forma potente el **título, artista, álbum, género y año** de las canciones directamente desde la interfaz táctil. Rust modifica in-place las estructuras de tags en el almacenamiento y reanaliza automáticamente las etiquetas para sincronizar la base de datos Room y los archivos JSON modulares.
- **Integración Nativa Total en APK:** El crate de Rust se compila para múltiples arquitecturas Android (`aarch64`, `armv7`, `x86_64`) y se empaqueta directamente en `jniLibs` para garantizar su presencia en el APK final sin omisiones.
- **Extracción de Arte de Portada:** Obtención directa de los bytes crudos de carátulas integradas en el contenedor de audio para su procesamiento posterior.

### 2. 🐛 Herramientas de Debug Avanzadas & Telemetría Cruda (Desarrollo en Móvil)
- **Diseñado para Programar sin PC:** Pensado para desarrolladores que gestionan y depuran la app directamente desde un smartphone sin acceso a ADB.
- **Códigos de Error Numéricos Crudos:** Captura códigos nativos de Oboe C++ (ej. `0` OK, `-998` Stream no listo, `-899` Dispositivo desconectado) y errores de Media3 (`PlaybackException.errorCode`).
- **Atrapador Global de Excepciones No Controladas (`RitmoCrashHandler`):** Intercepta cualquier crash inesperado a nivel de proceso (`Thread.UncaughtExceptionHandler`), capturando el StackTrace completo, código crudo y persistiendo el reporte en `SharedPreferences` para inspeccionarlo o copiarlo desde `DebugConsoleModal` tras reiniciar la app.
- **Registro con Timber & `RitmoDebugTree`:** Sistema de logging robusto con Timber conectado al búfer en memoria de `DebugLogManager`.
- **Detección Automática de Fugas de Memoria (`LeakCanary`):** Integrado mediante `debugImplementation` para supervisar retenciones de memoria en el APK Debug sin impacto en release.
- **Consola de Diagnóstico Táctil (`DebugConsoleModal`):** Permite inspeccionar en tiempo real el estado de los motores nativos, revisar la bitácora con códigos de error y marcas de tiempo, visualizar el último crash no controlado y copiar el informe completo al portapapeles con un solo toque.
- **Copia Individual de Logs:** Cada evento en la lista de logs dispone de su propio botón táctil de 48dp y soporte de tap directo para copiar el texto formateado (código numérico, tag, timestamp y mensaje) al portapapeles al instante.
- **Diálogos de Error Interceptado (`RawErrorDialog`):** Notificación visual inmediata ante cualquier anomalía con el código exacto y botón directo de copia.
- **Acceso Directo Rápido:** Icono de diagnóstico en la cabecera principal y sección técnica en la pantalla de Ajustes.

### 3. 🧩 Desarrollo y Arquitectura Modular
- **Desacoplamiento de Reproducción:** `AudioPlayerManager` delega la gestión de cola, repetición y modo aleatorio a `PlaybackQueueManager`, y el control del DSP de 10 bandas y persistencia a `EqualizerController`.
- **UI Descompuesta en Submódulos:** `MainMusicScreen` y `SettingsScreen` divididas en componentes especializados reutilizables bajo `ui.main` y `ui.settings`.
- **Crate de Rust Modularizado:** `ritmo_rust` segmentado en módulos independientes (`models.rs`, `id3.rs`, `vorbis.rs`, `flac.rs`, `ape.rs`, `mp4.rs`, `writer.rs`, `jni_bridge.rs`) para facilitar su mantenimiento y extensión.

### 4. 🎼 Doble Motor de Audio y Ecualizador DSP de 10 Bandas en C++
- **Motor Nativo Oboe C++:** Acceso directo a `AAudio` y `OpenSL ES` para ultra baja latencia y procesamiento en tiempo real dentro del callback de audio.
- **Motor Estándar ExoPlayer / Media3:** Motor de referencia para compatibilidad exhaustiva de códecs y reproducción continua.
- **Ecualizador de 10 Bandas Unificado:** Filtros Biquad IIR Transposed Direct Form II calculados muestra a muestra en C++ (31 Hz a 16 kHz, -12 dB a +12 dB) compartidos entre Oboe y Media3 (mediante `Media3EqualizerAudioProcessor`), garantizando idéntica respuesta acústica.

### 4. 🗄️ Almacenamiento Modular Desacoplado & Carátulas WebP Sin Pérdida
- **Estructura Modular en Almacenamiento Privado:**
  - `music/`: Pistas de audio locales importadas.
  - `covers/`: Carátulas de álbumes procesadas en WebP Lossless.
  - `artists/`: Metadatos sincronizados en archivos `.json` individuales por canción.
- **Compresión WebP Lossless en Segundo Plano:** Posibilidad de cambiar o asignar la carátula de cualquier pista con máxima calidad de compresión sin pérdida mediante Corrutinas (`Dispatchers.IO`).
- **Generación Procedural Automática de Carátulas:** Si una pista carece de arte embebido, el sistema genera automáticamente una portada audiófila única y determinista basada en el título y artista (gradientes armónicos, ondas concéntricas, visualizador espectral y monograma tipográfico con sombra) guardada en WebP Lossless.
- **Android Photo Picker Oficial:** Selección visual de carátulas sin requerir permisos invasivos de almacenamiento.

### 5. 📜 Visualizador y Editor de Letras Sincronizadas (LRC y Texto)
- **Sincronización en Tiempo Real:** Motor `LyricsParser` con soporte completo de marcas de tiempo estándar y centésimas (`[mm:ss.xx]`), seguimiento de la línea activa y salto temporal (*seek*) al tocar cualquier estrofa.
- **Transición Táctil en Reproductor:** Transición animada fluida en `FullPlayerView` que alterna entre la carátula y el componente `LyricsView` con un toque.
- **Persistencia y Compatibilidad Offline:** Almacenamiento directo en Room (columna `lyrics`), sincronización con archivos JSON modulares y lectura/escritura automática de archivos complementarios `.lrc` en el almacenamiento local junto a la pista de audio.
- **Editor Táctil Integrado (`EditLyricsDialog`):** Diálogo interactivo accesible en cualquier momento para ingresar, modificar o pegar letras desde el portapapeles.

### 6. 🎵 Generación Automática Inteligente de Playlists por Artista
- **Detección Automática de Colección (3+ Canciones):** Algoritmo en segundo plano que analiza la biblioteca y crea automáticamente una playlist dedicada para cualquier artista del que se dispongan 3 o más canciones.
- **Sincronización Proactiva:** Cualquier nueva canción importada de ese artista se añade automáticamente a la lista sin intervención del usuario.
- **Distintivo Visual en Interfaz:** Las listas automáticas se identifican con el badge `"ARTISTA"` y un icono dedicado en `PlaylistListView`.

### 7. 📱 Experiencia Táctil Ergonómica y Reproducción en Segundo Plano
- **Mini-Reproductor Nativo en Barra de Notificaciones y Pantalla de Bloqueo:** Notificación nativa persistente en primer plano mediante `DefaultMediaNotificationProvider`, canal `ritmo_playback_channel` e icono `ic_notification`. Muestra carátula, título, artista, barra de progreso y botones de reproducción/pausa/avance tanto en motor ExoPlayer como en motor Oboe C++.
- **`RitmoMediaSessionService`:** Servicio de medios en primer plano enlazado a la sesión multimedia para persistencia ininterrumpida al salir de la aplicación o con la pantalla apagada.
- **Permiso en Tiempo Real `POST_NOTIFICATIONS`:** Solicitud proactiva de permisos en Android 13+ para garantizar la visibilidad inmediata del reproductor del sistema.
- **Controles Táctiles de 48dp:** Diseñados específicamente para navegación y control cómodo con una sola mano en pantallas de smartphones.
- **Animaciones Fluidas:** Curvas `FastOutSlowInEasing`, escalado elástico `spring` en carátulas y barras animadas en vivo para la pista en reproducción.

---

## 🛠️ Stack Tecnológico

| Capa | Tecnologías |
| :--- | :--- |
| **Presentación y UI** | Kotlin 2.2+, Jetpack Compose, Material Design 3, Animaciones reactivas |
| **Servicio de Segundo Plano** | AndroidX Media3 Session (`MediaSessionService`) |
| **Persistencia Local** | Room Database 2.7+ (KSP) y Archivos JSON modulares por pista |
| **Núcleo de Metadatos y Tags** | Rust 2021 (`ritmo_rust` con `id3`, `metaflac`, `lofty`, `jni`), C-ABI |
| **Motor de Audio Nativo** | C++20, Google Oboe (AAudio / OpenSL ES), CMake 3.22+, Android NDK r26b |
| **Procesamiento DSP** | Filtros Biquad IIR en C++ integrados en Oboe y Media3 (AudioProcessor) |
| **Motor Estándar** | AndroidX Media3 / ExoPlayer 1.5+ |
| **Imágenes y Carátulas** | WebP Lossless, Coil Compose, Android Photo Picker |
| **Depuración y Diagnóstico** | `DebugLogManager` en memoria, `DebugConsoleModal`, códigos de error crudos |
| **Compilación y CI/CD** | Gradle 9.3+ Kotlin DSL, GitHub Actions con `cargo fetch` |

---

## 🚀 Compilación y Despliegue

### Compilación Local
```bash
# Compilar el APK de depuración con todos los módulos nativos
gradle :app:assembleDebug
```

### Flujo de GitHub Actions (`build-debug-apk.yml`)
1. Configuración de Java JDK 17 y Android SDK.
2. Instalación de Android NDK r26b y CMake 3.22.1.
3. Configuración de Rust stable y descarga de dependencias del crate (`cargo fetch`).
4. Generación de claves de firma `debug.keystore`.
5. Compilación limpia sin caché (`./gradlew :app:assembleDebug --no-build-cache`).
6. Publicación del artefacto APK listo para instalación directa en smartphones.
