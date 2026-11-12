package com.example.letitsnow

import android.view.MotionEvent

/**
 * Класс, отвечающий за обработку жестов очистки накопившегося снега пальцем.
 * Палец очищает ТОЛЬКО накопившийся снег, вычисляя непрерывные вытянутые сегменты
 * с мягким переходом прозрачности и снежными валиками по бокам.
 */
class SnowMask {

    companion object {
        private const val BASE_WIPE_RADIUS_DP = 22f // Базовый радиус очистки (~44dp диаметр)
    }

    var wipeRadius: Float = 0f
        private set

    private var density: Float = 1f
    private var prevTouchX: Float = -1f
    private var prevTouchY: Float = -1f

    /**
     * Обновление параметров маски при изменении размеров экрана.
     */
    fun setSize(w: Float, h: Float, screenDensity: Float) {
        if (w <= 0f || h <= 0f) return
        density = screenDensity
        wipeRadius = BASE_WIPE_RADIUS_DP * density
        prevTouchX = -1f
        prevTouchY = -1f
    }

    /**
     * Обработка касаний: непрерывная интерполяция между точками касания.
     * Очищает снег только там, где он физически присутствует.
     */
    fun onTouchEvent(
        event: MotionEvent,
        snowAccumulation: SnowAccumulation
    ): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val surfaceY = snowAccumulation.getSurfaceY(x)
                // Касание активирует очистку только если палец находится в пределах сугроба
                if (y >= surfaceY - wipeRadius && snowAccumulation.getSnowHeight(x) > 1f) {
                    prevTouchX = x
                    prevTouchY = y
                    snowAccumulation.clearAlongSegment(x, y, x, y, wipeRadius)
                    return true
                } else {
                    prevTouchX = -1f
                    prevTouchY = -1f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val surfaceY = snowAccumulation.getSurfaceY(x)
                val inSnow = (y >= surfaceY - wipeRadius && snowAccumulation.getSnowHeight(x) > 1f)

                if (inSnow) {
                    if (prevTouchX >= 0f && prevTouchY >= 0f) {
                        // Непрерывная очистка вытянутым сегментом вдоль движения пальца
                        snowAccumulation.clearAlongSegment(prevTouchX, prevTouchY, x, y, wipeRadius)
                    } else {
                        snowAccumulation.clearAlongSegment(x, y, x, y, wipeRadius)
                    }
                    prevTouchX = x
                    prevTouchY = y
                    return true
                } else {
                    // Палец вышел в свободный воздух над снегом
                    prevTouchX = -1f
                    prevTouchY = -1f
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                prevTouchX = -1f
                prevTouchY = -1f
                return true
            }
        }
        return false
    }
}
