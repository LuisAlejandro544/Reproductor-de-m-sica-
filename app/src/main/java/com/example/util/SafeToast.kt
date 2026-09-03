package com.example.util

import android.content.Context
import android.widget.Toast

/**
 * Utilidades seguras para la visualización de mensajes Toast en Android.
 *
 * PREVENCIÓN DE FUGAS DE MEMORIA:
 * En Android 11 a 14 (API 30-34), 'Toast.makeText(activityContext, ...)' provoca que
 * 'Toast$TN' y 'ToastPresenter.mWindowManager' retengan una referencia directa al
 * 'ContextImpl' de la Activity a través de variables globales en código nativo (IPC/Binder).
 * Si la Activity se destruye, dicha referencia produce una fuga de memoria detectada por LeakCanary.
 *
 * Al utilizar obligatoriamente 'context.applicationContext', el Toast queda anclado al ciclo de vida
 * del proceso global (Application), imposibilitando cualquier fuga de memoria de Activities.
 */
fun Context.showSafeToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    try {
        Toast.makeText(this.applicationContext, message, duration).show()
    } catch (_: Throwable) {
        // Protección ante hilos de fondo sin Looper o estados anómalos
    }
}
