package com.example.debug

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Atrapador Global de Excepciones No Controladas para Ritmo Music Player.
 *
 * Permite registrar cualquier crash imprevisto en el buffer de [DebugLogManager]
 * y persistirlo en [SharedPreferences] para que el desarrollador pueda inspeccionar
 * el código de error crudo y el StackTrace completo directamente desde el smartphone sin PC ni ADB.
 */
class RitmoCrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "RitmoCrashHandler"
        private const val PREFS_CRASH = "ritmo_crash_reports"
        private const val KEY_LAST_CRASH_TIME = "last_crash_time"
        private const val KEY_LAST_CRASH_TRACE = "last_crash_trace"
        private const val KEY_LAST_CRASH_MESSAGE = "last_crash_message"
        private const val KEY_LAST_CRASH_THREAD = "last_crash_thread"

        @Volatile
        private var installed = false

        fun install(context: Context) {
            if (installed) return
            synchronized(this) {
                if (installed) return
                val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
                val handler = RitmoCrashHandler(context.applicationContext, currentHandler)
                Thread.setDefaultUncaughtExceptionHandler(handler)
                installed = true
                Log.i(TAG, "Atrapador global de excepciones no controladas instalado.")
            }
        }

        fun getLastCrashReport(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
            val trace = prefs.getString(KEY_LAST_CRASH_TRACE, null) ?: return null
            val time = prefs.getString(KEY_LAST_CRASH_TIME, "Desconocido")
            val msg = prefs.getString(KEY_LAST_CRASH_MESSAGE, "Sin mensaje")
            val thread = prefs.getString(KEY_LAST_CRASH_THREAD, "main")

            return buildString {
                appendLine("💥 ÚLTIMO CRASH DETECTADO ($time)")
                appendLine("Hilo: $thread")
                appendLine("Mensaje: $msg")
                appendLine("StackTrace:")
                appendLine(trace)
            }
        }

        fun clearLastCrashReport(context: Context) {
            context.getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTraceStr = sw.toString()
        val errorMsg = throwable.message ?: throwable.javaClass.simpleName

        // 1. Guardar en memoria en DebugLogManager
        DebugLogManager.log(
            tag = TAG,
            message = "CRASH NO CONTROLADO en hilo '${thread.name}': $errorMsg",
            level = DebugLogLevel.CRITICAL,
            rawErrorCode = -9999,
            details = stackTraceStr
        )

        // 2. Persistir en SharedPreferences para lectura tras reinicio
        try {
            val prefs = context.getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_LAST_CRASH_TIME, timeStr)
                .putString(KEY_LAST_CRASH_THREAD, thread.name)
                .putString(KEY_LAST_CRASH_MESSAGE, errorMsg)
                .putString(KEY_LAST_CRASH_TRACE, stackTraceStr)
                .commit()
        } catch (t: Throwable) {
            Log.e(TAG, "Error persistiendo reporte de crash", t)
        }

        // 3. Delegar al manejador por defecto del sistema
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
