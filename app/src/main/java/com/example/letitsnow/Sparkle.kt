package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Модель отдельной блёстки (мерцающей сказочной искры).
 *
 * Блёстки медленно и плавно парят в воздухе среди снегопада или вспыхивают
 * на гребне сугроба, создавая ощущение мерцающего сказочного сияния.
 *
 * Форма: четырехконечная звезда (астроида) с острыми вытянутыми лучами,
 * вторичными диагональными лучами и сверхъярким ядром в центре.
 */
class Sparkle {
    var x: Float = 0f
    var y: Float = 0f
    var baseX: Float = 0f
    var baseY: Float = 0f
    var speedY: Float = 0f

    var swayRadius: Float = 0f
    var swaySpeed: Float = 0f
    var swayAngle: Float = 0f

    var maxRadius: Float = 0f
    var currentRadius: Float = 0f
    var alpha: Int = 0
    var maxAlpha: Int = 255

    var rotation: Float = 0f
    var rotationSpeed: Float = 0f

    var life: Float = 0f
    var maxLife: Float = 2.0f
    var isDead: Boolean = true

    // Флаг: искрится ли эта блёстка на поверхности сугроба
    var isOnSnowbank: Boolean = false

    // Предрассчитанный путь для формы четырехконечной звезды
    private val starPath = Path()

    /**
     * Сброс и инициализация блёстки в случайной точке по всему экрану.
     */
    fun reset(
        width: Float,
        height: Float,
        density: Float,
        accumulation: SnowAccumulation? = null,
        dirY: Float = 1f
    ) {
        if (width <= 0f || height <= 0f) return

        isDead = false
        life = 0f
        maxLife = 1.2f + Random.nextFloat() * 1.8f // 1.2 .. 3.0 сек

        maxRadius = (2.5f + Random.nextFloat() * 4.0f) * density
        maxAlpha = 190 + Random.nextInt(65)

        rotation = Random.nextFloat() * 360f
        rotationSpeed = (Random.nextFloat() - 0.5f) * 50f

        swayRadius = (2.0f + Random.nextFloat() * 5.0f) * density
        swaySpeed = 1.0f + Random.nextFloat() * 1.8f
        swayAngle = Random.nextFloat() * 6.28f

        // ~15% блёсток могут искриться на гребне сугроба (только при стандартном направлении гравитации),
        // остальные распределяются по всему экрану
        if (accumulation != null && accumulation.isSnowbankEnabled && dirY > 0.3f && Random.nextFloat() < 0.15f) {
            isOnSnowbank = true
            x = Random.nextFloat() * width
            baseX = x
            val surfaceY = accumulation.getSurfaceY(x)
            baseY = surfaceY - (Random.nextFloat() * 4f * density)
            y = baseY
            speedY = 0f
            maxLife = 0.8f + Random.nextFloat() * 1.0f // на снегу мерцают чуть быстрее
        } else {
            isOnSnowbank = false
            baseX = Random.nextFloat() * width
            baseY = Random.nextFloat() * height
            x = baseX
            y = baseY
            speedY = (4f + Random.nextFloat() * 12f) * density // медленное невесомое парение
        }
    }

    /**
     * Обновление состояния блёстки за шаг времени [dtSec] с учётом направления гравитации ([dirX], [dirY]).
     */
    fun update(
        dtSec: Float,
        width: Float,
        height: Float,
        density: Float,
        accumulation: SnowAccumulation? = null,
        dirX: Float = 0f,
        dirY: Float = 1f
    ) {
        if (isDead) return

        life += dtSec
        if (life >= maxLife) {
            isDead = true
            return
        }

        val progress = (life / maxLife).coerceIn(0f, 1f)
        // Плавная кривая мерцания: появление -> яркая вспышка -> плавное угасание
        val intensity = sin(progress * PI.toFloat()).coerceIn(0f, 1f)

        alpha = (maxAlpha * intensity).toInt().coerceIn(0, 255)
        currentRadius = maxRadius * (0.35f + 0.65f * intensity)
        rotation += rotationSpeed * dtSec

        if (!isOnSnowbank) {
            // Движение строго вдоль вектора направления гравитации (dirX, dirY)
            baseX += dirX * speedY * dtSec
            baseY += dirY * speedY * dtSec

            // Естественное покачивание перпендикулярно направлению падения (-dirY, dirX)
            swayAngle += swaySpeed * dtSec
            val sway = sin(swayAngle) * swayRadius

            x = baseX - dirY * sway
            y = baseY + dirX * sway

            val margin = 35f * density
            if (x < -margin || x > width + margin || y < -margin || y > height + margin) {
                isDead = true
            }
        } else if (accumulation != null && accumulation.isSnowbankEnabled && dirY > 0.3f) {
            // Привязана к динамическому сугробу
            val surfaceY = accumulation.getSurfaceY(x)
            y = surfaceY - 2f * density
        } else {
            isDead = true
        }
    }

    /**
     * Отрисовка блёстки на холсте с учетом размера [sizeMultiplier].
     */
    fun draw(
        canvas: Canvas,
        paint: Paint,
        colorTheme: SnowColorTheme,
        sizeMultiplier: Float = 1.0f
    ) {
        if (isDead || alpha <= 5) return

        val r = currentRadius * sizeMultiplier
        if (r <= 0.5f) return

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)

        // 1. Построение формы звезды-астроиды (4 вогнутых заостренных луча)
        starPath.reset()
        starPath.moveTo(0f, -r)
        starPath.quadTo(0f, 0f, r, 0f)
        starPath.quadTo(0f, 0f, 0f, r)
        starPath.quadTo(0f, 0f, -r, 0f)
        starPath.quadTo(0f, 0f, 0f, -r)
        starPath.close()

        val (starColor, coreColor, glowColor) = when (colorTheme) {
            SnowColorTheme.WHITE -> Triple(0xFFFFFFFF.toInt(), 0xFFFEF9C3.toInt(), 0x55E0F2FE.toInt())
            SnowColorTheme.SCARLET -> Triple(0xFFFF2E3B.toInt(), 0xFFFFF1F2.toInt(), 0x55DC2626.toInt())
            SnowColorTheme.PINK -> Triple(0xFFF472B6.toInt(), 0xFFFFF0F7.toInt(), 0x55FBCFE8.toInt())
            SnowColorTheme.BLACK -> Triple(0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(), 0x55475569.toInt())
        }

        // Мягкий внешний ореол сияния
        paint.style = Paint.Style.FILL
        paint.color = glowColor
        val glowAlpha = (alpha * 0.45f).toInt().coerceIn(0, 255)
        paint.alpha = glowAlpha
        canvas.drawCircle(0f, 0f, r * 0.85f, paint)

        // Основные 4 луча звезды
        paint.color = starColor
        paint.alpha = alpha
        canvas.drawPath(starPath, paint)

        // Диагональные мини-лучики (добавляют кристаллического перелива)
        val diagR = r * 0.40f
        starPath.reset()
        starPath.moveTo(0f, -diagR)
        starPath.quadTo(0f, 0f, diagR, 0f)
        starPath.quadTo(0f, 0f, 0f, diagR)
        starPath.quadTo(0f, 0f, -diagR, 0f)
        starPath.quadTo(0f, 0f, 0f, -diagR)
        starPath.close()

        canvas.save()
        canvas.rotate(45f)
        paint.color = starColor
        paint.alpha = (alpha * 0.70f).toInt()
        canvas.drawPath(starPath, paint)
        canvas.restore()

        // Сверхъяркое концентрированное ядро в центре
        paint.color = coreColor
        paint.alpha = alpha
        canvas.drawCircle(0f, 0f, r * 0.22f, paint)

        canvas.restore()
    }
}
