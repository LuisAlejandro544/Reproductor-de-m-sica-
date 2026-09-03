package com.example.debug

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class FpsStatus {
    EXCELLENT,  // >= 55 FPS (o cerca del refresco nativo)
    GOOD,       // 45 - 54 FPS
    MODERATE,   // 30 - 44 FPS
    JANK        // < 30 FPS o congelamientos severos
}

data class FpsMetrics(
    val fps: Double = 60.0,
    val frameTimeMs: Double = 16.6,
    val droppedFrames: Long = 0L,
    val totalFramesSampled: Long = 0L,
    val refreshRate: Float = 60f,
    val status: FpsStatus = FpsStatus.EXCELLENT,
    val isMonitoring: Boolean = false,
    val isOverlayVisible: Boolean = false
)

/**
 * Monitor de FPS y rendimiento de renderizado en tiempo real (estilo Takt / TinyDancer).
 *
 * Utiliza [Choreographer] del sistema Android para medir los intervalos exactos entre cuadros,
 * computar la tasa de cuadros por segundo (FPS), detectar caídas de frames (jank) y
 * alimentar el indicador flotante en pantalla para depuración en smartphone.
 */
object FpsMonitor : Choreographer.FrameCallback {

    private val isRunning = AtomicBoolean(false)
    private var lastFrameTimeNanos: Long = 0L
    private var frameCountInWindow: Int = 0
    private var windowStartTimeNanos: Long = 0L
    private var totalDroppedFrames: Long = 0L
    private var totalSampledFrames: Long = 0L
    private var targetRefreshRate: Float = 60f
    private var targetFrameIntervalNanos: Long = 16_666_666L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _metricsFlow = MutableStateFlow(FpsMetrics())
    val metricsFlow: StateFlow<FpsMetrics> = _metricsFlow.asStateFlow()

    fun initialize(context: Context) {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val display = windowManager?.defaultDisplay
            if (display != null) {
                targetRefreshRate = display.refreshRate.coerceIn(30f, 240f)
                targetFrameIntervalNanos = (1_000_000_000L / targetRefreshRate).toLong()
            }
        } catch (_: Exception) {
            targetRefreshRate = 60f
            targetFrameIntervalNanos = 16_666_666L
        }

        _metricsFlow.value = _metricsFlow.value.copy(
            refreshRate = targetRefreshRate
        )

        // Iniciar monitoreo por defecto en segundo plano
        start()
    }

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            mainHandler.post {
                lastFrameTimeNanos = 0L
                frameCountInWindow = 0
                windowStartTimeNanos = System.nanoTime()
                Choreographer.getInstance().postFrameCallback(this)
                _metricsFlow.value = _metricsFlow.value.copy(isMonitoring = true)
                DebugLogManager.log(
                    tag = "FpsMonitor",
                    message = "Monitor de FPS iniciado (Tasa objetivo: ${targetRefreshRate.toInt()}Hz)",
                    level = DebugLogLevel.INFO
                )
            }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            mainHandler.post {
                Choreographer.getInstance().removeFrameCallback(this)
                _metricsFlow.value = _metricsFlow.value.copy(isMonitoring = false)
                DebugLogManager.log(
                    tag = "FpsMonitor",
                    message = "Monitor de FPS detenido.",
                    level = DebugLogLevel.INFO
                )
            }
        }
    }

    fun toggleMonitoring() {
        if (isRunning.get()) stop() else start()
    }

    fun toggleOverlay() {
        val current = _metricsFlow.value.isOverlayVisible
        val nextState = !current
        _metricsFlow.value = _metricsFlow.value.copy(isOverlayVisible = nextState)
        if (nextState && !isRunning.get()) {
            start()
        }
    }

    fun setOverlayVisible(visible: Boolean) {
        _metricsFlow.value = _metricsFlow.value.copy(isOverlayVisible = visible)
        if (visible && !isRunning.get()) {
            start()
        }
    }

    fun resetStatistics() {
        totalDroppedFrames = 0L
        totalSampledFrames = 0L
        frameCountInWindow = 0
        windowStartTimeNanos = System.nanoTime()
        _metricsFlow.value = _metricsFlow.value.copy(
            droppedFrames = 0L,
            totalFramesSampled = 0L
        )
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning.get()) return

        if (lastFrameTimeNanos != 0L) {
            val frameDurationNanos = frameTimeNanos - lastFrameTimeNanos
            totalSampledFrames++

            // Calcular frames perdidos (jank)
            val dropped = ((frameDurationNanos - targetFrameIntervalNanos) / targetFrameIntervalNanos).coerceAtLeast(0L)
            if (dropped > 0) {
                totalDroppedFrames += dropped

                // Si se pierden más de 5 frames seguidos (>83ms de congelamiento), registrar evento de diagnóstico
                if (dropped >= 5) {
                    val durationMs = frameDurationNanos / 1_000_000.0
                    DebugLogManager.log(
                        tag = "FpsMonitor",
                        message = "Alerta de Frame Drop: $dropped cuadros perdidos (${String.format(java.util.Locale.US, "%.1f", durationMs)} ms de retardo)",
                        level = DebugLogLevel.WARN,
                        rawErrorCode = -7770 - dropped.toInt().coerceAtMost(20),
                        details = "Frame duration: $durationMs ms, Target interval: ${targetFrameIntervalNanos / 1_000_000.0} ms"
                    )
                }
            }
        }
        lastFrameTimeNanos = frameTimeNanos
        frameCountInWindow++

        // Actualizar estadísticas cada 500ms
        val elapsedWindowNanos = frameTimeNanos - windowStartTimeNanos
        if (elapsedWindowNanos >= 500_000_000L) { // 500 ms
            val elapsedSeconds = elapsedWindowNanos / 1_000_000_000.0
            val computedFps = (frameCountInWindow / elapsedSeconds).coerceIn(0.0, targetRefreshRate.toDouble() + 5.0)
            val avgFrameTimeMs = if (computedFps > 0.0) 1000.0 / computedFps else 0.0

            val status = when {
                computedFps >= (targetRefreshRate * 0.90) -> FpsStatus.EXCELLENT
                computedFps >= (targetRefreshRate * 0.70) -> FpsStatus.GOOD
                computedFps >= 30.0 -> FpsStatus.MODERATE
                else -> FpsStatus.JANK
            }

            _metricsFlow.value = _metricsFlow.value.copy(
                fps = computedFps,
                frameTimeMs = avgFrameTimeMs,
                droppedFrames = totalDroppedFrames,
                totalFramesSampled = totalSampledFrames,
                status = status
            )

            // Reiniciar ventana
            frameCountInWindow = 0
            windowStartTimeNanos = frameTimeNanos
        }

        // Continuar siguiente frame callback si sigue corriendo
        if (isRunning.get()) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}
