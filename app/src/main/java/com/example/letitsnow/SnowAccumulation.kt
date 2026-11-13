package com.example.letitsnow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Двумерная физическая модель накопления снега (2D Snow Density Grid)
 * с поддержкой 4 различных визуальных стилей сугроба:
 * - [SnowbankStyle.SMOOTH_HILLS]: Шелковистые широкие природные холмы с мягкими тенями.
 * - [SnowbankStyle.PUFFY_CLOUDS]: Пышные округлые облачные шапки свежего снега.
 * - [SnowbankStyle.ICY_SHELF]: Кристальная светящаяся ледяная полка со слоистой каймой.
 * - [SnowbankStyle.CLASSIC]: Оригинальный растровый сугроб с клеточным затенением.
 */
class SnowAccumulation {

    private var width: Float = 0f
    private var height: Float = 0f
    private var densityScale: Float = 1f

    var cols: Int = 0
        private set
    var rows: Int = 0
        private set

    var cellW: Float = 1f
        private set
    var cellH: Float = 1f
        private set

    /**
     * Базовая высота постоянного сугроба у нижнего края экрана (в dp).
     */
    var baseSnowbankHeightDp: Float = SnowSettings.DEFAULT_SNOWBANK_HEIGHT
        private set

    /**
     * Разрешен ли рост сугроба выше базового уровня при приземлении снежинок.
     */
    var allowGrowthAboveBase: Boolean = true

    // Физическая сетка плотности снега (0.0f - чисто, 1.0f - плотный снег)
    private var densities = FloatArray(0)

    // Буферы для классического растрового режима
    private var tempBlur = FloatArray(0)
    private var visualDensities = FloatArray(0)
    private var pixels = IntArray(0)
    private var gridBitmap: Bitmap? = null
    private val srcRect = Rect()
    private val dstRect = Rect()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }

    // Буферы высот для гладких векторных режимов
    private var rawSurfaceY = FloatArray(0)
    private var smoothSurfaceY = FloatArray(0)
    private var tempSurfaceY = FloatArray(0)

    // Преаллоцированные векторные пути (0 аллокаций во время отрисовки кадров)
    private val mainPath = Path()
    private val backPath = Path()
    private val crestPath = Path()

    // Кисти для стиля «Плавные холмы» (SMOOTH_HILLS)
    private val smoothMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }
    private val smoothBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x48A0C8F0.toInt()
    }
    private val smoothCrestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xF5FFFFFF.toInt()
    }

    // Кисти для стиля «Пушистый» (PUFFY_CLOUDS)
    private val puffyBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }
    private val puffyHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x35FFFFFF.toInt()
    }
    private val puffyRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xEEFFFFFF.toInt()
    }

    // Кисти для стиля «Ледяной» (ICY_SHELF)
    private val iceBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }
    private val iceBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x3360A5FA.toInt()
    }
    private val iceCrestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFE0F2FE.toInt()
    }

    /**
     * Инициализация или пересчет сетки при изменении размеров экрана.
     */
    fun setSize(w: Float, h: Float, screenDensity: Float) {
        if (w <= 0f || h <= 0f) return

        width = w
        height = h
        densityScale = screenDensity

        val prevCols = cols
        val prevRows = rows
        val prevDensities = densities

        val step = 4.5f * densityScale
        cols = (w / step).toInt().coerceIn(70, 110)
        rows = (h / step).toInt().coerceIn(140, 240)

        cellW = w / cols
        cellH = h / rows

        val total = cols * rows

        densities = FloatArray(total)
        tempBlur = FloatArray(total)
        visualDensities = FloatArray(total)
        pixels = IntArray(total)

        rawSurfaceY = FloatArray(cols)
        smoothSurfaceY = FloatArray(cols)
        tempSurfaceY = FloatArray(cols)

        smoothCrestPaint.strokeWidth = 2.4f * densityScale
        puffyRimPaint.strokeWidth = 2.2f * densityScale
        iceCrestPaint.strokeWidth = 3.0f * densityScale

        // Градиенты покрывают всю возможную высоту экрана, включая засыпание при разблокировке
        smoothMainPaint.shader = LinearGradient(
            0f, 0f,
            0f, h,
            intArrayOf(
                0xFFFFFFFF.toInt(),
                0xFFF6FAFE.toInt(),
                0xFFE2EFFB.toInt(),
                0xFFC5DCF4.toInt()
            ),
            floatArrayOf(0f, 0.35f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )

        puffyBodyPaint.shader = LinearGradient(
            0f, 0f,
            0f, h,
            intArrayOf(
                0xFFFFFFFF.toInt(),
                0xFFF8FBFF.toInt(),
                0xFFE5F0FA.toInt(),
                0xFFD0E4F7.toInt()
            ),
            floatArrayOf(0f, 0.40f, 0.80f, 1.0f),
            Shader.TileMode.CLAMP
        )

        iceBodyPaint.shader = LinearGradient(
            0f, 0f,
            0f, h,
            intArrayOf(
                0xFFF0F9FF.toInt(),
                0xCCE0F2FE.toInt(),
                0xAA93C5FD.toInt(),
                0x993B82F6.toInt()
            ),
            floatArrayOf(0f, 0.25f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )

        if (prevDensities.isNotEmpty() && prevCols > 0 && prevRows > 0) {
            for (r in 0 until rows) {
                val oldR = (r.toFloat() / rows * prevRows).toInt().coerceIn(0, prevRows - 1)
                for (c in 0 until cols) {
                    val oldC = (c.toFloat() / cols * prevCols).toInt().coerceIn(0, prevCols - 1)
                    val oldIndex = oldR * prevCols + oldC
                    if (oldIndex in prevDensities.indices) {
                        densities[r * cols + c] = prevDensities[oldIndex]
                    }
                }
            }
            ensureBaseSnowbank()
        } else {
            populateBaseSnowbank()
        }

        gridBitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        srcRect.set(0, 0, cols, rows)
        dstRect.set(0, 0, w.toInt(), h.toInt())
    }

    fun setBaseSnowbankHeight(heightDp: Float) {
        val clamped = heightDp.coerceIn(
            SnowSettings.MIN_SNOWBANK_HEIGHT,
            SnowSettings.MAX_SNOWBANK_HEIGHT
        )
        if (baseSnowbankHeightDp != clamped) {
            baseSnowbankHeightDp = clamped
            if (initialOfflineSnowAmount <= 0.001f) {
                populateBaseSnowbank()
            } else {
                ensureBaseSnowbank()
            }
        }
    }

    fun getBaseSnowRows(c: Int): Int {
        if (cols <= 0 || cellH <= 0f) return 0
        val targetBaseRows = (baseSnowbankHeightDp * densityScale / cellH).coerceIn(1f, rows * 0.75f)
        val wave = sin(c * 0.18f) * 1.8f + cos(c * 0.09f) * 1.2f
        return (targetBaseRows + wave).coerceIn(1f, (rows - 2).toFloat()).toInt()
    }

    fun populateBaseSnowbank() {
        if (cols <= 0 || rows <= 0) return
        densities.fill(0f)
        for (c in 0 until cols) {
            val colBaseRows = getBaseSnowRows(c)
            for (h in 0 until colBaseRows) {
                val r = rows - 1 - h
                if (r >= 0) {
                    val idx = r * cols + c
                    densities[idx] = if (h == colBaseRows - 1) 0.65f else 1.0f
                }
            }
        }
    }

    fun ensureBaseSnowbank() {
        if (cols <= 0 || rows <= 0) return
        for (c in 0 until cols) {
            val colBaseRows = getBaseSnowRows(c)
            for (h in 0 until colBaseRows) {
                val r = rows - 1 - h
                if (r >= 0) {
                    val idx = r * cols + c
                    val targetD = if (h == colBaseRows - 1) 0.65f else 1.0f
                    if (densities[idx] < targetD) {
                        densities[idx] = targetD
                    }
                }
            }
        }
    }

    fun getSurfaceY(x: Float): Float {
        if (cols == 0 || rows == 0) return height
        val c = (x / cellW).toInt().coerceIn(0, cols - 1)

        for (r in 0 until rows) {
            val d = densities[r * cols + c]
            if (d > 0.06f) {
                return r * cellH + (1.0f - d) * cellH
            }
        }
        return height
    }

    fun getSnowHeight(x: Float): Float {
        return (height - getSurfaceY(x)).coerceAtLeast(0f)
    }

    fun addSnow(x: Float, flakeRadius: Float, accumulationMultiplier: Float = 1f) {
        if (cols == 0 || rows == 0) return
        val c = (x / cellW).toInt().coerceIn(0, cols - 1)

        var targetRow = rows - 1
        for (r in 0 until rows) {
            if (densities[r * cols + c] > 0.06f) {
                targetRow = r
                break
            }
        }

        if (!allowGrowthAboveBase) {
            val baseColHeight = getBaseSnowRows(c)
            val baseTopRow = rows - baseColHeight
            if (targetRow <= baseTopRow) {
                return
            }
        }

        if (densities[targetRow * cols + c] >= 0.92f && targetRow > 0) {
            targetRow--
        }

        val deposit = flakeRadius * 0.14f * accumulationMultiplier

        densities[targetRow * cols + c] =
            (densities[targetRow * cols + c] + deposit * 0.5f).coerceAtMost(1.0f)

        if (c > 0) {
            densities[targetRow * cols + (c - 1)] =
                (densities[targetRow * cols + (c - 1)] + deposit * 0.25f).coerceAtMost(1.0f)
        }
        if (c < cols - 1) {
            densities[targetRow * cols + (c + 1)] =
                (densities[targetRow * cols + (c + 1)] + deposit * 0.25f).coerceAtMost(1.0f)
        }
    }

    fun clearAlongSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float
    ) {
        if (cols == 0 || rows == 0) return

        val minX = minOf(x0, x1) - radius * 1.35f
        val maxX = maxOf(x0, x1) + radius * 1.35f
        val minY = minOf(y0, y1) - radius * 1.35f
        val maxY = maxOf(y0, y1) + radius * 1.35f

        val startCol = (minX / cellW).toInt().coerceIn(0, cols - 1)
        val endCol = (maxX / cellW).toInt().coerceIn(0, cols - 1)
        val startRow = (minY / cellH).toInt().coerceIn(0, rows - 1)
        val endRow = (maxY / cellH).toInt().coerceIn(0, rows - 1)

        for (c in startCol..endCol) {
            val cx = (c + 0.5f) * cellW
            for (r in startRow..endRow) {
                val cy = (r + 0.5f) * cellH

                val dist = distToSegment(cx, cy, x0, y0, x1, y1)
                val noise = sin(cx * 0.08f) * sin(cy * 0.08f) * 0.12f * radius
                val effectiveR = radius + noise

                val idx = r * cols + c
                val currentDensity = densities[idx]

                if (dist < effectiveR * 0.65f) {
                    densities[idx] = 0f
                } else if (dist < effectiveR) {
                    val factor = ((dist - effectiveR * 0.65f) / (effectiveR * 0.35f)).coerceIn(0f, 1f)
                    densities[idx] = currentDensity * factor * factor
                } else if (dist < effectiveR * 1.25f && currentDensity > 0.05f) {
                    val ridgeFactor = (1.0f - (dist - effectiveR) / (effectiveR * 0.25f)).coerceIn(0f, 1f)
                    densities[idx] = (currentDensity + 0.35f * ridgeFactor).coerceAtMost(1.0f)
                }
            }
        }
    }

    private fun distToSegment(
        px: Float,
        py: Float,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(px - x0, py - y0)
        val t = (((px - x0) * dx + (py - y0) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x0 + t * dx
        val projY = y0 + t * dy
        return hypot(px - projX, py - projY)
    }

    fun update(dtSec: Float) {
        if (cols == 0 || rows == 0) return

        for (c in 0 until cols) {
            for (r in rows - 1 downTo 1) {
                val currIdx = r * cols + c
                val aboveIdx = (r - 1) * cols + c
                val dAbove = densities[aboveIdx]
                if (densities[currIdx] < 1.0f && dAbove > 0f) {
                    val space = 1.0f - densities[currIdx]
                    val flow = minOf(space, dAbove)
                    densities[currIdx] += flow
                    densities[aboveIdx] -= flow
                }
            }
        }

        val slipRate = (dtSec * 3f).coerceIn(0f, 0.3f)
        for (c in 0 until cols) {
            for (r in rows - 2 downTo 0) {
                val idx = r * cols + c
                val d = densities[idx]
                if (d > 0.45f) {
                    if (c > 0 && densities[(r + 1) * cols + (c - 1)] < 0.7f) {
                        val slip = minOf(0.06f, d * slipRate)
                        densities[idx] -= slip
                        densities[(r + 1) * cols + (c - 1)] += slip
                    }
                    if (c < cols - 1 && densities[(r + 1) * cols + (c + 1)] < 0.7f) {
                        val slip = minOf(0.06f, d * slipRate)
                        densities[idx] -= slip
                        densities[(r + 1) * cols + (c + 1)] += slip
                    }
                }
            }
        }
    }

    /**
     * Отрисовывает сугроб в соответствии с выбранным стилем [style].
     */
    fun draw(canvas: Canvas, style: SnowbankStyle = SnowbankStyle.SMOOTH_HILLS) {
        when (style) {
            SnowbankStyle.CLASSIC -> drawClassic(canvas)
            SnowbankStyle.SMOOTH_HILLS -> drawSmoothHills(canvas)
            SnowbankStyle.PUFFY_CLOUDS -> drawPuffyClouds(canvas)
            SnowbankStyle.ICY_SHELF -> drawIcyShelf(canvas)
        }
    }

    private fun computeSmoothProfile(blurRadius: Int = 5) {
        for (c in 0 until cols) {
            var surfY = height
            for (r in 0 until rows) {
                val d = densities[r * cols + c]
                if (d > 0.05f) {
                    surfY = r * cellH + (1.0f - d) * cellH
                    break
                }
            }
            rawSurfaceY[c] = surfY
        }

        for (c in 0 until cols) {
            var sum = 0f
            var count = 0
            for (offset in -blurRadius..blurRadius) {
                val idx = (c + offset).coerceIn(0, cols - 1)
                val weight = blurRadius + 1 - kotlin.math.abs(offset)
                sum += rawSurfaceY[idx] * weight
                count += weight
            }
            tempSurfaceY[c] = sum / count
        }

        for (c in 0 until cols) {
            var sum = 0f
            var count = 0
            for (offset in -blurRadius..blurRadius) {
                val idx = (c + offset).coerceIn(0, cols - 1)
                val weight = blurRadius + 1 - kotlin.math.abs(offset)
                sum += tempSurfaceY[idx] * weight
                count += weight
            }
            smoothSurfaceY[c] = sum / count
        }
    }

    private fun drawSmoothHills(canvas: Canvas) {
        if (cols <= 1 || rows <= 0) return
        computeSmoothProfile(blurRadius = 6)

        backPath.reset()
        backPath.moveTo(0f, height)
        val backStartY = (smoothSurfaceY[0] - 5f * densityScale).coerceAtMost(height)
        backPath.lineTo(0f, backStartY)

        var prevBackX = 0f
        var prevBackY = backStartY
        for (c in 1 until cols) {
            val cx = (c + 0.5f) * cellW
            val wave = (sin(c * 0.14f) * 4f - 2f) * densityScale
            val cy = (smoothSurfaceY[c] + wave).coerceIn(0f, height)
            val midX = (prevBackX + cx) * 0.5f
            val midY = (prevBackY + cy) * 0.5f
            backPath.quadTo(prevBackX, prevBackY, midX, midY)
            prevBackX = cx
            prevBackY = cy
        }
        val backEndY = (smoothSurfaceY[cols - 1] - 5f * densityScale).coerceAtMost(height)
        backPath.lineTo(width, backEndY)
        backPath.lineTo(width, height)
        backPath.close()

        canvas.drawPath(backPath, smoothBackPaint)

        mainPath.reset()
        crestPath.reset()

        mainPath.moveTo(0f, height)
        val startY = smoothSurfaceY[0].coerceAtMost(height)
        mainPath.lineTo(0f, startY)
        crestPath.moveTo(0f, startY)

        var prevX = 0f
        var prevY = startY
        for (c in 1 until cols) {
            val cx = (c + 0.5f) * cellW
            val cy = smoothSurfaceY[c].coerceIn(0f, height)
            val midX = (prevX + cx) * 0.5f
            val midY = (prevY + cy) * 0.5f
            mainPath.quadTo(prevX, prevY, midX, midY)
            crestPath.quadTo(prevX, prevY, midX, midY)
            prevX = cx
            prevY = cy
        }
        val endY = smoothSurfaceY[cols - 1].coerceAtMost(height)
        mainPath.lineTo(width, endY)
        crestPath.lineTo(width, endY)

        mainPath.lineTo(width, height)
        mainPath.close()

        canvas.drawPath(mainPath, smoothMainPaint)
        canvas.drawPath(crestPath, smoothCrestPaint)
    }

    private fun drawPuffyClouds(canvas: Canvas) {
        if (cols <= 1 || rows <= 0) return
        computeSmoothProfile(blurRadius = 4)

        mainPath.reset()
        crestPath.reset()

        mainPath.moveTo(0f, height)
        val startY = smoothSurfaceY[0].coerceAtMost(height)
        mainPath.lineTo(0f, startY)
        crestPath.moveTo(0f, startY)

        val stepCols = (cols / 8).coerceIn(4, 9)
        var prevX = 0f
        var prevY = startY

        var c = stepCols
        while (c < cols) {
            val targetX = (c + 0.5f) * cellW
            val targetY = smoothSurfaceY[c].coerceIn(0f, height)

            val midX = (prevX + targetX) * 0.5f
            val moundPeakY = (minOf(prevY, targetY) - 5f * densityScale).coerceAtLeast(0f)

            mainPath.quadTo(midX, moundPeakY, targetX, targetY)
            crestPath.quadTo(midX, moundPeakY, targetX, targetY)

            prevX = targetX
            prevY = targetY
            c += stepCols
        }

        val endY = smoothSurfaceY[cols - 1].coerceAtMost(height)
        mainPath.lineTo(width, endY)
        crestPath.lineTo(width, endY)
        mainPath.lineTo(width, height)
        mainPath.close()

        canvas.drawPath(mainPath, puffyBodyPaint)

        var hCol = stepCols / 2
        while (hCol < cols) {
            val hx = (hCol + 0.5f) * cellW
            val hy = smoothSurfaceY[hCol] - 2f * densityScale
            val r = (stepCols * cellW * 0.55f).coerceAtLeast(15f)
            canvas.drawCircle(hx, hy + r * 0.4f, r * 0.7f, puffyHighlightPaint)
            hCol += stepCols
        }

        canvas.drawPath(crestPath, puffyRimPaint)
    }

    private fun drawIcyShelf(canvas: Canvas) {
        if (cols <= 1 || rows <= 0) return
        computeSmoothProfile(blurRadius = 3)

        backPath.reset()
        backPath.moveTo(0f, height)
        val backStartY = (smoothSurfaceY[0] - 6f * densityScale).coerceAtMost(height)
        backPath.lineTo(0f, backStartY)

        val facetStep = (cols / 12).coerceIn(3, 7)
        var px = 0f
        var py = backStartY
        var c = facetStep
        while (c < cols) {
            val cx = (c + 0.5f) * cellW
            val cy = (smoothSurfaceY[c] - 4f * densityScale).coerceIn(0f, height)
            backPath.lineTo(cx, cy)
            px = cx
            py = cy
            c += facetStep
        }
        backPath.lineTo(width, smoothSurfaceY[cols - 1] - 4f * densityScale)
        backPath.lineTo(width, height)
        backPath.close()
        canvas.drawPath(backPath, iceBackPaint)

        mainPath.reset()
        crestPath.reset()

        mainPath.moveTo(0f, height)
        val startY = smoothSurfaceY[0].coerceAtMost(height)
        mainPath.lineTo(0f, startY)
        crestPath.moveTo(0f, startY)

        px = 0f
        py = startY
        c = facetStep
        while (c < cols) {
            val cx = (c + 0.5f) * cellW
            val cy = smoothSurfaceY[c].coerceIn(0f, height)
            mainPath.lineTo(cx, cy)
            crestPath.lineTo(cx, cy)
            px = cx
            py = cy
            c += facetStep
        }
        val endY = smoothSurfaceY[cols - 1].coerceAtMost(height)
        mainPath.lineTo(width, endY)
        crestPath.lineTo(width, endY)
        mainPath.lineTo(width, height)
        mainPath.close()

        canvas.drawPath(mainPath, iceBodyPaint)
        canvas.drawPath(crestPath, iceCrestPaint)
    }

    fun drawClassic(canvas: Canvas) {
        val bmp = gridBitmap ?: return
        if (bmp.isRecycled || cols == 0 || rows == 0) return

        for (r in 0 until rows) {
            val rOffset = r * cols
            for (c in 0 until cols) {
                val cM2 = if (c >= 2) c - 2 else 0
                val cM1 = if (c >= 1) c - 1 else 0
                val cP1 = if (c < cols - 1) c + 1 else cols - 1
                val cP2 = if (c < cols - 2) c + 2 else cols - 1

                tempBlur[rOffset + c] =
                    densities[rOffset + cM2] * 0.1f +
                    densities[rOffset + cM1] * 0.2f +
                    densities[rOffset + c] * 0.4f +
                    densities[rOffset + cP1] * 0.2f +
                    densities[rOffset + cP2] * 0.1f
            }
        }

        for (c in 0 until cols) {
            for (r in 0 until rows) {
                val rM2 = if (r >= 2) r - 2 else 0
                val rM1 = if (r >= 1) r - 1 else 0
                val rP1 = if (r < rows - 1) r + 1 else rows - 1
                val rP2 = if (r < rows - 2) r + 2 else rows - 1

                visualDensities[r * cols + c] =
                    tempBlur[rM2 * cols + c] * 0.1f +
                    tempBlur[rM1 * cols + c] * 0.2f +
                    tempBlur[r * cols + c] * 0.4f +
                    tempBlur[rP1 * cols + c] * 0.2f +
                    tempBlur[rP2 * cols + c] * 0.1f
            }
        }

        for (c in 0 until cols) {
            var surfaceRow = rows
            for (r in 0 until rows) {
                if (visualDensities[r * cols + c] > 0.05f) {
                    surfaceRow = r
                    break
                }
            }

            for (r in 0 until rows) {
                val idx = r * cols + c
                val vd = visualDensities[idx]

                if (vd <= 0.02f) {
                    pixels[idx] = 0
                } else {
                    val normD = ((vd - 0.02f) / 0.65f).coerceIn(0f, 1f)
                    val smoothAlpha = normD * normD * (3f - 2f * normD)
                    val alpha = (smoothAlpha * 255f).toInt().coerceIn(0, 255)

                    val depth = (r - surfaceRow).coerceAtLeast(0)
                    val shadowRatio = (depth / 16f).coerceIn(0f, 1f)
                    val microWave = (sin(c * 0.2f + r * 0.12f) * 4f).toInt()

                    val red = (255 - (40 * shadowRatio) + microWave).toInt().coerceIn(200, 255)
                    val green = (255 - (24 * shadowRatio) + microWave).toInt().coerceIn(215, 255)
                    val blue = 255

                    pixels[idx] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
        }

        bmp.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
    }

    private var initialOfflineSnowAmount: Float = 0f

    /**
     * Заполнение экрана снегом после разблокировки экрана:
     * Формирует глубокий холмистый покров (от 45% до 82% высоты экрана),
     * который переводит оверлей в интерактивный режим для очистки пальцем.
     */
    fun accumulateOfflineSnow(elapsedSeconds: Float, rateMultiplier: Float = 1f) {
        if (cols <= 0 || rows <= 0) return

        // Базовый уровень засыпки: минимум 45% экрана, плавно растет пропорционально времени сна
        val minFraction = 0.45f
        val baseMinRows = rows * minFraction
        val growth = elapsedSeconds * 0.8f * rateMultiplier
        val totalBaseRows = (baseMinRows + growth).coerceIn(baseMinRows, rows.toFloat() * 0.82f)

        for (c in 0 until cols) {
            val wave = sin(c * 0.12f) * 6f + cos(c * 0.07f) * 4.5f + sin(c * 0.25f) * 3f
            val colHeight = (totalBaseRows + wave).coerceIn(4f, (rows - 2).toFloat()).toInt()

            for (h in 0 until colHeight) {
                val r = rows - 1 - h
                if (r >= 0) {
                    val idx = r * cols + c
                    if (h == colHeight - 1) {
                        densities[idx] = (densities[idx] + 0.75f).coerceIn(0f, 1f)
                    } else {
                        densities[idx] = 1.0f
                    }
                }
            }
        }

        initialOfflineSnowAmount = (getTotalSnow() - getBaseSnowAmount()).coerceAtLeast(1f)
    }

    fun getBaseSnowAmount(): Float {
        if (cols <= 0 || rows <= 0) return 0f
        var sum = 0f
        for (c in 0 until cols) {
            val colRows = getBaseSnowRows(c)
            sum += (colRows - 1).coerceAtLeast(0) * 1.0f + 0.65f
        }
        return sum
    }

    fun getTotalSnow(): Float {
        var sum = 0f
        for (i in densities.indices) {
            sum += densities[i]
        }
        return sum
    }

    fun getClearedRatio(): Float {
        if (initialOfflineSnowAmount <= 0.001f) {
            return 1.0f
        }
        val currentOffline = (getTotalSnow() - getBaseSnowAmount()).coerceAtLeast(0f)
        return ((initialOfflineSnowAmount - currentOffline) / initialOfflineSnowAmount).coerceIn(0f, 1f)
    }

    fun clearAllSnow() {
        populateBaseSnowbank()
        pixels.fill(0)
        initialOfflineSnowAmount = 0f
    }
}
