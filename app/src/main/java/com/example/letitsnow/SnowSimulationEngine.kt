package com.example.letitsnow

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import java.io.File

/**
 * Единый синглтон физической симуляции снегопада.
 * Обеспечивает единое состояние снежинок и сугроба для:
 * - [SnowView] (внутри приложения)
 * - [SnowOverlayService] (оверлей поверх всех приложений)
 * - [SnowWallpaperService] (живые обои экрана блокировки и рабочего стола)
 *
 * Благодаря общему состоянию снегопад никогда не сбрасывается и не прерывается
 * при переходе между экраном блокировки, рабочим столом и приложениями.
 */
object SnowSimulationEngine {

    val snowflakes = ArrayList<Snowflake>(SnowSettings.MAX_INTENSITY)
    val sparkles = ArrayList<Sparkle>(60)
    val accumulation = SnowAccumulation()
    val snowMask = SnowMask()
    val vignette = SnowVignette()

    var resources: Resources? = null

    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var canvasWidth: Float = 0f
        private set
    var canvasHeight: Float = 0f
        private set
    var density: Float = 1f
        private set

    var currentSettings: SnowSettings = SnowSettings()
        private set

    private var lastUpdateNanos: Long = 0L

    var customBackgroundBitmap: Bitmap? = null
        private set
    private var loadedBackgroundPath: String? = null
    private var loadedTargetWidth: Int = 0
    private var loadedTargetHeight: Int = 0

    /**
     * Применяет новые настройки снегопада (вызывается из DataStore / меню настроек).
     */
    @Synchronized
    fun applySettings(settings: SnowSettings) {
        currentSettings = settings
        accumulation.isSnowbankEnabled = settings.isSnowbankEnabled
        accumulation.setBaseSnowbankHeight(settings.snowbankHeight)

        if (canvasWidth > 0f && canvasHeight > 0f) {
            adjustSnowflakeCount(settings.intensity)
            adjustSparkleCount(settings.sparklesCount)
            checkAndUpdateBackground()
        }
    }

    /**
     * Обновляет размеры холста симуляции при повороте экрана или смене дисплея.
     * Масштабирует текущие снежинки без их очистки и пересоздания с нуля.
     */
    @Synchronized
    fun updateCanvasSize(w: Float, h: Float, screenDensity: Float, res: Resources? = null) {
        if (w <= 0f || h <= 0f) return
        if (res != null) {
            resources = res
        }

        val oldW = canvasWidth
        val oldH = canvasHeight
        canvasWidth = w
        canvasHeight = h
        density = screenDensity

        snowMask.setSize(w, h, density)
        accumulation.setSize(w, h, density)
        vignette.setSize(w, h, density)

        if (snowflakes.isEmpty() || oldW <= 0f || oldH <= 0f) {
            initSnowflakes(w, h)
        } else {
            val scaleX = w / oldW
            val scaleY = h / oldH
            for (i in 0 until snowflakes.size) {
                val flake = snowflakes[i]
                flake.x = (flake.x * scaleX).coerceIn(0f, w)
                flake.baseX = (flake.baseX * scaleX).coerceIn(0f, w)
                flake.y = (flake.y * scaleY).coerceIn(-flake.radius * 2, h)
            }
            adjustSnowflakeCount(currentSettings.intensity)
        }

        if (oldW > 0f && oldH > 0f) {
            val scaleX = w / oldW
            val scaleY = h / oldH
            for (i in 0 until sparkles.size) {
                val s = sparkles[i]
                s.x = (s.x * scaleX).coerceIn(0f, w)
                s.baseX = (s.baseX * scaleX).coerceIn(0f, w)
                s.y = (s.y * scaleY).coerceIn(0f, h)
            }
        }
        adjustSparkleCount(currentSettings.sparklesCount)
        checkAndUpdateBackground()
    }

    private fun initSnowflakes(width: Float, height: Float) {
        snowflakes.clear()
        val targetCount = currentSettings.intensity
        repeat(targetCount) {
            val flake = Snowflake()
            flake.reset(width, height, density, isInitial = true)
            snowflakes.add(flake)
        }
    }

    private fun adjustSnowflakeCount(targetCount: Int) {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return
        if (snowflakes.size < targetCount) {
            val toAdd = minOf(4, targetCount - snowflakes.size)
            repeat(toAdd) {
                val flake = Snowflake()
                flake.reset(canvasWidth, canvasHeight, density, isInitial = false)
                snowflakes.add(flake)
            }
        } else if (snowflakes.size > targetCount) {
            while (snowflakes.size > targetCount) {
                snowflakes.removeAt(snowflakes.size - 1)
            }
        }
    }

    private fun adjustSparkleCount(targetCount: Int) {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return
        if (!currentSettings.isSparklesEnabled) {
            if (sparkles.isNotEmpty()) {
                sparkles.clear()
            }
            return
        }
        val count = targetCount.coerceIn(SnowSettings.MIN_SPARKLES_COUNT, SnowSettings.MAX_SPARKLES_COUNT)
        if (sparkles.size < count) {
            val needed = count - sparkles.size
            repeat(needed) {
                val sparkle = Sparkle()
                sparkle.reset(canvasWidth, canvasHeight, density, accumulation)
                sparkles.add(sparkle)
            }
        } else if (sparkles.size > count) {
            while (sparkles.size > count) {
                sparkles.removeAt(sparkles.size - 1)
            }
        }
    }

    /**
     * Продвигает симуляцию вперед во времени.
     * Защищен от скачков времени и множественных одновременных вызовов из разных сервисов.
     */
    @Synchronized
    fun updatePhysics(frameTimeNanos: Long, isOverlay: Boolean = false) {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return

        if (lastUpdateNanos == 0L) {
            lastUpdateNanos = frameTimeNanos
            return
        }

        val elapsedNanos = frameTimeNanos - lastUpdateNanos
        if (elapsedNanos <= 0L) {
            lastUpdateNanos = frameTimeNanos
            return
        }

        val dt = if (elapsedNanos > 500_000_000L) {
            0.016f
        } else {
            (elapsedNanos / 1_000_000_000f).coerceIn(0f, 0.1f)
        }
        lastUpdateNanos = frameTimeNanos

        adjustSnowflakeCount(currentSettings.intensity)
        accumulation.allowGrowthAboveBase = !isOverlay

        val w = canvasWidth
        val h = canvasHeight
        val fallSpeedMult = currentSettings.fallSpeed
        val accumMult = currentSettings.accumulationRate
        val sizeMult = currentSettings.snowflakeSize

        for (i in 0 until snowflakes.size) {
            snowflakes[i].update(
                dtSec = dt,
                width = w,
                height = h,
                density = density,
                accumulation = accumulation,
                fallSpeedMultiplier = fallSpeedMult,
                accumulationMultiplier = accumMult,
                sizeMultiplier = sizeMult
            )
        }

        accumulation.update(dt, accumMult)

        if (currentSettings.isSparklesEnabled) {
            adjustSparkleCount(currentSettings.sparklesCount)
            for (i in 0 until sparkles.size) {
                val s = sparkles[i]
                if (s.isDead) {
                    s.reset(w, h, density, accumulation)
                }
                s.update(dt, w, h, density, accumulation)
            }
        } else if (sparkles.isNotEmpty()) {
            sparkles.clear()
        }
    }

    /**
     * Отрисовывает состояние симуляции (снежинки, сугроб и сказочные блёстки) на переданный Canvas.
     */
    @Synchronized
    fun draw(canvas: Canvas, res: Resources? = resources) {
        if (res != null) {
            resources = res
        }
        // 1. Отрисовка снежинок (слой 1)
        val isGlow = currentSettings.isGlowEnabled
        val theme = currentSettings.colorTheme
        val style = currentSettings.snowflakeStyle
        val sizeMult = currentSettings.snowflakeSize

        for (i in 0 until snowflakes.size) {
            snowflakes[i].draw(
                canvas = canvas,
                paint = flakePaint,
                density = density,
                style = style,
                colorTheme = theme,
                isGlowEnabled = isGlow,
                sizeMultiplier = sizeMult
            )
        }

        // 2. Отрисовка сугроба (слой 2 поверх падающих в него снежинок)
        val activeBankStyle = currentSettings.snowbankStyle
        accumulation.draw(canvas, activeBankStyle, theme)

        // 3. Отрисовка волшебных блёсток (слой 3 поверх сугроба и снежинок)
        if (currentSettings.isSparklesEnabled && sparkles.isNotEmpty()) {
            val sSize = currentSettings.sparklesSize
            for (i in 0 until sparkles.size) {
                sparkles[i].draw(canvas, sparklePaint, theme, sSize)
            }
        }

        // 4. Отрисовка морозной виньетки по краям экрана (слой 4)
        if (currentSettings.isVignetteEnabled) {
            val r = res ?: resources
            if (r != null) {
                vignette.draw(
                    canvas = canvas,
                    resources = r,
                    style = currentSettings.vignetteStyle,
                    size = currentSettings.vignetteSize,
                    theme = theme
                )
            }
        } else {
            vignette.clear()
        }
    }

    /**
     * Отрисовывает фоновое изображение (пользовательское фото или переданный Paint/градиент).
     */
    @Synchronized
    fun drawBackground(canvas: Canvas, defaultPaint: Paint? = null) {
        val bmp = customBackgroundBitmap
        if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, 0f, 0f, null)
        } else if (defaultPaint != null) {
            canvas.drawRect(0f, 0f, canvasWidth, canvasHeight, defaultPaint)
        } else {
            canvas.drawColor(0xFF0B132B.toInt())
        }
    }

    @Synchronized
    private fun checkAndUpdateBackground() {
        val path = currentSettings.customBackgroundPath
        val targetW = canvasWidth.toInt()
        val targetH = canvasHeight.toInt()

        if (path == loadedBackgroundPath && targetW == loadedTargetWidth && targetH == loadedTargetHeight) {
            return
        }

        loadedBackgroundPath = path
        loadedTargetWidth = targetW
        loadedTargetHeight = targetH

        if (path.isNullOrEmpty() || targetW <= 0 || targetH <= 0) {
            customBackgroundBitmap?.recycle()
            customBackgroundBitmap = null
            return
        }

        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            customBackgroundBitmap?.recycle()
            customBackgroundBitmap = null
            return
        }

        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val origW = options.outWidth
            val origH = options.outHeight
            if (origW <= 0 || origH <= 0) {
                customBackgroundBitmap?.recycle()
                customBackgroundBitmap = null
                return
            }

            var sampleSize = 1
            while (origW / (sampleSize * 2) >= targetW && origH / (sampleSize * 2) >= targetH) {
                sampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            if (decoded != null) {
                val scale = maxOf(targetW.toFloat() / decoded.width, targetH.toFloat() / decoded.height)
                val scaledW = (decoded.width * scale).toInt().coerceAtLeast(1)
                val scaledH = (decoded.height * scale).toInt().coerceAtLeast(1)

                val scaled = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
                if (scaled != decoded) {
                    decoded.recycle()
                }

                val cropX = ((scaledW - targetW) / 2).coerceIn(0, (scaledW - targetW).coerceAtLeast(0))
                val cropY = ((scaledH - targetH) / 2).coerceIn(0, (scaledH - targetH).coerceAtLeast(0))
                val finalW = targetW.coerceAtMost(scaledW - cropX)
                val finalH = targetH.coerceAtMost(scaledH - cropY)

                val cropped = Bitmap.createBitmap(scaled, cropX, cropY, finalW, finalH)
                if (cropped != scaled) {
                    scaled.recycle()
                }

                customBackgroundBitmap?.recycle()
                customBackgroundBitmap = cropped
            } else {
                customBackgroundBitmap?.recycle()
                customBackgroundBitmap = null
            }
        } catch (_: Throwable) {
            customBackgroundBitmap?.recycle()
            customBackgroundBitmap = null
        }
    }
}
