package com.example.letitsnow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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

            // Создаем и запоминаем SnowView для отображения внутри приложения
            val snowView = remember {
                SnowView(context).apply {
                    applySettings(currentSettings)
                }
            }

            // Применяем настройки к SnowView при их изменении
            LaunchedEffect(currentSettings) {
                snowView.applySettings(currentSettings)
            }

            // Детектор встряхивания и вибрация для очистки снега потрясыванием
            val sensorManager = remember {
                context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            }
            val vibrator = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            }
            val shakeDetector = remember(snowView) {
                sensorManager?.let { sm ->
                    ShakeDetector(sm) {
                        snowView.accumulation.clearAllSnow()
                        snowView.invalidate()
                        vibrator?.let { v ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                v.vibrate(120)
                            }
                        }
                    }
                }
            }

            var isSettingsOpen by remember { mutableStateOf(false) }
            var isPendingOverlayActivation by remember { mutableStateOf(false) }

            // Диалоги онбординга и последовательного запроса разрешений
            var showWelcomeDialog by remember { mutableStateOf(false) }
            var hasCheckedFirstLaunch by remember { mutableStateOf(false) }
            var showOverlayPermissionRationale by remember { mutableStateOf(false) }
            var showOverlayPermissionDeniedDialog by remember { mutableStateOf(false) }
            var showNotificationRationale by remember { mutableStateOf(false) }

            // Показываем приветствие один раз при первом открытии приложения
            LaunchedEffect(currentSettings.isFirstLaunchCompleted) {
                if (!currentSettings.isFirstLaunchCompleted && !hasCheckedFirstLaunch) {
                    hasCheckedFirstLaunch = true
                    showWelcomeDialog = true
                }
            }

            // Функция полного выключения снегопада и выхода из приложения
            val stopAndExitApp: () -> Unit = {
                SnowOverlayService.stop(context)
                coroutineScope.launch {
                    settingsRepository.saveOverlayEnabled(false)
                }
                snowView.pause()
                (context as? ComponentActivity)?.finish()
            }

            // Прямой запуск оверлей-сервиса с сохранением состояния
            val launchOverlayServiceDirectly = {
                SnowOverlayService.start(context)
                coroutineScope.launch {
                    settingsRepository.saveOverlayEnabled(true)
                }
            }

            // Лаунчер запроса разрешения на показ уведомлений (Android 13+)
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ ->
                // На Android 13+ служба Foreground Service может работать даже при отклонении уведомлений.
                // Приложение сохраняет работоспособность в соответствии с требованиями системы.
                launchOverlayServiceDirectly()
            }

            // Проверка необходимости запроса разрешения на уведомления перед запуском оверлея
            val launchOverlayServiceWithCheck = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                ) {
                    showNotificationRationale = true
                } else {
                    launchOverlayServiceDirectly()
                }
            }

            // Лаунчер перехода в настройки разрешения отображения поверх окон
            val overlayPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                if (Settings.canDrawOverlays(context)) {
                    isPendingOverlayActivation = false
                    showOverlayPermissionDeniedDialog = false
                    if (!currentSettings.isFirstLaunchCompleted) {
                        coroutineScope.launch {
                            settingsRepository.saveFirstLaunchCompleted(true)
                        }
                    }
                    launchOverlayServiceWithCheck()
                } else if (isPendingOverlayActivation) {
                    isPendingOverlayActivation = false
                    showOverlayPermissionDeniedDialog = true
                    if (!currentSettings.isFirstLaunchCompleted) {
                        coroutineScope.launch {
                            settingsRepository.saveFirstLaunchCompleted(true)
                        }
                    }
                }
            }

            // Автоматический запуск службы, если оверлей включен в настройках
            LaunchedEffect(currentSettings.isOverlayEnabled) {
                if (currentSettings.isOverlayEnabled && Settings.canDrawOverlays(context) && !SnowOverlayService.isRunning) {
                    SnowOverlayService.start(context)
                }
            }

            // Управление жизненным циклом анимации в фоне/на переднем плане внутри приложения
            DisposableEffect(lifecycleOwner, snowView, shakeDetector) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            snowView.start()
                            shakeDetector?.start()
                            // Проверяем актуальное состояние разрешения на оверлей при возвращении в приложение
                            if (Settings.canDrawOverlays(context)) {
                                if (isPendingOverlayActivation) {
                                    isPendingOverlayActivation = false
                                    showOverlayPermissionDeniedDialog = false
                                    if (!currentSettings.isFirstLaunchCompleted) {
                                        coroutineScope.launch {
                                            settingsRepository.saveFirstLaunchCompleted(true)
                                        }
                                    }
                                    launchOverlayServiceWithCheck()
                                } else if (currentSettings.isOverlayEnabled && !SnowOverlayService.isRunning) {
                                    SnowOverlayService.start(context)
                                }
                            } else {
                                if (isPendingOverlayActivation) {
                                    isPendingOverlayActivation = false
                                    showOverlayPermissionDeniedDialog = true
                                    if (!currentSettings.isFirstLaunchCompleted) {
                                        coroutineScope.launch {
                                            settingsRepository.saveFirstLaunchCompleted(true)
                                        }
                                    }
                                }
                                if (currentSettings.isOverlayEnabled) {
                                    coroutineScope.launch {
                                        settingsRepository.saveOverlayEnabled(false)
                                    }
                                }
                            }
                        }
                        Lifecycle.Event.ON_PAUSE -> {
                            shakeDetector?.stop()
                            snowView.pause()
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    shakeDetector?.stop()
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val toggleOverlay: (Boolean) -> Unit = { enabled ->
                if (enabled) {
                    if (!Settings.canDrawOverlays(context)) {
                        showOverlayPermissionRationale = true
                    } else {
                        launchOverlayServiceWithCheck()
                    }
                } else {
                    isPendingOverlayActivation = false
                    SnowOverlayService.stop(context)
                    coroutineScope.launch {
                        settingsRepository.saveOverlayEnabled(false)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Отрисовка снегопада через существующий SnowView и Canvas
                AndroidView(
                    factory = { snowView },
                    modifier = Modifier.fillMaxSize()
                )

                // Кнопки управления в верхнем правом углу
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка быстрого выключения приложения и снегопада
                    IconButton(
                        onClick = stopAndExitApp,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0x99DC2626), CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_power),
                            contentDescription = "Выключить снег и выйти",
                            tint = Color.White
                        )
                    }

                    // Кнопка с иконкой шестерёнки для вызова настроек
                    IconButton(
                        onClick = { isSettingsOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0x660F172A), CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = "Настройки снега",
                            tint = Color.White
                        )
                    }
                }

                // Меню настроек в темной зимней стилистике
                SnowSettingsMenu(
                    isOpen = isSettingsOpen,
                    settings = currentSettings,
                    onSettingsChanged = { liveSettings ->
                        // Мгновенное применение к графическому движку без блокировок и дискового I/O
                        snowView.applySettings(liveSettings)
                    },
                    onSaveSettings = { updated ->
                        // Мгновенное применение к движку
                        snowView.applySettings(updated)
                        // Асинхронное сохранение в DataStore Preferences один раз при завершении изменения
                        coroutineScope.launch {
                            settingsRepository.saveSettings(updated)
                        }
                    },
                    onToggleOverlay = toggleOverlay,
                    onStopApp = stopAndExitApp,
                    onDismiss = { isSettingsOpen = false }
                )

                // 1. Диалог приветствия первого запуска (Onboarding)
                if (showWelcomeDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showWelcomeDialog = false
                            coroutineScope.launch {
                                settingsRepository.saveFirstLaunchCompleted(true)
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_snowflake_notification),
                                contentDescription = null,
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(36.dp)
                            )
                        },
                        title = {
                            Text(
                                text = "Добро пожаловать в Let It Snow! ❄️",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = "Приложение создаёт уютную зимнюю атмосферу снегопада прямо на вашем экране.\n\n" +
                                        "Вы можете включить снегопад поверх всех приложений или любоваться им внутри окна приложения.",
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showWelcomeDialog = false
                                    if (!Settings.canDrawOverlays(context)) {
                                        showOverlayPermissionRationale = true
                                    } else {
                                        coroutineScope.launch {
                                            settingsRepository.saveFirstLaunchCompleted(true)
                                        }
                                        launchOverlayServiceWithCheck()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Включить поверх экрана ❄️", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showWelcomeDialog = false
                                    coroutineScope.launch {
                                        settingsRepository.saveFirstLaunchCompleted(true)
                                    }
                                }
                            ) {
                                Text("Только в приложении", color = Color(0xFF94A3B8))
                            }
                        },
                        containerColor = Color(0xF20B132B),
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // 2. Диалог с объяснением необходимости разрешения SYSTEM_ALERT_WINDOW перед переходом в настройки
                if (showOverlayPermissionRationale) {
                    AlertDialog(
                        onDismissRequest = {
                            showOverlayPermissionRationale = false
                            isPendingOverlayActivation = false
                            if (!currentSettings.isFirstLaunchCompleted) {
                                coroutineScope.launch {
                                    settingsRepository.saveFirstLaunchCompleted(true)
                                }
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = null,
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        title = {
                            Text(
                                text = "Отображение поверх других окон",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = "Чтобы снежинки кружились на рабочем столе и поверх других приложений, требуется системное разрешение.\n\n" +
                                        "В открывшихся настройках системы найдите «Let It Snow» и переведите переключатель в активное положение.",
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showOverlayPermissionRationale = false
                                    isPendingOverlayActivation = true
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    overlayPermissionLauncher.launch(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Перейти в настройки", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showOverlayPermissionRationale = false
                                    isPendingOverlayActivation = false
                                    if (!currentSettings.isFirstLaunchCompleted) {
                                        coroutineScope.launch {
                                            settingsRepository.saveFirstLaunchCompleted(true)
                                        }
                                    }
                                }
                            ) {
                                Text("Отмена", color = Color(0xFF94A3B8))
                            }
                        },
                        containerColor = Color(0xF20B132B),
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // 3. Понятное объяснение в случае отказа в разрешении оверлея
                if (showOverlayPermissionDeniedDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showOverlayPermissionDeniedDialog = false
                        },
                        title = {
                            Text(
                                text = "Разрешение не предоставлено",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = "Без этого разрешения отображение поверх окон недоступно. Снегопад продолжит радовать вас внутри приложения.\n\n" +
                                        "Вы всегда можете включить снегопад поверх экрана позже в меню настроек.",
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showOverlayPermissionDeniedDialog = false
                                    showOverlayPermissionRationale = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Попробовать снова", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showOverlayPermissionDeniedDialog = false
                                }
                            ) {
                                Text("Понятно", color = Color(0xFF94A3B8))
                            }
                        },
                        containerColor = Color(0xF20B132B),
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // 4. Пояснение перед запросом разрешения на показ уведомлений (Android 13+)
                if (showNotificationRationale) {
                    AlertDialog(
                        onDismissRequest = {
                            showNotificationRationale = false
                            launchOverlayServiceDirectly()
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_snowflake_notification),
                                contentDescription = null,
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        title = {
                            Text(
                                text = "Уведомление службы",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = "Разрешите уведомления, чтобы видеть статус снегопада и быстро останавливать его прямо из шторки уведомлений.",
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showNotificationRationale = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        launchOverlayServiceDirectly()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Продолжить", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showNotificationRationale = false
                                    launchOverlayServiceDirectly()
                                }
                            ) {
                                Text("Не сейчас", color = Color(0xFF94A3B8))
                            }
                        },
                        containerColor = Color(0xF20B132B),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }
}
