package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

/**
 * Модель отдельной снежинки в симуляции снегопада.
 * Поддерживает визуальное разнообразие (точки, светящиеся хлопья, ледяные кристаллы)
 * и плавный переход при приземлении на снег.
 */
data class Snowflake(
    var x: Float = 0f,
    var y: Float = 0f,
    var radius: Float = 0f,
    var speedY: Float = 0f,
    var alpha: Int = 255,
    var swayRadius: Float = 0f,
    var swaySpeed: Float = 0f,
    var angle: Float = 0f,
    var baseX: Float = 0f,
    var type: Int = TYPE_SOFT_DOT,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,
    var isLanding: Boolean = false,
    var landingProgress: Float = 0f
) {
    companion object {
        const val TYPE_SOFT_DOT = 0    // Мягкие далекие белые точки
        const val TYPE_GLOWING = 1     // Слегка размытые хлопья с ореолом
        const val TYPE_CRYSTAL = 2     // Вращающиеся ледяные кристаллы
        private const val LANDING_DURATION = 0.22f // ~220мс для плавного слияния со снегом
    }

    /**
     * Обновляет позицию снежинки за прошедшее время [dtSec] с учетом настроек скорости и накопления.
     */
    fun update(
        dtSec: Float,
        width: Float,
        height: Float,
        density: Float,
        accumulation: SnowAccumulation? = null,
        fallSpeedMultiplier: Float = 1f,
        accumulationMultiplier: Float = 1f
    ) {
        if (isLanding) {
            // Анимация плавного приземления: снежинка замедляется, расплющивается и растворяется в сугробе
            landingProgress += dtSec / LANDING_DURATION
            y += speedY * 0.1f * dtSec
            if (landingProgress >= 1f) {
                reset(width, height, density, isInitial = false)
            }
            return
        }

        // Плавное движение вниз с учетом коэффициента скорости
        y += speedY * fallSpeedMultiplier * dtSec

        // Горизонтальное отклонение / покачивание
        angle += swaySpeed * dtSec
        x = baseX + sin(angle) * swayRadius

        // Вращение для снежинок-кристаллов
        if (type == TYPE_CRYSTAL) {
            rotation += rotationSpeed * dtSec
        }

        // Высота поверхности снега в текущей точке X (двумерная сетка накопления)
        val surfaceY = accumulation?.getSurfaceY(x) ?: height

        // Столкновение с сугробом: плавный переход и передача массы в снежную сетку
        if (y + radius >= surfaceY) {
            isLanding = true
            landingProgress = 0f
            accumulation?.addSnow(x, radius, accumulationMultiplier)
        } else if (y - radius > height) {
            accumulation?.addSnow(x, radius, accumulationMultiplier)
            reset(width, height, density, isInitial = false)
        }

        // Плавный циклический перенос по горизонтали при выходе за пределы экрана
        if (x + radius < 0f) {
            baseX += width + radius * 2f
            x = baseX + sin(angle) * swayRadius
        } else if (x - radius > width) {
            baseX -= width + radius * 2f
            x = baseX + sin(angle) * swayRadius
        }
    }

    /**
     * Инициализирует или сбрасывает свойства снежинки со случайными значениями
     * и выбором типа для естественного визуального разнообразия.
     */
    fun reset(width: Float, height: Float, density: Float, isInitial: Boolean = false) {
        isLanding = false
        landingProgress = 0f

        // Случайный тип: 45% мягкие точки, 35% светящиеся хлопья, 20% кристаллы
        val roll = Random.nextFloat()
        type = when {
            roll < 0.45f -> TYPE_SOFT_DOT
            roll < 0.80f -> TYPE_GLOWING
            else -> TYPE_CRYSTAL
        }

        when (type) {
            TYPE_SOFT_DOT -> {
                val minR = 1.2f * density
                val maxR = 2.6f * density
                radius = minR + Random.nextFloat() * (maxR - minR)
                speedY = (35f + Random.nextFloat() * 45f) * density
                alpha = 85 + Random.nextInt(90)
            }
            TYPE_GLOWING -> {
                val minR = 2.8f * density
                val maxR = 5.2f * density
                radius = minR + Random.nextFloat() * (maxR - minR)
                speedY = (55f + Random.nextFloat() * 55f) * density
                alpha = 160 + Random.nextInt(95)
            }
            TYPE_CRYSTAL -> {
                val minR = 3.2f * density
                val maxR = 5.5f * density
                radius = minR + Random.nextFloat() * (maxR - minR)
                speedY = (45f + Random.nextFloat() * 50f) * density
                alpha = 180 + Random.nextInt(75)
                rotation = Random.nextFloat() * 360f
                rotationSpeed = (Random.nextFloat() - 0.5f) * 100f
            }
        }

        // Параметры горизонтального покачивания
        swayRadius = (5f + Random.nextFloat() * 16f) * density
        swaySpeed = 0.9f + Random.nextFloat() * 2.2f
        angle = Random.nextFloat() * (2f * Math.PI.toFloat())

        baseX = Random.nextFloat() * width
        x = baseX + sin(angle) * swayRadius

        y = if (isInitial) {
            // При первом запуске все снежинки начинают сверху экрана (экран чистый)
            -radius - (Random.nextFloat() * height * 0.75f)
        } else {
            // При повторном появлении создаем чуть выше верхней границы экрана
            -radius - (Random.nextFloat() * 40f * density)
        }
    }

    /**
     * Отрисовка снежинки на Canvas с учетом ее типа, формы и эффекта приземления.
     */
    fun draw(canvas: Canvas, paint: Paint, density: Float) {
        val currentAlpha = if (isLanding) {
            (alpha * (1.0f - landingProgress)).toInt().coerceIn(0, 255)
        } else {
            alpha
        }
        if (currentAlpha <= 0) return

        // Эффект легкого расплющивания при столкновении с поверхностью снега
        val scaleX = if (isLanding) 1.0f + landingProgress * 0.5f else 1.0f
        val scaleY = if (isLanding) 1.0f - landingProgress * 0.5f else 1.0f

        when (type) {
            TYPE_SOFT_DOT -> {
                paint.style = Paint.Style.FILL
                paint.alpha = currentAlpha
                canvas.drawCircle(x, y, radius * scaleX, paint)
            }
            TYPE_GLOWING -> {
                paint.style = Paint.Style.FILL
                // Внешний мягкий ореол
                paint.alpha = currentAlpha / 4
                canvas.drawCircle(x, y, radius * 1.55f * scaleX, paint)
                // Яркое ядро
                paint.alpha = currentAlpha
                canvas.drawCircle(x, y, radius * 0.75f * scaleX, paint)
            }
            TYPE_CRYSTAL -> {
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(rotation)
                if (isLanding) {
                    canvas.scale(scaleX, scaleY)
                }

                paint.alpha = currentAlpha
                paint.style = Paint.Style.FILL
                canvas.drawCircle(0f, 0f, radius * 0.3f, paint)

                // 6-лучевой ледяной кристаллик (3 перекрестные линии)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f * density
                val r = radius * 0.9f
                canvas.drawLine(-r, 0f, r, 0f, paint)
                val cos60 = 0.5f * r
                val sin60 = 0.866f * r
                canvas.drawLine(-cos60, -sin60, cos60, sin60, paint)
                canvas.drawLine(-cos60, sin60, cos60, -sin60, paint)

                paint.style = Paint.Style.FILL // Возвращаем стиль
                canvas.restore()
            }
        }
    }
}
