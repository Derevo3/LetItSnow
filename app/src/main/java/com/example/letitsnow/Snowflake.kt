package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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

        private val sharedHeartPath = Path()
        private val sharedDiamondPath = Path()
        private val CRYSTAL_COS = floatArrayOf(1.0f, 0.5f, -0.5f, -1.0f, -0.5f, 0.5f)
        private val CRYSTAL_SIN = floatArrayOf(0.0f, 0.8660254f, 0.8660254f, 0.0f, -0.8660254f, -0.8660254f)

        private fun buildHeart(r: Float) {
            sharedHeartPath.reset()
            sharedHeartPath.moveTo(0f, -r * 0.32f)
            sharedHeartPath.cubicTo(-r * 0.65f, -r * 1.15f, -r * 1.35f, -r * 0.25f, 0f, r * 1.0f)
            sharedHeartPath.cubicTo(r * 1.35f, -r * 0.25f, r * 0.65f, -r * 1.15f, 0f, -r * 0.32f)
            sharedHeartPath.close()
        }
    }

    /**
     * Физическое обновление положения снежинки за шаг времени [dtSec].
     * [dirX] и [dirY] — единичный направляющий вектор силы тяжести (длина 1.0).
     */
    fun update(
        dtSec: Float,
        width: Float,
        height: Float,
        density: Float,
        accumulation: SnowAccumulation,
        fallSpeedMultiplier: Float = 1.0f,
        accumulationMultiplier: Float = 1.0f,
        sizeMultiplier: Float = 1.0f,
        dirX: Float = 0f,
        dirY: Float = 1f
    ) {
        if (width <= 0f || height <= 0f) return

        if (isLanding) {
            landingProgress += dtSec / LANDING_DURATION
            if (landingProgress >= 1.0f) {
                reset(width, height, density, isInitial = false, dirY = dirY)
            }
            return
        }

        // Фиксированная скорость падения без искусственного ускорения
        val baseSpeed = speedY * (fallSpeedMultiplier * depthSpeedFactor).coerceAtLeast(0.05f)

        // Движение строго вдоль вектора направления гравитации (dirX, dirY)
        baseX += dirX * baseSpeed * dtSec
        y += dirY * baseSpeed * dtSec

        // Естественное покачивание перпендикулярно направлению падения
        angle += swaySpeed * dtSec
        secondaryAngle += secondarySpeed * dtSec
        tiltAngle += tiltSpeed * dtSec

        val primarySway = sin(angle) * swayRadius
        val secondarySway = sin(secondaryAngle) * (swayRadius * 0.35f)
        val sway = primarySway + secondarySway

        // Вектор перпендикуляра: (-dirY, dirX)
        x = baseX - dirY * sway
        val effY = y + dirX * sway

        rotation += rotationSpeed * dtSec

        val effRadius = radius * sizeMultiplier

        // В режиме снежного покрова на горизонтальном экране
        if (accumulation.isSurfaceCoverActive) {
            if (effY - effRadius > height || effY + effRadius < 0f || x + effRadius < -30f * density || x - effRadius > width + 30f * density) {
                reset(width, height, density, isInitial = false, dirY = dirY)
                return
            }
            return
        }

        // Коллизия с сугробом у нижнего края экрана (только при движении вниз, dirY > 0.3)
        if (dirY > 0.3f && accumulation.isSnowbankEnabled && effY >= accumulation.minSurfaceY - effRadius * 0.4f) {
            val surfaceY = accumulation.getSurfaceY(x)
            val contactY = surfaceY - effRadius * 0.4f

            if (effY >= contactY) {
                accumulation.addSnow(x, effRadius, accumulationMultiplier)
                isLanding = true
                landingProgress = 0f
                y = contactY
                return
            }
        }

        // Бесшовный перенос и перерождение при выходе за пределы экрана
        val margin = 50f * density
        if (dirY >= 0f) {
            // Обычное падение вниз: если ушла ниже экрана -> возрождается сверху
            if (effY - effRadius > height + margin) {
                reset(width, height, density, isInitial = false, dirY = dirY)
                return
            }
        } else {
            // Перевёрнутый экран (снег идёт вверх): если ушла выше экрана -> возрождается снизу
            if (effY + effRadius < -margin) {
                reset(width, height, density, isInitial = false, dirY = dirY)
                return
            }
        }

        // Боковой перенос через границы экрана (Wrap-around)
        if (x + effRadius < -margin) {
            baseX += width + margin * 2
        } else if (x - effRadius > width + margin) {
            baseX -= width + margin * 2
        }
    }

    /**
     * Инициализация или перерождение снежинки сверху экрана.
     */
    fun reset(width: Float, height: Float, density: Float, isInitial: Boolean = false, dirY: Float = 1f) {
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
        } else if (dirY >= 0f) {
            -radius - (Random.nextFloat() * 30f * density)
        } else {
            height + radius + (Random.nextFloat() * 30f * density)
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

        when (style) {
            SnowflakeStyle.FAIRYTALE -> {
                drawFairytale(canvas, paint, density, effRadius, currentAlpha, scaleX, scaleY, sizeMultiplier, colorTheme)
            }
            SnowflakeStyle.FLUFFY -> {
                drawFluffy(canvas, paint, effRadius, currentAlpha, scaleX, scaleY, colorTheme)
            }
            SnowflakeStyle.HEARTS -> {
                drawHearts(canvas, paint, density, effRadius, currentAlpha, scaleX, scaleY, sizeMultiplier, colorTheme)
            }
            SnowflakeStyle.DIAMOND_DUST -> {
                drawDiamondDust(canvas, paint, density, effRadius, currentAlpha, scaleX, scaleY, sizeMultiplier, colorTheme)
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
        val glowColor = when (colorTheme) {
            SnowColorTheme.SCARLET -> 0xFFFF1722.toInt() // Насыщенный ярко-алый ореол (без розового!)
            SnowColorTheme.PINK -> 0xFFF472B6.toInt()    // Нежно-розовый ореол
            SnowColorTheme.BLACK -> 0xFF64748B.toInt()   // Пепельно-серебристый туманный ореол
            SnowColorTheme.WHITE -> 0xFFBAE6FD.toInt()   // Нежно-голубой сказочный ореол
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

    private fun getMicroColor(colorTheme: SnowColorTheme, variant: Int): Int {
        return when (colorTheme) {
            SnowColorTheme.WHITE -> color
            SnowColorTheme.SCARLET -> when (variant) {
                0 -> 0xFFFF1E27.toInt() // Насыщенный ярко-алый
                1 -> 0xFFDC2626.toInt() // Глубокий кроваво-красный
                else -> 0xFF991B1B.toInt() // Тёмно-бордовый
            }
            SnowColorTheme.PINK -> when (variant) {
                0 -> 0xFFFFF1F2.toInt() // Нежно-зефирный розовый
                1 -> 0xFFF472B6.toInt() // Тёплый розовый
                else -> 0xFFDB2777.toInt() // Малиново-розовый
            }
            SnowColorTheme.BLACK -> when (variant) {
                0 -> 0xFF334155.toInt() // Пепельно-сланцевый
                1 -> 0xFF1E293B.toInt() // Графитово-чёрный
                else -> 0xFF0F172A.toInt() // Обсидиановый
            }
        }
    }

    /**
     * СТИЛЬ: FAIRYTALE SNOWFALL («Сказочный ночной снегопад»)
     */
    private fun drawFairytale(
        canvas: Canvas,
        paint: Paint,
        density: Float,
        effRadius: Float,
        currentAlpha: Int,
        scaleX: Float,
        scaleY: Float,
        sizeMultiplier: Float,
        colorTheme: SnowColorTheme
    ) {
        when (type) {
            TYPE_MICRO -> {
                paint.style = Paint.Style.FILL
                val microColor = getMicroColor(colorTheme, microVariant)
                paint.color = microColor

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

                val outerColor = when (colorTheme) {
                    SnowColorTheme.WHITE -> color
                    SnowColorTheme.SCARLET -> 0xFF7F1D1D.toInt()
                    SnowColorTheme.PINK -> 0xFFFBCFE8.toInt()
                    SnowColorTheme.BLACK -> 0xFF0F172A.toInt()
                }
                val coreColor = when (colorTheme) {
                    SnowColorTheme.WHITE -> color
                    SnowColorTheme.SCARLET -> 0xFFFF1E27.toInt()
                    SnowColorTheme.PINK -> 0xFFFFF1F2.toInt()
                    SnowColorTheme.BLACK -> 0xFF475569.toInt()
                }
                val petalColor = when (colorTheme) {
                    SnowColorTheme.WHITE -> color
                    SnowColorTheme.SCARLET -> 0xFFB91C1C.toInt()
                    SnowColorTheme.PINK -> 0xFFF472B6.toInt()
                    SnowColorTheme.BLACK -> 0xFF1E293B.toInt()
                }

                paint.color = outerColor
                paint.alpha = (currentAlpha * 0.20f).toInt()
                canvas.drawCircle(0f, 0f, effRadius * 1.4f, paint)

                paint.color = coreColor
                paint.alpha = currentAlpha
                canvas.drawCircle(0f, 0f, effRadius * 0.62f, paint)

                paint.color = petalColor
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

                val strokeColor = when (colorTheme) {
                    SnowColorTheme.WHITE -> 0xFFBAE6FD.toInt()
                    SnowColorTheme.SCARLET -> 0xFFFF1E27.toInt()
                    SnowColorTheme.PINK -> 0xFFF472B6.toInt()
                    SnowColorTheme.BLACK -> 0xFF64748B.toInt()
                }
                val centerColor = when (colorTheme) {
                    SnowColorTheme.WHITE -> 0xFFFFFFFF.toInt()
                    SnowColorTheme.SCARLET -> 0xFFE11D48.toInt()
                    SnowColorTheme.PINK -> 0xFFFFF1F2.toInt()
                    SnowColorTheme.BLACK -> 0xFF1E293B.toInt()
                }

                paint.color = strokeColor
                paint.alpha = currentAlpha

                val r = effRadius * 1.15f
                val twig = r * 0.50f
                val twigDx = r * 0.15f
                val twigDy = r * 0.18f

                for (i in 0 until 6) {
                    val cosA = CRYSTAL_COS[i]
                    val sinA = CRYSTAL_SIN[i]
                    val ex = r * cosA
                    val ey = r * sinA
                    canvas.drawLine(0f, 0f, ex, ey, paint)
                    val tx = twig * cosA
                    val ty = twig * sinA
                    val bx = (twig - twigDx) * cosA
                    val by = (twig - twigDx) * sinA
                    val px = -sinA * twigDy
                    val py = cosA * twigDy
                    canvas.drawLine(tx, ty, bx + px, by + py, paint)
                    canvas.drawLine(tx, ty, bx - px, by - py, paint)
                }

                paint.style = Paint.Style.FILL
                paint.color = centerColor
                paint.alpha = currentAlpha
                canvas.drawCircle(0f, 0f, effRadius * 0.20f, paint)

                canvas.restore()
            }
        }
    }

    /**
     * СТИЛЬ: FLUFFY («Мягкие пушистые хлопья»)
     */
    private fun drawFluffy(
        canvas: Canvas,
        paint: Paint,
        effRadius: Float,
        currentAlpha: Int,
        scaleX: Float,
        scaleY: Float,
        colorTheme: SnowColorTheme
    ) {
        val tiltCos = if (isLanding) 1.0f else (abs(cos(tiltAngle)) * 0.6f + 0.4f)

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        canvas.scale(scaleX * tiltCos, scaleY)

        paint.style = Paint.Style.FILL

        val hazeColor = when (colorTheme) {
            SnowColorTheme.WHITE -> color
            SnowColorTheme.SCARLET -> 0xFF7F1D1D.toInt()
            SnowColorTheme.PINK -> 0xFFFBCFE8.toInt()
            SnowColorTheme.BLACK -> 0xFF1E293B.toInt()
        }
        val petalColor = when (colorTheme) {
            SnowColorTheme.WHITE -> color
            SnowColorTheme.SCARLET -> 0xFFDC2626.toInt()
            SnowColorTheme.PINK -> 0xFFF472B6.toInt()
            SnowColorTheme.BLACK -> 0xFF0F172A.toInt()
        }
        val coreColor = when (colorTheme) {
            SnowColorTheme.WHITE -> color
            SnowColorTheme.SCARLET -> 0xFFFF1E27.toInt()
            SnowColorTheme.PINK -> 0xFFFFF1F2.toInt()
            SnowColorTheme.BLACK -> 0xFF334155.toInt()
        }

        // Внешняя полупрозрачная дымка
        paint.color = hazeColor
        paint.alpha = (currentAlpha * 0.18f).toInt()
        canvas.drawCircle(0f, 0f, effRadius * 1.5f, paint)

        // Средние лепестки хлопьев
        paint.color = petalColor
        paint.alpha = (currentAlpha * 0.70f).toInt()
        val off = effRadius * 0.38f
        val subR = effRadius * 0.45f
        canvas.drawCircle(-off, 0f, subR, paint)
        canvas.drawCircle(off, 0f, subR, paint)
        canvas.drawCircle(0f, off, subR, paint)
        canvas.drawCircle(0f, -off, subR, paint)

        // Яркое ядро
        paint.color = coreColor
        paint.alpha = currentAlpha
        canvas.drawCircle(0f, 0f, effRadius * 0.52f, paint)

        canvas.restore()
    }

    /**
     * СТИЛЬ: HEARTS («Кружащиеся романтичные сердечки»)
     */
    private fun drawHearts(
        canvas: Canvas,
        paint: Paint,
        density: Float,
        effRadius: Float,
        currentAlpha: Int,
        scaleX: Float,
        scaleY: Float,
        sizeMultiplier: Float,
        colorTheme: SnowColorTheme
    ) {
        val tiltCos = if (isLanding) 1.0f else (abs(cos(tiltAngle)) * 0.45f + 0.55f)

        canvas.save()
        canvas.translate(x, y)
        // Сердечки игриво покачиваются и кружатся при полете
        canvas.rotate(rotation + sin(tiltAngle) * 20f)
        canvas.scale(scaleX * tiltCos, scaleY)

        val heartR = effRadius * 1.25f
        buildHeart(heartR)

        when (type) {
            TYPE_MICRO -> {
                // Маленькие аккуратные сердечки
                paint.style = Paint.Style.FILL
                val c = getMicroColor(colorTheme, microVariant)
                paint.color = c
                paint.alpha = currentAlpha
                canvas.drawPath(sharedHeartPath, paint)
            }
            TYPE_FLAKE -> {
                // Мягкие пушистые сердечки с лёгким романтичным ореолом
                paint.style = Paint.Style.FILL

                val auraColor = when (colorTheme) {
                    SnowColorTheme.WHITE -> 0x40E0F2FE.toInt()
                    SnowColorTheme.SCARLET -> 0x507F1D1D.toInt()
                    SnowColorTheme.PINK -> 0x50FBCFE8.toInt()
                    SnowColorTheme.BLACK -> 0x50334155.toInt()
                }
                val mainColor = when (colorTheme) {
                    SnowColorTheme.WHITE -> 0xFFFFFFFF.toInt()
                    SnowColorTheme.SCARLET -> 0xFFDC2626.toInt()
                    SnowColorTheme.PINK -> 0xFFF472B6.toInt()
                    SnowColorTheme.BLACK -> 0xFF1E293B.toInt()
                }
                val innerColor = when (colorTheme) {
                    SnowColorTheme.WHITE -> 0xFFF0F9FF.toInt()
                    SnowColorTheme.SCARLET -> 0xFFFF1E27.toInt()
                    SnowColorTheme.PINK -> 0xFFFDF2F8.toInt()
                    SnowColorTheme.BLACK -> 0xFF475569.toInt()
                }

                // Внешний ореол
                paint.color = auraColor
                paint.alpha = (currentAlpha * 0.35f).toInt()
                canvas.scale(1.35f, 1.35f)
                canvas.drawPath(sharedHeartPath, paint)
                canvas.scale(1f / 1.35f, 1f / 1.35f)

                // Основное тело
                paint.color = mainColor
                paint.alpha = currentAlpha
                canvas.drawPath(sharedHeartPath, paint)

                // Внутренний блик
                paint.color = innerColor
                paint.alpha = (currentAlpha * 0.75f).toInt()
                canvas.scale(0.55f, 0.55f)
                canvas.drawPath(sharedHeartPath, paint)
            }
            TYPE_CRYSTAL -> {
                // Изящные ажурные сердечки с контуром и сиянием
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (1.2f * density * sizeMultiplier).coerceAtLeast(1.0f)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND

                val strokeColor = when (colorTheme) {
                    SnowColorTheme.WHITE -> 0xFFBAE6FD.toInt()
                    SnowColorTheme.SCARLET -> 0xFFFF1E27.toInt()
                    SnowColorTheme.PINK -> 0xFFF472B6.toInt()
                    SnowColorTheme.BLACK -> 0xFF64748B.toInt()
                }
                val innerColor = when (colorTheme) {
                    SnowColorTheme.WHITE -> 0xFFFFFFFF.toInt()
                    SnowColorTheme.SCARLET -> 0xFFB91C1C.toInt()
                    SnowColorTheme.PINK -> 0xFFEC4899.toInt()
                    SnowColorTheme.BLACK -> 0xFF0F172A.toInt()
                }

                paint.color = strokeColor
                paint.alpha = currentAlpha
                canvas.drawPath(sharedHeartPath, paint)

                // Внутреннее заполненное сердечко
                paint.style = Paint.Style.FILL
                paint.color = innerColor
                paint.alpha = (currentAlpha * 0.85f).toInt()
                canvas.scale(0.5f, 0.5f)
                canvas.drawPath(sharedHeartPath, paint)
            }
        }

        canvas.restore()
    }

    /**
     * СТИЛЬ: DIAMOND_DUST («Алмазная пыль» — парящие микрокристаллы с живым блеском граней)
     */
    private fun drawDiamondDust(
        canvas: Canvas,
        paint: Paint,
        density: Float,
        effRadius: Float,
        currentAlpha: Int,
        scaleX: Float,
        scaleY: Float,
        sizeMultiplier: Float,
        colorTheme: SnowColorTheme
    ) {
        // Живое мерцание граней кристалла при вращении в воздухе
        val facetGlint = abs(sin(tiltAngle * 1.8f))
        val shimmerAlpha = (currentAlpha * (0.35f + 0.65f * facetGlint)).toInt().coerceIn(0, 255)

        val coreColor = when (colorTheme) {
            SnowColorTheme.WHITE -> 0xFFFFFFFF.toInt()
            SnowColorTheme.SCARLET -> 0xFFFF8591.toInt()
            SnowColorTheme.PINK -> 0xFFFFF1F2.toInt()
            SnowColorTheme.BLACK -> 0xFFE2E8F0.toInt()
        }
        val facetColor = when (colorTheme) {
            SnowColorTheme.WHITE -> 0xFFBAE6FD.toInt()
            SnowColorTheme.SCARLET -> 0xFFFF1E27.toInt()
            SnowColorTheme.PINK -> 0xFFF472B6.toInt()
            SnowColorTheme.BLACK -> 0xFF475569.toInt()
        }

        when (type) {
            TYPE_MICRO -> {
                // Тончайшие ледяные иголочки и искрящиеся микро-крупинки
                if (microVariant == 0) {
                    paint.style = Paint.Style.FILL
                    paint.color = coreColor
                    paint.alpha = shimmerAlpha
                    canvas.drawCircle(x, y, (effRadius * 0.72f * scaleX).coerceAtLeast(0.5f * density), paint)
                } else {
                    canvas.save()
                    canvas.translate(x, y)
                    canvas.rotate(rotation + microVariant * 45f)
                    canvas.scale(scaleX, scaleY)

                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = (0.65f * density * sizeMultiplier).coerceAtLeast(0.6f)
                    paint.color = if (facetGlint > 0.75f) coreColor else facetColor
                    paint.alpha = shimmerAlpha

                    val needleHalf = effRadius * 1.15f
                    canvas.drawLine(-needleHalf, 0f, needleHalf, 0f, paint)
                    canvas.restore()
                }
            }
            TYPE_FLAKE -> {
                // Огранённый ромбовидный ледяной кристалл-призма
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(rotation)
                canvas.scale(scaleX * (0.55f + 0.45f * abs(cos(tiltAngle))), scaleY)

                val dR = effRadius * 1.15f
                sharedDiamondPath.reset()
                sharedDiamondPath.moveTo(0f, -dR)
                sharedDiamondPath.lineTo(dR * 0.62f, 0f)
                sharedDiamondPath.lineTo(0f, dR)
                sharedDiamondPath.lineTo(-dR * 0.62f, 0f)
                sharedDiamondPath.close()

                paint.style = Paint.Style.FILL
                paint.color = facetColor
                paint.alpha = (shimmerAlpha * 0.75f).toInt()
                canvas.drawPath(sharedDiamondPath, paint)

                // Вспышка света на центральной грани
                if (facetGlint > 0.6f) {
                    paint.color = coreColor
                    paint.alpha = ((facetGlint - 0.6f) / 0.4f * currentAlpha).toInt().coerceIn(0, 255)
                    canvas.drawCircle(0f, 0f, dR * 0.35f, paint)
                }

                canvas.restore()
            }
            TYPE_CRYSTAL -> {
                // Миниатюрная 4-лучевая игольчатая искра с алмазным ядром
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(rotation)
                canvas.scale(scaleX, scaleY)

                val r = effRadius * 1.25f * (0.75f + 0.35f * facetGlint)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = (0.75f * density * sizeMultiplier).coerceAtLeast(0.65f)
                paint.color = facetColor
                paint.alpha = shimmerAlpha

                canvas.drawLine(-r, 0f, r, 0f, paint)
                canvas.drawLine(0f, -r, 0f, r, paint)

                val diag = r * 0.48f
                paint.strokeWidth = (0.55f * density * sizeMultiplier).coerceAtLeast(0.5f)
                canvas.drawLine(-diag, -diag, diag, diag, paint)
                canvas.drawLine(-diag, diag, diag, -diag, paint)

                paint.style = Paint.Style.FILL
                paint.color = coreColor
                paint.alpha = currentAlpha
                canvas.drawCircle(0f, 0f, effRadius * 0.32f, paint)

                canvas.restore()
            }
        }
    }
}
