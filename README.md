# Ritmo — Reproductor de Música Local de Alta Fidelidad

**Ritmo** es un reproductor de audio local moderno, privado y de alto rendimiento para Android. Ha sido diseñado específicamente para usuarios que buscan el control total de su biblioteca musical, sin rastreo, sin telemetría y sin permisos invasivos de escaneo en segundo plano.

La aplicación está concebida para su distribución independiente en tiendas de aplicaciones y repositorios de APKs de terceros (como **Uptodown**), funcionando de manera 100% autónoma y offline sin requerir servicios privativos de Google Play.

---

## 🎯 Características Principales

1. **Privacidad y Control Total de Archivos:**
   - No escanea todo tu almacenamiento ni lee tus fotos o documentos.
   - Solo reproduce e indexa las canciones y carpetas que tú decides importar explícitamente mediante el selector seguro del sistema.
   - Base de datos local aislada gestionada con **Room Database**.

2. **Doble Motor de Audio Seleccionable:**
   - **ExoPlayer / Media3:** Motor de referencia del ecosistema Android, con soporte amplio para múltiples códecs y streaming local.
   - **Oboe C++ Native Audio Engine:** Motor de audio nativo de ultra baja latencia con acceso directo a las rutas de hardware (`AAudio` y `OpenSL ES`) para una reproducción inmediata y preparación para efectos de sonido DSP avanzados.

3. **Arquitectura Híbrida Preparada para el Futuro (Kotlin + C++ + Rust):**
   - **Kotlin & Jetpack Compose:** Interfaz de usuario reactiva, fluida y con diseño contemporáneo en modo oscuro.
   - **C++ (Google Oboe):** Pipeline de audio nativo de bajo nivel para garantizar latencia mínima y cero micro-cortes.
   - **Rust Core:** Módulo nativo configurado y enlazado en la compilación para albergar en el futuro decodificadores audiófilos, algoritmos de hashing y procesadores de metadatos seguros contra fallos de memoria.

4. **Experiencia de Usuario:**
   - **Mini Reproductor:** Barra flotante acoplada con carátula, título, artista, progreso en tiempo real y controles rápidos.
   - **Reproductor Completo:** Despliegue dinámico con carátula a gran escala, barra deslizadora con timestamps, salto de 10s, modo aleatorio (*shuffle*) y modos de repetición.
   - **Buscador en Tiempo Real:** Filtro instantáneo por nombre de canción o artista.

---

## 🛠️ Stack Tecnológico

| Capa | Tecnologías |
| :--- | :--- |
| **Interfaz de Usuario (UI)** | Kotlin 2.2+, Jetpack Compose, Material Design 3 |
| **Persistencia Local** | Room Database 2.7+ con KSP |
| **Imágenes y Carátulas** | Coil Compose (decodificación asíncrona de miniaturas y bitmaps) |
| **Motor de Audio Estándar** | AndroidX Media3 / ExoPlayer 1.5+ |
| **Motor de Audio Nativo** | C++20, Google Oboe (AAudio / OpenSL ES), CMake 3.22+, Android NDK r26b |
| **Módulo Nativo de Extensión** | Rust 2021 (C-ABI bridge, `staticlib`/`cdylib`) |
| **Compilación y Build System** | Gradle 9.3+ con Kotlin DSL (`build.gradle.kts`) |

---

## 📁 Estructura del Repositorio

```
├── app/
│   ├── src/main/
│   │   ├── cpp/                 # Código nativo en C++ (Oboe Audio Engine y JNI)
│   │   │   ├── CMakeLists.txt   # Configuración de compilación nativa
│   │   │   ├── native_audio.cpp # Motor de reproducción Oboe
│   │   │   └── native_bridge.cpp# Métodos JNI expuestos a Kotlin
│   │   ├── rust/                # Módulo nativo en Rust
│   │   │   ├── Cargo.toml       # Definición de la biblioteca Rust
│   │   │   └── src/lib.rs       # Código fuente y exportaciones C-ABI
│   │   ├── java/com/example/    # Código Kotlin
│   │   │   ├── data/            # Entidades Room y DAOs
│   │   │   ├── playback/        # AudioPlayerManager (ExoPlayer y Oboe bridge)
│   │   │   ├── ui/              # Pantallas Compose y ViewModel
│   │   │   │   └── components/  # MiniPlayer, FullPlayerView, TrackListItem
│   │   │   └── util/            # Extractor de carátulas y metadatos
│   │   └── res/                 # Iconos, temas, cadenas en strings.xml
├── gradle/                      # Version Catalog (libs.versions.toml)
├── metadata.json                # Metadatos para AI Studio
├── README.md                    # Este documento
├── ROADMAP.md                   # Plan de evolución del proyecto
├── STRUCTURE.md                 # Análisis detallado de la arquitectura
├── AI_CONTEXT.md                # Contexto y directrices para asistentes de IA
└── AGENTS.md                    # Reglas persistentes de desarrollo y flujos
```

---

## 🚀 Compilación y Generación del APK

El proyecto está configurado con Gradle y Kotlin DSL para compilar de forma integrada tanto el código Kotlin como las bibliotecas nativas de C++ y Rust.

### Generar APK de depuración (Debug APK)
```bash
gradle :app:assembleDebug
```
El archivo resultante se encontrará en:
`app/build/outputs/apk/debug/app-debug.apk`

### Generar APK de lanzamiento firmado (Release APK)
```bash
gradle :app:assembleRelease
```

---

## 📱 Instalación en el Teléfono (Sin PC)

Si descargas el APK directamente en tu teléfono móvil:
1. Descarga el archivo `app-debug.apk` o la versión de distribución desde tu gestor de descargas o navegador.
2. Abre el archivo APK en tu dispositivo.
3. Si Android te solicita permisos de "Instalar aplicaciones desconocidas", concédeselos a tu navegador o administrador de archivos.
4. Pulsa en **Instalar** y abre **Ritmo**.
5. Pulsa en **Importar** para seleccionar los archivos de audio que desees escuchar.
