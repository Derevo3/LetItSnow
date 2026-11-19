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
 * с поддержкой двух режимов:
 * 1. Режим «Снег на горизонтально лежащем телефоне»:
 *    Равномерный естественный снежный покров по всей плоскости стекла с чистым следом от пальца,
 *    мягкими кромками, снежными валиками и микрокристаллической структурой.
 * 2. Обычный фоновый режим сугроба у нижнего края экрана с поддержкой 4 стилей:
 *    - [SnowbankStyle.SMOOTH_HILLS]: Шелковистые широкие природные холмы с мягкими тенями.
 *    - [SnowbankStyle.PUFFY_CLOUDS]: Пышные округлые облачные шапки свежего снега.
 *    - [SnowbankStyle.ICY_SHELF]: Кристальная светящаяся ледяная полка со слоистой каймой.
 *    - [SnowbankStyle.CLASSIC]: Детализированный сглаженный сугроб с мягким зимним градиентом.
 * При выборе темы «Алый» снег и сугробы автоматически окрашиваются в кроваво-красные тона.
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
     * Активен ли режим снежного покрова на горизонтально лежащем экране.
     */
    var isSurfaceCoverActive: Boolean = false
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

    // Физическая сетка плотности сугроба внизу экрана (0.0f - чисто, 1.0f - плотный снег)
    private var densities = FloatArray(0)

    // Сетка плотности снега на стекле горизонтального телефона
    private var surfaceDensities = FloatArray(0)

    // Маска очищенных пальцем участков (true = очищено, снег не восстанавливается)
    private var surfaceClearedMask = BooleanArray(0)

    private var initialSurfaceSnowAmount: Float = 0f
    private var initialOfflineSnowAmount: Float = 0f

    // Буферы для растровой отрисовки
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

    // Предварительно рассчитанные таблицы шума (LUT) для исключения вызова sin/cos в каждом кадре
    private var surfaceNoise = FloatArray(0)
    private var classicNoise = FloatArray(0)

    // Буферы высот для векторных режимов сугроба и кэш поверхности для быстрого поиска коллизий
    private var rawSurfaceY = FloatArray(0)
    private var smoothSurfaceY = FloatArray(0)
    private var tempSurfaceY = FloatArray(0)
    private var columnSurfaceY = FloatArray(0)

    /**
     * Минимальный Y поверхности сугроба по всем колонкам (наивысшая точка сугроба в системе координат Canvas).
     * Служит глобальной верхней границей (AABB) для мгновенного отсечения 95%+ снежинок за O(1).
     */
    var minSurfaceY: Float = 0f
        private set

    // Обратная ширина ячейки для замены деления на умножение при поиске колонки коллизии
    private var invCellW: Float = 0f

    // Флаги кэширования отрисовки (Dirty Tracking)
    private var isClassicDirty: Boolean = true
    private var lastClassicBloody: Boolean? = null

    private var isSurfaceDirty: Boolean = true
    private var lastSurfaceBloody: Boolean? = null

    private var isVectorDirty: Boolean = true
    private var lastVectorStyle: SnowbankStyle? = null
    private var lastVectorBloody: Boolean? = null

    fun markDirty() {
        isClassicDirty = true
        isSurfaceDirty = true
        isVectorDirty = true
    }

    // Преаллоцированные векторные пути
    private val mainPath = Path()
    private val backPath = Path()
    private val crestPath = Path()

    // Шейдеры градиентов для обычного и алого/кровавого сугроба
    private var smoothMainShaderNormal: Shader? = null
    private var smoothMainShaderBloody: Shader? = null

    private var puffyBodyShaderNormal: Shader? = null
    private var puffyBodyShaderBloody: Shader? = null

    private var iceBodyShaderNormal: Shader? = null
    private var iceBodyShaderBloody: Shader? = null

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
    private val puffyBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x40A0C8F0.toInt()
    }
    private val puffyRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
        invCellW = if (cellW > 0f) 1.0f / cellW else 0f
        minSurfaceY = h

        val total = cols * rows

        densities = FloatArray(total)
        surfaceDensities = FloatArray(total)
        surfaceClearedMask = BooleanArray(total)
        tempBlur = FloatArray(total)
        visualDensities = FloatArray(total)
        pixels = IntArray(total)

        surfaceNoise = FloatArray(total)
        classicNoise = FloatArray(total)

        rawSurfaceY = FloatArray(cols)
        smoothSurfaceY = FloatArray(cols)
        tempSurfaceY = FloatArray(cols)
        columnSurfaceY = FloatArray(cols)

        if (prevDensities.isNotEmpty() && prevCols > 0 && prevRows > 0) {
            val scaleR = prevRows.toFloat() / rows.toFloat()
            val scaleC = prevCols.toFloat() / cols.toFloat()
            for (r in 0 until rows) {
                val pr = (r * scaleR).toInt().coerceIn(0, prevRows - 1)
                val rOffset = r * cols
                val prOffset = pr * prevCols
                for (c in 0 until cols) {
                    val pc = (c * scaleC).toInt().coerceIn(0, prevCols - 1)
                    val oldVal = prevDensities[prOffset + pc]
                    densities[rOffset + c] = oldVal
                    surfaceDensities[rOffset + c] = oldVal
                }
            }
            markDirty()
        }

        // Предварительный расчёт шума (LUT) для исключения вызова sin/cos на каждом кадре
        for (r in 0 until rows) {
            val rOffset = r * cols
            val cy = r.toFloat()
            for (c in 0 until cols) {
                val cx = c.toFloat()
                val idx = rOffset + c

                val micro1 = sin(cx * 0.38f + cy * 0.26f) * cos(cx * 0.18f - cy * 0.32f)
                val micro2 = sin(cx * 0.82f - cy * 0.62f) * 0.45f
                surfaceNoise[idx] = (micro1 + micro2) * 5.0f

                classicNoise[idx] = sin(cx * 0.35f + cy * 0.22f) * cos(cx * 0.18f - cy * 0.28f) * 3.5f
            }
        }

        smoothCrestPaint.strokeWidth = 2.4f * densityScale
        puffyRimPaint.strokeWidth = 2.4f * densityScale
        iceCrestPaint.strokeWidth = 3.0f * densityScale

        smoothMainShaderNormal = LinearGradient(
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
        smoothMainShaderBloody = LinearGradient(
            0f, 0f,
            0f, h,
            intArrayOf(
                0xFFFF6B81.toInt(),
                0xFFEF4444.toInt(),
                0xFF991B1B.toInt(),
                0xFF450A0A.toInt()
            ),
            floatArrayOf(0f, 0.30f, 0.70f, 1.0f),
            Shader.TileMode.CLAMP
        )
        smoothMainPaint.shader = smoothMainShaderNormal

        puffyBodyShaderNormal = LinearGradient(
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
        puffyBodyShaderBloody = LinearGradient(
            0f, 0f,
            0f, h,
            intArrayOf(
                0xFFFFA4B3.toInt(),
                0xFFE11D48.toInt(),
                0xFF881337.toInt(),
                0xFF4C0519.toInt()
            ),
            floatArrayOf(0f, 0.35f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        puffyBodyPaint.shader = puffyBodyShaderNormal

        iceBodyShaderNormal = LinearGradient(
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
        iceBodyShaderBloody = LinearGradient(
            0f, 0f,
            0f, h,
            intArrayOf(
                0xFFFF859B.toInt(),
                0xCCE11D48.toInt(),
                0xAA991B1B.toInt(),
                0x995A0B17.toInt()
            ),
            floatArrayOf(0f, 0.25f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
        iceBodyPaint.shader = iceBodyShaderNormal

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

        if (gridBitmap == null || gridBitmap?.width != cols || gridBitmap?.height != rows || gridBitmap?.isRecycled == true) {
            gridBitmap?.recycle()
            gridBitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        }
        srcRect.set(0, 0, cols, rows)
        dstRect.set(0, 0, w.toInt(), h.toInt())

        updateColumnSurfaceY()
        markDirty()
    }

    fun setBaseSnowbankHeight(heightDp: Float) {
        val clamped = heightDp.coerceIn(
            SnowSettings.MIN_SNOWBANK_HEIGHT,
            SnowSettings.MAX_SNOWBANK_HEIGHT
        )
        if (baseSnowbankHeightDp != clamped) {
            baseSnowbankHeightDp = clamped
            if (initialOfflineSnowAmount <= 0.001f && !isSurfaceCoverActive) {
                populateBaseSnowbank()
            } else {
                ensureBaseSnowbank()
            }
            updateColumnSurfaceY()
            markDirty()
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
        updateColumnSurfaceY()
        markDirty()
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
        updateColumnSurfaceY()
        markDirty()
    }

    /**
     * Быстрое обновление закэшированного уровня поверхности для всех столбцов
     * и перерасчет глобальной верхней границы сугроба [minSurfaceY].
     */
    fun updateColumnSurfaceY() {
        if (cols <= 0 || rows <= 0) {
            minSurfaceY = height
            return
        }
        var minVal = height
        for (c in 0 until cols) {
            var surfY = height
            for (r in 0 until rows) {
                val d = densities[r * cols + c]
                if (d > 0.05f) {
                    surfY = r * cellH + (1.0f - d) * cellH
                    break
                }
            }
            columnSurfaceY[c] = surfY
            if (surfY < minVal) {
                minVal = surfY
            }
        }
        minSurfaceY = minVal
    }

    /**
     * Точечное обновление закэшированного уровня поверхности для одного столбца.
     */
    private fun updateSurfaceForColumn(c: Int) {
        if (c !in 0 until cols || rows <= 0) return
        var surfY = height
        for (r in 0 until rows) {
            val d = densities[r * cols + c]
            if (d > 0.05f) {
                surfY = r * cellH + (1.0f - d) * cellH
                break
            }
        }
        columnSurfaceY[c] = surfY
        if (surfY < minSurfaceY) {
            minSurfaceY = surfY
        }
    }

    /**
     * Мгновенный возврат высоты поверхности сугроба за O(1) без линейного перебора сетки
     * и без дорогой операции деления float.
     */
    fun getSurfaceY(x: Float): Float {
        if (isSurfaceCoverActive) return 0f
        if (cols == 0 || rows == 0) return height
        val c = (x * invCellW).toInt().coerceIn(0, cols - 1)
        return columnSurfaceY[c]
    }

    fun getSnowHeight(x: Float): Float {
        if (isSurfaceCoverActive) return height
        return (height - getSurfaceY(x)).coerceAtLeast(0f)
    }

    fun addSnow(x: Float, flakeRadius: Float, accumulationMultiplier: Float = 1f) {
        if (isSurfaceCoverActive) return
        if (cols == 0 || rows == 0) return
        val c = (x * invCellW).toInt().coerceIn(0, cols - 1)

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

        updateSurfaceForColumn(c)
        if (c > 0) updateSurfaceForColumn(c - 1)
        if (c < cols - 1) updateSurfaceForColumn(c + 1)
        markDirty()
    }

    /**
     * Очистка снега вдоль отрезка движения пальца.
     * В режиме «Снег на горизонтально лежащем телефоне» очищает покров на стекле,
     * а в обычном режиме — насыпь сугроба внизу экрана.
     */
    fun clearAlongSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float
    ) {
        if (cols == 0 || rows == 0) return

        if (isSurfaceCoverActive) {
            clearSurfaceAlongSegment(x0, y0, x1, y1, radius)
        } else {
            clearBankAlongSegment(x0, y0, x1, y1, radius)
        }
    }

    private fun clearSurfaceAlongSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float
    ) {
        val minX = minOf(x0, x1) - radius * 1.35f
        val maxX = maxOf(x0, x1) + radius * 1.35f
        val minY = minOf(y0, y1) - radius * 1.35f
        val maxY = maxOf(y0, y1) + radius * 1.35f

        val startCol = (minX * invCellW).toInt().coerceIn(0, cols - 1)
        val endCol = (maxX * invCellW).toInt().coerceIn(0, cols - 1)
        val startRow = (minY / cellH).toInt().coerceIn(0, rows - 1)
        val endRow = (maxY / cellH).toInt().coerceIn(0, rows - 1)

        for (c in startCol..endCol) {
            val cx = (c + 0.5f) * cellW
            for (r in startRow..endRow) {
                val cy = (r + 0.5f) * cellH

                val dist = distToSegment(cx, cy, x0, y0, x1, y1)
                // Органический шум кромки следа пальца (неровность снежных зерен)
                val noise = (sin(cx * 0.14f + cy * 0.10f) * cos(cx * 0.08f - cy * 0.12f)) * 0.14f * radius
                val effectiveR = radius + noise
                val idx = r * cols + c

                if (dist < effectiveR * 0.65f) {
                    // Центральная часть следа пальца: 100% чистое стекло
                    surfaceDensities[idx] = 0f
                    surfaceClearedMask[idx] = true
                } else if (dist < effectiveR) {
                    // Мягкая растушевка кромки (сглаживание Эрмита)
                    surfaceClearedMask[idx] = true
                    val factor = ((dist - effectiveR * 0.65f) / (effectiveR * 0.35f)).coerceIn(0f, 1f)
                    val smoothFactor = factor * factor * (3f - 2f * factor)
                    surfaceDensities[idx] = minOf(surfaceDensities[idx], smoothFactor)
                } else if (dist < effectiveR * 1.25f && !surfaceClearedMask[idx] && surfaceDensities[idx] > 0.05f) {
                    // Снежный валик по бокам от движения пальца
                    val ridgeFactor = (1.0f - (dist - effectiveR) / (effectiveR * 0.25f)).coerceIn(0f, 1f)
                    surfaceDensities[idx] = (surfaceDensities[idx] + 0.22f * ridgeFactor * ridgeFactor).coerceAtMost(1.0f)
                }
            }
        }
        markDirty()
    }

    private fun clearBankAlongSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float
    ) {
        val minX = minOf(x0, x1) - radius * 1.35f
        val maxX = maxOf(x0, x1) + radius * 1.35f
        val minY = minOf(y0, y1) - radius * 1.35f
        val maxY = maxOf(y0, y1) + radius * 1.35f

        val startCol = (minX * invCellW).toInt().coerceIn(0, cols - 1)
        val endCol = (maxX * invCellW).toInt().coerceIn(0, cols - 1)
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
            updateSurfaceForColumn(c)
        }
        var recalculatedMin = height
        for (c in 0 until cols) {
            if (columnSurfaceY[c] < recalculatedMin) {
                recalculatedMin = columnSurfaceY[c]
            }
        }
        minSurfaceY = recalculatedMin
        markDirty()
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

    fun update(dtSec: Float, rateMultiplier: Float = 1.0f) {
        if (cols == 0 || rows == 0) return

        if (isSurfaceCoverActive) {
            // В режиме «Снег на горизонтально лежащем телефоне»:
            // Снежный покров плавно и естественно уплотняется на нетронутых участках,
            // но очищенные пальцем следы сохраняются навсегда и не восстанавливаются.
            val growth = 0.025f * dtSec * rateMultiplier
            var changed = false
            for (i in surfaceDensities.indices) {
                if (!surfaceClearedMask[i] && surfaceDensities[i] < 1.0f) {
                    surfaceDensities[i] = minOf(1.0f, surfaceDensities[i] + growth)
                    changed = true
                } else if (surfaceClearedMask[i] && surfaceDensities[i] != 0f) {
                    surfaceDensities[i] = 0f
                    changed = true
                }
            }
            if (changed) {
                markDirty()
            }
            return
        }

        var movedSnow = false

        // В обычном режиме: физика гравитации и осыпания сугроба у нижнего края
        for (c in 0 until cols) {
            for (r in rows - 1 downTo 1) {
                val currIdx = r * cols + c
                val aboveIdx = (r - 1) * cols + c
                val dAbove = densities[aboveIdx]
                if (densities[currIdx] < 1.0f && dAbove > 0f) {
                    val space = 1.0f - densities[currIdx]
                    val flow = minOf(space, dAbove)
                    if (flow > 0.001f) {
                        densities[currIdx] += flow
                        densities[aboveIdx] -= flow
                        movedSnow = true
                    }
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
                        if (slip > 0.001f) {
                            densities[idx] -= slip
                            densities[(r + 1) * cols + (c - 1)] += slip
                            movedSnow = true
                        }
                    }
                    if (c < cols - 1 && densities[(r + 1) * cols + (c + 1)] < 0.7f) {
                        val slip = minOf(0.06f, d * slipRate)
                        if (slip > 0.001f) {
                            densities[idx] -= slip
                            densities[(r + 1) * cols + (c + 1)] += slip
                            movedSnow = true
                        }
                    }
                }
            }
        }

        if (movedSnow) {
            updateColumnSurfaceY()
            markDirty()
        }
    }

    /**
     * Отрисовывает накопившийся снег:
     * - При активном [isSurfaceCoverActive]: снежный покров на горизонтально лежащем стекле.
     * - Иначе: сугроб у нижнего края в соответствии со стилем [style] и цветом [isBloody].
     */
    fun draw(canvas: Canvas, style: SnowbankStyle = SnowbankStyle.SMOOTH_HILLS, isBloody: Boolean = false) {
        if (isSurfaceCoverActive) {
            drawSurfaceSnowCover(canvas, isBloody)
            return
        }

        when (style) {
            SnowbankStyle.CLASSIC -> drawClassic(canvas, isBloody)
            SnowbankStyle.SMOOTH_HILLS -> drawSmoothHills(canvas, isBloody)
            SnowbankStyle.PUFFY_CLOUDS -> drawPuffyClouds(canvas, isBloody)
            SnowbankStyle.ICY_SHELF -> drawIcyShelf(canvas, isBloody)
        }
    }

    /**
     * Отрисовка снежного покрова на горизонтально лежащем стекле телефона.
     * Использует кэширование растра и предрассчитанный шум для достижения максимального FPS.
     */
    private fun drawSurfaceSnowCover(canvas: Canvas, isBloody: Boolean) {
        val bmp = gridBitmap ?: return
        if (bmp.isRecycled || cols == 0 || rows == 0) return

        if (isSurfaceDirty || lastSurfaceBloody != isBloody) {
            // 1. Быстрое 3-точечное сглаживание по горизонтали
            for (r in 0 until rows) {
                val rOffset = r * cols
                for (c in 0 until cols) {
                    val cM1 = if (c >= 1) c - 1 else 0
                    val cP1 = if (c < cols - 1) c + 1 else cols - 1
                    tempBlur[rOffset + c] =
                        surfaceDensities[rOffset + cM1] * 0.25f +
                        surfaceDensities[rOffset + c]   * 0.50f +
                        surfaceDensities[rOffset + cP1] * 0.25f
                }
            }

            // 2. Быстрое 3-точечное сглаживание по вертикали
            for (c in 0 until cols) {
                for (r in 0 until rows) {
                    val rM1 = if (r >= 1) r - 1 else 0
                    val rP1 = if (r < rows - 1) r + 1 else rows - 1
                    val idx = r * cols + c
                    val blurred =
                        tempBlur[rM1 * cols + c] * 0.25f +
                        tempBlur[idx]            * 0.50f +
                        tempBlur[rP1 * cols + c] * 0.25f

                    // Гарантируем абсолютную чистоту внутри очищенного следа
                    visualDensities[idx] = if (surfaceClearedMask[idx]) {
                        minOf(blurred, surfaceDensities[idx])
                    } else {
                        blurred
                    }
                }
            }

            // 3. Формирование пикселей снежного покрова с использованием предрассчитанного шума
            for (r in 0 until rows) {
                val rOffset = r * cols
                for (c in 0 until cols) {
                    val idx = rOffset + c
                    val vd = visualDensities[idx]

                    if (vd <= 0.012f) {
                        pixels[idx] = 0 // Полная прозрачность (открытый экран под стеклом)
                    } else {
                        val normD = ((vd - 0.012f) / 0.40f).coerceIn(0f, 1f)
                        val smoothAlpha = normD * normD * (3f - 2f * normD)
                        val alpha = (smoothAlpha * 248f).toInt().coerceIn(0, 255)

                        val sparkle = surfaceNoise[idx]
                        val depth = (vd * 16f).coerceIn(0f, 16f)

                        val red: Int
                        val green: Int
                        val blue: Int

                        if (isBloody) {
                            red = (248 - depth * 1.8f + sparkle).toInt().coerceIn(160, 255)
                            green = (38 - depth * 0.8f + sparkle * 0.3f).toInt().coerceIn(12, 65)
                            blue = (50 - depth * 0.8f + sparkle * 0.3f).toInt().coerceIn(16, 85)
                        } else {
                            red = (255 - depth * 1.5f + sparkle).toInt().coerceIn(215, 255)
                            green = (255 - depth * 0.8f + sparkle).toInt().coerceIn(230, 255)
                            blue = 255
                        }

                        pixels[idx] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                    }
                }
            }

            bmp.setPixels(pixels, 0, cols, 0, 0, cols, rows)
            isSurfaceDirty = false
            lastSurfaceBloody = isBloody
        }

        canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
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

    private fun drawSmoothHills(canvas: Canvas, isBloody: Boolean) {
        if (cols <= 1 || rows <= 0) return

        if (isVectorDirty || lastVectorStyle != SnowbankStyle.SMOOTH_HILLS || lastVectorBloody != isBloody) {
            computeSmoothProfile(blurRadius = 6)

            if (isBloody) {
                smoothMainPaint.shader = smoothMainShaderBloody
                smoothBackPaint.color = 0x667F1D1D.toInt()
                smoothCrestPaint.color = 0xF5FF6B81.toInt()
            } else {
                smoothMainPaint.shader = smoothMainShaderNormal
                smoothBackPaint.color = 0x48A0C8F0.toInt()
                smoothCrestPaint.color = 0xF5FFFFFF.toInt()
            }

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

            isVectorDirty = false
            lastVectorStyle = SnowbankStyle.SMOOTH_HILLS
            lastVectorBloody = isBloody
        }

        canvas.drawPath(backPath, smoothBackPaint)
        canvas.drawPath(mainPath, smoothMainPaint)
        canvas.drawPath(crestPath, smoothCrestPaint)
    }

    private fun drawPuffyClouds(canvas: Canvas, isBloody: Boolean) {
        if (cols <= 1 || rows <= 0) return

        if (isVectorDirty || lastVectorStyle != SnowbankStyle.PUFFY_CLOUDS || lastVectorBloody != isBloody) {
            computeSmoothProfile(blurRadius = 4)

            if (isBloody) {
                puffyBodyPaint.shader = puffyBodyShaderBloody
                puffyBackPaint.color = 0x557F1D1D.toInt()
                puffyRimPaint.color = 0xEEFFA4B3.toInt()
            } else {
                puffyBodyPaint.shader = puffyBodyShaderNormal
                puffyBackPaint.color = 0x40A0C8F0.toInt()
                puffyRimPaint.color = 0xEEFFFFFF.toInt()
            }

            backPath.reset()
            backPath.moveTo(0f, height)
            val backStartY = (smoothSurfaceY[0] - 4f * densityScale).coerceAtMost(height)
            backPath.lineTo(0f, backStartY)

            val stepCols = (cols / 7).coerceIn(5, 10)
            var prevX = 0f
            var prevY = backStartY

            var c = stepCols / 2
            while (c < cols) {
                val targetX = (c + 0.5f) * cellW
                val targetY = (smoothSurfaceY[c] - 4f * densityScale).coerceIn(0f, height)
                val midX = (prevX + targetX) * 0.5f
                val crownY = (minOf(prevY, targetY) - 6f * densityScale).coerceAtLeast(0f)

                backPath.quadTo(midX, crownY, targetX, targetY)
                prevX = targetX
                prevY = targetY
                c += stepCols
            }
            val backEndY = (smoothSurfaceY[cols - 1] - 4f * densityScale).coerceAtMost(height)
            backPath.lineTo(width, backEndY)
            backPath.lineTo(width, height)
            backPath.close()

            mainPath.reset()
            crestPath.reset()

            mainPath.moveTo(0f, height)
            val startY = smoothSurfaceY[0].coerceAtMost(height)
            mainPath.lineTo(0f, startY)
            crestPath.moveTo(0f, startY)

            prevX = 0f
            prevY = startY

            c = stepCols
            while (c < cols) {
                val targetX = (c + 0.5f) * cellW
                val targetY = smoothSurfaceY[c].coerceIn(0f, height)

                val midX = (prevX + targetX) * 0.5f
                val crownY = (minOf(prevY, targetY) - 5f * densityScale).coerceAtLeast(0f)

                mainPath.quadTo(midX, crownY, targetX, targetY)
                crestPath.quadTo(midX, crownY, targetX, targetY)

                prevX = targetX
                prevY = targetY
                c += stepCols
            }

            val endY = smoothSurfaceY[cols - 1].coerceAtMost(height)
            mainPath.lineTo(width, endY)
            crestPath.lineTo(width, endY)

            mainPath.lineTo(width, height)
            mainPath.close()

            isVectorDirty = false
            lastVectorStyle = SnowbankStyle.PUFFY_CLOUDS
            lastVectorBloody = isBloody
        }

        canvas.drawPath(backPath, puffyBackPaint)
        canvas.drawPath(mainPath, puffyBodyPaint)
        canvas.drawPath(crestPath, puffyRimPaint)
    }

    private fun drawIcyShelf(canvas: Canvas, isBloody: Boolean) {
        if (cols <= 1 || rows <= 0) return

        if (isVectorDirty || lastVectorStyle != SnowbankStyle.ICY_SHELF || lastVectorBloody != isBloody) {
            computeSmoothProfile(blurRadius = 3)

            if (isBloody) {
                iceBodyPaint.shader = iceBodyShaderBloody
                iceBackPaint.color = 0x55881337.toInt()
                iceCrestPaint.color = 0xFFFF859B.toInt()
            } else {
                iceBodyPaint.shader = iceBodyShaderNormal
                iceBackPaint.color = 0x3360A5FA.toInt()
                iceCrestPaint.color = 0xFFE0F2FE.toInt()
            }

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

            isVectorDirty = false
            lastVectorStyle = SnowbankStyle.ICY_SHELF
            lastVectorBloody = isBloody
        }

        canvas.drawPath(backPath, iceBackPaint)
        canvas.drawPath(mainPath, iceBodyPaint)
        canvas.drawPath(crestPath, iceCrestPaint)
    }

    /**
     * СТИЛЬ «КЛАССИКА»: Детализированный сглаженный сугроб у нижнего края экрана.
     * Оптимизирован с кэшированием текстуры и предрассчитанным шумом.
     */
    fun drawClassic(canvas: Canvas, isBloody: Boolean = false) {
        val bmp = gridBitmap ?: return
        if (bmp.isRecycled || cols == 0 || rows == 0) return

        if (isClassicDirty || lastClassicBloody != isBloody) {
            // 1. Сглаживание по горизонтали (7-точечный Гауссов фильтр)
            for (r in 0 until rows) {
                val rOffset = r * cols
                for (c in 0 until cols) {
                    val cM3 = if (c >= 3) c - 3 else 0
                    val cM2 = if (c >= 2) c - 2 else 0
                    val cM1 = if (c >= 1) c - 1 else 0
                    val cP1 = if (c < cols - 1) c + 1 else cols - 1
                    val cP2 = if (c < cols - 2) c + 2 else cols - 1
                    val cP3 = if (c < cols - 3) c + 3 else cols - 1

                    tempBlur[rOffset + c] =
                        densities[rOffset + cM3] * 0.05f +
                        densities[rOffset + cM2] * 0.11f +
                        densities[rOffset + cM1] * 0.22f +
                        densities[rOffset + c]   * 0.24f +
                        densities[rOffset + cP1] * 0.22f +
                        densities[rOffset + cP2] * 0.11f +
                        densities[rOffset + cP3] * 0.05f
                }
            }

            // 2. Сглаживание по вертикали (7-точечный Гауссов фильтр)
            for (c in 0 until cols) {
                for (r in 0 until rows) {
                    val rM3 = if (r >= 3) r - 3 else 0
                    val rM2 = if (r >= 2) r - 2 else 0
                    val rM1 = if (r >= 1) r - 1 else 0
                    val rP1 = if (r < rows - 1) r + 1 else rows - 1
                    val rP2 = if (r < rows - 2) r + 2 else rows - 1
                    val rP3 = if (r < rows - 3) r + 3 else rows - 1

                    visualDensities[r * cols + c] =
                        tempBlur[rM3 * cols + c] * 0.05f +
                        tempBlur[rM2 * cols + c] * 0.11f +
                        tempBlur[rM1 * cols + c] * 0.22f +
                        tempBlur[r   * cols + c] * 0.24f +
                        tempBlur[rP1 * cols + c] * 0.22f +
                        tempBlur[rP2 * cols + c] * 0.11f +
                        tempBlur[rP3 * cols + c] * 0.05f
                }
            }

            // 3. Вычисление непрерывного уровня поверхности для каждого столбца
            for (c in 0 until cols) {
                var surfaceRowF = rows.toFloat()
                for (r in 0 until rows) {
                    val vd = visualDensities[r * cols + c]
                    if (vd > 0.03f) {
                        val prevVd = if (r > 0) visualDensities[(r - 1) * cols + c] else 0f
                        val delta = vd - prevVd
                        val frac = if (delta > 0.001f) ((0.03f - prevVd) / delta).coerceIn(0f, 1f) else 0f
                        surfaceRowF = (r - 1) + frac
                        break
                    }
                }
                rawSurfaceY[c] = surfaceRowF
            }

            for (c in 0 until cols) {
                val cLeft = (c - 1).coerceAtLeast(0)
                val cRight = (c + 1).coerceAtMost(cols - 1)
                smoothSurfaceY[c] = rawSurfaceY[cLeft] * 0.25f + rawSurfaceY[c] * 0.50f + rawSurfaceY[cRight] * 0.25f
            }

            // 4. Отрисовка детализированной текстуры с использованием предрассчитанного шума
            for (c in 0 until cols) {
                val surfF = smoothSurfaceY[c]
                for (r in 0 until rows) {
                    val idx = r * cols + c
                    val vd = visualDensities[idx]

                    if (vd <= 0.012f) {
                        pixels[idx] = 0
                    } else {
                        val normD = ((vd - 0.012f) / 0.42f).coerceIn(0f, 1f)
                        val smoothAlpha = normD * normD * (3f - 2f * normD)
                        val alpha = (smoothAlpha * 255f).toInt().coerceIn(0, 255)

                        val depth = (r - surfF).coerceAtLeast(0f)
                        val shadowRatio = (depth / 16f).coerceIn(0f, 1f)

                        val crestHighlight = if (depth < 2.2f) (1.0f - depth / 2.2f) * 16f else 0f
                        val microDetail = classicNoise[idx]

                        val red: Int
                        val green: Int
                        val blue: Int

                        if (isBloody) {
                            red = (245 - (80 * shadowRatio) + microDetail + crestHighlight).toInt().coerceIn(120, 255)
                            green = (45 - (30 * shadowRatio) + microDetail * 0.5f).toInt().coerceIn(10, 80)
                            blue = (55 - (35 * shadowRatio) + microDetail * 0.5f).toInt().coerceIn(15, 95)
                        } else {
                            red = (255 - (36 * shadowRatio) + microDetail + crestHighlight).toInt().coerceIn(195, 255)
                            green = (255 - (20 * shadowRatio) + microDetail + crestHighlight).toInt().coerceIn(215, 255)
                            blue = 255
                        }

                        pixels[idx] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                    }
                }
            }

            bmp.setPixels(pixels, 0, cols, 0, 0, cols, rows)
            isClassicDirty = false
            lastClassicBloody = isBloody
        }

        canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
    }

    /**
     * Заполнение экрана снегом:
     * Равномерно и естественно покрывает всю площадь экрана слоем снега
     * с микрорельефом, естественной текстурой и кристаллическими вариациями.
     */
    fun accumulateOfflineSnow(
        elapsedSeconds: Float = 0f,
        rateMultiplier: Float = 1f,
        amountRatio: Float = 0.15f
    ) {
        if (cols <= 0 || rows <= 0) return

        isSurfaceCoverActive = true
        surfaceClearedMask.fill(false)

        val targetRatio = amountRatio.coerceIn(0.05f, 1.0f)
        val baseDensity = (targetRatio * (1.0f + (elapsedSeconds * 0.005f * rateMultiplier))).coerceIn(targetRatio * 0.85f, 1.0f)

        for (r in 0 until rows) {
            val rOffset = r * cols
            for (c in 0 until cols) {
                val idx = rOffset + c
                val wave = (sin(c * 0.14f + r * 0.08f) * 0.05f +
                           cos(c * 0.07f - r * 0.12f) * 0.04f +
                           sin(c * 0.28f) * cos(r * 0.20f) * 0.02f) * (targetRatio.coerceAtMost(0.5f) / 0.5f)
                surfaceDensities[idx] = (baseDensity + wave).coerceIn(0.02f, 1.0f)
            }
        }

        initialSurfaceSnowAmount = getSurfaceSnowTotal().coerceAtLeast(1f)
        markDirty()
    }

    private fun getSurfaceSnowTotal(): Float {
        var sum = 0f
        for (i in surfaceDensities.indices) {
            sum += surfaceDensities[i]
        }
        return sum
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
        if (isSurfaceCoverActive) {
            if (initialSurfaceSnowAmount <= 0.001f) return 1.0f
            val current = getSurfaceSnowTotal()
            return ((initialSurfaceSnowAmount - current) / initialSurfaceSnowAmount).coerceIn(0f, 1f)
        }

        if (initialOfflineSnowAmount <= 0.001f) {
            return 1.0f
        }
        val currentOffline = (getTotalSnow() - getBaseSnowAmount()).coerceAtLeast(0f)
        return ((initialOfflineSnowAmount - currentOffline) / initialOfflineSnowAmount).coerceIn(0f, 1f)
    }

    fun clearAllSnow() {
        isSurfaceCoverActive = false
        surfaceClearedMask.fill(false)
        surfaceDensities.fill(0f)
        populateBaseSnowbank()
        pixels.fill(0)
        initialOfflineSnowAmount = 0f
        initialSurfaceSnowAmount = 0f
        markDirty()
    }
}
