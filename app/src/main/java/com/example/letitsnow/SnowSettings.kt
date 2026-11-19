package com.example.letitsnow

import android.content.Context
import androidx.annotation.StringRes
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
enum class SnowColorTheme(
    val title: String,
    val description: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int
) {
    WHITE(
        "Белый",
        "Классический чистый бело-голубой зимний снег",
        R.string.color_theme_white_title,
        R.string.color_theme_white_desc
    ),
    SCARLET(
        "Алый",
        "Мистический алый снегопад и кроваво-красные сугробы",
        R.string.color_theme_scarlet_title,
        R.string.color_theme_scarlet_desc
    )
}

/**
 * Варианты формы и геометрии падающих снежинок.
 */
enum class SnowflakeStyle(
    val title: String,
    val description: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int
) {
    FAIRYTALE(
        "Сказочный",
        "90% микро-снежинок (1-3 dp), 8% хлопьев и 2% изящных кристаллов",
        R.string.snowflake_style_fairytale_title,
        R.string.snowflake_style_fairytale_desc
    ),
    FLUFFY(
        "Хлопья",
        "Мягкие пушистые зимние хлопья",
        R.string.snowflake_style_fluffy_title,
        R.string.snowflake_style_fluffy_desc
    )
}

/**
 * Варианты внешнего вида сугроба внизу экрана.
 */
enum class SnowbankStyle(
    val title: String,
    val description: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int
) {
    SMOOTH_HILLS(
        "Плавные холмы",
        "Шелковистые природные дюны с мягкими зимними тенями",
        R.string.snowbank_style_smooth_hills_title,
        R.string.snowbank_style_smooth_hills_desc
    ),
    PUFFY_CLOUDS(
        "Пушистый",
        "Мягкие пышные шапки только что выпавшего снега",
        R.string.snowbank_style_puffy_clouds_title,
        R.string.snowbank_style_puffy_clouds_desc
    ),
    ICY_SHELF(
        "Ледяной",
        "Кристальная искрящаяся кромка со слоистой подсветкой",
        R.string.snowbank_style_icy_shelf_title,
        R.string.snowbank_style_icy_shelf_desc
    ),
    CLASSIC(
        "Классика",
        "Детализированный сглаженный сугроб с мягким зимним градиентом",
        R.string.snowbank_style_classic_title,
        R.string.snowbank_style_classic_desc
    )
}

/**
 * Модель настроек снегопада.
 */
data class SnowSettings(
    val fallSpeed: Float = DEFAULT_FALL_SPEED,
    val intensity: Int = DEFAULT_INTENSITY,
    val accumulationRate: Float = DEFAULT_ACCUMULATION_RATE,
    val isOverlayEnabled: Boolean = false,
    val isSnowbankEnabled: Boolean = true,
    val snowbankHeight: Float = DEFAULT_SNOWBANK_HEIGHT,
    val snowflakeSize: Float = DEFAULT_SNOWFLAKE_SIZE,
    val snowflakeStyle: SnowflakeStyle = SnowflakeStyle.FAIRYTALE,
    val colorTheme: SnowColorTheme = SnowColorTheme.WHITE,
    val isGlowEnabled: Boolean = false,
    val snowbankStyle: SnowbankStyle = SnowbankStyle.SMOOTH_HILLS,
    val useDetailedGraphics: Boolean = true,
    val isFirstLaunchCompleted: Boolean = false,
    val customBackgroundPath: String? = null
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
 * Репозиторий для сохранения и чтения настроек снегопада через DataStore Preferences.
 */
class SnowSettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val FALL_SPEED = floatPreferencesKey("fall_speed")
        val INTENSITY = intPreferencesKey("intensity")
        val ACCUMULATION_RATE = floatPreferencesKey("accumulation_rate")
        val IS_OVERLAY_ENABLED = booleanPreferencesKey("is_overlay_enabled")
        val IS_SNOWBANK_ENABLED = booleanPreferencesKey("is_snowbank_enabled")
        val SNOWBANK_HEIGHT = floatPreferencesKey("snowbank_height")
        val SNOWFLAKE_SIZE = floatPreferencesKey("snowflake_size")
        val SNOWFLAKE_STYLE = stringPreferencesKey("snowflake_style")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val IS_GLOW_ENABLED = booleanPreferencesKey("is_glow_enabled")
        val SNOWBANK_STYLE = stringPreferencesKey("snowbank_style")
        val USE_DETAILED_GRAPHICS = booleanPreferencesKey("use_detailed_graphics")
        val IS_FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("is_first_launch_completed")
        val CUSTOM_BACKGROUND_PATH = stringPreferencesKey("custom_background_path")
    }

    val settingsFlow: Flow<SnowSettings> = context.snowDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val fallSpeed = preferences[PreferencesKeys.FALL_SPEED] ?: SnowSettings.DEFAULT_FALL_SPEED
            val intensity = preferences[PreferencesKeys.INTENSITY] ?: SnowSettings.DEFAULT_INTENSITY
            val accumulationRate = preferences[PreferencesKeys.ACCUMULATION_RATE] ?: SnowSettings.DEFAULT_ACCUMULATION_RATE
            val isOverlayEnabled = preferences[PreferencesKeys.IS_OVERLAY_ENABLED] ?: false
            val isSnowbankEnabled = preferences[PreferencesKeys.IS_SNOWBANK_ENABLED] ?: true
            val snowbankHeight = preferences[PreferencesKeys.SNOWBANK_HEIGHT] ?: SnowSettings.DEFAULT_SNOWBANK_HEIGHT
            val snowflakeSize = preferences[PreferencesKeys.SNOWFLAKE_SIZE] ?: SnowSettings.DEFAULT_SNOWFLAKE_SIZE

            val styleName = preferences[PreferencesKeys.SNOWFLAKE_STYLE] ?: SnowflakeStyle.FAIRYTALE.name
            val snowflakeStyle = try {
                SnowflakeStyle.valueOf(styleName)
            } catch (_: IllegalArgumentException) {
                SnowflakeStyle.FAIRYTALE
            }

            val themeName = preferences[PreferencesKeys.COLOR_THEME] ?: SnowColorTheme.WHITE.name
            val colorTheme = try {
                SnowColorTheme.valueOf(themeName)
            } catch (_: IllegalArgumentException) {
                SnowColorTheme.WHITE
            }

            val isGlowEnabled = preferences[PreferencesKeys.IS_GLOW_ENABLED] ?: false

            val bankStyleName = preferences[PreferencesKeys.SNOWBANK_STYLE] ?: SnowbankStyle.SMOOTH_HILLS.name
            val snowbankStyle = try {
                SnowbankStyle.valueOf(bankStyleName)
            } catch (_: IllegalArgumentException) {
                SnowbankStyle.SMOOTH_HILLS
            }

            val useDetailedGraphics = preferences[PreferencesKeys.USE_DETAILED_GRAPHICS] ?: true
            val isFirstLaunchCompleted = preferences[PreferencesKeys.IS_FIRST_LAUNCH_COMPLETED] ?: false
            val customBackgroundPath = preferences[PreferencesKeys.CUSTOM_BACKGROUND_PATH]

            SnowSettings(
                fallSpeed = fallSpeed,
                intensity = intensity,
                accumulationRate = accumulationRate,
                isOverlayEnabled = isOverlayEnabled,
                isSnowbankEnabled = isSnowbankEnabled,
                snowbankHeight = snowbankHeight,
                snowflakeSize = snowflakeSize,
                snowflakeStyle = snowflakeStyle,
                colorTheme = colorTheme,
                isGlowEnabled = isGlowEnabled,
                snowbankStyle = snowbankStyle,
                useDetailedGraphics = useDetailedGraphics,
                isFirstLaunchCompleted = isFirstLaunchCompleted,
                customBackgroundPath = customBackgroundPath
            )
        }

    suspend fun saveSettings(settings: SnowSettings) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.FALL_SPEED] = settings.fallSpeed
            preferences[PreferencesKeys.INTENSITY] = settings.intensity
            preferences[PreferencesKeys.ACCUMULATION_RATE] = settings.accumulationRate
            preferences[PreferencesKeys.IS_OVERLAY_ENABLED] = settings.isOverlayEnabled
            preferences[PreferencesKeys.IS_SNOWBANK_ENABLED] = settings.isSnowbankEnabled
            preferences[PreferencesKeys.SNOWBANK_HEIGHT] = settings.snowbankHeight
            preferences[PreferencesKeys.SNOWFLAKE_SIZE] = settings.snowflakeSize
            preferences[PreferencesKeys.SNOWFLAKE_STYLE] = settings.snowflakeStyle.name
            preferences[PreferencesKeys.COLOR_THEME] = settings.colorTheme.name
            preferences[PreferencesKeys.IS_GLOW_ENABLED] = settings.isGlowEnabled
            preferences[PreferencesKeys.SNOWBANK_STYLE] = settings.snowbankStyle.name
            preferences[PreferencesKeys.USE_DETAILED_GRAPHICS] = settings.useDetailedGraphics
            preferences[PreferencesKeys.IS_FIRST_LAUNCH_COMPLETED] = settings.isFirstLaunchCompleted
            if (settings.customBackgroundPath != null) {
                preferences[PreferencesKeys.CUSTOM_BACKGROUND_PATH] = settings.customBackgroundPath
            } else {
                preferences.remove(PreferencesKeys.CUSTOM_BACKGROUND_PATH)
            }
        }
    }

    suspend fun saveOverlayEnabled(enabled: Boolean) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun saveFirstLaunchCompleted(completed: Boolean) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FIRST_LAUNCH_COMPLETED] = completed
        }
    }
}
