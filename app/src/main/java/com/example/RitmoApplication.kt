package com.example

import android.app.Application
import com.example.debug.DebugLogLevel
import com.example.debug.DebugLogManager
import com.example.debug.RitmoCrashHandler
import com.example.debug.RitmoDebugTree
import timber.log.Timber

/**
 * Clase de aplicación principal de Ritmo.
 *
 * Inicializa la suite de depuración avanzada:
 * - Árbol de Timber sincronizado con [DebugLogManager]
 * - Atrapador global de excepciones no controladas [RitmoCrashHandler]
 * - Detección automática de fugas de memoria con LeakCanary en builds debug
 */
class RitmoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Instalar atrapador global de excepciones no controladas
        RitmoCrashHandler.install(this)

        // 2. Plantar árbol de Timber para redirigir logs al sistema de diagnóstico en memoria
        Timber.plant(RitmoDebugTree())

        // 3. Registrar arranque de la aplicación
        Timber.i("Ritmo Application iniciada con éxito. Diagnóstico en memoria y Timber activos.")

        // 4. Verificar si hubo un crash previo para alertar al desarrollador
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
