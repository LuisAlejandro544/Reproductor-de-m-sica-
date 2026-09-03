package com.example

import android.app.Application
import com.example.debug.DebugLogLevel
import com.example.debug.DebugLogManager
import com.example.debug.RitmoCrashHandler
import com.example.debug.RitmoDebugTree
import com.github.anrwatchdog.ANRWatchDog
import com.pluto.Pluto
import com.pluto.plugins.rooms.db.PlutoRoomsDatabasePlugin
import timber.log.Timber

/**
 * Clase de aplicación principal de Ritmo.
 *
 * Inicializa la suite de depuración avanzada:
 * - Árbol de Timber sincronizado con [DebugLogManager]
 * - Atrapador global de excepciones no controladas [RitmoCrashHandler]
 * - Detección automática de congelamientos de UI con ANR-WatchDog
 * - Inspección táctil en pantalla de Base de Datos Room y diagnóstico con Pluto
 * - Detección automática de fugas de memoria con LeakCanary en builds debug
 */
class RitmoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Instalar atrapador global de excepciones no controladas
        RitmoCrashHandler.install(this)

        // 2. Plantar árbol de Timber para redirigir logs al sistema de diagnóstico en memoria
        Timber.plant(RitmoDebugTree())

        // 3. Inicializar ANR-WatchDog para detectar bloqueos de hilo principal en smartphone
        try {
            ANRWatchDog(3500)
                .setANRListener { anrError ->
                    Timber.e(anrError, "ANR detectado en hilo UI")
                    DebugLogManager.log(
                        tag = "ANR-WatchDog",
                        message = "¡Bloqueo de UI detectado (>3.5s)!: ${anrError.message}",
                        level = DebugLogLevel.CRITICAL,
                        rawErrorCode = -8888,
                        details = anrError.stackTraceToString()
                    )
                }
                .start()
            Timber.i("ANR-WatchDog iniciado correctamente (límite 3500ms).")
        } catch (e: Exception) {
            Timber.e(e, "Error al iniciar ANR-WatchDog")
        }

        // 4. Inicializar Pluto On-Device Debugger con plugin de inspección de Base de Datos Room
        try {
            Pluto.Installer(this)
                .addPlugin(PlutoRoomsDatabasePlugin("rooms-db"))
                .install()
            Timber.i("Pluto On-Device Debugger instalado con RoomsDatabasePlugin.")
        } catch (e: Exception) {
            Timber.e(e, "Error al iniciar Pluto Debugger")
        }

        // 5. Registrar arranque de la aplicación
        Timber.i("Ritmo Application iniciada con éxito. Diagnóstico en memoria, Pluto, ANR-WatchDog y Timber activos.")

        // 6. Verificar si hubo un crash previo para alertar al desarrollador
        RitmoCrashHandler.getLastCrashReport(this)?.let { crashReport ->
            DebugLogManager.log(
                tag = "RitmoCrashHandler",
                message = "Se detectó un reporte de crash previo guardado en almacenamiento.",
                level = DebugLogLevel.WARN,
                rawErrorCode = -9999,
                details = crashReport
            )
        }
    }
}
