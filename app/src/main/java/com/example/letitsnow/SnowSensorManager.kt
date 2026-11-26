package com.example.letitsnow

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlin.math.sqrt

/**
 * Менеджер датчика гравитации / акселерометра устройства.
 * Обеспечивает:
 * 1. Непрерывное определение вектора направления силы тяжести (Gravity Vector).
 *    Нормализованный вектор (gx, gy) с фиксированной длиной 1.0 задаёт чистое направление снегопада
 *    без паразитного ускорения при поворотах или наклонах.
 *    С учётом текущей ориентации дисплея (портретная/альбомная) снег всегда падает строго вниз
 *    относительно экрана.
 * 2. Распознавание встряхивания телефона (Shake Detection) для сброса наросшего сугроба.
 */
object SnowSensorManager : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var gravitySensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var isRegistered = false
    private var activeSubscribersCount = 0

    // Нормализованные компоненты вектора гравитации (-1.0 .. 1.0) в координатах экрана
    @Volatile
    var gravityX: Float = 0f
        private set

    @Volatile
    var gravityY: Float = 1f
        private set

    @Volatile
    var displayRotation: Int = Surface.ROTATION_0

    fun updateDisplayRotation(rotation: Int) {
        displayRotation = rotation
    }

    private var smoothedX: Float = 0f
    private var smoothedY: Float = 9.8f

    // Слушатели жеста встряхивания
    var onShakeListener: (() -> Unit)? = null
    private val shakeListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private var lastShakeTimestampMs: Long = 0L

    fun addShakeListener(listener: () -> Unit) {
        if (!shakeListeners.contains(listener)) {
            shakeListeners.add(listener)
        }
    }

    fun removeShakeListener(listener: () -> Unit) {
        shakeListeners.remove(listener)
    }

    @Synchronized
    fun register(context: Context) {
        activeSubscribersCount++
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.rotation ?: wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            } catch (_: Throwable) {
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        } else {
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }

        if (isRegistered) return

        val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return

        sensorManager = sm
        gravitySensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (gravitySensor != null) {
            sm.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        }
        if (accelSensor != null) {
            sm.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        isRegistered = (gravitySensor != null || accelSensor != null)
    }

    @Synchronized
    fun unregister() {
        activeSubscribersCount = (activeSubscribersCount - 1).coerceAtLeast(0)
        if (activeSubscribersCount == 0 && isRegistered) {
            sensorManager?.unregisterListener(this)
            sensorManager = null
            gravitySensor = null
            accelSensor = null
            isRegistered = false
            smoothedX = 0f
            smoothedY = 9.8f
            gravityX = 0f
            gravityY = 1f
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val type = event.sensor.type

        if (type == Sensor.TYPE_GRAVITY) {
            val ax = event.values[0]
            val ay = event.values[1]

            val alpha = 0.15f
            smoothedX += alpha * (ax - smoothedX)
            smoothedY += alpha * (ay - smoothedY)

            // Пересчёт вектора гравитации из координат устройства в координаты экрана
            val (rawGx, rawGy) = when (displayRotation) {
                Surface.ROTATION_90 -> Pair(smoothedY, smoothedX)
                Surface.ROTATION_180 -> Pair(smoothedX, -smoothedY)
                Surface.ROTATION_270 -> Pair(-smoothedY, -smoothedX)
                else -> Pair(-smoothedX, smoothedY)
            }

            val len = sqrt(rawGx * rawGx + rawGy * rawGy)
            if (len > 0.5f) {
                gravityX = rawGx / len
                gravityY = rawGy / len
            } else {
                gravityX = 0f
                gravityY = 1f
            }
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            // Распознавание встряхивания (порог 2.3g с кулдауном 800мс)
            val gX = ax / SensorManager.GRAVITY_EARTH
            val gY = ay / SensorManager.GRAVITY_EARTH
            val gZ = az / SensorManager.GRAVITY_EARTH
            val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

            if (gForce > 2.3f) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTimestampMs > 800L) {
                    lastShakeTimestampMs = now
                    onShakeListener?.invoke()
                    for (listener in shakeListeners) {
                        listener.invoke()
                    }
                }
            }

            // Fallback для вектора гравитации, если TYPE_GRAVITY не поддерживается аппаратно
            if (gravitySensor == null) {
                val alpha = 0.15f
                smoothedX += alpha * (ax - smoothedX)
                smoothedY += alpha * (ay - smoothedY)

                val (rawGx, rawGy) = when (displayRotation) {
                    Surface.ROTATION_90 -> Pair(smoothedY, smoothedX)
                    Surface.ROTATION_180 -> Pair(smoothedX, -smoothedY)
                    Surface.ROTATION_270 -> Pair(-smoothedY, -smoothedX)
                    else -> Pair(-smoothedX, smoothedY)
                }

                val len = sqrt(rawGx * rawGx + rawGy * rawGy)
                if (len > 0.5f) {
                    gravityX = rawGx / len
                    gravityY = rawGy / len
                } else {
                    gravityX = 0f
                    gravityY = 1f
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не требуется
    }
}
