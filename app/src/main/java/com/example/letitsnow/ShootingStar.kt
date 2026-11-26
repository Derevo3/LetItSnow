package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Редкое событие: падающая золотистая рождественская звёздочка
 * с мягким светящимся градиентным хвостом и ореолом сияния.
 *
 * Появляется периодически (раз в 5 минут) и плавно пролетает
 * через экран по диагонали, добавляя ощущение чуда и загадывания желаний.
 */
class ShootingStar {

    companion object {
        const val INITIAL_DELAY_SEC = 100f
        const val REPEAT_INTERVAL_SEC = 300f
    }

    var isActive: Boolean = false
        private set

    private var x: Float = 0f
    private var y: Float = 0f
    private var vx: Float = 0f
    private var vy: Float = 0f

    private var tailLength: Float = 0f
    private var progress: Float = 0f
    private var durationSec: Float = 1.6f
    private var timerUntilNextSpawn: Float = INITIAL_DELAY_SEC

    private val tailPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
        }
    }

    private val starPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    fun reset() {
        isActive = false
        progress = 0f
        timerUntilNextSpawn = INITIAL_DELAY_SEC
    }

    fun update(dtSec: Float, width: Float, height: Float, density: Float) {
        if (width <= 0f || height <= 0f) return

        if (!isActive) {
            timerUntilNextSpawn -= dtSec
            if (timerUntilNextSpawn <= 0f) {
                spawn(width, height, density)
            }
            return
        }

        progress += dtSec / durationSec
        if (progress >= 1.0f) {
            isActive = false
            // Следующее появление через 5 минут (300 секунд)
            timerUntilNextSpawn = REPEAT_INTERVAL_SEC
            return
        }

        x += vx * dtSec
        y += vy * dtSec
    }

    private fun spawn(width: Float, height: Float, density: Float) {
        isActive = true
        progress = 0f
        durationSec = 1.4f + Random.nextFloat() * 0.8f // 1.4 .. 2.2 секунды

        // Скорость полета по диагонали (слева направо вниз)
        val speed = (280f + Random.nextFloat() * 140f) * density
        val angleDeg = 30f + Random.nextFloat() * 25f // 30 .. 55 градусов
        val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

        vx = cos(angleRad) * speed
        vy = sin(angleRad) * speed
        tailLength = (60f + Random.nextFloat() * 50f) * density

        // Появление в верхней трети экрана
        x = -20f * density + Random.nextFloat() * (width * 0.7f)
        y = -10f * density + Random.nextFloat() * (height * 0.35f)
    }

    fun draw(canvas: Canvas, density: Float, theme: SnowColorTheme) {
        if (!isActive) return

        // Кривая прозрачности: быстрый взлёт яркости и плавное угасание
        val alphaNorm = when {
            progress < 0.15f -> progress / 0.15f
            progress > 0.70f -> (1.0f - progress) / 0.30f
            else -> 1.0f
        }.coerceIn(0f, 1f)

        val alphaInt = (alphaNorm * 255f).toInt()
        if (alphaInt <= 2) return

        // Направление хвоста (противоположно вектору скорости)
        val vLen = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(1f)
        val dirX = vx / vLen
        val dirY = vy / vLen

        val tailEndX = x - dirX * tailLength
        val tailEndY = y - dirY * tailLength

        val (coreColor, glowColor) = when (theme) {
            SnowColorTheme.SCARLET -> Pair(0xFFFFF1F2.toInt(), 0xFFEF4444.toInt())
            SnowColorTheme.PINK -> Pair(0xFFFFF0F5.toInt(), 0xFFF472B6.toInt())
            SnowColorTheme.BLACK -> Pair(0xFFFFFFFF.toInt(), 0xFF94A3B8.toInt())
            SnowColorTheme.WHITE -> Pair(0xFFFFFFFF.toInt(), 0xFFFDE047.toInt()) // Золотисто-желтый
        }

        // 1. Отрисовка градиентного светящегося хвоста
        tailPaint.strokeWidth = 2.6f * density
        tailPaint.shader = LinearGradient(
            x, y, tailEndX, tailEndY,
            intArrayOf(
                Color.argb(alphaInt, Color.red(coreColor), Color.green(coreColor), Color.blue(coreColor)),
                Color.argb((alphaInt * 0.5f).toInt(), Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.argb(0, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(x, y, tailEndX, tailEndY, tailPaint)

        // 2. Внешний ореол сияния головы звезды
        starPaint.shader = null
        starPaint.color = glowColor
        starPaint.alpha = (alphaInt * 0.45f).toInt()
        val glowR = (5.5f + sin(progress * 18f) * 1.5f) * density
        canvas.drawCircle(x, y, glowR, starPaint)

        // 3. Яркое ядро звезды
        starPaint.color = coreColor
        starPaint.alpha = alphaInt
        canvas.drawCircle(x, y, 2.2f * density, starPaint)
    }
}
