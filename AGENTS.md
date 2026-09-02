# Directrices y Reglas para Agentes de IA — Ritmo

Este archivo es leído automáticamente por los agentes de IA en cada sesión para garantizar coherencia arquitectónica y respeto estricto a las preferencias del desarrollador.

---

## 📌 Reglas Fundamentales de Desarrollo

1. **Razonar antes de actuar:**
   - Antes de realizar cualquier cambio, analiza metódicamente las herramientas disponibles, los archivos existentes y el impacto en la compilación. No realices modificaciones impulsivas.

2. **Entorno del Usuario (Móvil, sin PC):**
   - El usuario programa y gestiona el proyecto desde un smartphone.
   - Asegura siempre que los controles táctiles respeten el tamaño mínimo de 48dp, con espaciado amplio y navegación fluida con una mano.
   - En caso de crear herramientas auxiliares, no usar configuraciones tipo `persist.sys.*`.

3. **Canal de Distribución Independiente:**
   - El objetivo de despliegue es **Uptodown** o distribución directa de APK.
   - No depender de servicios de Google Play. La app debe ser 100% funcional offline.

4. **Calidad de Dependencias sobre Tamaño de APK:**
   - No intentes reinventar librerías complejas (audio, persistencia, imágenes) para ahorrar unos megabytes. Al usuario no le importa el peso del APK con tal de que todo sea robusto, estándar y funcione al 100%.

5. **Inclusión Obligatoria de C++ y Rust en la Compilación:**
   - Si se añade código en C++, Rust o módulos nativos, estos **deben estar completamente integrados y enlazados en los scripts de compilación de Gradle (`build.gradle.kts`, CMakeLists.txt)**.
   - No sustituir funcionalidades nativas con apaños temporales o fallbacks en Kotlin cuando se haya establecido un motor nativo para esa tarea.

6. **Seguridad Legal de Nombres y Marcas:**
   - No utilizar en nombres de archivos, paquetes o identificadores marcas comerciales de terceros protegidas por derechos de autor que puedan poner en riesgo legal la distribución del APK del usuario.

7. **Idioma de Mensajes y Documentación:**
   - Todos los mensajes de commit, documentación y textos de usuario deben mantenerse en español con redacción profesional y concisa.
   - Si existe un archivo `commit_message.txt`, su información debe estar en español y solo actualizarse si el usuario lo pide expresamente.

---

## 🎼 Arquitectura de Audio Dual

- **Motor Estándar:** `AudioEngineType.EXOPLAYER`
  - Utiliza `androidx.media3:media3-exoplayer`.
- **Motor Nativo:** `AudioEngineType.OBOE_CPP`
  - Utiliza `com.google.oboe:oboe` y el puente nativo JNI en `app/src/main/cpp`.
- Al iniciar la app, el usuario puede seleccionar su motor preferido, o alternarlo dinámicamente mediante el selector de la cabecera.
- Cualquier adición de procesamiento de señales (DSP, ecualizadores) debe integrarse en el módulo nativo de C++ (`app/src/main/cpp`), y los módulos de seguridad o decodificación complementaria en el crate de Rust (`app/src/main/rust`).
