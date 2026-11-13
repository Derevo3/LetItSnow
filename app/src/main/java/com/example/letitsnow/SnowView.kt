package com.example.letitsnow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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

    /**
     * Интерактивный режим (активируется при разблокировке для очистки сугроба пальцем).
     */
    var isInteractive: Boolean = false

    /**
     * Обратный вызов при очистке сугроба (для возврата в сквозной режим FLAG_NOT_TOUCHABLE).
     */
    var onSnowClearedListener: (() -> Unit)? = null

    val accumulation: SnowAccumulation
        get() = snowAccumulation

    private val density: Float = resources.displayMetrics.density
    private val snowflakes = ArrayList<Snowflake>(SnowSettings.MAX_INTENSITY)
    private val snowAccumulation = SnowAccumulation()
    private val snowMask = SnowMask()

    // Переиспользуемый Paint для отрисовки падающих снежинок (без аллокаций в onDraw)
    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Элементы кнопки быстрого смахивания в интерактивном режиме
    private val dismissButtonRect = RectF()
    private val dismissBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC0F172A.toInt() // Полупрозрачный темно-синий
        style = Paint.Style.FILL
    }
    private val dismissBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8860A5FA.toInt() // Ледяная кайма
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val dismissTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
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

                    // В активном оверлее снег не накапливается (только падает), пока телефон не заблокирован
                    val effectiveAccumulation = if (isOverlayMode && !isInteractive) null else snowAccumulation

                    // Обновляем координаты падающих снежинок (Слой 1)
                    for (i in 0 until snowflakes.size) {
                        snowflakes[i].update(
                            dtSec = dt,
                            width = w,
                            height = h,
                            density = density,
                            accumulation = effectiveAccumulation,
                            fallSpeedMultiplier = fallSpeedMultiplier,
                            accumulationMultiplier = accumulationRateMultiplier
                        )
                    }

                    // Обновляем физику 2D сетки снега только если снег накапливается или интерактивен
                    if (effectiveAccumulation != null) {
                        snowAccumulation.update(dt)
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
        val dm = resources.displayMetrics
        if (dm.widthPixels > 0 && dm.heightPixels > 0) {
            snowMask.setSize(dm.widthPixels.toFloat(), dm.heightPixels.toFloat(), density)
            snowAccumulation.setSize(dm.widthPixels.toFloat(), dm.heightPixels.toFloat(), density)
            initSnowflakes(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())

            val btnW = 160f * density
            val btnH = 38f * density
            val left = (dm.widthPixels - btnW) / 2f
            val top = 48f * density
            dismissButtonRect.set(left, top, left + btnW, top + btnH)
        }

        // Устанавливаем чистый темный фон по умолчанию (без замороженного слоя поверх)
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
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            snowMask.setSize(w.toFloat(), h.toFloat(), density)
            snowAccumulation.setSize(w.toFloat(), h.toFloat(), density)
            initSnowflakes(w.toFloat(), h.toFloat())

            val btnW = 160f * density
            val btnH = 38f * density
            val left = (w - btnW) / 2f
            val top = 48f * density
            dismissButtonRect.set(left, top, left + btnW, top + btnH)
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

        // Слой 1: Падающие снежинки с визуальным разнообразием (точки, светящиеся хлопья, кристаллы)
        for (i in 0 until snowflakes.size) {
            snowflakes[i].draw(canvas, flakePaint, density)
        }

        // Слой 2: Накопившийся снег (сглаженная 2D сетка плотности с тенями и валиками)
        snowAccumulation.draw(canvas)

        // Кнопка быстрого смахивания в интерактивном режиме оверлея
        if (isInteractive && isOverlayMode) {
            val r = 19f * density
            canvas.drawRoundRect(dismissButtonRect, r, r, dismissBtnBgPaint)
            canvas.drawRoundRect(dismissButtonRect, r, r, dismissBtnBorderPaint)
            val textY = dismissButtonRect.centerY() - ((dismissTextPaint.descent() + dismissTextPaint.ascent()) / 2f)
            canvas.drawText("Смахнуть снег ✕", dismissButtonRect.centerX(), textY, dismissTextPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isOverlayMode && !isInteractive) {
            return false
        }

        // Обработка клика по кнопке «Смахнуть снег»
        if (isInteractive && isOverlayMode && dismissButtonRect.contains(event.x, event.y)) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                snowAccumulation.clearAllSnow()
                invalidate()
                onSnowClearedListener?.invoke()
                performClick()
            }
            return true
        }

        // Палец убирает только накопившийся снег (сугроб)
        val handled = snowMask.onTouchEvent(event, snowAccumulation)
        if (handled) {
            invalidate()
            if (isOverlayMode && isInteractive) {
                // Если пользователь стер большую часть накопившегося сугроба (от 70%)
                if (snowAccumulation.getClearedRatio() >= 0.70f) {
                    snowAccumulation.clearAllSnow()
                    onSnowClearedListener?.invoke()
                }
            }
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
        invalidate()
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
        if (isOverlayMode) return // Жизненным циклом оверлея управляет SnowOverlayService
        if (visibility == VISIBLE) {
            start()
        } else {
            pause()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isOverlayMode) return // Жизненным циклом оверлея управляет SnowOverlayService
        if (visibility == VISIBLE && windowVisibility == VISIBLE) {
            start()
        } else {
            pause()
        }
    }
}
