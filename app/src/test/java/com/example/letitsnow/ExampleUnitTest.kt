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
        assertTrue(settings.isSnowbankEnabled)
        assertTrue(settings.isSnowbankGrowthEnabled)
        assertTrue(settings.isShakeToResetEnabled)
        assertEquals(40f, settings.snowbankHeight, 0.001f)
        assertEquals(1.0f, settings.snowflakeSize, 0.001f)
        assertEquals(SnowflakeStyle.FAIRYTALE, settings.snowflakeStyle)
        assertEquals(SnowColorTheme.WHITE, settings.colorTheme)
        assertFalse(settings.isGlowEnabled)
        assertFalse(settings.isSparklesEnabled)
        assertEquals(40, settings.sparklesCount)
        assertEquals(1.0f, settings.sparklesSize, 0.001f)
        assertEquals(SnowbankStyle.SMOOTH_HILLS, settings.snowbankStyle)
        assertFalse(settings.isVignetteEnabled)
        assertEquals(VignetteStyle.FROST_PATTERNS, settings.vignetteStyle)
        assertEquals(VignetteSize.MEDIUM, settings.vignetteSize)
        assertTrue(settings.isTiltWindEnabled)
        assertTrue(settings.isSpecialAnimationsEnabled)
        assertTrue(settings.useDetailedGraphics)
        assertFalse(settings.isFirstLaunchCompleted)
        assertNull(settings.customBackgroundPath)
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
        assertEquals(40f, SnowSettings.DEFAULT_SNOWBANK_HEIGHT, 0.001f)
        assertEquals(0.5f, SnowSettings.MIN_SNOWFLAKE_SIZE, 0.001f)
        assertEquals(2.5f, SnowSettings.MAX_SNOWFLAKE_SIZE, 0.001f)
        assertEquals(1.0f, SnowSettings.DEFAULT_SNOWFLAKE_SIZE, 0.001f)
        assertEquals(10, SnowSettings.MIN_SPARKLES_COUNT)
        assertEquals(150, SnowSettings.MAX_SPARKLES_COUNT)
        assertEquals(40, SnowSettings.DEFAULT_SPARKLES_COUNT)
        assertEquals(0.5f, SnowSettings.MIN_SPARKLES_SIZE, 0.001f)
        assertEquals(2.5f, SnowSettings.MAX_SPARKLES_SIZE, 0.001f)
        assertEquals(1.0f, SnowSettings.DEFAULT_SPARKLES_SIZE, 0.001f)
    }

    @Test
    fun snowflake_initialSpawn_isAboveScreen() {
        val flake = Snowflake()
        flake.reset(1080f, 1920f, 2.5f, isInitial = true)
        assertTrue("Initial snowflake must start above the screen", flake.y <= 0f)
        assertTrue("Depth speed factor must be initialized", flake.depthSpeedFactor >= 0.65f)
    }

    @Test
    fun snowSettings_styles_and_colors_haveAllOptions() {
        assertEquals(3, SnowflakeStyle.entries.size)
        assertEquals(4, SnowColorTheme.entries.size)
        assertEquals(4, SnowbankStyle.entries.size)
        assertEquals("Белый", SnowColorTheme.WHITE.title)
        assertEquals("Алый", SnowColorTheme.SCARLET.title)
        assertEquals("Розовый", SnowColorTheme.PINK.title)
        assertEquals("Чёрный", SnowColorTheme.BLACK.title)
        assertEquals("Сказочный", SnowflakeStyle.FAIRYTALE.title)
        assertEquals("Хлопья", SnowflakeStyle.FLUFFY.title)
        assertEquals("Сердечки", SnowflakeStyle.HEARTS.title)
    }

    @Test
    fun snowView_fpsConstants_areCorrect() {
        assertEquals(0, SnowView.FPS_UNLIMITED)
        assertEquals(60, SnowView.FPS_60)
        assertEquals(45, SnowView.FPS_45)
        assertEquals(40, SnowView.FPS_40)
        assertEquals(60, SnowView.DEFAULT_MAX_FPS)
    }

    @Test
    fun sparkle_lifecycle_and_reset_areValid() {
        val sparkle = Sparkle()
        assertTrue(sparkle.isDead)
        sparkle.reset(1080f, 1920f, 2.5f)
        assertFalse(sparkle.isDead)
        assertTrue(sparkle.maxRadius > 0f)
        assertTrue(sparkle.maxLife > 0f)

        sparkle.update(0.5f, 1080f, 1920f, 2.5f)
        assertTrue(sparkle.alpha > 0)
        assertTrue(sparkle.currentRadius > 0f)

        // Test tilt reaction
        val startBaseX = sparkle.baseX
        val startBaseY = sparkle.baseY
        sparkle.speedY = 20f
        sparkle.swayRadius = 0f // eliminate sway to check pure gravity drift
        sparkle.isOnSnowbank = false

        // Gravity to the right: dirX = 1, dirY = 0
        sparkle.update(0.1f, 1080f, 1920f, 2.5f, dirX = 1f, dirY = 0f)
        assertTrue("Sparkle baseX should increase when tilted right", sparkle.baseX > startBaseX)
        assertEquals(startBaseY, sparkle.baseY, 0.001f)
    }

    @Test
    fun vignette_options_and_drawables_areValid() {
        assertEquals(3, VignetteStyle.entries.size)
        assertEquals(2, VignetteSize.entries.size)

        assertEquals("Узоры", VignetteStyle.FROST_PATTERNS.title)
        assertEquals("Гирлянда", VignetteStyle.CHRISTMAS_LIGHTS.title)
        assertEquals("Ветки ели", VignetteStyle.EVERGREEN.title)

        assertEquals("Маленькая", VignetteSize.SMALL.title)
        assertEquals("Средняя", VignetteSize.MEDIUM.title)

        assertEquals(
            R.drawable.frost_patterns_small,
            SnowVignette.getDrawableRes(VignetteStyle.FROST_PATTERNS, VignetteSize.SMALL)
        )
        assertEquals(
            R.drawable.frost_patterns_medium,
            SnowVignette.getDrawableRes(VignetteStyle.FROST_PATTERNS, VignetteSize.MEDIUM)
        )
        assertEquals(
            R.drawable.christmas_lights,
            SnowVignette.getDrawableRes(VignetteStyle.CHRISTMAS_LIGHTS)
        )
        assertEquals(
            R.drawable.evergreen_border,
            SnowVignette.getDrawableRes(VignetteStyle.EVERGREEN)
        )
    }

    @Test
    fun shootingStar_lifecycle_isCorrect() {
        val star = ShootingStar()
        assertFalse("Star should not be active initially", star.isActive)

        // Advance time by 15 seconds to trigger first spawn (timer is 12s)
        star.update(15.0f, 1080f, 1920f, 2.5f)
        assertTrue("Star should be active after timer expires", star.isActive)

        // Advance time to complete the flight duration (max duration ~2.2s)
        star.update(3.0f, 1080f, 1920f, 2.5f)
        assertFalse("Star should be inactive after flight completes", star.isActive)
    }
}
