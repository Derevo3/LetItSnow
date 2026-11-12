package com.example.letitsnow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SnowSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepository = SnowSettingsRepository(this)

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val lifecycleOwner = this@MainActivity

            // Загрузка сохраненных настроек из DataStore
            val currentSettings by settingsRepository.settingsFlow.collectAsState(
                initial = SnowSettings()
            )

            // Создаем и запоминаем SnowView
            val snowView = remember {
                SnowView(context).apply {
                    applySettings(currentSettings)
                }
            }

            // Применяем настройки к SnowView при их изменении
            LaunchedEffect(currentSettings) {
                snowView.applySettings(currentSettings)
            }

            // Управление жизненным циклом анимации в фоне/на переднем плане
            DisposableEffect(lifecycleOwner, snowView) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> snowView.start()
                        Lifecycle.Event.ON_PAUSE -> snowView.pause()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            var isSettingsOpen by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxSize()) {
                // Отрисовка снегопада через существующий SnowView и Canvas
                AndroidView(
                    factory = { snowView },
                    modifier = Modifier.fillMaxSize()
                )

                // Кнопка с иконкой шестерёнки в верхнем правом углу
                IconButton(
                    onClick = { isSettingsOpen = true },
                    modifier = Modifier
                        .statusBarsPadding()
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(48.dp)
                        .background(Color(0x660F172A), CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = "Настройки снега",
                        tint = Color.White
                    )
                }

                // Меню настроек в темной зимней стилистике
                SnowSettingsMenu(
                    isOpen = isSettingsOpen,
                    settings = currentSettings,
                    onSettingsChanged = { updated ->
                        // Мгновенное применение к движку без ожидания записи на диск
                        snowView.applySettings(updated)
                        // Асинхронное сохранение в DataStore Preferences
                        coroutineScope.launch {
                            settingsRepository.saveSettings(updated)
                        }
                    },
                    onDismiss = { isSettingsOpen = false }
                )
            }
        }
    }
}
