package com.example.letitsnow

import kotlin.math.sin
import kotlin.random.Random

/**
 * Модель отдельной снежинки в симуляции снегопада.
 *
 * @property x Текущая координата X в пикселях.
 * @property y Текущая координата Y в пикселях.
 * @property radius Радиус снежинки в пикселях (масштабируется с учетом плотности экрана).
 * @property speedY Скорость падения по вертикали (пикселей в секунду).
 * @property alpha Прозрачность (0..255).
 * @property swayRadius Амплитуда горизонтального покачивания в пикселях.
 * @property swaySpeed Скорость колебания горизонтального покачивания.
 * @property angle Текущая фаза колебания (в радианах).
 * @property baseX Базовая координата X, относительно которой происходит покачивание.
 */
data class Snowflake(
    var x: Float = 0f,
    var y: Float = 0f,
    var radius: Float = 0f,
    var speedY: Float = 0f,
    var alpha: Int = 255,
    var swayRadius: Float = 0f,
    var swaySpeed: Float = 0f,
    var angle: Float = 0f,
    var baseX: Float = 0f
) {
    /**
     * Обновляет позицию снежинки за прошедшее время [dtSec] с учетом настроек скорости и накопления.
     * Если снежинка достигает поверхности сугроба или нижней границы экрана,
     * она передает массу в [accumulation] и появляется снова сверху.
     */
    fun update(
        dtSec: Float,
        width: Float,
        height: Float,
        density: Float,
        accumulation: SnowAccumulation? = null,
        fallSpeedMultiplier: Float = 1f,
        accumulationMultiplier: Float = 1f
    ) {
        // Плавное движение вниз с учетом коэффициента скорости
        y += speedY * fallSpeedMultiplier * dtSec

        // Горизонтальное отклонение / покачивание (сохраняет естественную частоту)
        angle += swaySpeed * dtSec
        x = baseX + sin(angle) * swayRadius

        // Высота поверхности снега в текущей точке X
        val snowHeight = accumulation?.getSnowHeight(x) ?: 0f
        val surfaceY = height - snowHeight

        // Если снежинка достигла поверхности сугроба или нижнего края экрана
        if (y + radius >= surfaceY) {
            accumulation?.addSnow(x, radius, accumulationMultiplier)
            reset(width, height, density, isInitial = false)
        } else if (y - radius > height) {
            accumulation?.addSnow(x, radius, accumulationMultiplier)
            reset(width, height, density, isInitial = false)
        }

        // Плавный циклический перенос по горизонтали при выходе за пределы экрана
        if (x + radius < 0f) {
            baseX += width + radius * 2f
            x = baseX + sin(angle) * swayRadius
        } else if (x - radius > width) {
            baseX -= width + radius * 2f
            x = baseX + sin(angle) * swayRadius
        }
    }

    /**
     * Инициализирует или сбрасывает свойства снежинки со случайными значениями,
     * учитывающими плотность экрана [density].
     */
    fun reset(width: Float, height: Float, density: Float, isInitial: Boolean = false) {
        // Разные размеры: от 1.5dp до 5.5dp
        val minRadius = 1.5f * density
        val maxRadius = 5.5f * density
        radius = minRadius + Random.nextFloat() * (maxRadius - minRadius)

        // Разная скорость падения: эффект параллакса (крупные хлопья падают быстрее)
        val sizeFactor = ((radius - minRadius) / (maxRadius - minRadius)).coerceIn(0f, 1f)
        val minSpeed = 30f * density
        val maxSpeed = 110f * density
        speedY = (minSpeed + sizeFactor * (maxSpeed - minSpeed)) * (0.85f + Random.nextFloat() * 0.3f)

        // Разная прозрачность: крупные снежинки более яркие (150..255), мелкие - более полупрозрачные (70..170)
        val baseAlpha = (100f + sizeFactor * 140f).toInt()
        alpha = (baseAlpha * (0.85f + Random.nextFloat() * 0.3f)).toInt().coerceIn(60, 255)

        // Параметры горизонтального покачивания
        swayRadius = (6f + Random.nextFloat() * 18f) * density
        swaySpeed = 1.0f + Random.nextFloat() * 2.5f
        angle = Random.nextFloat() * (2f * Math.PI.toFloat())

        baseX = Random.nextFloat() * width
        x = baseX + sin(angle) * swayRadius

        y = if (isInitial) {
            // При первом запуске распределяем снежинки по всей высоте экрана
            Random.nextFloat() * height
        } else {
            // При повторном появлении создаем чуть выше верхней границы экрана
            -radius - (Random.nextFloat() * 40f * density)
        }
    }
}
