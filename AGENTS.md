# Directrices y Reglas para Agentes de IA — Ritmo

Este archivo es leído automáticamente por los agentes de IA en cada sesión para garantizar coherencia arquitectónica, rigor técnico y respeto estricto a las preferencias del desarrollador.

---

## 📌 Reglas Fundamentales de Desarrollo

1. **Razonar antes de actuar:**
   - Antes de realizar cualquier cambio, analiza metódicamente las herramientas disponibles, los archivos existentes y el impacto en la compilación. No realices modificaciones impulsivas.

2. **Entorno del Desarrollador (Smartphone sin PC ni ADB):**
   - El usuario programa, compila, prueba y gestiona el proyecto exclusivamente desde un teléfono móvil.
   - **Herramientas de Debug Avanzadas en la UI:** Cualquier error nativo (C++, Rust) o del framework debe capturarse con su **código de error numérico crudo** (ej. códigos Oboe, `errno`, `PlaybackException.errorCode`) y exponerse en la interfaz mediante la `DebugConsoleModal` y `RawErrorDialog`, con opción de copiar el reporte completo al portapapeles.
   - Asegura siempre que todos los controles táctiles respeten el tamaño mínimo de 48dp, con espaciado amplio y navegación fluida con una sola mano.
   - En caso de crear herramientas auxiliares, no usar configuraciones tipo `persist.sys.*`.

3. **Canal de Distribución Independiente:**
   - El objetivo de despliegue es **Uptodown** o distribución directa de APK.
   - No depender de servicios de Google Play. La app debe ser 100% funcional offline.

4. **Calidad de Dependencias sobre Tamaño de APK:**
   - No intentes reinventar librerías complejas para ahorrar megabytes. Al usuario no le importa el peso del APK con tal de que todo sea robusto, estándar, probado y funcione al 100%.

5. **Inclusión Obligatoria de C++ y Rust en la Compilación:**
   - El código en C++ y Rust **debe estar completamente integrado y enlazado en los scripts de compilación de Gradle (`build.gradle.kts`, CMakeLists.txt)**.
   - No sustituir funcionalidades nativas con apaños temporales o fallbacks en Kotlin cuando se haya establecido un motor nativo para esa tarea.
   - En GitHub Actions, el flujo debe incluir la descarga de dependencias de Rust mediante `cargo fetch`.

6. **Seguridad Legal de Nombres y Marcas:**
   - No utilizar en nombres de archivos, paquetes o identificadores marcas comerciales de terceros protegidas por derechos de autor que puedan poner en riesgo legal la distribución del APK del usuario.

7. **Idioma de Mensajes y Documentación:**
   - Todos los mensajes de commit, documentación y textos de usuario deben mantenerse en español con redacción profesional y concisa.
   - Si existe un archivo `commit_message.txt`, su información debe estar en español y solo actualizarse si el usuario lo pide expresamente.

---

## 🦀 Núcleo Nativo Rust — Extractor, Indexador y Editor de Metadatos Audiófilo

- **Procesamiento de Tags en Rust Exclusivo:**
  - La extracción, indexación y análisis profundo de metadatos de audio (ID3v1, ID3v2, FLAC/Vorbis, Opus, APE) se ejecuta exclusivamente en el crate nativo de Rust (`app/src/main/rust`).
  - No se debe procesar o parsear tags mediante Kotlin cuando el analizador nativo de Rust está designado para ello.
- **Edición y Reescritura de Etiquetas en Rust:**
  - La edición de metadatos (título y artista) se delega directamente a Rust a través de `nativeUpdateMetadata`.
  - Rust realiza la reescritura en el archivo y el re-análisis nativo de la pista para sincronizar el álbum y las etiquetas con la base de datos Room y el archivo JSON modular.
- **Puente Nativo JNI y C-ABI:**
  - Las llamadas a Rust se orquestan mediante `RustAudioEngine.kt` y los símbolos exportados en C++ (`native_bridge.cpp`) enlazados estáticamente con `librust_audio.a`.

---

## 🎼 Arquitectura de Audio Dual y Procesamiento DSP en C++

- **Motor Estándar:** `AudioEngineType.EXOPLAYER`
  - Utiliza `androidx.media3:media3-exoplayer`.
  - Integrado con `RitmoMediaSessionService` para reproducción continua en segundo plano y controles en la pantalla de bloqueo.
  - **Ecualizador de 10 Bandas en C++ (Media3):** Integrado mediante `Media3EqualizerAudioProcessor` inyectado en `DefaultRenderersFactory`/`DefaultAudioSink`, aplicando los filtros Biquad IIR a nivel de muestra PCM en C++ a través de JNI.
- **Motor Nativo:** `AudioEngineType.OBOE_CPP`
  - Utiliza `com.google.oboe:oboe` y el puente nativo JNI en `app/src/main/cpp`.
  - **Ecualizador de 10 Bandas en C++ (Oboe):** Implementado con filtros Biquad IIR a nivel de muestra PCM en tiempo real dentro del callback nativo de Oboe.
- **Telemetría y Diagnóstico en C++:**
  - `OboeAudioPlayer` mantiene registro del último error numérico (`nativeGetLastErrorCode`), mensaje descriptivo (`nativeGetLastErrorString`), información del dispositivo (`nativeGetAudioDeviceInfo`) y estadísticas de stream (`nativeGetStreamStatsJson`), protegidos concurrentemente con `std::mutex mErrorMutex`.

---

## 🛠️ Sistema de Debug Crudo para Desarrollo en Smartphone

- **`DebugLogManager`:** Bitácora en memoria con búfer circular de hasta 200 eventos con niveles de severidad (`VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `CRITICAL`), código de error numérico crudo, marca de tiempo y detalles técnicos.
- **Consola de Diagnóstico Táctil (`DebugConsoleModal`):** Permite inspeccionar en tiempo real el estado de los motores C++ y Rust, los logs de ejecución y copiar un informe técnico completo al portapapeles con un toque.
- **Copia Individual de Logs:** Cada evento en la consola debe poder copiarse individualmente al portapapeles mediante su botón táctil de 48dp o mediante tap directo, facilitando el análisis aislado de fallos en el móvil.
- **Diálogo de Error Crudo (`RawErrorDialog`):** Notifica al desarrollador ante cualquier fallo de reproducción o procesamiento con el código numérico exacto para resolución inmediata sin necesidad de PC.

---

## 🎨 Generación Procedural Automática de Carátulas (Lossless WebP)

- **Portadas Procedurales sin Dependencias Externas:**
  - Cuando una pista de audio no disponga de carátula embebida en sus metadatos nativos, la app genera automáticamente una portada procedural audiófila única.
  - El algoritmo es determinista basado en el título y artista (`ArtworkProcessor.generateProceduralArtworkLosslessWebP`), integrando gradientes armónicos, ondas acústicas concéntricas, visualizador espectral de base y monograma tipográfico.
  - La portada se comprime en formato WebP sin pérdida (Lossless WebP al 100%) y se persiste en `covers/`, asignándose automáticamente al `TrackEntity` y al archivo JSON modular de la pista sin dejar ninguna canción sin carátula.
