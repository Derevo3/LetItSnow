package com.example.letitsnow

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Детектор встряхивания устройства на основе акселерометра (Sensor.TYPE_ACCELEROMETER).
 * Активируется только в интерактивном режиме засыпанного экрана для нулевого расхода батареи.
 */
class ShakeDetector(
    private val sensorManager: SensorManager,
    private val onShake: () -> Unit
) : SensorEventListener {

    companion object {
        // Порог ускорения (в единицах g). 1g - гравитация покоя.
        // 2.3g - комфортный порог для естественного встряхивания без ложных срабатываний.
        private const val SHAKE_THRESHOLD_G = 2.3f
        private const val MIN_INTERVAL_MS = 600L
    }

    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isListening = false
    private var lastShakeTime: Long = 0L

    fun start() {
        if (!isListening && accelerometer != null) {
            isListening = sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    fun stop() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH

        val gForce = sqrt(x * x + y * y + z * z)

        if (gForce > SHAKE_THRESHOLD_G) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > MIN_INTERVAL_MS) {
                lastShakeTime = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
