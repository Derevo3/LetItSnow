package com.example.letitsnow

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.snowDataStore: DataStore<Preferences> by preferencesDataStore(name = "snow_settings")

/**
 * Модель настроек снегопада.
 *
 * @property fallSpeed Коэффициент скорости падения снежинок (0.1 .. 3.0, по умолчанию 1.0).
 * @property intensity Количество одновременно существующих снежинок (10 .. 300, по умолчанию 150).
 * @property accumulationRate Коэффициент скорости накопления снега (0.1 .. 3.0, по умолчанию 1.0).
 */
data class SnowSettings(
    val fallSpeed: Float = DEFAULT_FALL_SPEED,
    val intensity: Int = DEFAULT_INTENSITY,
    val accumulationRate: Float = DEFAULT_ACCUMULATION_RATE
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
            SnowSettings(
                fallSpeed = preferences[PreferencesKeys.KEY_FALL_SPEED] ?: SnowSettings.DEFAULT_FALL_SPEED,
                intensity = preferences[PreferencesKeys.KEY_INTENSITY] ?: SnowSettings.DEFAULT_INTENSITY,
                accumulationRate = preferences[PreferencesKeys.KEY_ACCUMULATION_RATE] ?: SnowSettings.DEFAULT_ACCUMULATION_RATE
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
        }
    }

    /**
     * Сохранение скорости снегопада.
     */
    suspend fun saveFallSpeed(fallSpeed: Float) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_FALL_SPEED] = fallSpeed
        }
    }

    /**
     * Сохранение интенсивности снегопада.
     */
    suspend fun saveIntensity(intensity: Int) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_INTENSITY] = intensity
        }
    }

    /**
     * Сохранение скорости накопления снега.
     */
    suspend fun saveAccumulationRate(accumulationRate: Float) {
        context.snowDataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_ACCUMULATION_RATE] = accumulationRate
        }
    }
}
