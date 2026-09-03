package com.example.debug

import timber.log.Timber

/**
 * Configurador dinámico y seguro de LeakCanary.
 *
 * Aplica reglas avanzadas de exclusión para fugas conocidas del framework del sistema operativo
 * (como Toast$TN, ToastPresenter y WindowManagerImpl anclados en variables nativas en Android 11-14
 * y capas de personalización de fabricantes como TECNO HiOS).
 *
 * Utiliza introspección segura para no requerir dependencias estáticas en builds release.
 */
object LeakCanaryConfigurator {

    fun configure() {
        try {
            val leakCanaryClass = Class.forName("leakcanary.LeakCanary")
            val sharkMatchersClass = Class.forName("shark.AndroidReferenceMatchers")
            val companionField = sharkMatchersClass.getField("Companion")
            val companion = companionField.get(null)
            val getAppDefaultsMethod = companion.javaClass.getMethod("getAppDefaults")

            @Suppress("UNCHECKED_CAST")
            val matchers = (getAppDefaultsMethod.invoke(companion) as List<Any>).toMutableList()

            val ignoredFieldMethod = companion.javaClass.getMethod(
                "ignoredInstanceField",
                String::class.java,
                String::class.java
            )

            // Fugas conocidas del framework del sistema operativo (Android 11 a 14 / ROMs de fabricantes)
            // donde variables globales en código nativo (IPC) retienen Toast$TN y ToastPresenter
            matchers.add(ignoredFieldMethod.invoke(companion, "android.widget.Toast\$TN", "mPresenter"))
            matchers.add(ignoredFieldMethod.invoke(companion, "android.widget.ToastPresenter", "mWindowManager"))
            matchers.add(ignoredFieldMethod.invoke(companion, "android.view.WindowManagerImpl", "mContext"))

            val getConfigMethod = leakCanaryClass.getMethod("getConfig")
            val config = getConfigMethod.invoke(null) ?: return

            val newBuilderMethod = config.javaClass.getMethod("newBuilder")
            val builder = newBuilderMethod.invoke(config)

            val referenceMatchersMethod = builder.javaClass.getMethod("referenceMatchers", List::class.java)
            referenceMatchersMethod.invoke(builder, matchers)

            val buildMethod = builder.javaClass.getMethod("build")
            val newConfig = buildMethod.invoke(builder)

            val setConfigMethod = leakCanaryClass.getMethod("setConfig", config.javaClass)
            setConfigMethod.invoke(null, newConfig)

            Timber.i("LeakCanary configurado con exclusión de fugas del framework del OS para Toast.")
        } catch (_: ClassNotFoundException) {
            // En builds release o sin LeakCanary, no se realiza ninguna acción
        } catch (e: Throwable) {
            Timber.w(e, "No se pudo aplicar configuración personalizada de LeakCanary")
        }
    }
}
