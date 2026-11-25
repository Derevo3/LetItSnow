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
        "Насыщенный кроваво-алый снегопад и рубиновые сугробы",
        R.string.color_theme_scarlet_title,
        R.string.color_theme_scarlet_desc
    ),
    PINK(
        "Розовый",
        "Нежный пастельно-розовый снегопад и воздушные сугробы",
        R.string.color_theme_pink_title,
        R.string.color_theme_pink_desc
    ),
    BLACK(
        "Чёрный",
        "Загадочный обсидиановый снегопад и угольные сугробы",
        R.string.color_theme_black_title,
        R.string.color_theme_black_desc
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
    ),
    HEARTS(
        "Сердечки",
        "Кружащиеся в воздухе романтичные сердечки",
        R.string.snowflake_style_hearts_title,
        R.string.snowflake_style_hearts_desc
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
        "Дюны",
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
 * Варианты стиля виньетки по краям экрана.
 */
enum class VignetteStyle(
    val title: String,
    val description: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int
) {
    FROST_PATTERNS(
        "Узоры",
        "Морозные узоры на стекле",
        R.string.vignette_style_patterns_title,
        R.string.vignette_style_patterns_desc
    ),
    CHRISTMAS_LIGHTS(
        "Гирлянда",
        "Новогодняя гирлянда",
        R.string.vignette_style_lights_title,
        R.string.vignette_style_lights_desc
    ),
    EVERGREEN(
        "Ветки ели",
        "Хвойные еловые ветви",
        R.string.vignette_style_evergreen_title,
        R.string.vignette_style_evergreen_desc
    )
}

/**
 * Варианты размера виньетки по краям экрана.
 */
enum class VignetteSize(
    val title: String,
    @param:StringRes val titleRes: Int
) {
    SMALL("Маленькая", R.string.vignette_size_small),
    MEDIUM("Средняя", R.string.vignette_size_medium)
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
    val isSparklesEnabled: Boolean = false,
    val sparklesCount: Int = DEFAULT_SPARKLES_COUNT,
    val sparklesSize: Float = DEFAULT_SPARKLES_SIZE,
    val snowbankStyle: SnowbankStyle = SnowbankStyle.SMOOTH_HILLS,
    val isVignetteEnabled: Boolean = false,
    val vignetteStyle: VignetteStyle = VignetteStyle.FROST_PATTERNS,
    val vignetteSize: VignetteSize = VignetteSize.MEDIUM,
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
        const val DEFAULT_SNOWBANK_HEIGHT = 40f

        const val MIN_SNOWFLAKE_SIZE = 0.5f
        const val MAX_SNOWFLAKE_SIZE = 2.5f
        const val DEFAULT_SNOWFLAKE_SIZE = 1.0f

        const val MIN_SPARKLES_COUNT = 10
        const val MAX_SPARKLES_COUNT = 150
        const val DEFAULT_SPARKLES_COUNT = 40

        const val MIN_SPARKLES_SIZE = 0.5f
        const val MAX_SPARKLES_SIZE = 2.5f
        const val DEFAULT_SPARKLES_SIZE = 1.0f
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
        val IS_SPARKLES_ENABLED = booleanPreferencesKey("is_sparkles_enabled")
        val SPARKLES_COUNT = intPreferencesKey("sparkles_count")
        val SPARKLES_SIZE = floatPreferencesKey("sparkles_size")
        val SNOWBANK_STYLE = stringPreferencesKey("snowbank_style")
        val IS_VIGNETTE_ENABLED = booleanPreferencesKey("is_vignette_enabled")
        val VIGNETTE_STYLE = stringPreferencesKey("vignette_style")
        val VIGNETTE_SIZE = stringPreferencesKey("vignette_size")
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
            val isSparklesEnabled = preferences[PreferencesKeys.IS_SPARKLES_ENABLED] ?: false
            val sparklesCount = preferences[PreferencesKeys.SPARKLES_COUNT] ?: SnowSettings.DEFAULT_SPARKLES_COUNT
            val sparklesSize = preferences[PreferencesKeys.SPARKLES_SIZE] ?: SnowSettings.DEFAULT_SPARKLES_SIZE

            val bankStyleName = preferences[PreferencesKeys.SNOWBANK_STYLE] ?: SnowbankStyle.SMOOTH_HILLS.name
            val snowbankStyle = try {
                SnowbankStyle.valueOf(bankStyleName)
            } catch (_: IllegalArgumentException) {
                SnowbankStyle.SMOOTH_HILLS
            }

            val isVignetteEnabled = preferences[PreferencesKeys.IS_VIGNETTE_ENABLED] ?: false
            val vigStyleName = preferences[PreferencesKeys.VIGNETTE_STYLE] ?: VignetteStyle.FROST_PATTERNS.name
            val vignetteStyle = try {
                VignetteStyle.valueOf(vigStyleName)
            } catch (_: IllegalArgumentException) {
                VignetteStyle.FROST_PATTERNS
            }
            val vigSizeName = preferences[PreferencesKeys.VIGNETTE_SIZE] ?: VignetteSize.MEDIUM.name
            val vignetteSize = try {
                VignetteSize.valueOf(vigSizeName)
            } catch (_: IllegalArgumentException) {
                VignetteSize.MEDIUM
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
                isSparklesEnabled = isSparklesEnabled,
                sparklesCount = sparklesCount,
                sparklesSize = sparklesSize,
                snowbankStyle = snowbankStyle,
                isVignetteEnabled = isVignetteEnabled,
                vignetteStyle = vignetteStyle,
                vignetteSize = vignetteSize,
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
            preferences[PreferencesKeys.IS_SPARKLES_ENABLED] = settings.isSparklesEnabled
            preferences[PreferencesKeys.SPARKLES_COUNT] = settings.sparklesCount
            preferences[PreferencesKeys.SPARKLES_SIZE] = settings.sparklesSize
            preferences[PreferencesKeys.SNOWBANK_STYLE] = settings.snowbankStyle.name
            preferences[PreferencesKeys.IS_VIGNETTE_ENABLED] = settings.isVignetteEnabled
            preferences[PreferencesKeys.VIGNETTE_STYLE] = settings.vignetteStyle.name
            preferences[PreferencesKeys.VIGNETTE_SIZE] = settings.vignetteSize.name
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
