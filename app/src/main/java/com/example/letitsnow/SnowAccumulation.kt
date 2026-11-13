package com.example.letitsnow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Двумерная физическая модель накопления снега (2D Snow Density Grid)
 * с отдельным высококачественным визуальным слоем сглаживания (Gaussian Blur + Shading).
 *
 * Физические данные плотности не портятся сглаживанием: сглаживается именно
 * визуальный слой перед отправкой на экран, исключая пикселизацию и ступенчатые контуры.
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

    // Физическая сетка плотности снега (0.0f - чисто, 1.0f - плотный снег)
    private var densities = FloatArray(0)

    // Буферы для сепарабельного 2D Gaussian размытия визуального слоя
    private var tempBlur = FloatArray(0)
    private var visualDensities = FloatArray(0)

    // Массив пикселей для аппаратно-фильтруемого Bitmap
    private var pixels = IntArray(0)

    private var gridBitmap: Bitmap? = null
    private val srcRect = Rect()
    private val dstRect = Rect()

    // Кисть с билинейной интерполяцией GPU для мягкого масштабирования
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

        // Сохраняем предыдущие размеры сетки ДО их перезаписи
        val prevCols = cols
        val prevRows = rows
        val prevDensities = densities

        // Размер ячейки ~4.5dp для баланса детализации и производительности
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

        // При смене ориентации корректно переносим существующий накопленный снег
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
        }

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

        // Поиск самой верхней ячейки со снегом в колонке c
        for (r in 0 until rows) {
            val d = densities[r * cols + c]
            if (d > 0.06f) {
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
     * Снег плавно распределяется по мягкому гауссову ядру на соседние ячейки,
     * исключая появление резких квадратных блоков.
     */
    fun addSnow(x: Float, flakeRadius: Float, accumulationMultiplier: Float = 1f) {
        if (cols == 0 || rows == 0) return
        val c = (x / cellW).toInt().coerceIn(0, cols - 1)

        // Находим целевую ячейку поверхности
        var targetRow = rows - 1
        for (r in 0 until rows) {
            if (densities[r * cols + c] > 0.06f) {
                targetRow = r
                break
            }
        }

        // Если ячейка заполнена, снег начинает заполнять ячейку сверху
        if (densities[targetRow * cols + c] >= 0.92f && targetRow > 0) {
            targetRow--
        }

        val deposit = flakeRadius * 0.14f * accumulationMultiplier

        // Мягкое распределение на соседние ячейки
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

    /**
     * Очистка снега вдоль непрерывного сегмента движения пальца:
     * форма вытянута вдоль вектора движения, с мягким переходом прозрачности
     * и естественными валиками по краям.
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
                val noise = sin(cx * 0.08f) * sin(cy * 0.08f) * 0.12f * radius
                val effectiveR = radius + noise

                val idx = r * cols + c
                val currentDensity = densities[idx]

                if (dist < effectiveR * 0.65f) {
                    // Ядро дорожки: полностью чисто
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
     * Отрисовка визуального слоя:
     * 1. 2D сепарабельное размытие плотностей (Gaussian smoothing) устраняет ступенчатость и пиксели.
     * 2. Вычисление глубины снега и наложение естественных голубоватых теней в нижних/внутренних слоях.
     * 3. Мягкие волнообразные контуры и аппаратная билинейная интерполяция при выводе на экран.
     */
    fun draw(canvas: Canvas) {
        val bmp = gridBitmap ?: return
        if (bmp.isRecycled || cols == 0 || rows == 0) return

        // 1. Быстрый сепарабельный 2D Gaussian фильтр визуального слоя (~0.05мс)
        // Горизонтальный проход (ядро: 0.1, 0.2, 0.4, 0.2, 0.1)
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

        // Вертикальный проход
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

        // 2. Генерация цветов с глубиной, тенями и мягкими границами
        for (c in 0 until cols) {
            // Находим верхнюю границу снега в колонке c для расчета глубины
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
                    pixels[idx] = 0 // Прозрачный фон
                } else {
                    // Плавный переход прозрачности (Smoothstep) исключает ступенчатость краев
                    val normD = ((vd - 0.02f) / 0.65f).coerceIn(0f, 1f)
                    val smoothAlpha = normD * normD * (3f - 2f * normD)
                    val alpha = (smoothAlpha * 255f).toInt().coerceIn(0, 255)

                    // Глубина ячейки от поверхности
                    val depth = (r - surfaceRow).coerceAtLeast(0)

                    // Цветовые градиенты:
                    // Поверхность — чистый кристальный белый (#FFFFFF)
                    // Внутренние и нижние слои — легкая прохладная голубоватая тень (#D2E4F7)
                    val shadowRatio = (depth / 16f).coerceIn(0f, 1f)

                    // Небольшая волнообразная текстура для естественности снежной массы
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
     * Мгновенный расчет и создание сугробов за время блокировки экрана [elapsedSeconds].
     * Формирует естественные холмистые сугробы снизу вверх пропорционально времени сна.
     */
    fun accumulateOfflineSnow(elapsedSeconds: Float, rateMultiplier: Float = 1f) {
        if (cols <= 0 || rows <= 0) return

        // Базовая высота сугроба заметна над панелью навигации (не менее 18% высоты экрана)
        val minRows = (rows * 0.18f).coerceAtLeast(14f)
        val growth = elapsedSeconds * 0.35f * rateMultiplier
        val baseRows = (minRows + growth).coerceIn(minRows, rows.toFloat() * 0.85f)

        for (c in 0 until cols) {
            // Естественные холмы и волнообразный рельеф
            val wave = sin(c * 0.12f) * 4.5f + cos(c * 0.07f) * 3.5f + sin(c * 0.28f) * 2f
            val colHeight = (baseRows + wave).coerceIn(4f, (rows - 2).toFloat()).toInt()

            for (h in 0 until colHeight) {
                val r = rows - 1 - h
                if (r >= 0) {
                    val idx = r * cols + c
                    if (h == colHeight - 1) {
                        // Верхняя кромка холма (пушистый снег)
                        densities[idx] = (densities[idx] + 0.65f).coerceIn(0f, 1f)
                    } else {
                        // Плотный слежавшийся снег
                        densities[idx] = 1.0f
                    }
                }
            }
        }

        initialOfflineSnowAmount = getTotalSnow()
    }

    /**
     * Возвращает суммарную плотность всего накопившегося снега на экране.
     */
    fun getTotalSnow(): Float {
        var sum = 0f
        for (i in densities.indices) {
            sum += densities[i]
        }
        return sum
    }

    /**
     * Возвращает долю снега, очищенного пальцем от первоначального объема (от 0.0 до 1.0).
     */
    fun getClearedRatio(): Float {
        if (initialOfflineSnowAmount <= 0.001f) {
            val total = getTotalSnow()
            return if (total <= 0.001f) 1.0f else 0.0f
        }
        val current = getTotalSnow()
        return ((initialOfflineSnowAmount - current) / initialOfflineSnowAmount).coerceIn(0f, 1f)
    }

    /**
     * Полное удаление всего накопившегося снега.
     */
    fun clearAllSnow() {
        densities.fill(0f)
        pixels.fill(0)
        initialOfflineSnowAmount = 0f
    }
}
