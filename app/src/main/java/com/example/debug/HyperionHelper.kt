package com.example.debug

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import timber.log.Timber

/**
 * Ayudante para interactuar con Hyperion-Android de forma segura y táctil.
 *
 * Facilita abrir el menú de plugins en dispositivos móviles sin necesidad de sacudir el teléfono
 * o depender de la barra de notificaciones, soportando tanto debug como release (no-op).
 */
object HyperionHelper {

    /**
     * Intenta abrir el cajón de plugins de Hyperion sobre la actividad actual.
     * @return true si se invocó exitosamente, false en caso contrario.
     */
    fun open(context: Context): Boolean {
        return try {
            val activity = findActivity(context) ?: return false
            if (activity.isFinishing || activity.isDestroyed) return false
            val hyperionClass = Class.forName("com.willowtreeapps.hyperion.core.Hyperion")
            val openMethod = hyperionClass.getMethod("open", Activity::class.java)
            openMethod.invoke(null, activity)
            true
        } catch (_: ClassNotFoundException) {
            Timber.d("Hyperion no presente en este entorno de ejecución")
            false
        } catch (e: Throwable) {
            Timber.w(e, "Error al abrir Hyperion programáticamente")
            false
        }
    }

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
