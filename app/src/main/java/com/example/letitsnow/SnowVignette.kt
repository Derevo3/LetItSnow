package com.example.letitsnow

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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
 * Оптимизировано для мгновенного поворота экрана (0 мс задержки):
 * - Базовый прозрачный битмап обрабатывается один раз и сохраняется в памяти.
 * - При смене ориентации (портрет <-> альбом) повторная обработка миллионов пикселей НЕ выполняется.
 * - Отрисовка под текущие размеры и ориентацию выполняется аппаратно на GPU через матрицу трансформации.
 */
class SnowVignette {

    private var width: Float = 0f
    private var height: Float = 0f
    private var density: Float = 1f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Кэш обработанного базового битмапа с прозрачным фоном
    private var baseTransparentBitmap: Bitmap? = null
    private var cachedResId: Int = 0

    // Матрица аппаратной отрисовки на GPU
    private val drawMatrix = Matrix()
    private var lastMatrixTargetW: Float = -1f
    private var lastMatrixTargetH: Float = -1f
    private var lastMatrixBmpW: Int = -1
    private var lastMatrixBmpH: Int = -1

    @get:DrawableRes
    val drawableRes: Int
        get() = getDrawableRes(VignetteStyle.FROST_PATTERNS, VignetteSize.SMALL)

    companion object {
        @DrawableRes
        fun getDrawableRes(style: VignetteStyle, size: VignetteSize = VignetteSize.SMALL): Int {
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
        // При повороте экрана битмап НЕ сбрасывается и НЕ пересчитывается!
        // Сбрасывается только кэш матрицы для мгновенного обновления под новый размер
        lastMatrixTargetW = -1f
        lastMatrixTargetH = -1f
    }

    fun invalidate() {
        baseTransparentBitmap?.recycle()
        baseTransparentBitmap = null
        cachedResId = 0
        lastMatrixTargetW = -1f
        lastMatrixTargetH = -1f
    }

    fun clear() {
        invalidate()
    }

    fun draw(
        canvas: Canvas,
        resources: Resources,
        style: VignetteStyle = VignetteStyle.FROST_PATTERNS,
        size: VignetteSize = VignetteSize.SMALL,
        theme: SnowColorTheme = SnowColorTheme.WHITE,
        frameTimeNanos: Long = 0L
    ) {
        val targetW = width
        val targetH = height
        if (targetW <= 0f || targetH <= 0f) return

        val resId = getDrawableRes(style, size)

        if (baseTransparentBitmap == null || cachedResId != resId || baseTransparentBitmap?.isRecycled == true) {
            loadAndCacheBaseBitmap(resources, resId, style)
        }

        val bmp = baseTransparentBitmap ?: return
        if (bmp.isRecycled) return

        // Обновляем матрицу трансформации на GPU только если размеры изменились
        if (lastMatrixTargetW != targetW || lastMatrixTargetH != targetH ||
            lastMatrixBmpW != bmp.width || lastMatrixBmpH != bmp.height
        ) {
            updateDrawMatrix(targetW, targetH, bmp.width, bmp.height)
        }

        // Поддержка цветовой темы для морозных узоров
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

        // Аппаратная отрисовка на GPU без создания промежуточных битмапов
        canvas.drawBitmap(bmp, drawMatrix, paint)
    }

    private fun updateDrawMatrix(targetW: Float, targetH: Float, bmpW: Int, bmpH: Int) {
        drawMatrix.reset()
        val isLandscape = targetW > targetH
        val isBmpPortrait = bmpW < bmpH

        if (isLandscape && isBmpPortrait) {
            // Альбомная ориентация: поворот портретного ресурса на 90° по часовой стрелке
            // bmp.height масштабируется в targetW, bmp.width масштабируется в targetH
            val scaleX = targetH / bmpW.toFloat()
            val scaleY = targetW / bmpH.toFloat()
            drawMatrix.postScale(scaleX, scaleY)
            drawMatrix.postRotate(90f)
            drawMatrix.postTranslate(targetW, 0f)
        } else if (!isLandscape && !isBmpPortrait) {
            // Портретный экран, но альбомный ресурс: поворот на 90°
            val scaleX = targetH / bmpW.toFloat()
            val scaleY = targetW / bmpH.toFloat()
            drawMatrix.postScale(scaleX, scaleY)
            drawMatrix.postRotate(90f)
            drawMatrix.postTranslate(targetW, 0f)
        } else {
            // Обычное масштабирование без поворота
            val scaleX = targetW / bmpW.toFloat()
            val scaleY = targetH / bmpH.toFloat()
            drawMatrix.postScale(scaleX, scaleY)
        }

        lastMatrixTargetW = targetW
        lastMatrixTargetH = targetH
        lastMatrixBmpW = bmpW
        lastMatrixBmpH = bmpH
    }

    private fun loadAndCacheBaseBitmap(
        resources: Resources,
        resId: Int,
        style: VignetteStyle
    ) {
        baseTransparentBitmap?.recycle()
        baseTransparentBitmap = null
        cachedResId = 0

        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(resources, resId, options)
            val origW = options.outWidth
            val origH = options.outHeight
            if (origW <= 0 || origH <= 0) return

            // Ограничиваем базовый размер для молниеносной обработки пикселей и экономии памяти
            var sampleSize = 1
            while (origW / (sampleSize * 2) >= 720 && origH / (sampleSize * 2) >= 1280) {
                sampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val decoded = BitmapFactory.decodeResource(resources, resId, options) ?: return

            val w = decoded.width
            val h = decoded.height
            val pixels = IntArray(w * h)
            decoded.getPixels(pixels, 0, w, 0, 0, w, h)
            decoded.recycle()

            val isFrost = style == VignetteStyle.FROST_PATTERNS

            // Сверхбыстрая целочисленная обработка альфа-прозрачности (без float делений)
            for (i in pixels.indices) {
                val color = pixels[i]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val maxC = maxOf(r, maxOf(g, b))

                if (maxC <= 14) {
                    pixels[i] = 0 // Полная прозрачность для темного центра экрана
                } else if (isFrost) {
                    val a = (((maxC - 10) * 255) / 245).coerceIn(0, 255)
                    val nr = ((r * 255) / maxC).coerceAtMost(255)
                    val ng = ((g * 255) / maxC).coerceAtMost(255)
                    val nb = ((b * 255) / maxC).coerceAtMost(255)
                    pixels[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
                } else {
                    val a = if (maxC >= 40) {
                        255
                    } else {
                        (((maxC - 14) * 255) / 26).coerceIn(0, 255)
                    }
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            val finalBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            finalBitmap.setPixels(pixels, 0, w, 0, 0, w, h)

            baseTransparentBitmap = finalBitmap
            cachedResId = resId
            lastMatrixTargetW = -1f
            lastMatrixTargetH = -1f
        } catch (_: Throwable) {
            baseTransparentBitmap?.recycle()
            baseTransparentBitmap = null
            cachedResId = 0
        }
    }
}
