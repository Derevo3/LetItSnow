package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Редкое событие: снежинка, приземляющаяся прямо на стекло экрана,
 * тающая в прозрачную капельку воды и затем естественно стекающая вниз по стеклу,
 * оставляя тонкий влажный след.
 *
 * Появляется периодически (раз в 5 минут) при включённой настройке событий.
 */
class MeltingSnowflake {

    companion object {
        const val INITIAL_DELAY_SEC = 200f
        const val REPEAT_INTERVAL_SEC = 300f
    }

    var isActive: Boolean = false
        private set

    var progress: Float = 0f
        private set

    var dropX: Float = 0f
        private set

    var dropY: Float = 0f
        private set

    private var x: Float = 0f
    private var y: Float = 0f
    private var maxRadius: Float = 0f
    private var rotationDeg: Float = 0f
    private var durationSec: Float = 6.5f
    private var timerUntilNextSpawn: Float = INITIAL_DELAY_SEC

    private var wobblePhase: Float = 0f
    private var baseRunSpeed: Float = 0f
    private var velX: Float = 0f
    private var velY: Float = 1f
    private var stretchFactor: Float = 1f

    // Кольцевой буфер точек влажного следа (без аллокаций на каждый кадр)
    private val maxTrailPoints = 48
    private val trailX = FloatArray(maxTrailPoints)
    private val trailY = FloatArray(maxTrailPoints)
    private var trailCount: Int = 0
    private var lastTrailX: Float = 0f
    private var lastTrailY: Float = 0f

    private val crystalPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }

    private val dropPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    private val ringPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    private val trailPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    fun reset() {
        isActive = false
        progress = 0f
        dropX = 0f
        dropY = 0f
        trailCount = 0
        stretchFactor = 1f
        timerUntilNextSpawn = INITIAL_DELAY_SEC
    }

    fun update(
        dtSec: Float,
        width: Float,
        height: Float,
        density: Float,
        dirX: Float = 0f,
        dirY: Float = 1f
    ) {
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
            progress = 0f
            trailCount = 0
            // Следующее появление через 5 минут (300 секунд)
            timerUntilNextSpawn = REPEAT_INTERVAL_SEC
            return
        }

        // Фаза стекания капли после того, как кристалл растаял (progress >= 0.30)
        if (progress >= 0.30f) {
            val runT = ((progress - 0.30f) / 0.70f).coerceIn(0f, 1f)

            // Плавный срыв с места (преодоление поверхностного натяжения) и мягкое замедление при высыхании
            val startEase = (runT / 0.18f).coerceIn(0f, 1f)
            val endEase = if (runT > 0.78f) {
                1.0f - ((runT - 0.78f) / 0.22f) * 0.65f
            } else {
                1.0f
            }
            // Лёгкая микро-пульсация скорости, как на реальном стекле
            val stickSlip = 0.78f + 0.22f * sin(runT * 16f + wobblePhase)
            val speed = baseRunSpeed * startEase * startEase * endEase * stickSlip

            // Перпендикулярный вектор для естественного лёгкого петляния капли
            val perpX = -dirY
            val perpY = dirX
            val meander = sin(runT * 13f + wobblePhase) * (9.0f * density) * startEase

            val stepDx = (dirX * speed + perpX * meander) * dtSec
            val stepDy = (dirY * speed + perpY * meander) * dtSec

            dropX += stepDx
            dropY += stepDy

            if (hypot(stepDx, stepDy) > 0.001f) {
                velX = stepDx
                velY = stepDy
            }
            stretchFactor = 1.0f + (0.24f * startEase * endEase).coerceIn(0f, 0.24f)

            val distSinceLast = hypot(dropX - lastTrailX, dropY - lastTrailY)
            if (distSinceLast >= 3.5f * density) {
                appendTrailPoint(dropX, dropY)
            }
        }
    }

    private fun appendTrailPoint(px: Float, py: Float) {
        if (trailCount < maxTrailPoints) {
            trailX[trailCount] = px
            trailY[trailCount] = py
            trailCount++
        } else {
            for (i in 0 until maxTrailPoints - 1) {
                trailX[i] = trailX[i + 1]
                trailY[i] = trailY[i + 1]
            }
            trailX[maxTrailPoints - 1] = px
            trailY[maxTrailPoints - 1] = py
        }
        lastTrailX = px
        lastTrailY = py
    }

    private fun spawn(width: Float, height: Float, density: Float) {
        isActive = true
        progress = 0f
        durationSec = 6.0f + Random.nextFloat() * 1.8f

        // Выбираем место преимущественно в верхней-средней зоне, чтобы капле было куда стекать
        x = width * (0.16f + Random.nextFloat() * 0.68f)
        y = height * (0.12f + Random.nextFloat() * 0.42f)
        dropX = x
        dropY = y
        maxRadius = (5.5f + Random.nextFloat() * 3.0f) * density
        rotationDeg = Random.nextFloat() * 60f
        wobblePhase = Random.nextFloat() * (2f * PI.toFloat())
        baseRunSpeed = (44f + Random.nextFloat() * 20f) * density
        velX = 0f
        velY = 1f
        stretchFactor = 1f

        trailCount = 0
        appendTrailPoint(x, y)
    }

    fun draw(canvas: Canvas, density: Float, theme: SnowColorTheme) {
        if (!isActive) return

        // Фаза 1 (0.00 .. 0.14): Мягкое приземление кристалла на стекло
        // Фаза 2 (0.14 .. 0.38): Таяние кристалла и превращение в прозрачную капельку с бликом
        // Фаза 3 (0.30 .. 0.85): Капелька срывается и стекает вниз, оставляя влажный след
        // Фаза 4 (0.80 .. 1.00): Мягкое высыхание капельки и следа
        val crystalAlpha = when {
            progress < 0.14f -> (progress / 0.14f)
            progress < 0.38f -> 1.0f - ((progress - 0.14f) / 0.24f)
            else -> 0f
        }.coerceIn(0f, 1f)

        val dropAlpha = when {
            progress < 0.16f -> 0f
            progress < 0.34f -> (progress - 0.16f) / 0.18f
            progress < 0.80f -> 1.0f
            else -> 1.0f - ((progress - 0.80f) / 0.20f)
        }.coerceIn(0f, 1f)

        val (crystalColor, dropRimColor, highlightColor) = when (theme) {
            SnowColorTheme.WHITE -> Triple(0xFFE0F2FE.toInt(), 0xFFBAE6FD.toInt(), 0xFFFFFFFF.toInt())
            SnowColorTheme.SCARLET -> Triple(0xFFFFCBD1.toInt(), 0xFFFF8591.toInt(), 0xFFFFF1F2.toInt())
            SnowColorTheme.PINK -> Triple(0xFFFCE7F3.toInt(), 0xFFF9A8D4.toInt(), 0xFFFFF5FA.toInt())
            SnowColorTheme.BLACK -> Triple(0xFF94A3B8.toInt(), 0xFF64748B.toInt(), 0xFFE2E8F0.toInt())
        }

        // 1. Отрисовка тающего шестилучевого кристалла на месте касания
        if (crystalAlpha > 0.02f) {
            val shrink = 1.0f - (progress * 0.55f).coerceIn(0f, 0.38f)
            val r = maxRadius * shrink

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rotationDeg)

            crystalPaint.color = crystalColor
            crystalPaint.alpha = (crystalAlpha * 215f).toInt()
            crystalPaint.strokeWidth = (1.0f * density).coerceAtLeast(0.9f)

            val twig = r * 0.52f
            val twigDx = r * 0.18f
            val twigDy = r * 0.20f

            for (i in 0 until 6) {
                canvas.drawLine(0f, 0f, r, 0f, crystalPaint)
                canvas.drawLine(twig, 0f, twig - twigDx, twigDy, crystalPaint)
                canvas.drawLine(twig, 0f, twig - twigDx, -twigDy, crystalPaint)
                canvas.rotate(60f)
            }
            canvas.restore()
        }

        // 2. Отрисовка тонкого влажного следа за стекающей каплей
        if (dropAlpha > 0.02f && trailCount >= 2) {
            trailPaint.color = dropRimColor
            val maxTrailWidth = (maxRadius * 0.36f).coerceAtLeast(1.2f * density)

            for (i in 1 until trailCount) {
                val frac = i.toFloat() / (trailCount - 1).coerceAtLeast(1)
                val segAlpha = (dropAlpha * (14f + 46f * frac * frac)).toInt()
                if (segAlpha > 1) {
                    trailPaint.alpha = segAlpha
                    trailPaint.strokeWidth = (maxTrailWidth * (0.28f + 0.72f * frac)).coerceAtLeast(0.6f * density)
                    canvas.drawLine(trailX[i - 1], trailY[i - 1], trailX[i], trailY[i], trailPaint)
                }
            }
            // Соединяем последнюю точку следа с центром текущей капли
            trailPaint.alpha = (dropAlpha * 60f).toInt()
            trailPaint.strokeWidth = maxTrailWidth
            canvas.drawLine(trailX[trailCount - 1], trailY[trailCount - 1], dropX, dropY, trailPaint)
        }

        // 3. Отрисовка стекающей полупрозрачной капельки воды
        if (dropAlpha > 0.02f) {
            val dropScale = if (progress > 0.76f) {
                1.0f - ((progress - 0.76f) / 0.24f) * 0.42f
            } else {
                0.76f + 0.24f * sin(((progress - 0.16f).coerceAtLeast(0f) / 0.60f).coerceIn(0f, 1f) * 1.57f)
            }
            val dropR = maxRadius * 0.76f * dropScale

            val rimR = Color.red(dropRimColor)
            val rimG = Color.green(dropRimColor)
            val rimB = Color.blue(dropRimColor)

            val moveAngleDeg = (atan2(velY, velX) * 180f / PI.toFloat())
            val squeeze = 1.0f / kotlin.math.sqrt(stretchFactor)

            canvas.save()
            canvas.translate(dropX, dropY)
            canvas.rotate(moveAngleDeg)
            canvas.scale(stretchFactor, squeeze)

            // Тело капли: полупрозрачный центр + мягкий водяной ободок
            dropPaint.shader = RadialGradient(
                0f,
                0f,
                dropR,
                intArrayOf(
                    Color.argb((dropAlpha * 28f).toInt(), rimR, rimG, rimB),
                    Color.argb((dropAlpha * 68f).toInt(), rimR, rimG, rimB),
                    Color.argb((dropAlpha * 118f).toInt(), rimR, rimG, rimB),
                    Color.argb(0, rimR, rimG, rimB)
                ),
                floatArrayOf(0f, 0.58f, 0.88f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(0f, 0f, dropR, dropPaint)
            dropPaint.shader = null

            // Тонкий мениск границы капли
            ringPaint.color = dropRimColor
            ringPaint.alpha = (dropAlpha * 98f).toInt()
            ringPaint.strokeWidth = (0.85f * density).coerceAtLeast(0.75f)
            canvas.drawCircle(0f, 0f, dropR * 0.88f, ringPaint)

            canvas.restore()

            // Крошечный блик света на верхне-левой стороне капельки (сохраняет направление света)
            dropPaint.color = highlightColor
            dropPaint.alpha = (dropAlpha * 200f).toInt()
            canvas.drawCircle(
                dropX - dropR * 0.28f,
                dropY - dropR * 0.28f,
                (dropR * 0.22f).coerceAtLeast(1.0f * density),
                dropPaint
            )
        }
    }
}
