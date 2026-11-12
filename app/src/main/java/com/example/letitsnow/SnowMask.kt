package com.example.letitsnow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.MotionEvent
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Класс, отвечающий за маску снега и инея на стекле, механику очистки пальцем
 * и постепенное естественное восстановление (замерзание).
 */
class SnowMask {

    companion object {
        const val DEFAULT_RECOVERY_SPEED = 14f // Скорость замерзания (альфа в секунду)
        const val DEFAULT_RECOVERY_DELAY = 2.0f // Задержка в секундах перед началом замерзания
        private const val BASE_WIPE_RADIUS_DP = 26f // Радиус очистки (~52dp диаметр, чуть больше пальца)
    }

    var recoverySpeed: Float = DEFAULT_RECOVERY_SPEED
    var recoveryDelaySeconds: Float = DEFAULT_RECOVERY_DELAY
    var wipeRadius: Float = 0f
        private set

    private var width: Int = 0
    private var height: Int = 0
    private var density: Float = 1f

    private var maskBitmap: Bitmap? = null
    private var maskCanvas: Canvas? = null

    private var lastTouchX: Float = -1f
    private var lastTouchY: Float = -1f
    private var idleTime: Float = 0f
    private var recoveryAccumulator: Float = 0f

    // Кисть для прозрачной очистки следа под пальцем
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Кисть для снежного валика по краям очищенной зоны
    private val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Кисть для снежной крошки вокруг следа
    private val crumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD9FFFFFF.toInt()
        style = Paint.Style.FILL
    }

    // Кисть для постепенного повторного замерзания очищенных участков
    private val refrostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x01E4F1FA.toInt() // Мягкий морозный тон инея
        style = Paint.Style.FILL
    }

    // Кисть для отрисовки маски на экране с билинейной фильтрацией
    private val displayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    /**
     * Установка или обновление размеров маски при создании или смене ориентации экрана.
     */
    fun setSize(w: Int, h: Int, screenDensity: Float) {
        if (w <= 0 || h <= 0) return
        if (w == width && h == height && maskBitmap != null) return

        width = w
        height = h
        density = screenDensity
        wipeRadius = BASE_WIPE_RADIUS_DP * density

        val oldBitmap = maskBitmap
        val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val newCanvas = Canvas(newBitmap)

        if (oldBitmap != null && !oldBitmap.isRecycled) {
            // Масштабируем существующую маску, сохраняя уже нарисованные следы
            val srcRect = Rect(0, 0, oldBitmap.width, oldBitmap.height)
            val dstRect = Rect(0, 0, w, h)
            newCanvas.drawBitmap(oldBitmap, srcRect, dstRect, displayPaint)
            oldBitmap.recycle()
        } else {
            // Первичное покрытие стекла морозным инеем
            fillWithFrost(newCanvas, w, h)
        }

        maskBitmap = newBitmap
        maskCanvas = newCanvas
    }

    /**
     * Первичное заполнение поверхности стекла зимним морозным слоем и кристаллами.
     */
    private fun fillWithFrost(canvas: Canvas, w: Int, h: Int) {
        // Базовый полупрозрачный слой морозной дымки
        val frostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xBFE2EFF9.toInt() // ~75% непрозрачности морозного льдисто-белого стекла
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), frostPaint)

        // Деликатные кристаллики инея для реалистичной текстуры стекла
        val crystalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF.toInt()
            style = Paint.Style.FILL
        }
        val random = Random(12345)
        val crystalCount = (w * h / 2500).coerceIn(300, 1800)
        for (i in 0 until crystalCount) {
            val cx = random.nextFloat() * w
            val cy = random.nextFloat() * h
            val r = (1f + random.nextFloat() * 3.5f) * density
            crystalPaint.alpha = random.nextInt(25, 75)
            canvas.drawCircle(cx, cy, r, crystalPaint)
        }
    }

    /**
     * Обработка событий касания и жестов пальцем.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                wipeAt(lastTouchX, lastTouchY)
                idleTime = 0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val currentX = event.x
                val currentY = event.y
                if (lastTouchX >= 0f && lastTouchY >= 0f) {
                    // Плавная интерполяция между точками движения для исключения разрывов при быстром движении
                    val dist = hypot(currentX - lastTouchX, currentY - lastTouchY)
                    val step = (wipeRadius * 0.22f).coerceAtLeast(3f * density)
                    val steps = maxOf(1, (dist / step).toInt())

                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        val interpX = lastTouchX + t * (currentX - lastTouchX)
                        val interpY = lastTouchY + t * (currentY - lastTouchY)
                        wipeAt(interpX, interpY)
                    }
                } else {
                    wipeAt(currentX, currentY)
                }
                lastTouchX = currentX
                lastTouchY = currentY
                idleTime = 0f
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastTouchX = -1f
                lastTouchY = -1f
                idleTime = 0f
                return true
            }
        }
        return false
    }

    /**
     * Нанесение очищающего мазка в точке [x], [y] с формированием снежного валика и крошки.
     */
    private fun wipeAt(x: Float, y: Float) {
        val canvas = maskCanvas ?: return

        // 1. Естественная неровность границы (псевдослучайное отклонение радиуса)
        val jitter = (Random.nextFloat() - 0.5f) * 0.22f // -11% .. +11%
        val currentRadius = wipeRadius * (1f + jitter)

        // 2. Снежный валик по краям следа (где снег сгребается пальцем)
        ridgePaint.strokeWidth = 3f * density + Random.nextFloat() * 2f * density
        canvas.drawCircle(x, y, currentRadius + ridgePaint.strokeWidth * 0.5f, ridgePaint)

        // 3. Полупрозрачная снежная крошка вдоль границы
        val crumbCount = Random.nextInt(2, 5)
        for (i in 0 until crumbCount) {
            val angle = Random.nextFloat() * (2f * Math.PI.toFloat())
            val dist = currentRadius + (2f + Random.nextFloat() * 8f) * density
            val cx = x + cos(angle) * dist
            val cy = y + sin(angle) * dist
            val crumbRadius = (0.7f + Random.nextFloat() * 1.8f) * density
            crumbPaint.alpha = Random.nextInt(90, 200)
            canvas.drawCircle(cx, cy, crumbRadius, crumbPaint)
        }

        // 4. Очистка центральной области следа (прозрачное стекло под пальцем)
        canvas.drawCircle(x, y, currentRadius, clearPaint)
    }

    /**
     * Постепенное восстановление маски снега (повторное замерзание стекла).
     */
    fun update(dtSec: Float) {
        if (maskCanvas == null || width <= 0 || height <= 0) return

        idleTime += dtSec
        if (idleTime > recoveryDelaySeconds) {
            recoveryAccumulator += recoverySpeed * dtSec
            if (recoveryAccumulator >= 1f) {
                val alphaStep = recoveryAccumulator.toInt().coerceIn(1, 5)
                recoveryAccumulator -= alphaStep
                refrostPaint.alpha = alphaStep
                maskCanvas?.drawPaint(refrostPaint)
            }
        }
    }

    /**
     * Отрисовка маски на основном Canvas.
     */
    fun draw(canvas: Canvas) {
        maskBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, displayPaint)
            }
        }
    }
}
