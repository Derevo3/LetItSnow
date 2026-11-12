package com.example.letitsnow

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun snowSettings_defaults_areCorrect() {
        val settings = SnowSettings()
        assertEquals(1.0f, settings.fallSpeed, 0.001f)
        assertEquals(150, settings.intensity)
        assertEquals(1.0f, settings.accumulationRate, 0.001f)
    }

    @Test
    fun snowSettings_constants_areValid() {
        assertEquals(0.1f, SnowSettings.MIN_FALL_SPEED, 0.001f)
        assertEquals(3.0f, SnowSettings.MAX_FALL_SPEED, 0.001f)
        assertEquals(10, SnowSettings.MIN_INTENSITY)
        assertEquals(300, SnowSettings.MAX_INTENSITY)
        assertEquals(0.1f, SnowSettings.MIN_ACCUMULATION_RATE, 0.001f)
        assertEquals(3.0f, SnowSettings.MAX_ACCUMULATION_RATE, 0.001f)
    }

    @Test
    fun snowflake_initialSpawn_isAboveScreen() {
        val flake = Snowflake()
        flake.reset(1080f, 1920f, 2.5f, isInitial = true)
        assertTrue("Initial snowflake must start above the screen", flake.y <= 0f)
    }
}