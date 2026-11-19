package com.example.letitsnow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View

/**
 * Кастомный View с четким разделением двух слоев:
 * Слой 1 — плавно падающие снежинки (не зависят от касаний, не застывают в воздухе).
 * Слой 2 — физически накопившийся снег у нижнего края экрана (сугроб поверх функциональных кнопок).
 *
 * Использует единый синглтон [SnowSimulationEngine], разделяемый с [SnowWallpaperService]
 * и оверлеем [SnowOverlayService], что гарантирует непрерывность снегопада при переходе
 * между экраном блокировки, рабочим столом и приложениями.
 */
class SnowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // Глубокий темно-синий цвет ночного зимнего неба по умолчанию
        private const val DEFAULT_BACKGROUND_COLOR = 0xFF0B132B.toInt()

        const val FPS_UNLIMITED = 0
        const val FPS_60 = 60
        const val FPS_45 = 45
        const val FPS_40 = 40
        const val DEFAULT_MAX_FPS = 60
    }

    /**
     * Ограничение максимальной частоты кадров анимации (FPS_UNLIMITED, FPS_60, FPS_45, FPS_40).
     * По умолчанию 60 FPS для устранения избыточного расхода батареи на дисплеях с 90 Гц и 120 Гц.
     */
    var maxFps: Int = DEFAULT_MAX_FPS
        set(value) {
            field = value
            updateFrameInterval()
        }

    private var minFrameIntervalNanos: Long = 0L

    private fun updateFrameInterval() {
        minFrameIntervalNanos = if (maxFps > 0) {
            // Запас 1.5 мс для компенсации аппаратного джиттера тайминга VSYNC в Choreographer
            ((1_000_000_000L / maxFps) - 1_500_000L).coerceAtLeast(0L)
        } else {
            0L
        }
    }

    /**
     * Коэффициент скорости падения снежинок (0.1 .. 3.0).
     */
    var fallSpeedMultiplier: Float
        get() = SnowSimulationEngine.currentSettings.fallSpeed
        set(value) {
            SnowSimulationEngine.applySettings(SnowSimulationEngine.currentSettings.copy(fallSpeed = value))
        }

    /**
     * Коэффициент скорости накопления снега (0.1 .. 3.0).
     */
    var accumulationRateMultiplier: Float
        get() = SnowSimulationEngine.currentSettings.accumulationRate
        set(value) {
            SnowSimulationEngine.applySettings(SnowSimulationEngine.currentSettings.copy(accumulationRate = value))
        }

    /**
     * Масштаб размера снежинок (0.5 .. 2.5).
     */
    var snowflakeSizeMultiplier: Float
        get() = SnowSimulationEngine.currentSettings.snowflakeSize
        set(value) {
            SnowSimulationEngine.applySettings(SnowSimulationEngine.currentSettings.copy(snowflakeSize = value))
        }

    /**
     * Выбранный визуальный стиль падающих снежинок («Сказочный», «Хлопья»).
     */
    var snowflakeStyle: SnowflakeStyle
        get() = SnowSimulationEngine.currentSettings.snowflakeStyle
        set(value) {
            SnowSimulationEngine.applySettings(SnowSimulationEngine.currentSettings.copy(snowflakeStyle = value))
        }

    /**
     * Цветовая тема снегопада и сугробов («Белый», «Алый»).
     */
    var colorTheme: SnowColorTheme
        get() = SnowSimulationEngine.currentSettings.colorTheme
        set(value) {
            SnowSimulationEngine.applySettings(SnowSimulationEngine.currentSettings.copy(colorTheme = value))
        }

    /**
     * Включено ли мягкое волшебное сияние вокруг снежинок.
     */
    var isGlowEnabled: Boolean
        get() = SnowSimulationEngine.currentSettings.isGlowEnabled
        set(value) {
            SnowSimulationEngine.applySettings(SnowSimulationEngine.currentSettings.copy(isGlowEnabled = value))
        }

    /**
     * Выбранный визуальный стиль сугроба.
     */
    var snowbankStyle: SnowbankStyle
        get() = SnowSimulationEngine.currentSettings.snowbankStyle
        set(value) {
            SnowSimulationEngine.applySettings(SnowSimulationEngine.currentSettings.copy(snowbankStyle = value))
        }

    /**
     * Флаг использования улучшенной графики.
     */
    var useDetailedGraphics: Boolean
        get() = SnowSimulationEngine.currentSettings.useDetailedGraphics
        set(value) {
            SnowSimulationEngine.applySettings(SnowSimulationEngine.currentSettings.copy(useDetailedGraphics = value))
        }

    /**
     * Целевое количество одновременно отображаемых снежинок (10 .. 300).
     */
    var targetSnowflakeCount: Int
        get() = SnowSimulationEngine.currentSettings.intensity
        set(value) {
            val coerced = value.coerceIn(SnowSettings.MIN_INTENSITY, SnowSettings.MAX_INTENSITY)
            SnowSimulationEngine.applySettings(SnowSimulationEngine.currentSettings.copy(intensity = coerced))
        }

    /**
     * Режим отображения в качестве системного оверлея поверх всех приложений.
     */
    var isOverlayMode: Boolean = false
        set(value) {
            field = value
            if (value) {
                setBackgroundColor(Color.TRANSPARENT)
            } else {
                setBackgroundColor(DEFAULT_BACKGROUND_COLOR)
            }
        }

    val accumulation: SnowAccumulation
        get() = SnowSimulationEngine.accumulation

    val snowflakes: ArrayList<Snowflake>
        get() = SnowSimulationEngine.snowflakes

    private val density: Float = resources.displayMetrics.density

    private var isRunning = false
    private var isCallbackScheduled = false
    private var lastRenderedFrameTimeNanos: Long = 0L

    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            isCallbackScheduled = false
            if (!isRunning) return

            if (lastRenderedFrameTimeNanos > 0L) {
                val elapsedNanos = frameTimeNanos - lastRenderedFrameTimeNanos
                if (elapsedNanos <= 0L) {
                    lastRenderedFrameTimeNanos = frameTimeNanos
                    if (isRunning) {
                        postNextFrame()
                    }
                    return
                }

                if (minFrameIntervalNanos > 0L && elapsedNanos < minFrameIntervalNanos) {
                    // Пропускаем этот такт VSYNC, удерживая равномерный FPS и снижая нагрузку на CPU/GPU
                    if (isRunning) {
                        postNextFrame()
                    }
                    return
                }

                val w = width.toFloat()
                val h = height.toFloat()

                if (w > 0f && h > 0f) {
                    SnowSimulationEngine.updatePhysics(frameTimeNanos, isOverlayMode)
                    invalidate()
                }
            }
            lastRenderedFrameTimeNanos = frameTimeNanos

            if (isRunning) {
                postNextFrame()
            }
        }
    }

    init {
        updateFrameInterval()

        val dm = resources.displayMetrics
        if (dm.widthPixels > 0 && dm.heightPixels > 0) {
            SnowSimulationEngine.updateCanvasSize(dm.widthPixels.toFloat(), dm.heightPixels.toFloat(), density)
        }

        if (isOverlayMode) {
            setBackgroundColor(Color.TRANSPARENT)
        } else if (background == null) {
            setBackgroundColor(DEFAULT_BACKGROUND_COLOR)
        }
    }

    /**
     * Применяет настройки снегопада в реальном времени.
     */
    fun applySettings(settings: SnowSettings) {
        SnowSimulationEngine.applySettings(settings)
        invalidate()
    }

    /**
     * Явное обновление размеров холста под физический размер экрана (для оверлея).
     */
    fun updateCanvasSize(w: Float, h: Float) {
        if (w > 0f && h > 0f) {
            SnowSimulationEngine.updateCanvasSize(w, h, density)
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCanvasSize(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isOverlayMode && SnowSimulationEngine.customBackgroundBitmap != null) {
            SnowSimulationEngine.drawBackground(canvas, null)
        }
        SnowSimulationEngine.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isOverlayMode) {
            return false
        }

        val handled = SnowSimulationEngine.snowMask.onTouchEvent(event, SnowSimulationEngine.accumulation)
        if (handled) {
            invalidate()
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                performClick()
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun start() {
        if (!isRunning) {
            isRunning = true
            lastRenderedFrameTimeNanos = 0L
            postNextFrame()
        }
        invalidate()
    }

    fun pause() {
        isRunning = false
        choreographer.removeFrameCallback(frameCallback)
        isCallbackScheduled = false
        lastRenderedFrameTimeNanos = 0L
    }

    private fun postNextFrame() {
        if (!isCallbackScheduled && isRunning) {
            isCallbackScheduled = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isOverlayMode) {
            if (visibility == VISIBLE) {
                start()
            }
        } else if (windowVisibility == VISIBLE) {
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            if (!isOverlayMode || this.visibility == VISIBLE) {
                start()
            }
        } else {
            pause()
        }
    }
}
