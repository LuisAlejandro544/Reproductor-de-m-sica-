# Contexto de IA para el Proyecto — Ritmo

Este archivo proporciona todo el contexto técnico, restricciones de diseño, perfil del usuario y reglas del ciclo de desarrollo para que cualquier asistente de Inteligencia Artificial entienda de inmediato el proyecto y actúe con máxima precisión.

---

## 🧭 Visión del Proyecto
- **Nombre:** Ritmo
- **Naturaleza:** Reproductor de audio local para Android con enfoque estricto en privacidad, alto rendimiento y fidelidad sonora.
- **Filosofía:** El usuario tiene soberanía total sobre sus archivos. La app **no** escanea de fondo ni solicita permisos invasivos; solo indexa y almacena localmente los archivos que el usuario importa selectivamente mediante el selector seguro de Android.
- **Doble Motor:** Soporta conmutación en caliente entre el motor estándar de Android (**ExoPlayer / Media3**) y un motor nativo de ultra baja latencia (**Oboe C++ / AAudio**), con arquitectura preparada para **Rust**.
- **Ecualizador de 10 Bandas en C++ (Doble Motor):** Cuenta con un ecualizador de 10 bandas con filtros Biquad IIR a nivel de muestra PCM en C++. Está disponible de manera unificada tanto en el motor Oboe (procesamiento directo en callback de audio) como en Media3/ExoPlayer (mediante `Media3EqualizerAudioProcessor`), garantizando exactamente la misma respuesta acústica y preajustes sonoros independientemente del motor seleccionado.
- **Reproducción en Segundo Plano:** Implementada con `RitmoMediaSessionService` para permitir control continuo desde la barra de notificaciones, pantalla de bloqueo y accesorios Bluetooth.
- **Animaciones Fluidas:** Transiciones y animaciones cuidadas con Jetpack Compose (`AnimatedContent`, curvas `FastOutSlowInEasing`, escalado elástico `spring` y barras visualizadoras animadas en tiempo real).

---

## 👤 Perfil del Usuario y Restricciones Operativas

1. **Dispositivo del Usuario:**
   - El usuario **no tiene PC**, únicamente utiliza su smartphone para programar, gestionar e interactuar con la app y el entorno.
   - Toda la interfaz, navegación y controles deben ser ergonómicos, cómodos y 100% accesibles para una sola mano en pantallas táctiles móviles (tamaño táctil mínimo de 48dp).

2. **Canal de Distribución:**
   - La aplicación no se subirá a Google Play Store; se distribuirá directamente como archivo APK en tiendas de terceros como **Uptodown** o descarga directa.
   - No depender de servicios privativos de Google Play (Play Licensing, In-App Billing, etc.). La app debe ser 100% autónoma y offline.

3. **Política de Peso vs. Dependencias:**
   - Al usuario **no le preocupa el tamaño final del archivo APK**, siempre y cuando las dependencias sean 100% funcionales y aporten valor real.
   - Evitar soluciones "inventadas desde cero sin dependencias" cuando existan librerías estándar y probadas en la industria (usar dependencias robustas y consolidadas).

4. **Reglas sobre Lenguajes Compilados (C++, Rust, Kotlin):**
   - Si se integra C++, Rust o módulos nativos, **deben estar incluidos y configurados obligatoriamente en el pipeline de Gradle (`build.gradle.kts`, CMakeLists.txt)**. No deben saltarse ni omitirse.
   - No usar funciones de reemplazo provisional (*fallback*) de Kotlin si se solicitó o diseñó la funcionalidad con un framework nativo específico.

5. **Protección de Propiedad Intelectual:**
   - Evitar en todo momento nombrar archivos o identificadores con marcas registradas protegidas por derechos de autor que puedan poner al usuario en riesgo.

6. **Información y Mensajes de Commit:**
   - La información de `commit_message.txt` siempre debe redactarse en español y solo actualizarse cuando el usuario lo pida expresamente.

---

## 🔄 Mapa del Ciclo de Desarrollo

Cada interacción del asistente con el código debe encajar en una de estas 7 fases:

1. **01 El Arquitecto (Planificación y Diseño):**
   - Evalúa el stack técnico, modelo de datos y decisiones de diseño antes de escribir código nuevo.
2. **02 El Constructor (Generación de Código):**
   - Código limpio, modular, tipado, con manejo exhaustivo de errores y listo para producción.
3. **03 El Detective (Debugging):**
   - Diagnóstico metódico de errores con hipótesis, análisis paso a paso y causa raíz.
4. **04 El Crítico (Code Review):**
   - Revisión rigurosa de seguridad, rendimiento, patrones y mantenibilidad.
5. **05 El Optimizador (Refactoring):**
   - Mejora de rendimiento, legibilidad y reducción de consumo de CPU/batería sin alterar el comportamiento observable.
6. **06 El Escudo (Testing):**
   - Pruebas unitarias, escenarios límite (*edge cases*) y tests locales con Robolectric.
7. **07 El Narrador (Documentación):**
   - Mantenimiento de documentación técnica clara, precisa y directamente accionable.
