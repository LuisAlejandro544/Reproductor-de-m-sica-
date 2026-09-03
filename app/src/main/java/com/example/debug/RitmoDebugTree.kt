package com.example.debug

import android.util.Log
import timber.log.Timber

/**
 * Árbol de Timber para Ritmo Music Player.
 *
 * Conecta automáticamente las llamadas de registro de Timber con el búfer
 * de diagnóstico en memoria de [DebugLogManager]. De este modo, cualquier componente
 * que use Timber registrará sus eventos directamente en la consola táctil de depuración
 * del smartphone sin requerir ADB.
 */
class RitmoDebugTree : Timber.DebugTree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)

        val cleanTag = tag ?: "Ritmo"
        val level = when (priority) {
            Log.VERBOSE -> DebugLogLevel.VERBOSE
            Log.DEBUG -> DebugLogLevel.DEBUG
            Log.INFO -> DebugLogLevel.INFO
            Log.WARN -> DebugLogLevel.WARN
            Log.ERROR -> DebugLogLevel.ERROR
            Log.ASSERT -> DebugLogLevel.CRITICAL
            else -> DebugLogLevel.DEBUG
        }

        if (t != null) {
            DebugLogManager.logError(
                tag = cleanTag,
                message = message,
                throwable = t,
                rawErrorCode = priority
            )
        } else {
            DebugLogManager.log(
                tag = cleanTag,
                message = message,
                level = level
            )
        }
    }
}
