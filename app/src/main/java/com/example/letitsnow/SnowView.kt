package com.example.letitsnow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View

/**
 * Кастомный View с четким разделением двух слоев:
 * Слой 1 — плавно падающие снежинки (не зависят от касаний, не застывают в воздухе).
 * Слой 2 — физически накопившийся снег у нижнего края экрана (сугроб поверх функциональных кнопок).
 *
 * В режиме оверлея поверх приложений (isOverlayMode = true) отображается падающий снег
 * и сугроб внизу экрана поверх функциональных кнопок со сквозным прохождением касаний.
 */
class SnowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // Глубокий темно-синий цвет ночного зимнего неба по умолчанию
        private const val DEFAULT_BACKGROUND_COLOR = 0xFF0B132B.toInt()
    }

    /**
     * Коэффициент скорости падения снежинок (0.1 .. 3.0).
     */
    var fallSpeedMultiplier: Float = SnowSettings.DEFAULT_FALL_SPEED

    /**
     * Коэффициент скорости накопления снега (0.1 .. 3.0).
     */
    var accumulationRateMultiplier: Float = SnowSettings.DEFAULT_ACCUMULATION_RATE

    /**
     * Масштаб размера снежинок (0.5 .. 2.5).
     */
    var snowflakeSizeMultiplier: Float = SnowSettings.DEFAULT_SNOWFLAKE_SIZE

    /**
     * Выбранный визуальный стиль падающих снежинок («Сказочный», «Хлопья»).
     */
    var snowflakeStyle: SnowflakeStyle = SnowflakeStyle.FAIRYTALE

    /**
     * Цветовая тема снегопада и сугробов («Белый», «Алый»).
     */
    var colorTheme: SnowColorTheme = SnowColorTheme.WHITE

    /**
     * Включено ли мягкое волшебное сияние вокруг снежинок.
     */
    var isGlowEnabled: Boolean = false

    /**
     * Выбранный визуальный стиль сугроба.
     */
    var snowbankStyle: SnowbankStyle = SnowbankStyle.SMOOTH_HILLS

    /**
     * Флаг использования улучшенной графики.
     */
    var useDetailedGraphics: Boolean = true

    /**
     * Целевое количество одновременно отображаемых снежинок (10 .. 300).
     */
    var targetSnowflakeCount: Int = SnowSettings.DEFAULT_INTENSITY
        set(value) {
            field = value.coerceIn(SnowSettings.MIN_INTENSITY, SnowSettings.MAX_INTENSITY)
            if (width > 0 && height > 0) {
                if (snowflakes.size > field) {
                    while (snowflakes.size > field) {
                        snowflakes.removeAt(snowflakes.size - 1)
                    }
                }
            }
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
        get() = snowAccumulation

    private val density: Float = resources.displayMetrics.density
    private val snowflakes = ArrayList<Snowflake>(SnowSettings.MAX_INTENSITY)
    private val snowAccumulation = SnowAccumulation()
    private val snowMask = SnowMask()

    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var isRunning = false
    private var isCallbackScheduled = false
    private var lastFrameTimeNanos: Long = 0L

    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            isCallbackScheduled = false
            if (!isRunning) return

            if (lastFrameTimeNanos > 0L) {
                val dt = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                val w = width.toFloat()
                val h = height.toFloat()

                if (w > 0f && h > 0f) {
                    if (snowflakes.size < targetSnowflakeCount) {
                        val toAdd = minOf(2, targetSnowflakeCount - snowflakes.size)
                        repeat(toAdd) {
                            val flake = Snowflake()
                            flake.reset(w, h, density, isInitial = false)
                            snowflakes.add(flake)
                        }
                    } else if (snowflakes.size > targetSnowflakeCount) {
                        while (snowflakes.size > targetSnowflakeCount) {
                            snowflakes.removeAt(snowflakes.size - 1)
                        }
                    }

                    snowAccumulation.allowGrowthAboveBase = !isOverlayMode

                    for (i in 0 until snowflakes.size) {
                        snowflakes[i].update(
                            dtSec = dt,
                            width = w,
                            height = h,
                            density = density,
                            accumulation = snowAccumulation,
                            fallSpeedMultiplier = fallSpeedMultiplier,
                            accumulationMultiplier = accumulationRateMultiplier,
                            sizeMultiplier = snowflakeSizeMultiplier
                        )
                    }

                    snowAccumulation.update(dt, accumulationRateMultiplier)

                    invalidate()
                }
            }
            lastFrameTimeNanos = frameTimeNanos

            if (isRunning) {
                postNextFrame()
            }
        }
    }

    init {
        val dm = resources.displayMetrics
        if (dm.widthPixels > 0 && dm.heightPixels > 0) {
            snowMask.setSize(dm.widthPixels.toFloat(), dm.heightPixels.toFloat(), density)
            snowAccumulation.setSize(dm.widthPixels.toFloat(), dm.heightPixels.toFloat(), density)
            initSnowflakes(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
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
        fallSpeedMultiplier = settings.fallSpeed
        targetSnowflakeCount = settings.intensity
        accumulationRateMultiplier = settings.accumulationRate
        snowAccumulation.setBaseSnowbankHeight(settings.snowbankHeight)
        snowflakeSizeMultiplier = settings.snowflakeSize
        snowflakeStyle = settings.snowflakeStyle
        colorTheme = settings.colorTheme
        isGlowEnabled = settings.isGlowEnabled
        snowbankStyle = settings.snowbankStyle
        useDetailedGraphics = settings.useDetailedGraphics
        invalidate()
    }

    /**
     * Явное обновление размеров холста под физический размер экрана (для оверлея).
     */
    fun updateCanvasSize(w: Float, h: Float) {
        if (w > 0f && h > 0f) {
            snowMask.setSize(w, h, density)
            snowAccumulation.setSize(w, h, density)
            initSnowflakes(w, h)
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCanvasSize(w.toFloat(), h.toFloat())
    }

    private fun initSnowflakes(width: Float, height: Float) {
        snowflakes.clear()
        repeat(targetSnowflakeCount) {
            val flake = Snowflake()
            flake.reset(width, height, density, isInitial = true)
            snowflakes.add(flake)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val activeFlakeStyle = snowflakeStyle
        val activeColorTheme = colorTheme
        val activeGlow = isGlowEnabled
        val activeBankStyle = snowbankStyle
        val isBloody = activeColorTheme == SnowColorTheme.SCARLET

        for (i in 0 until snowflakes.size) {
            snowflakes[i].draw(
                canvas = canvas,
                paint = flakePaint,
                density = density,
                style = activeFlakeStyle,
                colorTheme = activeColorTheme,
                isGlowEnabled = activeGlow,
                sizeMultiplier = snowflakeSizeMultiplier
            )
        }

        snowAccumulation.draw(canvas, activeBankStyle, isBloody)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isOverlayMode) {
            return false
        }

        val handled = snowMask.onTouchEvent(event, snowAccumulation)
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
            lastFrameTimeNanos = 0L
            postNextFrame()
        }
        invalidate()
    }

    fun pause() {
        if (isRunning) {
            isRunning = false
            choreographer.removeFrameCallback(frameCallback)
            isCallbackScheduled = false
        }
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
            start()
        } else if (windowVisibility == VISIBLE && isShown) {
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isOverlayMode) {
            pause()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isOverlayMode) return
        if (visibility == VISIBLE) {
            start()
        } else {
            pause()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isOverlayMode) return
        if (visibility == VISIBLE && windowVisibility == VISIBLE) {
            start()
        } else {
            pause()
        }
    }
}
