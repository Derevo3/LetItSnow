package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

/**
 * Кинематографическая частица переднего плана с эффектом расфокусировки (Bokeh / Depth of Field).
 *
 * Пролетает близко к «объективу камеры» поверх снежинок и сугроба,
 * создавая глубокий 3D-объём сцены с помощью мягкого радиального градиента
 * и ускоренного параллакса переднего плана.
 */
class BokehFlake {
    var x: Float = 0f
    var y: Float = 0f
    var baseX: Float = 0f
    var radius: Float = 0f
    var speedY: Float = 0f
    var alpha: Int = 26
    var swayRadius: Float = 0f
    var swaySpeed: Float = 0f
    var angle: Float = 0f
    var depthSpeedFactor: Float = 1.45f

    private var cachedShaderRadius: Float = -1f
    private var cachedShaderColor: Int = 0
    private var cachedShaderAlpha: Int = -1
    private var cachedShader: RadialGradient? = null

    fun reset(width: Float, height: Float, density: Float, isInitial: Boolean = false, dirY: Float = 1f) {
        if (width <= 0f || height <= 0f) return

        val depthRoll = Random.nextFloat() // 0.0 (средний расфокус) .. 1.0 (максимально близко к линзе)
        depthSpeedFactor = 1.30f + depthRoll * 0.45f

        radius = (14f + depthRoll * 16f) * density
        speedY = (36f + depthRoll * 22f + Random.nextFloat() * 10f) * density
        // Чем ближе и крупнее боке, тем оно воздушнее и прозрачнее
        alpha = (34 - (depthRoll * 14).toInt() + Random.nextInt(6)).coerceIn(16, 40)

        swayRadius = (6f + depthRoll * 9f) * density
        swaySpeed = 0.45f + Random.nextFloat() * 0.55f
        angle = Random.nextFloat() * 6.28f

        baseX = Random.nextFloat() * width
        x = baseX + sin(angle) * swayRadius

        y = if (isInitial) {
            Random.nextFloat() * height
        } else if (dirY >= 0f) {
            -radius * 1.5f - Random.nextFloat() * 40f * density
        } else {
            height + radius * 1.5f + Random.nextFloat() * 40f * density
        }
    }

    fun update(
        dtSec: Float,
        width: Float,
        height: Float,
        density: Float,
        fallSpeedMultiplier: Float = 1.0f,
        sizeMultiplier: Float = 1.0f,
        dirX: Float = 0f,
        dirY: Float = 1f
    ) {
        if (width <= 0f || height <= 0f) return

        val baseSpeed = speedY * (fallSpeedMultiplier * depthSpeedFactor).coerceAtLeast(0.08f)
        baseX += dirX * baseSpeed * dtSec
        y += dirY * baseSpeed * dtSec

        angle += swaySpeed * dtSec
        val sway = sin(angle) * swayRadius

        x = baseX - dirY * sway
        val effY = y + dirX * sway
        val effRadius = radius * sizeMultiplier.coerceIn(0.7f, 1.45f)
        val margin = effRadius * 1.6f + 20f * density

        if (dirY >= 0f) {
            if (effY - effRadius > height + margin) {
                reset(width, height, density, isInitial = false, dirY = dirY)
                return
            }
        } else {
            if (effY + effRadius < -margin) {
                reset(width, height, density, isInitial = false, dirY = dirY)
                return
            }
        }

        if (x + effRadius < -margin) {
            baseX += width + margin * 2f
        } else if (x - effRadius > width + margin) {
            baseX -= width + margin * 2f
        }
    }

    fun draw(
        canvas: Canvas,
        paint: Paint,
        colorTheme: SnowColorTheme,
        sizeMultiplier: Float = 1.0f
    ) {
        val effRadius = radius * sizeMultiplier.coerceIn(0.7f, 1.45f)
        if (effRadius <= 1f || alpha <= 0) return

        val baseColor = when (colorTheme) {
            SnowColorTheme.WHITE -> 0xFFE8F4FF.toInt()
            SnowColorTheme.SCARLET -> 0xFFFF3B47.toInt()
            SnowColorTheme.PINK -> 0xFFFBCFE8.toInt()
            SnowColorTheme.BLACK -> 0xFF64748B.toInt()
        }

        if (cachedShader == null ||
            cachedShaderRadius != effRadius ||
            cachedShaderColor != baseColor ||
            cachedShaderAlpha != alpha
        ) {
            val r = Color.red(baseColor)
            val g = Color.green(baseColor)
            val b = Color.blue(baseColor)

            // Оптический профиль линзового боке: мягкое ядро, чуть более плотный световой ободок и кремовое затухание
            cachedShader = RadialGradient(
                0f,
                0f,
                effRadius,
                intArrayOf(
                    Color.argb((alpha * 0.72f).toInt(), r, g, b),
                    Color.argb((alpha * 0.90f).toInt(), r, g, b),
                    Color.argb((alpha * 0.36f).toInt(), r, g, b),
                    Color.argb(0, r, g, b)
                ),
                floatArrayOf(0f, 0.58f, 0.84f, 1f),
                Shader.TileMode.CLAMP
            )
            cachedShaderRadius = effRadius
            cachedShaderColor = baseColor
            cachedShaderAlpha = alpha
        }

        canvas.save()
        canvas.translate(x, y)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = cachedShader
        canvas.drawCircle(0f, 0f, effRadius, paint)
        paint.shader = null
        canvas.restore()
    }
}
