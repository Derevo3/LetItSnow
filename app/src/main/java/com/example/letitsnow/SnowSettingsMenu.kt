package com.example.letitsnow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.painterResource
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
    onSaveSettings: (SnowSettings) -> Unit = onSettingsChanged,
    onToggleOverlay: (Boolean) -> Unit,
    onStopApp: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var fallSpeed by remember(settings.fallSpeed) { mutableFloatStateOf(settings.fallSpeed) }
    var intensity by remember(settings.intensity) { mutableIntStateOf(settings.intensity) }
    var accumulationRate by remember(settings.accumulationRate) { mutableFloatStateOf(settings.accumulationRate) }
    var snowbankHeight by remember(settings.snowbankHeight) { mutableFloatStateOf(settings.snowbankHeight) }
    var snowflakeSize by remember(settings.snowflakeSize) { mutableFloatStateOf(settings.snowflakeSize) }

    fun buildCurrentSettings(): SnowSettings {
        return settings.copy(
            fallSpeed = fallSpeed,
            intensity = intensity,
            accumulationRate = accumulationRate,
            snowbankHeight = snowbankHeight,
            snowflakeSize = snowflakeSize
        )
    }

    val handleDismiss = {
        onSaveSettings(buildCurrentSettings())
        onDismiss()
    }

    val isScarlet = settings.colorTheme == SnowColorTheme.SCARLET
    val primaryAccent = if (isScarlet) Color(0xFFF87171) else Color(0xFF93C5FD)
    val activeTrackColor = if (isScarlet) Color(0xFFEF4444) else Color(0xFF3B82F6)
    val activeBgColor = if (isScarlet) Color(0xFFDC2626) else Color(0xFF2563EB)
    val cardBorderColor = if (isScarlet) Color(0x44EF4444) else Color(0x3360A5FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x55000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { handleDismiss() }
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
                    ) { }
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xF20B132B)
                ),
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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

                    // 1. Выбор цвета снега и сугробов
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Цвет",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = settings.colorTheme.title,
                                color = primaryAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SnowColorTheme.entries.forEach { theme ->
                                val isSelected = settings.colorTheme == theme
                                val isThemeScarlet = theme == SnowColorTheme.SCARLET
                                val btnBg = if (isSelected) {
                                    if (isThemeScarlet) Color(0xFFDC2626) else Color(0xFF2563EB)
                                } else Color(0x331E293B)
                                val btnBorder = if (isSelected) {
                                    if (isThemeScarlet) Color(0xFFF87171) else Color(0xFF93C5FD)
                                } else Color(0x2264748B)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(btnBg, RoundedCornerShape(10.dp))
                                        .border(1.dp, btnBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            val updated = buildCurrentSettings().copy(colorTheme = theme)
                                            onSettingsChanged(updated)
                                            onSaveSettings(updated)
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = theme.title,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Text(
                            text = settings.colorTheme.description,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    // 2. Выбор стиля снежинок
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Стиль снежинок",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = settings.snowflakeStyle.title,
                                color = primaryAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SnowflakeStyle.entries.forEach { style ->
                                val isSelected = settings.snowflakeStyle == style
                                val btnBg = if (isSelected) activeBgColor else Color(0x331E293B)
                                val btnBorder = if (isSelected) primaryAccent else Color(0x2264748B)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(btnBg, RoundedCornerShape(10.dp))
                                        .border(1.dp, btnBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            val updated = buildCurrentSettings().copy(snowflakeStyle = style)
                                            onSettingsChanged(updated)
                                            onSaveSettings(updated)
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = style.title,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Text(
                            text = settings.snowflakeStyle.description,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    // 3. Переключатель «Сияние»
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x331E293B), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Сияние",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Нежное сказочное свечение вокруг снежинок",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.isGlowEnabled,
                            onCheckedChange = { enabled ->
                                val updated = buildCurrentSettings().copy(isGlowEnabled = enabled)
                                onSettingsChanged(updated)
                                onSaveSettings(updated)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = primaryAccent,
                                checkedTrackColor = activeTrackColor,
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }

                    // 4. Выбор стиля сугроба
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Стиль сугроба",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = settings.snowbankStyle.title,
                                color = primaryAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SnowbankStyle.entries.forEach { style ->
                                val isSelected = settings.snowbankStyle == style
                                val btnBg = if (isSelected) activeBgColor else Color(0x331E293B)
                                val btnBorder = if (isSelected) primaryAccent else Color(0x2264748B)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(btnBg, RoundedCornerShape(10.dp))
                                        .border(1.dp, btnBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            val updated = buildCurrentSettings().copy(snowbankStyle = style)
                                            onSettingsChanged(updated)
                                            onSaveSettings(updated)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = style.title,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Text(
                            text = settings.snowbankStyle.description,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    // 5. Ползунок размера снежинок
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Размер снежинок",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = String.format(Locale.US, "%.1fx", snowflakeSize),
                                color = primaryAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = snowflakeSize,
                            onValueChange = { newValue ->
                                snowflakeSize = (newValue * 10).roundToInt() / 10f
                                onSettingsChanged(buildCurrentSettings())
                            },
                            onValueChangeFinished = {
                                onSaveSettings(buildCurrentSettings())
                            },
                            valueRange = SnowSettings.MIN_SNOWFLAKE_SIZE..SnowSettings.MAX_SNOWFLAKE_SIZE,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryAccent,
                                activeTrackColor = activeTrackColor,
                                inactiveTrackColor = activeTrackColor.copy(alpha = 0.2f)
                            )
                        )
                    }

                    // 6. Ползунок скорости снегопада
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
                                color = primaryAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = fallSpeed,
                            onValueChange = { newValue ->
                                fallSpeed = (newValue * 10).roundToInt() / 10f
                                onSettingsChanged(buildCurrentSettings())
                            },
                            onValueChangeFinished = {
                                onSaveSettings(buildCurrentSettings())
                            },
                            valueRange = SnowSettings.MIN_FALL_SPEED..SnowSettings.MAX_FALL_SPEED,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryAccent,
                                activeTrackColor = activeTrackColor,
                                inactiveTrackColor = activeTrackColor.copy(alpha = 0.2f)
                            )
                        )
                    }

                    // 7. Ползунок интенсивности (количества снежинок)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Количество снежинок",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = intensity.toString(),
                                color = primaryAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = intensity.toFloat(),
                            onValueChange = { newValue ->
                                intensity = newValue.roundToInt()
                                onSettingsChanged(buildCurrentSettings())
                            },
                            onValueChangeFinished = {
                                onSaveSettings(buildCurrentSettings())
                            },
                            valueRange = SnowSettings.MIN_INTENSITY.toFloat()..SnowSettings.MAX_INTENSITY.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = primaryAccent,
                                activeTrackColor = activeTrackColor,
                                inactiveTrackColor = activeTrackColor.copy(alpha = 0.2f)
                            )
                        )
                    }

                    // 8. Ползунок скорости накопления снега
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
                                color = primaryAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = accumulationRate,
                            onValueChange = { newValue ->
                                accumulationRate = (newValue * 10).roundToInt() / 10f
                                onSettingsChanged(buildCurrentSettings())
                            },
                            onValueChangeFinished = {
                                onSaveSettings(buildCurrentSettings())
                            },
                            valueRange = SnowSettings.MIN_ACCUMULATION_RATE..SnowSettings.MAX_ACCUMULATION_RATE,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryAccent,
                                activeTrackColor = activeTrackColor,
                                inactiveTrackColor = activeTrackColor.copy(alpha = 0.2f)
                            )
                        )
                    }

                    // 9. Ползунок высоты сугроба
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Высота сугроба",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${snowbankHeight.roundToInt()} dp",
                                color = primaryAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = snowbankHeight,
                            onValueChange = { newValue ->
                                snowbankHeight = newValue.roundToInt().toFloat()
                                onSettingsChanged(buildCurrentSettings())
                            },
                            onValueChangeFinished = {
                                onSaveSettings(buildCurrentSettings())
                            },
                            valueRange = SnowSettings.MIN_SNOWBANK_HEIGHT..SnowSettings.MAX_SNOWBANK_HEIGHT,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryAccent,
                                activeTrackColor = activeTrackColor,
                                inactiveTrackColor = activeTrackColor.copy(alpha = 0.2f)
                            )
                        )
                    }

                    // 10. Переключатель оверлея поверх всех приложений
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x331E293B), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Поверх всех приложений",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Падающий снег на рабочем столе и поверх других приложений",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.isOverlayEnabled,
                            onCheckedChange = { onToggleOverlay(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = primaryAccent,
                                checkedTrackColor = activeTrackColor,
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Кнопка «Выключить снег и выйти»
                    Button(
                        onClick = onStopApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_power),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Выключить снег и выйти",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Кнопка «Закрыть настройки»
                    Button(
                        onClick = handleDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScarlet) Color(0x33EF4444) else Color(0x333B82F6),
                            contentColor = primaryAccent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Закрыть настройки",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
