package com.example.letitsnow

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.DrawableRes

/**
 * Отрисовка декоративной праздничной виньетки по краям экрана.
 * Поддерживает стили:
 * - [VignetteStyle.FROST_PATTERNS] — кристальные морозные узоры (маленькие и средние) с поддержкой цветовых тем.
 * - [VignetteStyle.CHRISTMAS_LIGHTS] — уютная новогодняя гирлянда с разноцветными лампочками.
 * - [VignetteStyle.EVERGREEN] — праздничные хвойные еловые ветви по краям дисплея.
 *
 * Использует динамическое вырезание темного фона в плавную альфа-прозрачность
 * и аппаратное кэширование под разрешение экрана.
 */
class SnowVignette {

    private var width: Float = 0f
    private var height: Float = 0f
    private var density: Float = 1f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Кэш текущего обработанного битмапа под размер дисплея и выбранный ресурс
    private var cachedBitmap: Bitmap? = null
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0
    private var cachedResId: Int = 0

    @get:DrawableRes
    val drawableRes: Int
        get() = getDrawableRes(VignetteStyle.FROST_PATTERNS, VignetteSize.MEDIUM)

    companion object {
        @DrawableRes
        fun getDrawableRes(style: VignetteStyle, size: VignetteSize = VignetteSize.MEDIUM): Int {
            return when (style) {
                VignetteStyle.FROST_PATTERNS -> {
                    when (size) {
                        VignetteSize.SMALL -> R.drawable.frost_patterns_small
                        VignetteSize.MEDIUM -> R.drawable.frost_patterns_medium
                    }
                }
                VignetteStyle.CHRISTMAS_LIGHTS -> R.drawable.christmas_lights
                VignetteStyle.EVERGREEN -> R.drawable.evergreen_border
            }
        }
    }

    fun setSize(w: Float, h: Float, screenDensity: Float) {
        if (w == width && h == height && screenDensity == density) return
        width = w
        height = h
        density = screenDensity
        invalidate()
    }

    fun invalidate() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        cachedWidth = 0
        cachedHeight = 0
        cachedResId = 0
    }

    fun clear() {
        invalidate()
    }

    fun draw(
        canvas: Canvas,
        resources: Resources,
        style: VignetteStyle = VignetteStyle.FROST_PATTERNS,
        size: VignetteSize = VignetteSize.MEDIUM,
        theme: SnowColorTheme = SnowColorTheme.WHITE,
        frameTimeNanos: Long = 0L
    ) {
        val targetW = width.toInt()
        val targetH = height.toInt()
        if (targetW <= 0 || targetH <= 0) return

        val resId = getDrawableRes(style, size)

        if (cachedBitmap == null || cachedWidth != targetW || cachedHeight != targetH || cachedResId != resId) {
            loadAndCacheBitmap(resources, targetW, targetH, resId, style)
        }

        // Поддержка цветовой темы для морозных узоров, гирлянду и еловые ветви оставляем в сочных природных цветах
        if (style == VignetteStyle.FROST_PATTERNS && theme != SnowColorTheme.WHITE) {
            paint.colorFilter = when (theme) {
                SnowColorTheme.SCARLET -> PorterDuffColorFilter(Color.rgb(255, 120, 120), PorterDuff.Mode.MULTIPLY)
                SnowColorTheme.PINK -> PorterDuffColorFilter(Color.rgb(255, 160, 210), PorterDuff.Mode.MULTIPLY)
                SnowColorTheme.BLACK -> PorterDuffColorFilter(Color.rgb(180, 190, 205), PorterDuff.Mode.MULTIPLY)
                SnowColorTheme.WHITE -> null
            }
        } else {
            paint.colorFilter = null
        }

        // Мягкое живое мерцание огоньков для новогодней гирлянды
        if (style == VignetteStyle.CHRISTMAS_LIGHTS && frameTimeNanos > 0L) {
            val cycle = (frameTimeNanos / 1_000_000L) % 2400L
            val angle = (cycle / 2400.0) * 2.0 * Math.PI
            val pulse = 0.82f + 0.18f * kotlin.math.sin(angle).toFloat()
            paint.alpha = (pulse * 255f).toInt().coerceIn(180, 255)
        } else {
            paint.alpha = 255
        }

        cachedBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            }
        }
    }

    private fun loadAndCacheBitmap(
        resources: Resources,
        targetW: Int,
        targetH: Int,
        resId: Int,
        style: VignetteStyle
    ) {
        cachedBitmap?.recycle()
        cachedBitmap = null
        cachedResId = 0

        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(resources, resId, options)
            val origW = options.outWidth
            val origH = options.outHeight
            if (origW <= 0 || origH <= 0) return

            var sampleSize = 1
            while (origW / (sampleSize * 2) >= targetW && origH / (sampleSize * 2) >= targetH) {
                sampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val decoded = BitmapFactory.decodeResource(resources, resId, options) ?: return

            // 1. Масштабируем точно под размер экрана
            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled != decoded) {
                decoded.recycle()
            }

            // 2. Преобразуем черный фон в прозрачность
            val pixels = IntArray(targetW * targetH)
            scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
            scaled.recycle()

            val isFrost = style == VignetteStyle.FROST_PATTERNS

            for (i in pixels.indices) {
                val color = pixels[i]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val maxC = maxOf(r, maxOf(g, b))

                if (maxC <= 14) {
                    pixels[i] = 0 // Полная прозрачность для темного центра экрана
                } else if (isFrost) {
                    // Морозные кристаллы: яркость определяет прозрачность, цвета нормализуются для чистоты свечения
                    val aNorm = ((maxC - 10) / 245f).coerceIn(0f, 1f)
                    val a = (aNorm * 255f).toInt()
                    val boost = 255f / maxC
                    val nr = (r * boost).toInt().coerceAtMost(255)
                    val ng = (g * boost).toInt().coerceAtMost(255)
                    val nb = (b * boost).toInt().coerceAtMost(255)
                    pixels[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
                } else {
                    // Гирлянда и ветки ели: насыщенные цветные объекты с мягким сглаживанием по контуру
                    val a = if (maxC >= 40) {
                        255
                    } else {
                        (((maxC - 14) / 26f) * 255f).toInt().coerceIn(0, 255)
                    }
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            val finalBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            finalBitmap.setPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

            cachedBitmap = finalBitmap
            cachedWidth = targetW
            cachedHeight = targetH
            cachedResId = resId
        } catch (_: Throwable) {
            cachedBitmap?.recycle()
            cachedBitmap = null
            cachedResId = 0
        }
    }
}
