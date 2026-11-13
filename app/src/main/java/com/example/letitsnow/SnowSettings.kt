package com.example.letitsnow

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.snowDataStore: DataStore<Preferences> by preferencesDataStore(name = "snow_settings")

/**
 * Цветовые темы снегопада и сугробов.
 */
enum class SnowColorTheme(val title: String, val description: String) {
    WHITE("Белый", "Классический чистый бело-голубой зимний снег"),
    SCARLET("Алый", "Мистический алый снегопад и кроваво-красные сугробы")
}

/**
 * Варианты формы и геометрии падающих снежинок.
 */
enum class SnowflakeStyle(val title: String, val description: String) {
    FAIRYTALE("Сказочный", "90% микро-снежинок (1-3 dp), 8% хлопьев и 2% изящных кристаллов"),
    FLUFFY("Хлопья", "Мягкие пушистые зимние хлопья")
}

/**
 * Варианты внешнего вида сугроба внизу экрана.
 */
enum class SnowbankStyle(val title: String, val description: String) {
    SMOOTH_HILLS("Плавные холмы", "Шелковистые природные дюны с мягкими зимними тенями"),
    PUFFY_CLOUDS("Пушистый", "Мягкие пышные шапки только что выпавшего снега"),
    ICY_SHELF("Ледяной", "Кристальная искрящаяся кромка со слоистой подсветкой"),
    CLASSIC("Классика", "Детализированный сглаженный сугроб с мягким зимним градиентом")
}

/**
 * Модель настроек снегопада.
 */
data class SnowSettings(
    val fallSpeed: Float = DEFAULT_FALL_SPEED,
    val intensity: Int = DEFAULT_INTENSITY,
    val accumulationRate: Float = DEFAULT_ACCUMULATION_RATE,
    val isOverlayEnabled: Boolean = false,
    val snowbankHeight: Float = DEFAULT_SNOWBANK_HEIGHT,
    val isUnlockAccumulationEnabled: Boolean = true,
    val snowflakeSize: Float = DEFAULT_SNOWFLAKE_SIZE,
    val snowflakeStyle: SnowflakeStyle = SnowflakeStyle.FAIRYTALE,
    val colorTheme: SnowColorTheme = SnowColorTheme.WHITE,
    val isGlowEnabled: Boolean = false,
    val snowbankStyle: SnowbankStyle = SnowbankStyle.SMOOTH_HILLS,
    val useDetailedGraphics: Boolean = true
) {
    companion object {
        const val MIN_FALL_SPEED = 0.1f
        const val MAX_FALL_SPEED = 3.0f
        const val DEFAULT_FALL_SPEED = 1.0f

        const val MIN_INTENSITY = 10
        const val MAX_INTENSITY = 300
        const val DEFAULT_INTENSITY = 150

        const val MIN_ACCUMULATION_RATE = 0.1f
        const val MAX_ACCUMULATION_RATE = 3.0f
        const val DEFAULT_ACCUMULATION_RATE = 1.0f

        const val MIN_SNOWBANK_HEIGHT = 15f
        const val MAX_SNOWBANK_HEIGHT = 150f
        const val DEFAULT_SNOWBANK_HEIGHT = 65f

        const val MIN_SNOWFLAKE_SIZE = 0.5f
        const val MAX_SNOWFLAKE_SIZE = 2.5f
        const val DEFAULT_SNOWFLAKE_SIZE = 1.0f
    }
}

/**
 * Репозиторий для сохранения и чтения настроек с использованием DataStore Preferences.
 */
class SnowSettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val KEY_FALL_SPEED = floatPreferencesKey("snow_fall_speed")
        val KEY_INTENSITY = intPreferencesKey("snow_intensity")
        val KEY_ACCUMULATION_RATE = floatPreferencesKey("snow_accumulation_rate")
        val KEY_OVERLAY_ENABLED = booleanPreferencesKey("snow_overlay_enabled")
        val KEY_SNOWBANK_HEIGHT = floatPreferencesKey("snowbank_height")
        val KEY_UNLOCK_ACCUMULATION = booleanPreferencesKey("unlock_accumulation_enabled")
        val KEY_SNOWFLAKE_SIZE = floatPreferencesKey("snowflake_size")
        val KEY_SNOWFLAKE_STYLE = stringPreferencesKey("snowflake_style")
        val KEY_COLOR_THEME = stringPreferencesKey("snow_color_theme")
        val KEY_GLOW_ENABLED = booleanPreferencesKey("snow_glow_enabled")
        val KEY_SNOWBANK_STYLE = stringPreferencesKey("snowbank_style")
        val KEY_USE_DETAILED_GRAPHICS = booleanPreferencesKey("use_detailed_graphics")
    }

    /**
     * Поток актуальных настроек снегопада.
     */
    val settingsFlow: Flow<SnowSettings> = context.snowDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val legacyStyle = preferences[PreferencesKeys.KEY_SNOWFLAKE_STYLE]

            val styleFlake = legacyStyle?.let { name ->
                if (name == "BLOODY") SnowflakeStyle.FAIRYTALE
                else try { SnowflakeStyle.valueOf(name) } catch (_: Exception) { null }
            } ?: SnowflakeStyle.FAIRYTALE

            val colorTheme = preferences[PreferencesKeys.KEY_COLOR_THEME]?.let { name ->
                try { SnowColorTheme.valueOf(name) } catch (_: Exception) { null }
            } ?: if (legacyStyle == "BLOODY") SnowColorTheme.SCARLET else SnowColorTheme.WHITE

            val isGlowEnabled = preferences[PreferencesKeys.KEY_GLOW_ENABLED] ?: false

            val styleBank = preferences[PreferencesKeys.KEY_SNOWBANK_STYLE]?.let { name ->
                try { SnowbankStyle.valueOf(name) } catch (_: Exception) { null }
            } ?: SnowbankStyle.SMOOTH_HILLS

            SnowSettings(
                fallSpeed = preferences[PreferencesKeys.KEY_FALL_SPEED] ?: SnowSettings.DEFAULT_FALL_SPEED,
                intensity = preferences[PreferencesKeys.KEY_INTENSITY] ?: SnowSettings.DEFAULT_INTENSITY,
                accumulationRate = preferences[PreferencesKeys.KEY_ACCUMULATION_RATE] ?: SnowSettings.DEFAULT_ACCUMULATION_RATE,
                isOverlayEnabled = preferences[PreferencesKeys.KEY_OVERLAY_ENABLED] ?: false,
                snowbankHeight = preferences[PreferencesKeys.KEY_SNOWBANK_HEIGHT] ?: SnowSettings.DEFAULT_SNOWBANK_HEIGHT,
                isUnlockAccumulationEnabled = preferences[PreferencesKeys.KEY_UNLOCK_ACCUMULATION] ?: true,
                snowflakeSize = preferences[PreferencesKeys.KEY_SNOWFLAKE_SIZE] ?: SnowSettings.DEFAULT_SNOWFLAKE_SIZE,
                snowflakeStyle = styleFlake,
                colorTheme = colorTheme,
                isGlowEnabled = isGlowEnabled,
                snowbankStyle = styleBank,
                useDetailedGraphics = preferences[PreferencesKeys.KEY_USE_DETAILED_GRAPHICS] ?: true
            )
        }

    /**
     * Сохранение всех настроек целиком.
     */
    suspend fun saveSettings(settings: SnowSettings) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_FALL_SPEED] = settings.fallSpeed
            preferences[PreferencesKeys.KEY_INTENSITY] = settings.intensity
            preferences[PreferencesKeys.KEY_ACCUMULATION_RATE] = settings.accumulationRate
            preferences[PreferencesKeys.KEY_OVERLAY_ENABLED] = settings.isOverlayEnabled
            preferences[PreferencesKeys.KEY_SNOWBANK_HEIGHT] = settings.snowbankHeight
            preferences[PreferencesKeys.KEY_UNLOCK_ACCUMULATION] = settings.isUnlockAccumulationEnabled
            preferences[PreferencesKeys.KEY_SNOWFLAKE_SIZE] = settings.snowflakeSize
            preferences[PreferencesKeys.KEY_SNOWFLAKE_STYLE] = settings.snowflakeStyle.name
            preferences[PreferencesKeys.KEY_COLOR_THEME] = settings.colorTheme.name
            preferences[PreferencesKeys.KEY_GLOW_ENABLED] = settings.isGlowEnabled
            preferences[PreferencesKeys.KEY_SNOWBANK_STYLE] = settings.snowbankStyle.name
            preferences[PreferencesKeys.KEY_USE_DETAILED_GRAPHICS] = settings.useDetailedGraphics
        }
    }

    suspend fun saveFallSpeed(fallSpeed: Float) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_FALL_SPEED] = fallSpeed
        }
    }

    suspend fun saveIntensity(intensity: Int) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_INTENSITY] = intensity
        }
    }

    suspend fun saveAccumulationRate(accumulationRate: Float) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_ACCUMULATION_RATE] = accumulationRate
        }
    }

    suspend fun saveOverlayEnabled(enabled: Boolean) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun saveSnowbankHeight(snowbankHeight: Float) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_SNOWBANK_HEIGHT] = snowbankHeight
        }
    }

    suspend fun saveUnlockAccumulationEnabled(enabled: Boolean) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_UNLOCK_ACCUMULATION] = enabled
        }
    }

    suspend fun saveSnowflakeSize(size: Float) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_SNOWFLAKE_SIZE] = size
        }
    }

    suspend fun saveSnowflakeStyle(style: SnowflakeStyle) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_SNOWFLAKE_STYLE] = style.name
        }
    }

    suspend fun saveColorTheme(theme: SnowColorTheme) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_COLOR_THEME] = theme.name
        }
    }

    suspend fun saveGlowEnabled(enabled: Boolean) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_GLOW_ENABLED] = enabled
        }
    }

    suspend fun saveSnowbankStyle(style: SnowbankStyle) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_SNOWBANK_STYLE] = style.name
        }
    }

    suspend fun saveUseDetailedGraphics(enabled: Boolean) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_USE_DETAILED_GRAPHICS] = enabled
        }
    }
}
