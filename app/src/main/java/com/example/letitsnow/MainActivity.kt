package com.example.letitsnow

import android.Manifest
import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SnowViewModel(application: Application) : AndroidViewModel(application) {
    val repository = SnowSettingsRepository(application)

    // StateFlow с текущими настройками, сохраняющимися при поворотах экрана
    val settingsState: StateFlow<SnowSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SnowSettings()
    )

    val snowView: SnowView = SnowView(application).apply {
        isOverlayMode = false
    }

    override fun onCleared() {
        super.onCleared()
        snowView.pause()
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var settingsRepository: SnowSettingsRepository
    private lateinit var snowViewModel: SnowViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        snowViewModel = ViewModelProvider(this)[SnowViewModel::class.java]
        settingsRepository = snowViewModel.repository

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            val coroutineScope = rememberCoroutineScope()

            // Загрузка сохраненных настроек из ViewModel (сохраняет значение при поворотах экрана)
            val currentSettings by snowViewModel.settingsState.collectAsState()

            // Используем SnowView из ViewModel, чтобы сохранять анимацию при повороте экрана и сворачивании
            val snowView = snowViewModel.snowView

            // Применяем настройки к SnowView при их изменении
            LaunchedEffect(currentSettings) {
                snowView.applySettings(currentSettings)
            }

            // Детектор встряхивания и вибрация для очистки снега потрясыванием
            val vibrator = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            }

            val onShake = remember(vibrator, currentSettings.isShakeToResetEnabled, currentSettings.isSnowbankEnabled) {
                {
                    if (currentSettings.isShakeToResetEnabled && currentSettings.isSnowbankEnabled) {
                        snowView.invalidate()
                        if (vibrator?.hasVibrator() == true) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(
                                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(120)
                            }
                        }
                    }
                }
            }

            // Состояние открытия меню настроек (сохраняется при смене языка и повороте экрана)
            var isSettingsOpen by rememberSaveable { mutableStateOf(false) }

            BackHandler(enabled = isSettingsOpen) {
                isSettingsOpen = false
            }

            // Диалоги онбординга и последовательного запроса разрешений
            var showWelcomeDialog by rememberSaveable { mutableStateOf(false) }
            var showOverlayPermissionRationale by rememberSaveable { mutableStateOf(false) }
            var showOverlayPermissionDeniedDialog by rememberSaveable { mutableStateOf(false) }
            var showNotificationRationale by rememberSaveable { mutableStateOf(false) }
            var isPendingOverlayActivation by rememberSaveable { mutableStateOf(false) }
            var hasCheckedFirstLaunch by rememberSaveable { mutableStateOf(false) }

            // Показываем приветствие один раз при первом открытии приложения только ПОСЛЕ
            // фактического чтения актуального состояния из DataStore Preferences
            LaunchedEffect(Unit) {
                if (!hasCheckedFirstLaunch) {
                    hasCheckedFirstLaunch = true
                    val loadedSettings = settingsRepository.settingsFlow.first()
                    if (!loadedSettings.isFirstLaunchCompleted) {
                        showWelcomeDialog = true
                    }
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

            val checkWallpaperActive = {
                try {
                    val wm = WallpaperManager.getInstance(context)
                    val info = wm.wallpaperInfo
                    info != null && info.packageName == context.packageName && info.serviceName == SnowWallpaperService::class.java.name
                } catch (_: Exception) {
                    false
                }
            }

            var isWallpaperActive by remember { mutableStateOf(checkWallpaperActive()) }

            val wallpaperErrorMsg = stringResource(R.string.error_open_wallpaper_chooser)
            val openLiveWallpaperChooser = {
                try {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(context, SnowWallpaperService::class.java)
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallbackIntent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            wallpaperErrorMsg,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            // Управление жизненным циклом анимации в фоне/на переднем плане внутри приложения
            DisposableEffect(lifecycleOwner, snowView, onShake) {
                SnowSensorManager.addShakeListener(onShake)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    snowView.start()
                }
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            snowView.start()
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
                            isWallpaperActive = checkWallpaperActive()
                        }
                        Lifecycle.Event.ON_PAUSE -> {
                            snowView.pause()
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    SnowSensorManager.removeShakeListener(onShake)
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

            val activeTrackColor = when (currentSettings.colorTheme) {
                SnowColorTheme.SCARLET -> Color(0xFFEF4444)
                SnowColorTheme.PINK -> Color(0xFFEC4899)
                SnowColorTheme.BLACK -> Color(0xFF475569)
                SnowColorTheme.WHITE -> Color(0xFF388BFD)
            }
            val pillBorderColor = when (currentSettings.colorTheme) {
                SnowColorTheme.SCARLET -> Color(0x40EF4444)
                SnowColorTheme.PINK -> Color(0x40EC4899)
                SnowColorTheme.BLACK -> Color(0x4064748B)
                SnowColorTheme.WHITE -> Color(0x38388BFD)
            }
            val pillBgColor = Color(0x2E0B2040)
            val pillDividerColor = when (currentSettings.colorTheme) {
                SnowColorTheme.SCARLET -> Color(0x33EF4444)
                SnowColorTheme.PINK -> Color(0x33EC4899)
                SnowColorTheme.BLACK -> Color(0x3364748B)
                SnowColorTheme.WHITE -> Color(0x33388BFD)
            }
            val pillSwitchColors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFF8FAFC),
                checkedTrackColor = activeTrackColor,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFF7A8B9E),
                uncheckedTrackColor = Color(0x661E2D42),
                uncheckedBorderColor = Color(0x26388BFD)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // Отрисовка снегопада через существующий SnowView и Canvas
                AndroidView(
                    factory = {
                        (snowView.parent as? ViewGroup)?.removeView(snowView)
                        snowView.start()
                        snowView
                    },
                    update = { view ->
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            view.start()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Верхний контейнер: кнопки управления и быстрые переключатели
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Кнопки управления в верхнем правом углу
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка быстрого выключения приложения с неоновым кольцом
                        IconButton(
                            onClick = stopAndExitApp,
                            modifier = Modifier
                                .size(46.dp)
                                .background(Color(0x33102A4A), CircleShape)
                                .border(1.5.dp, Color(0x99388BFD), CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_power),
                                contentDescription = stringResource(R.string.cd_exit_app),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Кнопка с иконкой шестерёнки для вызова настроек
                        IconButton(
                            onClick = { isSettingsOpen = true },
                            modifier = Modifier
                                .size(46.dp)
                                .background(Color(0x33102A4A), CircleShape)
                                .border(1.dp, Color(0x40388BFD), CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = stringResource(R.string.cd_open_settings),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4 строки переключателей в стиле pill (капсулы)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Поверх всех приложений
                        QuickControlPill(
                            iconRes = R.drawable.ic_layers,
                            title = stringResource(R.string.settings_overlay_label),
                            isChecked = currentSettings.isOverlayEnabled,
                            onCheckedChange = { toggleOverlay(it) },
                            pillBgColor = pillBgColor,
                            pillBorderColor = pillBorderColor,
                            pillDividerColor = pillDividerColor,
                            switchColors = pillSwitchColors
                        )

                        // 2. Снег на экране блокировки
                        QuickControlPill(
                            iconRes = R.drawable.ic_snowflake_crystal,
                            title = stringResource(R.string.wallpaper_service_label),
                            isChecked = isWallpaperActive,
                            onCheckedChange = { openLiveWallpaperChooser() },
                            pillBgColor = pillBgColor,
                            pillBorderColor = pillBorderColor,
                            pillDividerColor = pillDividerColor,
                            switchColors = pillSwitchColors
                        )

                        // 3. Сугроб внизу экрана
                        QuickControlPill(
                            iconRes = R.drawable.ic_snowbank,
                            title = stringResource(R.string.settings_snowbank_enabled_label),
                            isChecked = currentSettings.isSnowbankEnabled,
                            onCheckedChange = { enabled ->
                                val updated = currentSettings.copy(isSnowbankEnabled = enabled)
                                snowView.applySettings(updated)
                                coroutineScope.launch {
                                    settingsRepository.saveSettings(updated)
                                }
                            },
                            pillBgColor = pillBgColor,
                            pillBorderColor = pillBorderColor,
                            pillDividerColor = pillDividerColor,
                            switchColors = pillSwitchColors
                        )

                        // 4. Виньетка
                        QuickControlPill(
                            iconRes = R.drawable.ic_snowflake_notification,
                            title = stringResource(R.string.settings_vignette_enabled_label),
                            isChecked = currentSettings.isVignetteEnabled,
                            onCheckedChange = { enabled ->
                                val updated = currentSettings.copy(isVignetteEnabled = enabled)
                                snowView.applySettings(updated)
                                coroutineScope.launch {
                                    settingsRepository.saveSettings(updated)
                                }
                            },
                            pillBgColor = pillBgColor,
                            pillBorderColor = pillBorderColor,
                            pillDividerColor = pillDividerColor,
                            switchColors = pillSwitchColors
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
                                text = stringResource(R.string.dialog_welcome_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.dialog_welcome_text),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showWelcomeDialog = false
                                    coroutineScope.launch {
                                        settingsRepository.saveFirstLaunchCompleted(true)
                                    }
                                    if (!Settings.canDrawOverlays(context)) {
                                        showOverlayPermissionRationale = true
                                    } else {
                                        launchOverlayServiceWithCheck()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.dialog_welcome_btn_overlay), fontWeight = FontWeight.SemiBold)
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
                                Text(stringResource(R.string.dialog_welcome_btn_in_app), color = Color(0xFF94A3B8))
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
                                text = stringResource(R.string.dialog_overlay_rationale_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.dialog_overlay_rationale_text),
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
                                Text(stringResource(R.string.dialog_overlay_rationale_btn_settings), fontWeight = FontWeight.SemiBold)
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
                                Text(stringResource(R.string.dialog_overlay_rationale_btn_cancel), color = Color(0xFF94A3B8))
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
                                text = stringResource(R.string.dialog_overlay_denied_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.dialog_overlay_denied_text),
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
                                Text(stringResource(R.string.dialog_overlay_denied_btn_retry), fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showOverlayPermissionDeniedDialog = false
                                }
                            ) {
                                Text(stringResource(R.string.dialog_overlay_denied_btn_ok), color = Color(0xFF94A3B8))
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
                                text = stringResource(R.string.dialog_notification_rationale_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.dialog_notification_rationale_text),
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
                                Text(stringResource(R.string.dialog_notification_rationale_btn_continue), fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showNotificationRationale = false
                                    launchOverlayServiceDirectly()
                                }
                            ) {
                                Text(stringResource(R.string.dialog_notification_rationale_btn_not_now), color = Color(0xFF94A3B8))
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

/**
 * Отдельная строка быстрого переключателя на главном экране в стиле полупрозрачной капсулы (pill).
 */
@Composable
private fun QuickControlPill(
    iconRes: Int,
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    pillBgColor: Color,
    pillBorderColor: Color,
    pillDividerColor: Color,
    switchColors: SwitchColors,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(pillBgColor, CircleShape)
            .border(1.dp, pillBorderColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!isChecked) }
            .padding(start = 18.dp, end = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(pillDividerColor)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = switchColors
            )
        }
    }
}
