package com.example.letitsnow

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Менеджер датчика гравитации / акселерометра устройства.
 * Обеспечивает:
 * 1. Непрерывное определение вектора направления силы тяжести (Gravity Vector).
 *    Нормализованный вектор (gx, gy) с фиксированной длиной 1.0 задаёт чистое направление снегопада
 *    без паразитного ускорения при поворотах или наклонах.
 * 2. Распознавание встряхивания телефона (Shake Detection) для сброса наросшего сугроба.
 */
object SnowSensorManager : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var gravitySensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var isRegistered = false
    private var activeSubscribersCount = 0

    // Нормализованные компоненты вектора гравитации (-1.0 .. 1.0)
    @Volatile
    var gravityX: Float = 0f
        private set

    @Volatile
    var gravityY: Float = 1f
        private set

    private var smoothedX: Float = 0f
    private var smoothedY: Float = 9.8f

    // Слушатель жеста встряхивания
    var onShakeListener: (() -> Unit)? = null
    private var lastShakeTimestampMs: Long = 0L

    @Synchronized
    fun register(context: Context) {
        activeSubscribersCount++
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

            val rawGx = -smoothedX
            val rawGy = smoothedY

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
                }
            }

            // Fallback для вектора гравитации, если TYPE_GRAVITY не поддерживается аппаратно
            if (gravitySensor == null) {
                val alpha = 0.15f
                smoothedX += alpha * (ax - smoothedX)
                smoothedY += alpha * (ay - smoothedY)

                val rawGx = -smoothedX
                val rawGy = smoothedY

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
