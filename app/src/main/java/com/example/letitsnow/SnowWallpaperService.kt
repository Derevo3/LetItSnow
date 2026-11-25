package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Системная служба живых обоев для экрана блокировки и рабочего стола (Android Live Wallpaper).
 *
 * Использует единый синглтон [SnowSimulationEngine], благодаря чему:
 * 1. Анимация снегопада бесшовно продолжается между экраном блокировки и оверлеем без сброса.
 * 2. Отрисовывается красивый зимний ночной градиент фона и снег с сугробом.
 * 3. Когда экран блокировки открыт или виден рабочий стол, живые обои отображают снегопад.
 * 4. При разблокировке устройства оверлейная служба [SnowOverlayService] подхватывает
 *    ту же симуляцию поверх приложений без скачков снежинок.
 */
class SnowWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return SnowWallpaperEngine()
    }

    inner class SnowWallpaperEngine : Engine(), Choreographer.FrameCallback {

        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var settingsJob: Job? = null
        private var isVisibleState = false
        private var isFrameScheduled = false
        private var lastRenderedFrameTimeNanos: Long = 0L

        // Запас 1.5 мс для компенсации аппаратного джиттера тайминга VSYNC на 60 FPS
        private val minFrameIntervalNanos: Long = ((1_000_000_000L / SnowView.DEFAULT_MAX_FPS) - 1_500_000L).coerceAtLeast(0L)

        private val choreographer: Choreographer by lazy { Choreographer.getInstance() }
        private val settingsRepository by lazy { SnowSettingsRepository(applicationContext) }

        private val bgPaint = Paint()
        private var surfaceWidth = 0f
        private var surfaceHeight = 0f

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Подписываемся на изменения настроек снегопада
            settingsJob = serviceScope.launch {
                settingsRepository.settingsFlow.collectLatest { settings ->
                    SnowSimulationEngine.applySettings(settings)
                    updateBackgroundGradient()
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible
            if (visible) {
                scheduleNextFrame()
            } else {
                choreographer.removeFrameCallback(this)
                isFrameScheduled = false
                lastRenderedFrameTimeNanos = 0L
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width.toFloat()
            surfaceHeight = height.toFloat()

            val density = resources.displayMetrics.density
            SnowSimulationEngine.updateCanvasSize(surfaceWidth, surfaceHeight, density, resources)
            updateBackgroundGradient()

            if (isVisibleState) {
                scheduleNextFrame()
            }
        }

        private fun updateBackgroundGradient() {
            if (surfaceWidth <= 0f || surfaceHeight <= 0f) return

            val theme = SnowSimulationEngine.currentSettings.colorTheme
            val colors = when (theme) {
                SnowColorTheme.SCARLET -> intArrayOf(0xFF1A0505.toInt(), 0xFF2A0808.toInt(), 0xFF3D0C0C.toInt())
                SnowColorTheme.PINK -> intArrayOf(0xFF200614.toInt(), 0xFF330A21.toInt(), 0xFF4A0E30.toInt())
                SnowColorTheme.BLACK -> intArrayOf(0xFF030712.toInt(), 0xFF090D16.toInt(), 0xFF111827.toInt())
                SnowColorTheme.WHITE -> intArrayOf(0xFF070B19.toInt(), 0xFF0B132B.toInt(), 0xFF1C2541.toInt())
            }

            val gradient = LinearGradient(
                0f, 0f, 0f, surfaceHeight,
                colors,
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            bgPaint.shader = gradient
        }

        override fun doFrame(frameTimeNanos: Long) {
            isFrameScheduled = false
            if (!isVisibleState) return

            if (lastRenderedFrameTimeNanos > 0L) {
                val elapsedNanos = frameTimeNanos - lastRenderedFrameTimeNanos
                if (elapsedNanos <= 0L) {
                    lastRenderedFrameTimeNanos = frameTimeNanos
                    if (isVisibleState) {
                        scheduleNextFrame()
                    }
                    return
                }

                // Ограничение частоты кадров для энергоэффективности на 120 Гц дисплеях
                if (minFrameIntervalNanos > 0L && elapsedNanos < minFrameIntervalNanos) {
                    scheduleNextFrame()
                    return
                }
            }
            lastRenderedFrameTimeNanos = frameTimeNanos

            // Обновляем общую физику симуляции
            SnowSimulationEngine.updatePhysics(frameTimeNanos, isOverlay = false)

            drawFrame()

            scheduleNextFrame()
        }

        private fun scheduleNextFrame() {
            if (!isFrameScheduled && isVisibleState) {
                isFrameScheduled = true
                choreographer.postFrameCallback(this)
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }

                if (canvas != null) {
                    // Отрисовываем фон (пользовательское фото или зимний градиент)
                    SnowSimulationEngine.drawBackground(canvas, bgPaint)

                    // Отрисовываем общий снегопад, сугроб и морозную виньетку
                    SnowSimulationEngine.draw(canvas, resources)
                }
            } catch (_: Exception) {
                // Защита от исключений при смене жизненного цикла поверхности
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) { }
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            // Позволяет взаимодействовать с сугробом на экране блокировки/рабочем столе при поддержке лаунчером
            val handled = SnowSimulationEngine.snowMask.onTouchEvent(event, SnowSimulationEngine.accumulation)
            if (handled && isVisibleState) {
                drawFrame()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            isVisibleState = false
            choreographer.removeFrameCallback(this)
            isFrameScheduled = false
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            isVisibleState = false
            choreographer.removeFrameCallback(this)
            isFrameScheduled = false
            settingsJob?.cancel()
            serviceScope.cancel()
            super.onDestroy()
        }
    }
}
