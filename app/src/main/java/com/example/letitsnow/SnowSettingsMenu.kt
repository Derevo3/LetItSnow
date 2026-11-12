package com.example.letitsnow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Меню настроек снегопада в темной зимней стилистике на Jetpack Compose.
 */
@Composable
fun SnowSettingsMenu(
    isOpen: Boolean,
    settings: SnowSettings,
    onSettingsChanged: (SnowSettings) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    // Локальное состояние для мгновенного плавного отклика ползунков
    var fallSpeed by remember(settings.fallSpeed) { mutableFloatStateOf(settings.fallSpeed) }
    var intensity by remember(settings.intensity) { mutableIntStateOf(settings.intensity) }
    var accumulationRate by remember(settings.accumulationRate) { mutableFloatStateOf(settings.accumulationRate) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x55000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Предотвращаем закрытие при клике внутри карточки */ }
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xF20B132B) // Глубокий темно-синий ночной зимний фон
                ),
                border = BorderStroke(1.dp, Color(0x3360A5FA)) // Тонкая ледяная кайма
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Заголовок
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Настройки снега",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 1. Ползунок скорости снегопада
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Скорость снегопада",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = String.format(Locale.US, "%.1fx", fallSpeed),
                                color = Color(0xFF93C5FD),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = fallSpeed,
                            onValueChange = { newValue ->
                                fallSpeed = (newValue * 10).roundToInt() / 10f
                                onSettingsChanged(
                                    SnowSettings(
                                        fallSpeed = fallSpeed,
                                        intensity = intensity,
                                        accumulationRate = accumulationRate
                                    )
                                )
                            },
                            valueRange = SnowSettings.MIN_FALL_SPEED..SnowSettings.MAX_FALL_SPEED,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF93C5FD),
                                activeTrackColor = Color(0xFF3B82F6),
                                inactiveTrackColor = Color(0x333B82F6)
                            )
                        )
                    }

                    // 2. Ползунок интенсивности снегопада
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Интенсивность снегопада",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "$intensity снежинок",
                                color = Color(0xFF93C5FD),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = intensity.toFloat(),
                            onValueChange = { newValue ->
                                intensity = newValue.roundToInt()
                                onSettingsChanged(
                                    SnowSettings(
                                        fallSpeed = fallSpeed,
                                        intensity = intensity,
                                        accumulationRate = accumulationRate
                                    )
                                )
                            },
                            valueRange = SnowSettings.MIN_INTENSITY.toFloat()..SnowSettings.MAX_INTENSITY.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF93C5FD),
                                activeTrackColor = Color(0xFF3B82F6),
                                inactiveTrackColor = Color(0x333B82F6)
                            )
                        )
                    }

                    // 3. Ползунок скорости накопления снега
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Скорость накопления снега",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = String.format(Locale.US, "%.1fx", accumulationRate),
                                color = Color(0xFF93C5FD),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = accumulationRate,
                            onValueChange = { newValue ->
                                accumulationRate = (newValue * 10).roundToInt() / 10f
                                onSettingsChanged(
                                    SnowSettings(
                                        fallSpeed = fallSpeed,
                                        intensity = intensity,
                                        accumulationRate = accumulationRate
                                    )
                                )
                            },
                            valueRange = SnowSettings.MIN_ACCUMULATION_RATE..SnowSettings.MAX_ACCUMULATION_RATE,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF93C5FD),
                                activeTrackColor = Color(0xFF3B82F6),
                                inactiveTrackColor = Color(0x333B82F6)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Кнопка «Закрыть»
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1D4ED8),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Закрыть",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
