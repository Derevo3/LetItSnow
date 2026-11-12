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
 * Кастомный View для отрисовки и анимации снегопада, накопления сугробов
 * и интерактивной очистки снега пальцем с использованием Canvas и Choreographer.
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

    // Переиспользуемый Paint без аллокаций в onDraw
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

                    // Обновляем физику сугробов
                    snowAccumulation.update(dt)

                    // Обновляем маску очистки (постепенное повторное замерзание)
                    snowMask.update(dt)

                    // Обновляем координаты снежинок и регистрируем падение на сугробы
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
        // Устанавливаем контрастный темный фон, если фон не был задан снаружи
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
            snowMask.setSize(w, h, density)
            snowAccumulation.setSize(w.toFloat(), h.toFloat(), density)
            initSnowflakes(w.toFloat(), h.toFloat())
        }
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

        // 1. Отрисовка всех падающих снежинок
        for (i in 0 until snowflakes.size) {
            val flake = snowflakes[i]
            paint.alpha = flake.alpha
            canvas.drawCircle(flake.x, flake.y, flake.radius, paint)
        }

        // 2. Отрисовка накопившихся сугробов поверх снежинок в нижней части экрана
        snowAccumulation.draw(canvas)

        // 3. Отрисовка слоя инея и очищенных пальцем областей
        snowMask.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = snowMask.onTouchEvent(event)
        if (handled) {
            // Если касание проходит по нижней области, счищаем также и накопившийся сугроб
            val snowHeight = snowAccumulation.getSnowHeight(event.x)
            if (event.y + snowMask.wipeRadius >= height - snowHeight) {
                snowAccumulation.clearSnow(event.x, snowMask.wipeRadius)
            }
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
