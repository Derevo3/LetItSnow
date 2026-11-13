package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Модель отдельной снежинки в концепции FAIRYTALE SNOWFALL («Сказочный ночной снегопад»).
 *
 * Распределение частиц:
 * - 90% [TYPE_MICRO]: Крошечные белые точки и микроскопические снежные хлопья (1–3 dp).
 * - 8%  [TYPE_FLAKE]: Небольшие различимые снежные хлопья (мягкие органические хлопья).
 * - 2%  [TYPE_CRYSTAL]: Изящные, тонкие шестилучевые снежные кристаллы (воздушное кружево).
 *
 * Никаких пузырей, ярких колец, искусственных звёзд и грубых обводок.
 * Полная поддержка индивидуального параллакса глубины, мягкого покачивания и переиспользования памяти.
 */
data class Snowflake(
    var x: Float = 0f,
    var y: Float = 0f,
    var radius: Float = 0f,
    var speedY: Float = 0f,
    var alpha: Int = 255,
    var color: Int = 0xFFFFFFFF.toInt(),
    var swayRadius: Float = 0f,
    var swaySpeed: Float = 0f,
    var angle: Float = 0f,
    var secondaryAngle: Float = 0f,
    var secondarySpeed: Float = 0f,
    var baseX: Float = 0f,
    var type: Int = TYPE_MICRO,
    var microVariant: Int = 0,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,
    var isLanding: Boolean = false,
    var landingProgress: Float = 0f,
    var tiltAngle: Float = 0f,
    var tiltSpeed: Float = 0f,
    var depthZ: Float = 0.5f // 0.0 (глубокий далекий фон) .. 1.0 (ближний план)
) {
    companion object {
        const val TYPE_MICRO = 0    // 90% — крошечные точки и микроскопические хлопья (1–3 dp)
        const val TYPE_FLAKE = 1    // 8%  — небольшие различимые хлопья
        const val TYPE_CRYSTAL = 2  // 2%  — изящные шестилучевые кристаллы
        private const val LANDING_DURATION = 0.22f
    }

    /**
     * Плавное обновление позиции снежинки с учетом индивидуального покачивания и гравитации.
     */
    fun update(
        dtSec: Float,
        width: Float,
        height: Float,
        density: Float,
        accumulation: SnowAccumulation? = null,
        fallSpeedMultiplier: Float = 1f,
        accumulationMultiplier: Float = 1f,
        sizeMultiplier: Float = 1f
    ) {
        if (isLanding) {
            landingProgress += dtSec / LANDING_DURATION
            y += speedY * 0.1f * dtSec
            if (landingProgress >= 1f) {
                reset(width, height, density, isInitial = false)
            }
            return
        }

        // Вертикальное перемещение
        y += speedY * fallSpeedMultiplier * dtSec

        // Естественное покачивание с двумя несоизмеримыми гармониками (исключает механическое однообразие)
        angle += swaySpeed * dtSec
        secondaryAngle += secondarySpeed * dtSec
        x = baseX + sin(angle) * swayRadius + sin(secondaryAngle) * (swayRadius * 0.28f)

        // Вращение и 3D-наклон
        if (type != TYPE_MICRO) {
            rotation += rotationSpeed * dtSec
            tiltAngle += tiltSpeed * dtSec
        }

        val surfaceY = accumulation?.getSurfaceY(x) ?: height
        val effectiveRadius = radius * sizeMultiplier

        // Соприкосновение с сугробом
        if (y + effectiveRadius >= surfaceY) {
            isLanding = true
            landingProgress = 0f
            accumulation?.addSnow(x, effectiveRadius, accumulationMultiplier)
        } else if (y - effectiveRadius > height) {
            accumulation?.addSnow(x, effectiveRadius, accumulationMultiplier)
            reset(width, height, density, isInitial = false)
        }

        // Горизонтальный перенос при выходе за границы
        if (x + effectiveRadius < 0f) {
            baseX += width + effectiveRadius * 2f
            x = baseX + sin(angle) * swayRadius
        } else if (x - effectiveRadius > width) {
            baseX -= width + effectiveRadius * 2f
            x = baseX + sin(angle) * swayRadius
        }
    }

    /**
     * Инициализирует параметры снежинки со строгим соблюдением пропорций 90% / 8% / 2%
     * и размеров преимущественно 1–3 dp.
     */
    fun reset(width: Float, height: Float, density: Float, isInitial: Boolean = false) {
        isLanding = false
        landingProgress = 0f

        // Условное расстояние в пространстве (0.0 — глубина фона, 1.0 — передний план)
        depthZ = Random.nextFloat()
        microVariant = Random.nextInt(3)

        // Распределение частиц согласно концепции FAIRYTALE SNOWFALL:
        val roll = Random.nextFloat()
        type = when {
            roll < 0.90f -> TYPE_MICRO   // 90%
            roll < 0.98f -> TYPE_FLAKE   // 8%
            else -> TYPE_CRYSTAL         // 2%
        }

        when (type) {
            TYPE_MICRO -> {
                // Основная масса: крошечные снежинки 1–2.8 dp
                val minR = 0.55f * density
                val maxR = 1.35f * density
                radius = minR + depthZ * (maxR - minR)

                // Глубокий параллакс: далекие медленнее, ближние чуть быстрее
                speedY = (22f + depthZ * 36f + Random.nextFloat() * 12f) * density
                alpha = (70 + (depthZ * 145).toInt() + Random.nextInt(30)).coerceIn(65, 245)

                // Оттенки: чистый белый, холодный бело-голубой, мягкий ледяной
                color = when (Random.nextInt(4)) {
                    0 -> 0xFFFFFFFF.toInt()
                    1 -> 0xFFF2F7FC.toInt()
                    2 -> 0xFFE2EFFD.toInt()
                    else -> 0xFFD4E8FA.toInt()
                }
                rotation = 0f
                rotationSpeed = 0f
            }
            TYPE_FLAKE -> {
                // Небольшие различимые хлопья (2.5–4.5 dp)
                val minR = 1.45f * density
                val maxR = 2.35f * density
                radius = minR + depthZ * (maxR - minR)

                speedY = (36f + depthZ * 28f + Random.nextFloat() * 14f) * density
                alpha = (150 + Random.nextInt(85)).coerceIn(140, 245)
                color = if (Random.nextBoolean()) 0xFFFFFFFF.toInt() else 0xFFF0F6FD.toInt()

                rotation = Random.nextFloat() * 360f
                rotationSpeed = (Random.nextFloat() - 0.5f) * 45f
            }
            TYPE_CRYSTAL -> {
                // Изящные шестилучевые кристаллы (4.5–6.8 dp) — встречаются редко (2%)
                val minR = 2.5f * density
                val maxR = 3.6f * density
                radius = minR + depthZ * (maxR - minR)

                speedY = (30f + depthZ * 24f + Random.nextFloat() * 12f) * density
                alpha = (180 + Random.nextInt(70)).coerceIn(170, 250)
                color = 0xFFFFFFFF.toInt()

                rotation = Random.nextFloat() * 360f
                rotationSpeed = (Random.nextFloat() - 0.5f) * 55f
            }
        }

        // Параметры покачивания
        swayRadius = (2.5f + depthZ * 8f + Random.nextFloat() * 3f) * density
        swaySpeed = 0.8f + Random.nextFloat() * 1.5f
        secondarySpeed = swaySpeed * 1.618f // Золотое сечение для естественности

        angle = Random.nextFloat() * 6.28f
        secondaryAngle = Random.nextFloat() * 6.28f

        tiltAngle = Random.nextFloat() * 6.28f
        tiltSpeed = 0.8f + Random.nextFloat() * 1.8f

        baseX = Random.nextFloat() * width
        x = baseX + sin(angle) * swayRadius

        y = if (isInitial) {
            -radius - (Random.nextFloat() * height * 0.75f)
        } else {
            -radius - (Random.nextFloat() * 30f * density)
        }
    }

    /**
     * Отрисовка снежинки на Canvas с учетом выбранного стиля.
     */
    fun draw(
        canvas: Canvas,
        paint: Paint,
        density: Float,
        style: SnowflakeStyle = SnowflakeStyle.FAIRYTALE,
        sizeMultiplier: Float = 1.0f
    ) {
        val currentAlpha = if (isLanding) {
            (alpha * (1.0f - landingProgress)).toInt().coerceIn(0, 255)
        } else {
            alpha
        }
        if (currentAlpha <= 0) return

        val effRadius = radius * sizeMultiplier
        val scaleX = if (isLanding) 1.0f + landingProgress * 0.5f else 1.0f
        val scaleY = if (isLanding) 1.0f - landingProgress * 0.5f else 1.0f

        when (style) {
            SnowflakeStyle.FAIRYTALE -> {
                drawFairytale(canvas, paint, density, effRadius, currentAlpha, scaleX, scaleY, sizeMultiplier)
            }
            SnowflakeStyle.FLUFFY -> {
                drawFluffy(canvas, paint, effRadius, currentAlpha, scaleX, scaleY)
            }
        }
    }

    /**
     * СТИЛЬ: FAIRYTALE SNOWFALL (Сказочный ночной снегопад)
     * - Чистый белый и холодный бело-голубой.
     * - Никаких пузырей, ярких колец и искусственных звёзд.
     * - 90% микро-снежинок (1-3 dp), 8% хлопьев и 2% изящных шестилучевых кристаллов.
     */
    private fun drawFairytale(
        canvas: Canvas,
        paint: Paint,
        density: Float,
        effRadius: Float,
        currentAlpha: Int,
        scaleX: Float,
        scaleY: Float,
        sizeMultiplier: Float
    ) {
        paint.color = color

        when (type) {
            TYPE_MICRO -> {
                // 90%: Крошечные белые точки и микроскопические хлопья
                paint.style = Paint.Style.FILL

                when (microVariant) {
                    0 -> {
                        // Чистая четкая микро-точка
                        paint.alpha = currentAlpha
                        canvas.drawCircle(x, y, effRadius * scaleX, paint)
                    }
                    1 -> {
                        // Мягкая микро-точка с естественным краем (без резких колец)
                        paint.alpha = (currentAlpha * 0.35f).toInt()
                        canvas.drawCircle(x, y, effRadius * 1.35f * scaleX, paint)
                        paint.alpha = currentAlpha
                        canvas.drawCircle(x, y, effRadius * 0.75f * scaleX, paint)
                    }
                    else -> {
                        // Сдвоенный микро-комочек (две касающиеся микро-точки)
                        paint.alpha = currentAlpha
                        val subR = effRadius * 0.65f * scaleX
                        val offset = subR * 0.55f
                        canvas.drawCircle(x - offset, y, subR, paint)
                        canvas.drawCircle(x + offset, y, subR, paint)
                    }
                }
            }
            TYPE_FLAKE -> {
                // 8%: Небольшие различимые мягкие хлопья снега (органический вид)
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(rotation)
                if (isLanding) canvas.scale(scaleX, scaleY)

                paint.style = Paint.Style.FILL

                // Нежнейшая полупрозрачная подсветка без колец
                paint.alpha = (currentAlpha * 0.20f).toInt()
                canvas.drawCircle(0f, 0f, effRadius * 1.4f, paint)

                // Центральное мягкое ядро
                paint.alpha = currentAlpha
                canvas.drawCircle(0f, 0f, effRadius * 0.62f, paint)

                // 3 мягких лепестка вокруг ядра, формирующие форму снежного комочка
                paint.alpha = (currentAlpha * 0.75f).toInt()
                val petalDist = effRadius * 0.40f
                val petalR = effRadius * 0.45f
                canvas.drawCircle(petalDist, 0f, petalR, paint)
                canvas.drawCircle(-petalDist * 0.5f, petalDist * 0.866f, petalR, paint)
                canvas.drawCircle(-petalDist * 0.5f, -petalDist * 0.866f, petalR, paint)

                canvas.restore()
            }
            TYPE_CRYSTAL -> {
                // 2%: Изящные шестилучевые кристаллы (воздушное кружево)
                val tiltCos = if (isLanding) 1.0f else (abs(cos(tiltAngle)) * 0.65f + 0.35f)

                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(rotation)
                canvas.scale(scaleX * tiltCos, scaleY)

                // Тонкие, воздушные лучи
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = (0.75f * density * sizeMultiplier).coerceAtLeast(0.7f)
                paint.alpha = currentAlpha

                val r = effRadius * 1.15f
                val twig = r * 0.50f
                val twigDx = r * 0.15f
                val twigDy = r * 0.18f

                // 6 изящных лучей с тонкими V-веточками
                for (i in 0 until 6) {
                    canvas.drawLine(0f, 0f, r, 0f, paint)
                    canvas.drawLine(twig, 0f, twig - twigDx, twigDy, paint)
                    canvas.drawLine(twig, 0f, twig - twigDx, -twigDy, paint)
                    canvas.rotate(60f)
                }

                // Крошечный центральный кристаллик
                paint.style = Paint.Style.FILL
                paint.alpha = currentAlpha
                canvas.drawCircle(0f, 0f, effRadius * 0.20f, paint)

                canvas.restore()
            }
        }
    }

    /**
     * СТИЛЬ: FLUFFY (Мягкие пушистые хлопья)
     */
    private fun drawFluffy(
        canvas: Canvas,
        paint: Paint,
        effRadius: Float,
        currentAlpha: Int,
        scaleX: Float,
        scaleY: Float
    ) {
        val tiltCos = if (isLanding) 1.0f else (abs(cos(tiltAngle)) * 0.6f + 0.4f)
        paint.color = color

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        canvas.scale(scaleX * tiltCos, scaleY)

        paint.style = Paint.Style.FILL
        paint.alpha = (currentAlpha * 0.15f).toInt()
        canvas.drawCircle(0f, 0f, effRadius * 1.5f, paint)

        paint.alpha = (currentAlpha * 0.70f).toInt()
        val off = effRadius * 0.38f
        val subR = effRadius * 0.45f
        canvas.drawCircle(-off, 0f, subR, paint)
        canvas.drawCircle(off, 0f, subR, paint)
        canvas.drawCircle(0f, off, subR, paint)
        canvas.drawCircle(0f, -off, subR, paint)

        paint.alpha = currentAlpha
        canvas.drawCircle(0f, 0f, effRadius * 0.52f, paint)

        canvas.restore()
    }
}
