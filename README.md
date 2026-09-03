# Ritmo — Reproductor de Música Local de Alta Fidelidad

**Ritmo** es un reproductor de audio local moderno, privado y de alto rendimiento para Android. Ha sido diseñado específicamente para usuarios que buscan el control total de su biblioteca musical, sin rastreo, sin telemetría y sin permisos invasivos de escaneo en segundo plano.

La aplicación está concebida para su distribución independiente en tiendas de aplicaciones y repositorios de APKs de terceros (como **Uptodown**), funcionando de manera 100% autónoma y offline sin requerir servicios privativos de Google Play.

---

## 🎯 Características Principales

1. **Privacidad y Control Total de Archivos:**
   - No escanea todo tu almacenamiento ni accede a fotos o documentos.
   - Solo reproduce e indexa las canciones y carpetas que decides importar explícitamente mediante el selector seguro del sistema.
   - Base de datos local aislada gestionada con **Room Database**.

2. **Doble Motor de Audio Seleccionable:**
   - **ExoPlayer / Media3:** Motor de referencia del ecosistema Android, con soporte amplio de códecs y reproducción continua.
   - **Oboe C++ Native Audio Engine:** Motor de audio nativo de ultra baja latencia con acceso directo a hardware (`AAudio` y `OpenSL ES`).

3. **Ecualizador Gráfico y Paramétrico de 10 Bandas en C++ (Doble Motor):**
   - Implementado a bajo nivel con filtros Biquad IIR Transposed Direct Form II por muestra en tiempo real.
   - 10 bandas de frecuencia ajustables (-12 dB a +12 dB): 31 Hz, 62 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz y 16 kHz.
   - Preajustes sonoros integrados: Plano, Refuerzo de graves, Agudos brillantes, Vocal, Rock, Pop y Música clásica.
   - **Soporte unificado:** Disponible tanto en **Oboe C++** como en **ExoPlayer (Media3)** mediante `Media3EqualizerAudioProcessor` conectado al motor DSP en C++ vía JNI, ofreciendo ecualización consistente en ambos entornos.

4. **Reproducción Continua en Segundo Plano (MediaSessionService):**
   - Integración nativa con `RitmoMediaSessionService` (`androidx.media3.session.MediaSessionService`).
   - Controles de reproducción interactivos y sincronización de metadatos (título, artista, carátula) en la barra de notificaciones del sistema y la pantalla de bloqueo.

5. **Animaciones Suaves y Diseño Ergonómico:**
   - Animación elástica (*spring*) reactiva en la carátula durante la reproducción y pausa.
   - Transiciones animadas con escalado y fundido (`AnimatedContent`) en controles de reproducción.
   - Indicador visual animado de barras de audio en tiempo real en la pista que está sonando.
   - Despliegue con curvas de aceleración ergonómicas (`FastOutSlowInEasing`) en el reproductor completo y la pantalla de ajustes.
   - Controles táctiles con área mínima de interacción de 48dp optimizados para uso con una sola mano en smartphones.

6. **Almacenamiento Modular y Carátulas WebP sin Pérdida:**
   - Estructura limpia de almacenamiento en `Android/data/<package>/files/` dividida en subcarpetas especializadas:
     - `music/`: Archivos de audio locales importados.
     - `covers/`: Carátulas de álbumes procesadas en formato WebP.
     - `artists/`: Archivos `.json` modulares e independientes por cada canción que enlazan metadatos, audio y carátula.
   - **Compresión WebP Lossless en Segundo Plano:** Posibilidad de asignar o cambiar la carátula de cualquier pista (tenga o no imagen previa) convirtiéndola a WebP a máxima compresión sin pérdida de fidelidad (`Bitmap.CompressFormat.WEBP_LOSSLESS`), ejecutado en segundo plano con Corrutinas (`Dispatchers.IO`) sin congelar la interfaz.
   - Selector visual de imágenes con Android Photo Picker oficial (cero permisos invasivos requeridos).

---

## 🛠️ Stack Tecnológico

| Capa | Tecnologías |
| :--- | :--- |
| **Interfaz de Usuario (UI)** | Kotlin 2.2+, Jetpack Compose, Material Design 3, Animaciones reactivas |
| **Servicio de Segundo Plano** | AndroidX Media3 Session (`MediaSessionService`) |
| **Persistencia Local** | Room Database 2.7+ con KSP y Archivos JSON modulares por pista |
| **Imágenes y Carátulas** | Formato WebP Lossless (sin pérdida), Coil Compose, Android Photo Picker |
| **Motor de Audio Estándar** | AndroidX Media3 / ExoPlayer 1.5+ |
| **Motor de Audio Nativo** | C++20, Google Oboe (AAudio / OpenSL ES), CMake 3.22+, Android NDK r26b |
| **Ecualizador DSP** | Filtros Biquad IIR en C++ integrados en Oboe y en Media3 (AudioProcessor) |
| **Módulo Nativo de Extensión** | Rust 2021 (C-ABI bridge, `staticlib`/`cdylib`) |
| **Compilación y Build System** | Gradle 9.3+ con Kotlin DSL (`build.gradle.kts`) |

---

## 📁 Estructura del Repositorio

```
├── app/
│   ├── src/main/
│   │   ├── cpp/                 # Código nativo en C++ (Oboe Audio Engine, Equalizer y JNI)
│   │   │   ├── CMakeLists.txt   # Configuración de compilación nativa
│   │   │   ├── equalizer.h      # Filtros Biquad IIR de 10 bandas en C++
│   │   │   ├── native_audio.h   # Cabecera del motor Oboe con ecualizador
│   │   │   ├── native_audio.cpp # Implementación del motor y aplicación de DSP
│   │   │   └── native_bridge.cpp# Métodos JNI expuestos a Kotlin
│   │   ├── rust/                # Módulo nativo en Rust (C-ABI)
│   │   ├── java/com/example/    # Código Kotlin
│   │   │   ├── data/            # Entidades Room y DAOs
│   │   │   ├── playback/        # AudioPlayerManager, Media3EqualizerAudioProcessor, OboeAudioBridge, RitmoMediaSessionService
│   │   │   ├── ui/              # Pantallas Compose y ViewModel
│   │   │   │   ├── MainMusicScreen.kt # Pantalla principal con lista y buscador
│   │   │   │   ├── SettingsScreen.kt  # Pantalla de configuración independiente
│   │   │   │   ├── MusicPlayerViewModel.kt # ViewModel con gestión de EQ, WebP y estados
│   │   │   │   └── components/  # EqualizerModal, FullPlayerView, MiniPlayer, TrackListItem
│   │   │   └── util/            # AppStorageManager, ArtworkProcessor, AudioMetadataHelper, FormatUtils
│   │   └── res/                 # Iconos, temas, cadenas en strings.xml
│   ├── storage/                 # Directorio en runtime: Android/data/<app>/files/ (music/, covers/, artists/)
│   └── ...
├── gradle/                      # Version Catalog (libs.versions.toml)
├── commit_message.txt           # Mensaje del último commit sincronizado
├── README.md                    # Este documento
├── ROADMAP.md                   # Plan de evolución del proyecto
├── STRUCTURE.md                 # Arquitectura y diseño técnico
├── AI_CONTEXT.md                # Contexto y directrices para agentes de IA
└── AGENTS.md                    # Reglas persistentes de desarrollo
```

---

## 🚀 Compilación y Generación del APK

### Generar APK de depuración (Debug APK)
```bash
gradle :app:assembleDebug
```
El archivo resultante se ubica en:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Instalación en el Teléfono (Sin PC)

1. Descarga el archivo `app-debug.apk` directamente en tu smartphone.
2. Abre el instalador de paquetes de tu dispositivo.
3. Si el sistema lo requiere, habilita "Instalar aplicaciones desconocidas" para tu navegador o gestor de archivos.
4. Abre **Ritmo**, selecciona tu motor preferido y disfruta de tu música con privacidad y alta fidelidad sonora.
