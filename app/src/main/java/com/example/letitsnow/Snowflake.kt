package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Модель отдельной снежинки в концепции FAIRYTALE SNOWFALL («Сказочный ночной снегопад»)
 * и расширенных визуальных стилей.
 *
 * Распределение частиц:
 * - 90% [TYPE_MICRO]: Крошечные частицы и микроскопические хлопья (1–3 dp).
 * - 8%  [TYPE_FLAKE]: Небольшие различимые хлопья (мягкие органические хлопья).
 * - 2%  [TYPE_CRYSTAL]: Изящные, тонкие шестилучевые кристаллы (воздушное кружево).
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
    var depthZ: Float = 0.5f, // 0.0 (глубокий далекий фон) .. 1.0 (ближний план)
    var depthSpeedFactor: Float = 1.0f // Предрассчитанный множитель скорости на основе depthZ
) {
    companion object {
        const val TYPE_MICRO = 0    // 90% — крошечные точки и микроскопические хлопья (1–3 dp)
        const val TYPE_FLAKE = 1    // 8%  — небольшие различимые хлопья
        const val TYPE_CRYSTAL = 2  // 2%  — изящные шестилучевые кристаллы
        private const val LANDING_DURATION = 0.22f
    }

    /**
     * Физическое обновление положения снежинки за шаг времени [dtSec].
     */
    fun update(
        dtSec: Float,
        width: Float,
        height: Float,
        density: Float,
        accumulation: SnowAccumulation,
        fallSpeedMultiplier: Float = 1.0f,
        accumulationMultiplier: Float = 1.0f,
        sizeMultiplier: Float = 1.0f
    ) {
        if (width <= 0f || height <= 0f) return

        if (isLanding) {
            landingProgress += dtSec / LANDING_DURATION
            if (landingProgress >= 1.0f) {
                reset(width, height, density, isInitial = false)
            }
            return
        }

        val effSpeedMultiplier = (fallSpeedMultiplier * depthSpeedFactor).coerceAtLeast(0.05f)
        y += speedY * effSpeedMultiplier * dtSec

        angle += swaySpeed * dtSec
        secondaryAngle += secondarySpeed * dtSec
        tiltAngle += tiltSpeed * dtSec

        val primarySway = sin(angle) * swayRadius
        val secondarySway = sin(secondaryAngle) * (swayRadius * 0.35f)
        x = baseX + primarySway + secondarySway

        rotation += rotationSpeed * dtSec

        val effRadius = radius * sizeMultiplier

        // В режиме снежного покрова на горизонтальном экране
        if (accumulation.isSurfaceCoverActive) {
            if (y - effRadius > height) {
                reset(width, height, density, isInitial = false)
                return
            }
            if (x + effRadius < -30f * density) {
                baseX += width + 60f * density
            } else if (x - effRadius > width + 30f * density) {
                baseX -= width + 60f * density
            }
            return
        }

        // Быстрая отсечка: проверяем коллизию с сугробом только когда сугроб включен и снежинка достигает гребня сугроба
        if (accumulation.isSnowbankEnabled && y >= accumulation.minSurfaceY - effRadius * 0.4f) {
            val surfaceY = accumulation.getSurfaceY(x)
            val contactY = surfaceY - effRadius * 0.4f

            if (y >= contactY) {
                accumulation.addSnow(x, effRadius, accumulationMultiplier)
                isLanding = true
                landingProgress = 0f
                y = contactY
                return
            }
        }

        if (y - effRadius > height) {
            reset(width, height, density, isInitial = false)
            return
        }

        if (x + effRadius < -30f * density) {
            baseX += width + 60f * density
        } else if (x - effRadius > width + 30f * density) {
            baseX -= width + 60f * density
        }
    }

    /**
     * Инициализация или перерождение снежинки сверху экрана.
     */
    fun reset(width: Float, height: Float, density: Float, isInitial: Boolean = false) {
        isLanding = false
        landingProgress = 0f

        depthZ = Random.nextFloat()
        depthSpeedFactor = 0.65f + depthZ * 0.70f

        val typeRoll = Random.nextFloat()
        type = when {
            typeRoll < 0.90f -> TYPE_MICRO   // 90%
            typeRoll < 0.98f -> TYPE_FLAKE   // 8%
            else -> TYPE_CRYSTAL             // 2%
        }

        microVariant = Random.nextInt(3)

        when (type) {
            TYPE_MICRO -> {
                val minR = 0.55f * density
                val maxR = 1.45f * density
                radius = minR + depthZ * (maxR - minR)

                speedY = (24f + depthZ * 34f + Random.nextFloat() * 12f) * density
                alpha = (70 + (depthZ * 145).toInt() + Random.nextInt(30)).coerceIn(65, 245)

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

        swayRadius = (2.5f + depthZ * 8f + Random.nextFloat() * 3f) * density
        swaySpeed = 0.8f + Random.nextFloat() * 1.5f
        secondarySpeed = swaySpeed * 1.618f

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
     * Отрисовка снежинки на Canvas с учетом выбранного стиля, цветовой темы и сияния.
     */
    fun draw(
        canvas: Canvas,
        paint: Paint,
        density: Float,
        style: SnowflakeStyle = SnowflakeStyle.FAIRYTALE,
        colorTheme: SnowColorTheme = SnowColorTheme.WHITE,
        isGlowEnabled: Boolean = false,
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

        // Мягкое сказочное сияние вокруг снежинки
        if (isGlowEnabled) {
            drawGlow(canvas, paint, density, effRadius, currentAlpha, colorTheme, scaleX, scaleY)
        }

        val isScarlet = colorTheme == SnowColorTheme.SCARLET
        when (style) {
            SnowflakeStyle.FAIRYTALE -> {
                if (isScarlet) {
                    drawScarletFairytale(canvas, paint, density, effRadius, currentAlpha, scaleX, scaleY, sizeMultiplier)
                } else {
                    drawFairytale(canvas, paint, density, effRadius, currentAlpha, scaleX, scaleY, sizeMultiplier)
                }
            }
            SnowflakeStyle.FLUFFY -> {
                if (isScarlet) {
                    drawFluffyScarlet(canvas, paint, effRadius, currentAlpha, scaleX, scaleY)
                } else {
                    drawFluffy(canvas, paint, effRadius, currentAlpha, scaleX, scaleY)
                }
            }
        }
    }

    /**
     * Отрисовка мягкого сказочного сияния вокруг частицы.
     */
    private fun drawGlow(
        canvas: Canvas,
        paint: Paint,
        density: Float,
        effRadius: Float,
        currentAlpha: Int,
        colorTheme: SnowColorTheme,
        scaleX: Float,
        scaleY: Float
    ) {
        val glowColor = if (colorTheme == SnowColorTheme.SCARLET) {
            0xFFFF3B56.toInt() // Нежный рубиновый ореол
        } else {
            0xFFBAE6FD.toInt() // Нежно-голубой сказочный ореол
        }

        paint.color = glowColor
        paint.style = Paint.Style.FILL

        val glowR = effRadius * 2.2f + 2f * density

        // Внешнее рассеянное сияние
        paint.alpha = (currentAlpha * 0.22f).toInt()
        canvas.drawCircle(x, y, glowR * scaleX, paint)

        // Внутреннее более яркое свечение
        paint.alpha = (currentAlpha * 0.45f).toInt()
        canvas.drawCircle(x, y, (glowR * 0.55f) * scaleX, paint)
    }

    /**
     * СТИЛЬ: FAIRYTALE SNOWFALL (Сказочный ночной снегопад) — Белый/Ледяной
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
                paint.style = Paint.Style.FILL

                when (microVariant) {
                    0 -> {
                        paint.alpha = currentAlpha
                        canvas.drawCircle(x, y, effRadius * scaleX, paint)
                    }
                    1 -> {
                        paint.alpha = (currentAlpha * 0.35f).toInt()
                        canvas.drawCircle(x, y, effRadius * 1.35f * scaleX, paint)
                        paint.alpha = currentAlpha
                        canvas.drawCircle(x, y, effRadius * 0.75f * scaleX, paint)
                    }
                    else -> {
                        paint.alpha = currentAlpha
                        val subR = effRadius * 0.65f * scaleX
                        val offset = subR * 0.55f
                        canvas.drawCircle(x - offset, y, subR, paint)
                        canvas.drawCircle(x + offset, y, subR, paint)
                    }
                }
            }
            TYPE_FLAKE -> {
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(rotation)
                if (isLanding) canvas.scale(scaleX, scaleY)

                paint.style = Paint.Style.FILL

                paint.alpha = (currentAlpha * 0.20f).toInt()
                canvas.drawCircle(0f, 0f, effRadius * 1.4f, paint)

                paint.alpha = currentAlpha
                canvas.drawCircle(0f, 0f, effRadius * 0.62f, paint)

                paint.alpha = (currentAlpha * 0.75f).toInt()
                val petalDist = effRadius * 0.40f
                val petalR = effRadius * 0.45f
                canvas.drawCircle(petalDist, 0f, petalR, paint)
                canvas.drawCircle(-petalDist * 0.5f, petalDist * 0.866f, petalR, paint)
                canvas.drawCircle(-petalDist * 0.5f, -petalDist * 0.866f, petalR, paint)

                canvas.restore()
            }
            TYPE_CRYSTAL -> {
                val tiltCos = if (isLanding) 1.0f else (abs(cos(tiltAngle)) * 0.65f + 0.35f)

                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(rotation)
                canvas.scale(scaleX * tiltCos, scaleY)

                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = (0.75f * density * sizeMultiplier).coerceAtLeast(0.7f)
                paint.alpha = currentAlpha

                val r = effRadius * 1.15f
                val twig = r * 0.50f
                val twigDx = r * 0.15f
                val twigDy = r * 0.18f

                for (i in 0 until 6) {
                    canvas.drawLine(0f, 0f, r, 0f, paint)
                    canvas.drawLine(twig, 0f, twig - twigDx, twigDy, paint)
                    canvas.drawLine(twig, 0f, twig - twigDx, -twigDy, paint)
                    canvas.rotate(60f)
                }

                paint.style = Paint.Style.FILL
                paint.alpha = currentAlpha
                canvas.drawCircle(0f, 0f, effRadius * 0.20f, paint)

                canvas.restore()
            }
        }
    }

    /**
     * СТИЛЬ: FLUFFY (Мягкие пушистые хлопья) — Белый
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

    /**
     * СТИЛЬ: FAIRYTALE — Алый / Кроваво-рубиновый
     */
    private fun drawScarletFairytale(
        canvas: Canvas,
        paint: Paint,
        density: Float,
        effRadius: Float,
        currentAlpha: Int,
        scaleX: Float,
        scaleY: Float,
        sizeMultiplier: Float
    ) {
        when (type) {
            TYPE_MICRO -> {
                paint.style = Paint.Style.FILL

                val bloodyColor = when (microVariant) {
                    0 -> 0xFFFF2A3A.toInt() // Ярко-алый
                    1 -> 0xFFDC2626.toInt() // Насыщенный кровавый
                    else -> 0xFF991B1B.toInt() // Глубокий бордовый
                }
                paint.color = bloodyColor

                when (microVariant) {
                    0 -> {
                        paint.alpha = currentAlpha
                        canvas.drawCircle(x, y, effRadius * scaleX, paint)
                    }
                    1 -> {
                        paint.alpha = (currentAlpha * 0.35f).toInt()
                        canvas.drawCircle(x, y, effRadius * 1.45f * scaleX, paint)
                        paint.alpha = currentAlpha
                        canvas.drawCircle(x, y, effRadius * 0.75f * scaleX, paint)
                    }
                    else -> {
                        paint.alpha = currentAlpha
                        val r = effRadius * 0.75f * scaleX
                        canvas.drawCircle(x, y, r, paint)
                        val tailY = y - r * 0.9f
                        val tailR = r * 0.45f
                        canvas.drawCircle(x, tailY, tailR, paint)
                    }
                }
            }
            TYPE_FLAKE -> {
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(rotation)
                if (isLanding) canvas.scale(scaleX, scaleY)

                paint.style = Paint.Style.FILL

                paint.color = 0xFF7F1D1D.toInt()
                paint.alpha = (currentAlpha * 0.25f).toInt()
                canvas.drawCircle(0f, 0f, effRadius * 1.5f, paint)

                paint.color = 0xFFFF1E27.toInt()
                paint.alpha = currentAlpha
                canvas.drawCircle(0f, 0f, effRadius * 0.65f, paint)

                paint.color = 0xFFE11D48.toInt()
                paint.alpha = (currentAlpha * 0.85f).toInt()
                val petalDist = effRadius * 0.42f
                val petalR = effRadius * 0.48f
                canvas.drawCircle(petalDist, 0f, petalR, paint)
                canvas.drawCircle(-petalDist * 0.5f, petalDist * 0.866f, petalR, paint)
                canvas.drawCircle(-petalDist * 0.5f, -petalDist * 0.866f, petalR, paint)

                canvas.restore()
            }
            TYPE_CRYSTAL -> {
                val tiltCos = if (isLanding) 1.0f else (abs(cos(tiltAngle)) * 0.65f + 0.35f)

                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(rotation)
                canvas.scale(scaleX * tiltCos, scaleY)

                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = (0.85f * density * sizeMultiplier).coerceAtLeast(0.75f)
                paint.color = 0xFFFF2D55.toInt()
                paint.alpha = currentAlpha

                val r = effRadius * 1.15f
                val twig = r * 0.50f
                val twigDx = r * 0.15f
                val twigDy = r * 0.18f

                for (i in 0 until 6) {
                    canvas.drawLine(0f, 0f, r, 0f, paint)
                    canvas.drawLine(twig, 0f, twig - twigDx, twigDy, paint)
                    canvas.drawLine(twig, 0f, twig - twigDx, -twigDy, paint)
                    canvas.rotate(60f)
                }

                paint.style = Paint.Style.FILL
                paint.color = 0xFFFF0033.toInt()
                paint.alpha = currentAlpha
                canvas.drawCircle(0f, 0f, effRadius * 0.24f, paint)

                canvas.restore()
            }
        }
    }

    /**
     * СТИЛЬ: FLUFFY — Алый / Кроваво-рубиновый
     */
    private fun drawFluffyScarlet(
        canvas: Canvas,
        paint: Paint,
        effRadius: Float,
        currentAlpha: Int,
        scaleX: Float,
        scaleY: Float
    ) {
        val tiltCos = if (isLanding) 1.0f else (abs(cos(tiltAngle)) * 0.6f + 0.4f)

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        canvas.scale(scaleX * tiltCos, scaleY)

        paint.style = Paint.Style.FILL

        // Внешняя полупрозрачная багровая дымка
        paint.color = 0xFF7F1D1D.toInt()
        paint.alpha = (currentAlpha * 0.22f).toInt()
        canvas.drawCircle(0f, 0f, effRadius * 1.5f, paint)

        // Средние рубиновые лепестки хлопьев
        paint.color = 0xFFDC2626.toInt()
        paint.alpha = (currentAlpha * 0.70f).toInt()
        val off = effRadius * 0.38f
        val subR = effRadius * 0.45f
        canvas.drawCircle(-off, 0f, subR, paint)
        canvas.drawCircle(off, 0f, subR, paint)
        canvas.drawCircle(0f, off, subR, paint)
        canvas.drawCircle(0f, -off, subR, paint)

        // Яркое алое ядро
        paint.color = 0xFFFF2A3A.toInt()
        paint.alpha = currentAlpha
        canvas.drawCircle(0f, 0f, effRadius * 0.52f, paint)

        canvas.restore()
    }
}
