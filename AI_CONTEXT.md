# Contexto de IA para el Proyecto — Ritmo

Este archivo proporciona el contexto técnico completo, restricciones de diseño, perfil del desarrollador y directrices del ciclo de construcción para que cualquier asistente de Inteligencia Artificial comprenda la arquitectura de **Ritmo** y actúe con máxima precisión.

---

## 🧭 Visión del Proyecto
- **Nombre:** Ritmo
- **Naturaleza:** Reproductor de música local audiófilo para Android con enfoque estricto en privacidad, alto rendimiento, procesamiento nativo y fidelidad acústica.
- **Filosofía de Privacidad:** El usuario tiene soberanía total sobre sus pistas. La app no realiza escaneos invasivos en segundo plano; únicamente indexa los archivos seleccionados explícitamente mediante el selector seguro del sistema.
- **Arquitectura Híbrida Tripartita (Kotlin + C++ + Rust):**
  - **Kotlin & Jetpack Compose:** Capa de presentación visual táctil reactiva, Material Design 3 y orquestación MVVM.
  - **C++20 & Google Oboe / AAudio:** Motor de audio nativo de ultra baja latencia con procesamiento DSP en tiempo real (filtros Biquad IIR para ecualización de 10 bandas compartida con Media3).
  - **Rust 2021 (`ritmo_rust`):** Núcleo audiófilo de extracción, indexación y reescritura avanzada multi-campo de metadatos (título, artista, álbum, género, año en ID3v1, ID3v2, FLAC/Vorbis, Opus, APE) con análisis nativo automático y compilación directa integrada en `jniLibs` para garantizar la presencia de las librerías dinámicas en el APK final.
- **Herramientas de Depuración Avanzadas con Códigos Crudos:** Suite integrada en la UI (`DebugLogManager`, `DebugConsoleModal`, `RawErrorDialog`) con captura de códigos numéricos de C++ Oboe, Media3 y Rust para depuración total en smartphone sin necesidad de PC ni ADB.
- **Suite de Debug para Smartphone:**
  - **Timber & `RitmoDebugTree`:** Sistema de logging robusto conectado directamente a `DebugLogManager`.
  - **Atrapador Global de Excepciones No Controladas (`RitmoCrashHandler`):** Captura stacktraces de cualquier fallo imprevisto del proceso, los persiste en `SharedPreferences` y los expone en la `DebugConsoleModal`.
  - **LeakCanary:** Vigilancia automática de fugas de memoria en APK Debug sin impacto en builds de producción.
- **Desarrollo y Diseño Modular:**
  - `AudioPlayerManager` dividido en `PlaybackQueueManager` y `EqualizerController`.
  - Vistas UI (`MainMusicScreen`, `SettingsScreen`) modularizadas en submódulos limpios bajo `ui.main` y `ui.settings`.
  - Crate Rust `ritmo_rust` dividido en submódulos especializados (`models`, `id3`, `vorbis`, `flac`, `ape`, `mp4`, `writer`, `jni_bridge`).
- **Reproducción en Segundo Plano y Mini-Reproductor del Sistema:**
  - `RitmoMediaSessionService` como servicio en primer plano con `DefaultMediaNotificationProvider`, canal dedicado e icono `ic_notification`.
  - Sincronización continua de metadatos tanto para motor ExoPlayer como Oboe C++, manteniendo controles activos en la barra de notificaciones, pantalla de bloqueo y dispositivos Bluetooth.
  - Solicitud proactiva de permiso `POST_NOTIFICATIONS` en Android 13+.
- **Visualizador y Editor de Letras Sincronizadas (LRC y Texto):**
  - `LyricsParser`: Analizador de marcas de tiempo estándar y centésimas `[mm:ss.xx]`, seguimiento de estrofa activa y persistencia de archivos complementarios `.lrc` / `.txt` en disco.
  - `LyricsView` y `EditLyricsDialog`: Autodesplazamiento sincronizado, interacción de salto temporal (*seek*) al pulsar cualquier línea y diálogo táctil de 48dp para ingresar o pegar letras.
  - Integración fluida en `FullPlayerView` con switcher animado entre carátula de alta resolución y letras.
- **Generación Totalmente Automática de Playlists por Artista (3+ Canciones):**
  - Algoritmo en `MusicPlayerViewModel` que monitorea la biblioteca y genera automáticamente playlists dedicadas para artistas con 3 o más pistas.
  - Distintivo visual en `PlaylistListView` con badge `ARTISTA` e icono personalizado.

---

## 👤 Perfil del Usuario y Restricciones Operativas

1. **Desarrollo Exclusivo en Smartphone (Sin PC ni ADB):**
   - El desarrollador interactúa, programa y prueba la app directamente desde un teléfono móvil.
   - **Diagnóstico Crudo Imprescindible:** Todos los errores nativos o del sistema deben registrarse con códigos de error numéricos exactos y ponerse a disposición en la interfaz mediante la consola de depuración y la acción de "Copiar Reporte".
   - Todo control táctil debe contar con al menos 48dp de área interactiva y admitir uso fluido con una sola mano.

2. **Canal de Distribución Independiente:**
   - Publicación orientada a **Uptodown** o descarga directa de APK.
   - No utilizar servicios privativos de Google Play; la app debe operar 100% desconectada y autónoma.

3. **Política de Dependencias de Primera Línea:**
   - La funcionalidad y robustez prevalecen sobre el peso del APK.
   - Se emplean librerías estándar consolidadas (Oboe, Media3, Room, Coil, crates de Rust `id3`, `metaflac`, `lofty`).

4. **Integración Obligatoria de C++ y Rust:**
   - Toda lógica nativa en C++ y Rust debe compilarse y enlazarse en `build.gradle.kts` y `CMakeLists.txt`.
   - Está prohibido colocar reemplazos provisionales en Kotlin para tareas asignadas a los motores nativos.
   - Los workflows de CI/CD (GitHub Actions) deben incluir la descarga de dependencias con `cargo fetch`.

5. **Protección de Propiedad Intelectual:**
   - No utilizar marcas de terceros en identificadores, paquetes o recursos.

6. **Idioma y Control de Commit:**
   - Todos los mensajes de commit y documentación deben mantenerse en español. `commit_message.txt` solo se modifica bajo solicitud explícita del usuario.

---

## 🦀 Módulo Nativo Rust (`ritmo_rust`)
- **Ubicación:** `app/src/main/rust/`
- **Capacidades:**
  - `extract_audio_metadata`: Parser audiófilo multi-formato que retorna JSON estructurado con título, artista, álbum, duración, bitrate, sample rate y canales.
  - `extract_audio_artwork`: Extracción de bytes crudos de arte de portada embebido.
  - `update_audio_metadata`: Modificación física y reescritura de etiquetas de título y artista en el archivo de audio, seguida de re-análisis nativo de álbum.
  - `rust_ping` y `rust_version`: Métodos de diagnóstico para comprobar la integridad del enlace nativo.

---

## 🛠️ Telemetría y Consola de Diagnóstico
- **`DebugLogManager`:** Búfer circular de telemetría en memoria (hilo seguro con Mutex) accesible globalmente.
- **Códigos de Error Nativos Oboe:**
  - `0`: Éxito / Sin errores.
  - `-998`: Puntero o recurso de reproducción no inicializado.
  - `-999`: Error fatal o no categorizado de AAudio/OpenSL ES.
  - Códigos numéricos estándar de Oboe (ej. `-899` Dispositivo desconectado, `-898` Servidor de audio muerto).
- **Códigos de Error Media3:** Captura de `PlaybackException.errorCode` y nombre semántico.
- **Códigos de Error Rust:** Prefijados con tags técnicos como `[RUST_NATIVE_ERR_TAG_WRITE]` y descriptores de I/O.
- **Copia Individual de Logs:** `DebugConsoleModal` permite copiar al portapapeles cualquier evento individual mediante su botón interactivo de 48dp o mediante tap directo sobre el elemento.

---

## 🎨 Generación Procedural Automática de Carátulas
- **Objetivo:** Garantizar que ninguna pista carezca de arte visual representativo.
- **Algoritmo (`ArtworkProcessor`):**
  - Si la extracción nativa en Rust o la búsqueda embebida no encuentran carátula, se invoca `generateProceduralArtworkLosslessWebP`.
  - Genera una carátula determinista de 512x512 píxeles a partir del hash del título y artista, empleando paletas audiófilas armónicas, anillos acústicos concéntricos, espectro estilizado y monograma tipográfico con sombra.
  - Se codifica en formato WebP Lossless al 100% de fidelidad y se persiste en `covers/`, registrándose en Room y en el archivo JSON modular.

---

## 🔄 Mapa de las 7 Fases del Ciclo de Construcción
1. **01 El Arquitecto:** Planificación y diseño de contratos JNI, entidades y modelos de datos.
2. **02 El Constructor:** Implementación técnica modular, tipada, con validación de inputs y manejo de errores.
3. **03 El Detective:** Debugging metódico con códigos de error crudos y análisis de causa raíz.
4. **04 El Crítico:** Code review de seguridad, concurrencia (mutexes) y rendimiento en tiempo real.
5. **05 El Optimizador:** Refactorización, reducción de copias de memoria y optimización de buffers PCM.
6. **06 El Escudo:** Pruebas unitarias y validación con suites locales Robolectric.
7. **07 El Narrador:** Documentación técnica precisa, clara y en español.
