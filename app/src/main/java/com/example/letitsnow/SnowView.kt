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
 * Слой 2 — физически накопившийся снег (двумерная сетка плотности), постепенно заполняющий
 * пространство снизу вверх и очищаемый пальцем с мягкими краями и валиками.
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
     * Целевое количество одновременно отображаемых снежинок (10 .. 300).
     */
    var targetSnowflakeCount: Int = SnowSettings.DEFAULT_INTENSITY
        set(value) {
            field = value.coerceIn(SnowSettings.MIN_INTENSITY, SnowSettings.MAX_INTENSITY)
            if (width > 0 && height > 0) {
                // При уменьшении интенсивности сразу удаляем лишние частицы из конца списка
                if (snowflakes.size > field) {
                    while (snowflakes.size > field) {
                        snowflakes.removeAt(snowflakes.size - 1)
                    }
                }
            }
        }

    private val density: Float = resources.displayMetrics.density
    private val snowflakes = ArrayList<Snowflake>(SnowSettings.MAX_INTENSITY)
    private val snowAccumulation = SnowAccumulation()
    private val snowMask = SnowMask()

    // Переиспользуемый Paint для отрисовки падающих снежинок (без аллокаций в onDraw)
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
                // Вычисляем дельту времени между кадрами (ограничиваем 0.1с для предотвращения скачков)
                val dt = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                val w = width.toFloat()
                val h = height.toFloat()

                if (w > 0f && h > 0f) {
                    // При увеличении интенсивности плавно добавляем новые частицы сверху (по 2 за кадр)
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

                    // Обновляем координаты падающих снежинок (Слой 1)
                    for (i in 0 until snowflakes.size) {
                        snowflakes[i].update(
                            dtSec = dt,
                            width = w,
                            height = h,
                            density = density,
                            accumulation = snowAccumulation,
                            fallSpeedMultiplier = fallSpeedMultiplier,
                            accumulationMultiplier = accumulationRateMultiplier
                        )
                    }

                    // Обновляем физику 2D сетки снега (гравитационное оседание и оползание)
                    snowAccumulation.update(dt)

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
        // Устанавливаем чистый темный фон по умолчанию (без замороженного слоя поверх)
        if (background == null) {
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
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            snowMask.setSize(w.toFloat(), h.toFloat(), density)
            snowAccumulation.setSize(w.toFloat(), h.toFloat(), density)
            initSnowflakes(w.toFloat(), h.toFloat())
        }
    }

    private fun initSnowflakes(width: Float, height: Float) {
        snowflakes.clear()
        repeat(targetSnowflakeCount) {
            val flake = Snowflake()
            // isInitial = true: все снежинки начинают сверху экрана (экран полностью чист)
            flake.reset(width, height, density, isInitial = true)
            snowflakes.add(flake)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Слой 1: Падающие снежинки (плавно движутся, независимы от касаний)
        for (i in 0 until snowflakes.size) {
            val flake = snowflakes[i]
            flakePaint.alpha = flake.alpha
            canvas.drawCircle(flake.x, flake.y, flake.radius, flakePaint)
        }

        // Слой 2: Накопившийся снег (2D физическая сетка плотности с естественными холмами)
        snowAccumulation.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Палец убирает только накопившийся снег (сугроб), не рисуя круги в воздухе
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

    /**
     * Запуск или возобновление анимации снегопада.
     */
    fun start() {
        if (!isRunning) {
            isRunning = true
            lastFrameTimeNanos = 0L
            postNextFrame()
        }
    }

    /**
     * Остановка анимации для экономии ресурсов батареи и процессора.
     */
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
        if (windowVisibility == VISIBLE && isShown) {
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
            start()
        } else {
            pause()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && windowVisibility == VISIBLE) {
            start()
        } else {
            pause()
        }
    }
}
