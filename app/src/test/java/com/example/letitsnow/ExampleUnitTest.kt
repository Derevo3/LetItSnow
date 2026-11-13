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
        assertEquals(65f, settings.snowbankHeight, 0.001f)
        assertTrue(settings.isUnlockAccumulationEnabled)
        assertEquals(1.0f, settings.snowflakeSize, 0.001f)
        assertEquals(SnowflakeStyle.FAIRYTALE, settings.snowflakeStyle)
        assertEquals(SnowColorTheme.WHITE, settings.colorTheme)
        assertFalse(settings.isGlowEnabled)
        assertEquals(SnowbankStyle.SMOOTH_HILLS, settings.snowbankStyle)
        assertTrue(settings.useDetailedGraphics)
    }

    @Test
    fun snowSettings_constants_areValid() {
        assertEquals(0.1f, SnowSettings.MIN_FALL_SPEED, 0.001f)
        assertEquals(3.0f, SnowSettings.MAX_FALL_SPEED, 0.001f)
        assertEquals(10, SnowSettings.MIN_INTENSITY)
        assertEquals(300, SnowSettings.MAX_INTENSITY)
        assertEquals(0.1f, SnowSettings.MIN_ACCUMULATION_RATE, 0.001f)
        assertEquals(3.0f, SnowSettings.MAX_ACCUMULATION_RATE, 0.001f)
        assertEquals(15f, SnowSettings.MIN_SNOWBANK_HEIGHT, 0.001f)
        assertEquals(150f, SnowSettings.MAX_SNOWBANK_HEIGHT, 0.001f)
        assertEquals(65f, SnowSettings.DEFAULT_SNOWBANK_HEIGHT, 0.001f)
        assertEquals(0.5f, SnowSettings.MIN_SNOWFLAKE_SIZE, 0.001f)
        assertEquals(2.5f, SnowSettings.MAX_SNOWFLAKE_SIZE, 0.001f)
        assertEquals(1.0f, SnowSettings.DEFAULT_SNOWFLAKE_SIZE, 0.001f)
    }

    @Test
    fun snowflake_initialSpawn_isAboveScreen() {
        val flake = Snowflake()
        flake.reset(1080f, 1920f, 2.5f, isInitial = true)
        assertTrue("Initial snowflake must start above the screen", flake.y <= 0f)
    }

    @Test
    fun snowSettings_styles_and_colors_haveAllOptions() {
        assertEquals(2, SnowflakeStyle.entries.size)
        assertEquals(2, SnowColorTheme.entries.size)
        assertEquals(4, SnowbankStyle.entries.size)
        assertEquals("Белый", SnowColorTheme.WHITE.title)
        assertEquals("Алый", SnowColorTheme.SCARLET.title)
        assertEquals("Сказочный", SnowflakeStyle.FAIRYTALE.title)
        assertEquals("Хлопья", SnowflakeStyle.FLUFFY.title)
    }
}
