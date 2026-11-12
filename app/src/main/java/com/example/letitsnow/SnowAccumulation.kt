package com.example.letitsnow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.hypot

/**
 * Двумерная физическая модель накопления снега (2D Snow Density Grid).
 * Снег накапливается снизу вверх по всей высоте экрана без искусственных ограничений.
 * Падающие снежинки физически взаимодействуют с поверхностью накопленного снега.
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

    // Двумерная сетка плотности снега (0.0f - чисто, 1.0f - плотный снег)
    private var densities = FloatArray(0)
    private var pixels = IntArray(0)

    private var gridBitmap: Bitmap? = null
    private val srcRect = Rect()
    private val dstRect = Rect()

    // Кисть с билинейной GPU-фильтрацией для мягких естественных границ снега
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }

    /**
     * Инициализация или пересчет сетки при изменении размеров экрана.
     */
    fun setSize(w: Float, h: Float, screenDensity: Float) {
        if (w <= 0f || h <= 0f) return

        width = w
        height = h
        densityScale = screenDensity

        // Размер ячейки ~5dp для оптимального баланса детализации и производительности
        val step = 5f * densityScale
        cols = (w / step).toInt().coerceIn(60, 100)
        rows = (h / step).toInt().coerceIn(120, 220)

        cellW = w / cols
        cellH = h / rows

        val total = cols * rows
        val oldDensities = densities
        val oldCols = cols
        val oldRows = rows

        densities = FloatArray(total)
        pixels = IntArray(total)

        // Если экран повернулся, переносим существующие плотности
        if (oldDensities.isNotEmpty() && oldCols > 0 && oldRows > 0) {
            for (r in 0 until rows) {
                val oldR = (r.toFloat() / rows * oldRows).toInt().coerceIn(0, oldRows - 1)
                for (c in 0 until cols) {
                    val oldC = (c.toFloat() / cols * oldCols).toInt().coerceIn(0, oldCols - 1)
                    densities[r * cols + c] = oldDensities[oldR * oldCols + oldC]
                }
            }
        }

        gridBitmap?.recycle()
        gridBitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)

        srcRect.set(0, 0, cols, rows)
        dstRect.set(0, 0, w.toInt(), h.toInt())
    }

    /**
     * Возвращает Y-координату поверхности снега в горизонтальной позиции [x].
     * Если снега в колонке нет, возвращает дно экрана [height].
     */
    fun getSurfaceY(x: Float): Float {
        if (cols == 0 || rows == 0) return height
        val c = (x / cellW).toInt().coerceIn(0, cols - 1)

        // Ищем самую верхнюю ячейку (минимальный r), где есть снег
        for (r in 0 until rows) {
            val d = densities[r * cols + c]
            if (d > 0.08f) {
                return r * cellH + (1.0f - d) * cellH
            }
        }
        return height
    }

    /**
     * Возвращает высоту снега (от дна экрана) в точке [x].
     */
    fun getSnowHeight(x: Float): Float {
        return (height - getSurfaceY(x)).coerceAtLeast(0f)
    }

    /**
     * Добавление снега при приземлении снежинки в точке [x].
     * Снег ложится на текущую поверхность и может накапливаться до самого верха экрана.
     */
    fun addSnow(x: Float, flakeRadius: Float, accumulationMultiplier: Float = 1f) {
        if (cols == 0 || rows == 0) return
        val c = (x / cellW).toInt().coerceIn(0, cols - 1)

        // Находим целевую ячейку поверхности
        var targetRow = rows - 1
        for (r in 0 until rows) {
            if (densities[r * cols + c] > 0.08f) {
                targetRow = r
                break
            }
        }

        // Если ячейка уже заполнена, снег начинает ложиться на ячейку выше
        if (densities[targetRow * cols + c] >= 0.95f && targetRow > 0) {
            targetRow--
        }

        val deposit = flakeRadius * 0.16f * accumulationMultiplier

        // Добавляем снег в целевую ячейку и распределяем часть на соседние колонки
        densities[targetRow * cols + c] =
            (densities[targetRow * cols + c] + deposit * 0.6f).coerceAtMost(1.0f)

        if (c > 0) {
            densities[targetRow * cols + (c - 1)] =
                (densities[targetRow * cols + (c - 1)] + deposit * 0.2f).coerceAtMost(1.0f)
        }
        if (c < cols - 1) {
            densities[targetRow * cols + (c + 1)] =
                (densities[targetRow * cols + (c + 1)] + deposit * 0.2f).coerceAtMost(1.0f)
        }
    }

    /**
     * Очистка снега вдоль непрерывного сегмента движения пальца:
     * форма вытянута вдоль вектора движения, с мягким переходом прозрачности
     * и снежными валиками по бокам.
     */
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

                // Естественная микро-неровность границы дорожки
                val noise = kotlin.math.sin(cx * 0.08f) * kotlin.math.cos(cy * 0.08f) * 0.12f * radius
                val effectiveR = radius + noise

                val idx = r * cols + c
                val currentDensity = densities[idx]

                if (dist < effectiveR * 0.65f) {
                    // Ядро дорожки: полностью очищается
                    densities[idx] = 0f
                } else if (dist < effectiveR) {
                    // Плавный переход прозрачности
                    val factor = ((dist - effectiveR * 0.65f) / (effectiveR * 0.35f)).coerceIn(0f, 1f)
                    densities[idx] = currentDensity * factor * factor
                } else if (dist < effectiveR * 1.25f && currentDensity > 0.05f) {
                    // Снежные валики: скопление сдвинутого снега по краям дорожки
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

    /**
     * Физический шаг симуляции: гравитационное оседание в пустые ячейки снизу
     * и естественное оползание по диагонали (угол откоса).
     */
    fun update(dtSec: Float) {
        if (cols == 0 || rows == 0) return

        // 1. Гравитационное оседание: снег падает в пустоты прямо под ним
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

        // 2. Диагональное оползание (формирование естественных пологих холмиков)
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
     * Отрисовка накопившегося снега на Canvas через аппаратно фильтруемый Bitmap.
     */
    fun draw(canvas: Canvas) {
        val bmp = gridBitmap ?: return
        if (bmp.isRecycled || cols == 0 || rows == 0) return

        val total = cols * rows
        for (i in 0 until total) {
            val d = densities[i]
            if (d <= 0.02f) {
                pixels[i] = 0 // Прозрачно: открывает фон
            } else {
                val alpha = (d * 255f).toInt().coerceIn(0, 255)
                // Естественный морозный градиент снега: от чистого белого к мягкому льдистому оттенку
                val red = (220 + 35 * d).toInt().coerceIn(0, 255)
                val green = (235 + 20 * d).toInt().coerceIn(0, 255)
                val blue = 255
                pixels[i] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }

        bmp.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
    }
}
