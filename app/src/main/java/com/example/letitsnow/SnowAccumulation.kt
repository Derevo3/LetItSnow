package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * Класс, отвечающий за симуляцию накопления снега, физику сугробов и их отрисовку на Canvas.
 */
class SnowAccumulation {

    private var width: Float = 0f
    private var height: Float = 0f
    private var density: Float = 1f
    private var stepX: Float = 1f
    private var count: Int = 0
    private var maxHeight: Float = 0f

    // Массивы высот для переднего и заднего слоя сугробов (в пикселях)
    private var heights = FloatArray(0)
    private var backHeights = FloatArray(0)
    private var tempHeights = FloatArray(0)
    private var xCoords = FloatArray(0)

    // Переиспользуемые пути для отрисовки без аллокаций в onDraw
    private val frontPath = Path()
    private val backPath = Path()
    private val crestPath = Path()

    // Кисти для переднего сугроба, задней тени и морозного контура
    private val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99A8C8E6.toInt() // Полупрозрачный холодный голубоватый оттенок тени
        style = Paint.Style.FILL
    }

    private val crestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt() // Яркий морозный белый гребень
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Инициализация или обновление размеров экрана и плотности пикселей.
     */
    fun setSize(w: Float, h: Float, screenDensity: Float) {
        if (w <= 0f || h <= 0f) return

        width = w
        height = h
        density = screenDensity
        crestPaint.strokeWidth = 2f * density

        // Максимальная высота сугробов — около 22% от высоты экрана
        maxHeight = (h * 0.22f).coerceAtMost(300f * density)

        // Шаг между точками около 6dp для оптимального баланса плавности и производительности
        val stepDp = 6f * density
        count = (w / stepDp).toInt().coerceIn(70, 220)
        stepX = w / (count - 1)

        val oldHeights = heights
        heights = FloatArray(count)
        backHeights = FloatArray(count)
        tempHeights = FloatArray(count)
        xCoords = FloatArray(count)

        for (i in 0 until count) {
            xCoords[i] = i * stepX
            // Если массив уже существовал, переносим пропорционально
            if (oldHeights.isNotEmpty()) {
                val oldIdx = (i.toFloat() / count * oldHeights.size).toInt().coerceIn(0, oldHeights.size - 1)
                heights[i] = oldHeights[oldIdx]
                backHeights[i] = oldHeights[oldIdx] * 0.9f
            }
        }

        // Вертикальный градиент: от кристально белого на гребне к мягкому зимнему льдисто-голубому внизу
        val gradient = LinearGradient(
            0f,
            height - maxHeight,
            0f,
            height,
            intArrayOf(
                Color.WHITE,
                0xFFF4F9FD.toInt(),
                0xFFD4E6F7.toInt()
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        frontPaint.shader = gradient
    }

    /**
     * Возвращает высоту снега (от нижнего края экрана) в заданной горизонтальной координате [x].
     */
    fun getSnowHeight(x: Float): Float {
        if (count == 0) return 0f
        val clampedX = x.coerceIn(0f, width)
        val fIndex = (clampedX / stepX).coerceIn(0f, (count - 1).toFloat())
        val i = fIndex.toInt()
        val frac = fIndex - i

        return if (i >= count - 1) {
            heights[count - 1]
        } else {
            heights[i] * (1f - frac) + heights[i + 1] * frac
        }
    }

    /**
     * Добавляет порцию снега в точке [x] при приземлении снежинки.
     * Распределяет объем по нормальному распределению на соседние сегменты, предотвращая острые пики.
     * [accumulationMultiplier] регулирует скорость накопления снега в соответствии с настройками.
     */
    fun addSnow(x: Float, flakeRadius: Float, accumulationMultiplier: Float = 1f) {
        if (count == 0) return
        val clampedX = x.coerceIn(0f, width)
        val centerIdx = (clampedX / stepX).toInt().coerceIn(0, count - 1)

        // Объем добавления пропорционален размеру снежинки и коэффициенту накопления
        val baseDeposit = flakeRadius * 0.35f * accumulationMultiplier

        // Ядро распределения на 5 точек: 0.38, 0.23, 0.08
        val weights = floatArrayOf(0.08f, 0.23f, 0.38f, 0.23f, 0.08f)

        for (offset in -2..2) {
            val idx = (centerIdx + offset).coerceIn(0, count - 1)
            val weight = weights[offset + 2]

            // Замедление накопления при приближении к максимальной высоте
            val saturationFactor = (1f - heights[idx] / maxHeight).coerceIn(0f, 1f)
            val added = baseDeposit * weight * saturationFactor
            heights[idx] += added

            // Добавляем снег на задний слой сугроба со сдвигом для создания объема и рельефа
            val backIdx = (centerIdx + offset + 3).coerceIn(0, count - 1)
            val backSaturation = (1f - backHeights[backIdx] / maxHeight).coerceIn(0f, 1f)
            backHeights[backIdx] += added * 0.85f * backSaturation
        }
    }

    /**
     * Очищает часть сугроба при касании пальцем в точке [x] с радиусом [radius].
     */
    fun clearSnow(x: Float, radius: Float) {
        if (count == 0) return
        val startX = x - radius
        val endX = x + radius
        val startIdx = (startX / stepX).toInt().coerceIn(0, count - 1)
        val endIdx = (endX / stepX).toInt().coerceIn(0, count - 1)

        for (i in startIdx..endIdx) {
            val dist = kotlin.math.abs(xCoords[i] - x)
            if (dist < radius) {
                val factor = (dist / radius).coerceIn(0f, 1f)
                heights[i] *= factor
                backHeights[i] *= factor
            }
        }
    }

    /**
     * Физический шаг симуляции: распространение снега под действием гравитации
     * (угол естественного откоса) и мягкое сглаживание ветром/оседанием.
     */
    fun update(dtSec: Float) {
        if (count < 2) return

        // 1. Оползание снега при превышении максимального угла наклона (угол естественного откоса)
        val maxSlope = 1.3f * density
        val avalancheSpeed = (dtSec * 8f).coerceAtMost(0.8f)

        for (i in 0 until count - 1) {
            val diff = heights[i] - heights[i + 1]
            if (diff > maxSlope) {
                val slip = (diff - maxSlope) * 0.5f * avalancheSpeed
                heights[i] -= slip
                heights[i + 1] += slip
            } else if (-diff > maxSlope) {
                val slip = (-diff - maxSlope) * 0.5f * avalancheSpeed
                heights[i] += slip
                heights[i + 1] -= slip
            }

            // Аналогично для заднего слоя
            val backDiff = backHeights[i] - backHeights[i + 1]
            if (backDiff > maxSlope) {
                val slip = (backDiff - maxSlope) * 0.5f * avalancheSpeed
                backHeights[i] -= slip
                backHeights[i + 1] += slip
            } else if (-backDiff > maxSlope) {
                val slip = (-backDiff - maxSlope) * 0.5f * avalancheSpeed
                backHeights[i] += slip
                backHeights[i + 1] -= slip
            }
        }

        // 2. Мягкая диффузия (сглаживание неровностей для естественного округлого вида сугробов)
        val smoothFactor = (dtSec * 1.2f).coerceIn(0f, 0.15f)

        for (i in 0 until count) {
            val left = if (i > 0) heights[i - 1] else heights[i]
            val right = if (i < count - 1) heights[i + 1] else heights[i]
            tempHeights[i] = heights[i] + ((left + right) * 0.5f - heights[i]) * smoothFactor
        }
        System.arraycopy(tempHeights, 0, heights, 0, count)

        for (i in 0 until count) {
            val left = if (i > 0) backHeights[i - 1] else backHeights[i]
            val right = if (i < count - 1) backHeights[i + 1] else backHeights[i]
            tempHeights[i] = backHeights[i] + ((left + right) * 0.5f - backHeights[i]) * smoothFactor
        }
        System.arraycopy(tempHeights, 0, backHeights, 0, count)
    }

    /**
     * Отрисовка сугробов на Canvas без создания объектов в цикле.
     */
    fun draw(canvas: Canvas) {
        if (count < 2 || width <= 0f || height <= 0f) return

        // 1. Отрисовка заднего теневого слоя сугроба (голубоватый тон)
        buildDunePath(backPath, backHeights, yOffset = 3f * density)
        canvas.drawPath(backPath, backPaint)

        // 2. Отрисовка основного белого сугроба с градиентом
        buildDunePath(frontPath, heights, yOffset = 0f)
        canvas.drawPath(frontPath, frontPaint)

        // 3. Отрисовка верхнего морозного контура
        buildCrestPath(crestPath, heights)
        canvas.drawPath(crestPath, crestPaint)
    }

    private fun buildDunePath(path: Path, hArray: FloatArray, yOffset: Float) {
        path.rewind()
        val firstY = (height - hArray[0] + yOffset).coerceAtMost(height)
        path.moveTo(0f, height)
        path.lineTo(0f, firstY)

        for (i in 0 until count - 1) {
            val x0 = xCoords[i]
            val y0 = (height - hArray[i] + yOffset).coerceAtMost(height)
            val x1 = xCoords[i + 1]
            val y1 = (height - hArray[i + 1] + yOffset).coerceAtMost(height)
            val midX = (x0 + x1) * 0.5f
            val midY = (y0 + y1) * 0.5f
            path.quadTo(x0, y0, midX, midY)
        }

        val lastY = (height - hArray[count - 1] + yOffset).coerceAtMost(height)
        path.lineTo(width, lastY)
        path.lineTo(width, height)
        path.lineTo(0f, height)
        path.close()
    }

    private fun buildCrestPath(path: Path, hArray: FloatArray) {
        path.rewind()
        val firstY = (height - hArray[0]).coerceAtMost(height)
        path.moveTo(0f, firstY)

        for (i in 0 until count - 1) {
            val x0 = xCoords[i]
            val y0 = (height - hArray[i]).coerceAtMost(height)
            val x1 = xCoords[i + 1]
            val y1 = (height - hArray[i + 1]).coerceAtMost(height)
            val midX = (x0 + x1) * 0.5f
            val midY = (y0 + y1) * 0.5f
            path.quadTo(x0, y0, midX, midY)
        }

        val lastY = (height - hArray[count - 1]).coerceAtMost(height)
        path.lineTo(width, lastY)
    }
}
